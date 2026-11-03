## Context

- 報價 API（`mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=...`）上市與上櫃共用，差別只在 `ex_ch` 的前綴。Android 目前在 `ListViewModel.getStockPriceInfoRx` 與 `UpdateWidgetPeriodicTask.fetchData` 寫死 `tse_`。
- 新增股票的清單是 `res/values/stockNo_suggestions.xml` 的 `countries_array`，只被 `AddStockFragment.onViewCreated` 與 `AddStockDialogFragment.onCreateDialog` 讀取。`AddStockFragment` 在 `onViewCreated` 把清單複製進 `AddStockViewModel.stockNames` 一次；`AddStockViewModel` 的 `init` 在清單設定前就呼叫 `filterSearchQuery`。
- `AddStockDialogFragment` 取輸入的第一段字串就加入，沒有任何驗證；輸入框 `input_stockNo` 是 `inputType="number"`，無法輸入 00981A 這類英文字尾代號。`AddStockFragment` 是從清單點星號加入，代號一定來自清單。
- `CandleStickChartViewModel.getCandleStickData` 對上個月回應做 `body()?.data!!`，上櫃股票（證交所 STOCK_DAY 回傳只有 `stat`）會 NPE；請求丟出例外（無網路）時，例外在 `viewModelScope.launch(Dispatchers.IO)` 中未被捕捉。`CandleStickChartFragment` 的 `originalCandleData` 觀察者對空清單呼叫 `data.last()`，`initOpenCloseHighLowValue` 對 "X0.00" 呼叫 `toFloat()`，`CalculateChartDataUseCase` 對 "--" 價格呼叫 `toFloat()`，都會 crash。
- 小工具的更新由 `ListViewModel.setupWorkManagerForUpdateWidget` 排入 `UpdateWidgetPeriodicTask`（CoroutineWorker，在 app process 內執行），輸入資料是股號 JSON 陣列。
- iOS 參考實作：`StockMarketLookup.swift`、`StockListParser.swift`、`StockListRepository.swift`、`StockListRefresher.swift`、`ChartService.swift`、`scripts/generate_stock_list.py` 與對應測試。
- 雲端開發環境無法連到 dl.google.com（Android SDK 與 Google Maven 的檔案都在這裡）與證交所、櫃買中心網站，無法執行 `./gradlew assembleDebug`／`testDebugUnitTest`，也無法重新產生清單。

## Goals / Non-Goals

**Goals:**

- 上櫃股票在清單頁與小工具都有即時報價。
- 新增股票可搜尋到上櫃股票與新 ETF；對話框輸入的代號必須精確存在於清單。
- 清單會自動更新、不會被壞資料覆蓋、永不為空。
- 上櫃股票與資料不完整的股票詳細頁不 crash，K 線區無資料時顯示「無資料」。
- 純邏輯有 JVM unit test，移植自 iOS 測試。

**Non-Goals:**

- 上櫃 K 線（櫃買中心歷史資料端點需另行調查，與 iOS 相同另開變更）、興櫃、權證。
- iOS 的 App Group 市場對照檔（Android 小工具的 worker 跑在 app 同一個 process）。
- 定期背景更新（PeriodicWork）：與 iOS 一致，只在 app 回到前景時檢查。
- Room／Firestore schema 變更；`db/InvestHistory.kt`、`ui/detail/AddHistoryFragment.kt`、設定頁（由手續費與稅變更負責）。
- Gradle／Kotlin／AGP 升級（build-modernization 負責）；版號遞增（多個 1.15 變更並行，由發版時處理，避免衝突）。

## Decisions

### 純邏輯放在不依賴 Android 的套件

`stocklist/` 下的 `StockMarket`、`StockListEntry`、`StockListParser`、`StockListRepository`、`StockListRefresher`、`StockMarketLookup`、`StockSearch`、`OkHttpStockListFetcher`，以及 `use_case/chart/` 下的 `MovingAverage`、`CandleRows`，只依賴 Kotlin 標準函式庫、kotlinx.coroutines、Moshi、OkHttp（皆已是 app 的依賴，均可從 Maven Central 取得）。用到 Android 的接線放 `stocklist/android/`（`SharedPreferencesRefreshTimeStore`、`StockListRefreshWorker`）。檔案存取用 `java.io.File`／`java.nio.file`，內建清單用 `() -> InputStream?` 注入，時間用 `() -> Long` 注入，更新時間用 `RefreshTimeStore` 介面注入。替代方案是 Robolectric 測 `Context` 版本，但會增加依賴且雲端環境無法下載。

### JSON 用 Moshi 的串流 API 手寫解析

不使用 `@JsonClass` codegen 或反射 adapter：不受 KSP 設定影響（`app/build.gradle` 的 KSP 與 Kotlin 版本目前不一致，由 build-modernization 處理），也沒有 minify 的反射風險；只讀需要的兩個欄位，其他欄位用 `skipValue()` 跳過。下載內容整份讀成 String 再解析（櫃買中心約 4.6 MB，iOS 端量測），記憶體峰值約為字串大小的數倍，可接受；不以省記憶體為理由。`org.json` 在 JVM unit test 中是空殼，不採用。

### 清單載入順序：快取，其次內建備援

與 iOS 相同。快取在 `context.filesDir/StockList.json`，內建清單在 `assets/StockList.json`。內建清單直接沿用 iOS repo 於 2026-10-26 的 `StockList.json`（同樣由兩個官方來源以相同過濾規則產生，上市 1,380、上櫃 1,007，已檢查不含 70–73 開頭的 6 碼代號）；同時移植產生腳本，之後在可連線的環境執行 `python3 scripts/generate_stock_list.py` 更新。與 iOS 不同處：`hasCache` 定義為「快取解碼成功且非空」，損毀的快取不會壓住更新 7 天。

### 狀態以不可變快照原子替換

iOS 用 main actor 保證讀寫順序；Android 的讀取來自 UI thread、WorkManager thread、Rx io thread。`StockListRepository` 把 `entries`、`marketByCode`、`searchStrings`、`fromCache` 包成一個不可變的 `Snapshot`，存在 `@Volatile` 欄位；`replace` 用 `synchronized` 序列化，先寫同目錄的暫存檔 `StockList.json.tmp`（寫完 `fd.sync()`），再以 `Files.move(tmp, cache, ATOMIC_MOVE, REPLACE_EXISTING)` 取代，失敗時盡力刪除暫存檔（`tmp.delete()`，忽略結果，不得蓋掉原本的例外）並丟 `IOException`，成功後才替換快照。讀取不加鎖，且不會看到新舊混合。

### 更新策略：過期才更新，兩個市場獨立防呆，single-flight

與 iOS 相同：沒有有效快取或距上次成功更新 ≥ 7 天才更新；每個市場的抓取結果非空且 ≥ 目前筆數 × 0.8 才採用；至少一個市場被採用才寫快取並記錄時間；寫入失敗時兩個市場都回報 `KEPT_OLD_DATA`。兩個市場以 `async` 並行抓取，每個市場各自 `catch (e: Exception)`（`CancellationException` 重拋），所以單一市場的任何例外不會影響另一市場。single-flight：`synchronized(lock)` 內若已有 in-flight `Deferred` 就沿用；否則判斷是否過期，需要時以 `CoroutineStart.LAZY` 建立 `async` 並登記，離開鎖後才 `start()`；完成時（`invokeOnCompletion`）在同一把鎖內、僅在登記的仍是自己時清除。refresh 跑在 refresher 自己的 `CoroutineScope(SupervisorJob() + dispatcher)`，呼叫者被取消不會中斷進行中的更新。

### 背景執行：WorkManager 一次性 unique work，排入點只在 `MainActivity.onStart`

失敗（兩市場都未採用或寫入失敗）時記錄 `lastFailedAttemptMillis`，24 小時內不再嘗試，避免每次回到前景都重新下載數 MB（iOS 沒有這個保護；24 小時為取捨，非量測值）；成功時清除。更新時間以 `commit()` 同步寫入，與快取檔一致。`MainActivity.onStart` 一律以 `enqueueUniqueWork("stock_list_refresh", ExistingWorkPolicy.KEEP, ...)` 排入 `StockListRefreshWorker`（`NetworkType.CONNECTED`），主執行緒不讀檔也不讀 SharedPreferences。worker 在背景呼叫 `refreshIfNeeded()`（未過期時立即回傳 skipped），以 try/catch 包住，一律回傳 `Result.success()`（失敗不重試、不顯示錯誤，下次回到前景再判斷）。不用 `ProcessLifecycleOwner`，因為那需要新增 `lifecycle-process` 依賴；旋轉螢幕造成的重複 `onStart` 由 KEEP 與過期判斷吸收。下載使用 OkHttp，`connectTimeout` 與 `readTimeout` 各 30 秒（對應 iOS 的 `timeoutInterval` 為閒置逾時），另設 `callTimeout` 5 分鐘作為上限（4.6 MB 約需 15 KB/s，估計值）；以 `enqueue` 加 `suspendCancellableCoroutine` 實作，協程取消時呼叫 `call.cancel()`，下載卡住不會讓 in-flight 永遠不結束；非 200 丟 `IOException`。

### 報價查詢依市場決定前綴，小工具直接讀 repository

`StockMarketLookup(marketByCode)` 提供 `market(code)`、`exChannel(code)`、`exChannelQuery(codes)`。`StockListRepository.marketLookup()` 回傳目前快照的 lookup：目前清單的市場優先，目前清單沒有的代號用內建清單的市場（每日快照可能缺少停牌的追蹤股，否則會被當成上市而查不到報價），兩者都沒有才用 TSE。repository 每次 `reload` 都解析內建清單以建立這份備援對照。`ListViewModel`（在 Rx io thread 組查詢字串）與 `UpdateWidgetPeriodicTask`（在 worker thread）透過 `MyApplication.stockListRepository` 取得。

### 新增股票改讀 repository，對話框精確比對

`StockSearch.filter(strings, query)` 是 `contains` 判斷的純函式；`StockSearch.codeFromInput(text)` 先 trim、取第一個空格（" "）前的字串並以 `uppercase()` 轉大寫（00981a 視為 00981A）。`AddStockViewModel` 改為持有 `stockNamesProvider: () -> List<String>`，每次 `filterSearchQuery` 都重新讀取；`init` 不再篩選，由 `AddStockFragment` 設定 provider 為 `{ stockListRepository.searchStrings }` 後呼叫一次。`AddStockDialogFragment` 的自動完成來源同上；輸入框改 `inputType="textCapCharacters"`；按下「新增」時 以 `codeFromInput` 取代號，`stockListRepository.contains(code)` 為真才加入並關閉，否則在 `TextInputLayout`（`outlinedTextField`）顯示「查無此股票代號」且不關閉（以 `setOnShowListener` 覆寫正向按鈕的 click listener，避免 AlertDialog 自動關閉）。

### K 線無資料與均線

`CandleRows.merge(lastMonth: List<List<String>>?, currentMonth: List<List<String>>?)` 把 null 當成空清單合併，再以 `CandleRows.sanitize` 丟掉欄位不足或開高低收、成交股數不是數字的列。`CandleRows.priceChange(text)` 把 "X0.00"、"+1.5" 等文字轉成數字，無法解析時回 0。`CandleStickChartViewModel.getCandleStickData` 以 try/catch 分別包住兩個請求（失敗或非 2xx 視為 null），合併後為空時把 `candleDataUnavailable` 設為 true 且不產生圖表資料；只有一個月失敗時顯示另一個月的資料（之前會整個不顯示或 crash）。蠟燭、成交量、x 軸標籤與 `originalCandleData` 都由同一份 merge＋sanitize 後的清單產生，點擊圖表與買賣紀錄定位用的索引才會一致；`CalculateChartDataUseCase.generateBarData` 改為去逗號後 `toFloat()`，與 `sanitize` 的判斷一致且不依裝置語系。Fragment 觀察到 `candleDataUnavailable` 時呼叫 `chart.clear()` 並 `setNoDataText("無資料")`；`originalCandleData` 遇到空清單直接返回。均線抽成 `MovingAverage.compute(closes, window): List<Pair<Int, Float>>`，筆數不足回傳空清單；fragment 只把非空的均線加入 `LineData`，兩條都空時不設定 line data。

### 驗證方式

- 純邏輯：`app/src/test/java/com/example/mynewsapp/stocklist/` 與 `app/src/test/java/com/example/mynewsapp/use_case/chart/` 的 JUnit 4 測試。由於雲端環境無法下載 Android SDK 與 AGP，另建暫存的獨立 Kotlin/JVM Gradle 專案（不進版控）：Kotlin Gradle plugin 1.9.24、`languageVersion`／`apiVersion` 1.6（app 的 Kotlin plugin 是 1.6.21）、Moshi 1.13.0、kotlinx-coroutines-core 1.6.4、OkHttp／mockwebserver 5.0.0-alpha.2、JUnit 4.13.2，source set 指向上述兩個 main 目錄（排除 `stocklist/android/`）與兩個 test 目錄，並以 system property `stocklist.asset` 指向 `app/src/main/assets/StockList.json`。正式指令 `./gradlew testDebugUnitTest assembleDebug` 需在有 Android SDK 的環境執行，是合併前的必要關卡。
- Android 端（Fragment、Worker、ViewModel）：無法在雲端建置，列為手動驗證項目（PR 測試計畫）。

## Implementation Contract

- 語言限制：只用 Kotlin 1.6 可用的語法與標準函式庫（例如用 `values()` 不用 `entries`，不用 `..<`、`data object`）。
- `enum class StockMarket(val prefix: String) { TSE("tse"), OTC("otc") }`；JSON 值為 `"tse"`／`"otc"`，其他值解碼失敗。
- `data class StockListEntry(val code: String, val name: String, val market: StockMarket) { val searchString: String get() = "$code $name" }`
- `object StockListParser { fun parseTwse(json: String): List<StockListEntry>; fun parseOtc(json: String): List<StockListEntry>; fun isKeptOtcCode(code: String): Boolean; fun merge(tse: List<StockListEntry>, otc: List<StockListEntry>): List<StockListEntry>; fun encodeFile(entries: List<StockListEntry>): String; fun decodeFile(json: String): List<StockListEntry> }`；格式錯誤丟 `IOException`（含 Moshi 的 `JsonDataException`／`JsonEncodingException` 轉成的 `IOException`）。
- `class StockListRepository(cacheFile: File, bundledSource: () -> InputStream?)`：建構時載入；`bundledSource` 丟例外或回 null、內容無法解碼時視為沒有內建清單（不丟例外）；`entries`、`searchStrings`、`hasCache`（目前快照來自快取）、`market(code): StockMarket?`、`contains(code)`、`marketLookup()`、`replace(newEntries)`（失敗丟 `IOException`，記憶體與原快取檔不變、不留暫存檔）、`reload()`。重複代號以第一筆為準。
- `class StockListRefresher(repository, fetcher: StockListFetching, timeStore: RefreshTimeStore, now: () -> Long = System::currentTimeMillis, dispatcher: CoroutineDispatcher = Dispatchers.IO)`：`isRefreshDue()`、`suspend refreshIfNeeded(): RefreshResult`；`data class RefreshResult(tse: MarketOutcome, otc: MarketOutcome)`，`MarketOutcome { UPDATED, SKIPPED, KEPT_OLD_DATA }`；常數 `REFRESH_INTERVAL_MILLIS = 7 天`、`MINIMUM_RATIO = 0.8`。refresher 本身不寫 log；worker 依結果與 `repository.entries` 以 Timber 記錄 `stock list refreshed: tse=N otc=M`（至少一個市場 UPDATED 時）。
- `interface StockListFetching { suspend fun fetchTwse(): String; suspend fun fetchOtc(): String }`；`interface RefreshTimeStore { var lastRefreshMillis: Long?; var lastFailedAttemptMillis: Long? }`；常數 `FAILED_ATTEMPT_BACKOFF_MILLIS = 24 小時`。
- `class OkHttpStockListFetcher(client: OkHttpClient = defaultClient(), twseUrl, otcUrl)`，`companion fun defaultClient()` 回傳 connect／read 30 秒、callTimeout 5 分鐘的 client；協程取消時取消 HTTP call：在 `Dispatchers.IO` 執行，非 200 丟 `IOException`。
- `class StockMarketLookup(marketByCode: Map<String, StockMarket>)`：`market(code)`（未知回 TSE）、`exChannel(code)`、`exChannelQuery(codes)`。
- `object StockSearch { fun filter(strings: List<String>, query: String): List<String>; fun codeFromInput(text: String): String }`：`contains` 比對，空字串回傳全部；`codeFromInput` 為 trim、取第一個空格前、以 `uppercase()` 轉大寫。
- `object MovingAverage { fun compute(closes: List<Float>, window: Int): List<Pair<Int, Float>> }`，第 i 點（i ≥ window−1）為 closes[i−window+1..i] 的平均，x 為 i。
- `object CandleRows { fun merge(lastMonth: List<List<String>>?, currentMonth: List<List<String>>?): List<List<String>>; fun sanitize(rows): List<List<String>>; fun priceChange(text: String): Float }`；STOCK_DAY 列格式：0 日期、1 成交股數、2 成交金額（成交量長條使用）、3 開、4 高、5 低、6 收、7 漲跌，數字可含千分位逗號。
- 失敗模式：refresher 與 worker 不讓任何例外（`CancellationException` 除外）離開；UI 不顯示更新錯誤。

## Risks / Trade-offs

- [Risk] 無法在雲端建置，Android 端程式碼未經編譯器檢查 → PR 說明列出合併前必須在本機執行的 `./gradlew assembleDebug testDebugUnitTest` 與手動驗證步驟；build-modernization 合併後再 rebase 確認。
- [Risk] 內建清單非本次重新產生，資料日期為 2026-10-26 → 第一次啟動沒有快取，背景更新立即執行；之後可重跑腳本。
- [Risk] UI 第一次讀取 repository 時等待背景預載 → `MyApplication.onCreate` 以背景執行緒預先載入（`by lazy` 同步鎖）；預估解析 170 KB JSON 在中階手機約數十毫秒（未量測，可在 debug build 以 Timber 記錄載入耗時確認）。
- [Risk] 一個月份請求失敗時只顯示部分 K 線 → 刻意選擇：比整個空白或 crash 好，且下次進入頁面重抓。
