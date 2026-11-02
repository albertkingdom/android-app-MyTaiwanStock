## 1. 準備（驗證方式）

- [ ] 1.1 從最新的 `rx` 切出分支 `feature/1.15/otc-stock-support`，並依「驗證方式」在 scratchpad 建立不進版控的 JVM 驗證專案（Kotlin Gradle plugin 1.9.24、languageVersion／apiVersion 1.6、Moshi 1.13.0、kotlinx-coroutines-core 1.6.4、OkHttp 與 mockwebserver 5.0.0-alpha.2、JUnit 4.13.2；main 指向 `stocklist/`（排除 `stocklist/android/`）與 `use_case/chart/`，test 指向對應的兩個 test 目錄，`stocklist.asset` 指向內建清單），驗證：`git branch --show-current` 顯示分支名稱；驗證專案能下載依賴並編譯（Maven Central 回 429 時以 `--max-workers=1` 重試；Gradle 8.14.3 與 KGP 1.9.24 不相容時改用 Gradle 8.7）。
- [ ] 1.2 移植產生腳本 `scripts/generate_stock_list.py`（輸出改為 `app/src/main/assets/StockList.json`，過濾規則與 iOS 相同），並把 iOS repo 的 `MyTaiwanStock/Resources/StockList.json` 複製為 `app/src/main/assets/StockList.json`，驗證：以 python 讀檔統計上市 1,380、上櫃 1,007，含 6488（otc）、00929、2330（tse），且沒有前兩碼為 70–73 的 6 碼代號；`python3 -m py_compile scripts/generate_stock_list.py` 通過。

## 2. 清單資料與載入（Build the list from official open data、Load the list from cache with a bundled fallback、Validate stock numbers by exact match）

- [ ] 2.1 先寫 `StockListParserTest`（「JSON 用 Moshi 的串流 API 手寫解析」）：權證排除範例（710000、700019、73000U、72400U 排除；6488、00679B、006201、00411A 保留為 otc）、範例表五列、同代號兩來源並存時保留 tse、前後空白被去除、空代號或空名稱被丟棄、非陣列或缺欄位時整體丟 IOException、未知 market 值解碼失敗、檔案格式 encode 後 decode 相同、結果依代號排序，驗證：在 JVM 驗證專案執行並確認失敗（類別不存在）。[after: 1.1]
- [ ] 2.2 實作 `StockMarket`、`StockListEntry`、`StockListParser`（「純邏輯放在不依賴 Android 的套件」），驗證：2.1 全部通過。[after: 2.1]
- [ ] 2.3 先寫 `StockListRepositoryTest`（移植 iOS `StockListRepositoryTests` 中與 App Group 無關的案例，「清單載入順序：快取，其次內建備援」、「狀態以不可變快照原子替換」）：有效快取被使用且 `hasCache` 為真；快取不存在、損毀、為空時使用內建清單且 `hasCache` 為假；`searchStrings` 含「6488 環球晶」；`market` 對 6488 回 OTC、未知回 null；`contains` 對 6488、2330 為真，對 "23" 與 "" 為假；快取含重複代號時 `market` 取第一筆；`replace` 後記憶體與新建的 repository 都讀到新清單；快取路徑是非空目錄時 `replace` 丟 IOException、記憶體不變；已有快取檔而暫存檔路徑是非空目錄時 `replace` 丟 IOException、記憶體不變、原快取檔 byte-identical；成功後與「快取路徑是非空目錄」失敗後目錄內都沒有 `*.tmp` 檔；`bundledSource` 丟例外時視為沒有內建清單（清單為空且不 crash）；以 `TestAssets`（先讀 system property `stocklist.asset`，否則從 `user.dir` 往上找 `app/src/main/assets/StockList.json`）載入真實清單，含 6488（otc）、00929、2330（tse），驗證：執行並確認失敗。[after: 2.2, 1.2]
- [ ] 2.4 實作 `StockListRepository`，驗證：2.3 全部通過。[after: 2.3]

## 3. 背景更新（Refresh the list in the background when stale、Protect the list from bad refresh results、Run at most one refresh at a time）

- [ ] 3.1 先寫 `StockListRefresherTest`（移植 iOS `StockListRefresherTests`，「更新策略：過期才更新，兩個市場獨立防呆，single-flight」；fetcher、時間、更新時間儲存皆注入，內建清單為上市 1,380＋上櫃 1,007）：6 天不更新且不呼叫 fetcher、7 天更新、沒有快取時即使剛更新也更新、快取損毀時 1 天前更新也更新、成功後第二次呼叫 SKIPPED；上櫃 700 筆不採用但上市 1,390 筆採用；806 筆採用；1,380 筆採用；空陣列兩市場都不採用且不記錄時間；逾時（IOException）與非 JSON 保留舊資料、不記錄時間、不建立快取；上櫃 fetcher 丟 IllegalStateException 時上市仍採用；快取路徑為非空目錄（寫入失敗）時回報兩市場 KEPT_OLD_DATA、清單不變、不記錄時間；同時呼叫兩次 `refreshIfNeeded`，以 `entered` 訊號確認第一個已進入 fetch 後再發第二個、再打開閘門，每個來源只抓一次且兩次結果相同；更新成功後 `repository.searchStrings` 立即反映新清單，驗證：執行並確認失敗。[after: 2.4, 1.2]
- [ ] 3.2 實作 `StockListRefresher`，驗證：3.1 全部通過，80% 防呆範例表四列各有對應斷言。[after: 3.1]
- [ ] 3.3 先寫 `OkHttpStockListFetcherTest`（mockwebserver）：200 回傳 body、500 丟 IOException、`OkHttpStockListFetcher.defaultClient()` 的 connectTimeoutMillis／readTimeoutMillis 為 30000 且 callTimeoutMillis 為 0，再實作 `OkHttpStockListFetcher`，驗證：先失敗後通過；`app/build.gradle` 加入 `testImplementation 'com.squareup.okhttp3:mockwebserver:5.0.0-alpha.2'`。[after: 3.2]
- [ ] 3.4 Android 端接線（「背景執行：WorkManager 一次性 unique work，排入點只在 `MainActivity.onStart`」）：`stocklist/android/SharedPreferencesRefreshTimeStore`、`stocklist/android/StockListRefreshWorker`（try/catch 包住 `refreshIfNeeded`，一律 `Result.success()`，有市場更新時以 Timber 記錄 `stock list refreshed: tse=N otc=M`）、`MyApplication` 持有 `stockListRepository` 與 `stockListRefresher` 並在 `onCreate` 背景預載、`MainActivity.onStart` 以 `ExistingWorkPolicy.KEEP` 與 `NetworkType.CONNECTED` 排入 `stock_list_refresh`，驗證：`grep -rn "enqueueUniqueWork(" app/src/main` 只有 `MainActivity` 一處；手動驗證（需裝置）：清除資料後開啟 app，logcat 出現一次 `stock list refreshed`，關閉網路時 app 正常、清單可搜尋。[after: 3.3]

## 4. 上櫃報價與小工具（Query quotes with the market prefix、Widget uses the same market lookup）

- [ ] 4.1 先寫 `StockMarketLookupTest`（「報價查詢依市場決定前綴，小工具直接讀 repository」）：6488 為 OTC、2330 為 TSE、9999 為 TSE；`exChannel` 範例表三列；`exChannelQuery(["6488","2330"])` 為 `otc_6488.tw|tse_2330.tw`；空清單為空字串；由 repository 的 `marketLookup()` 取得時結果相同，驗證：執行並確認失敗。[after: 2.4]
- [ ] 4.2 實作 `StockMarketLookup`，並把 `ListViewModel.getStockPriceInfoRx` 與 `UpdateWidgetPeriodicTask.fetchData` 的寫死前綴改為 `stockListRepository.marketLookup().exChannelQuery(...)`，驗證：4.1 通過；`grep -rn 'tse_\$' app/src/main/java` 沒有結果；手動驗證：清單加入 6488 後顯示即時價格，桌面小工具也顯示 6488 價格。[after: 4.1, 3.4]

## 5. 新增股票（Provide search strings for adding stocks、Validate stock numbers by exact match）

- [ ] 5.1 先寫 `StockSearchTest`：「環球」命中「6488 環球晶」、「23」命中含 23 的字串、空字串回傳全部；`codeFromInput(" 6488 環球晶")` 為 "6488"、`codeFromInput("00981a")` 為 "00981A"、`codeFromInput("")` 為 ""，再實作 `StockSearch.filter` 與 `StockSearch.codeFromInput`，驗證：先失敗後通過。[after: 2.4]
- [ ] 5.2 「新增股票改讀 repository，對話框精確比對」：`AddStockViewModel` 改持有 provider、每次篩選重新讀取並使用 `StockSearch.filter`，`init` 不再篩選；`AddStockFragment` 設定 provider 為 `stockListRepository.searchStrings` 後篩選一次；`AddStockDialogFragment` 改讀 repository、以 `StockSearch.codeFromInput` 取代號後精確比對、錯誤顯示在 `outlinedTextField` 且不關閉；`dialog_add_newstock.xml` 改 `inputType="text"`；刪除 `res/values/stockNo_suggestions.xml`，驗證：`grep -rn "countries_array" app/src` 沒有結果；手動驗證：搜尋「環球」看得到 6488 環球晶，對話框輸入 23 顯示「查無此股票代號」不關閉、輸入 00981A 與 6488 加入成功。[after: 5.1, 4.2]

## 6. K 線無資料（Show no data when candle data is unavailable）

- [ ] 6.1 先寫 `MovingAverageTest`（0、3、5、9、10 筆的 5 日與 10 日點數符合範例表，數值為視窗平均、容差 1e-4、x 為索引）與 `CandleRowsTest`（上個月為 null 時不 crash 並回傳本月列、兩個月都 null 回空清單、"--" 價格與欄位不足的列被丟棄、6 列含 1 列 "--" 剩 5 列、千分位逗號可解析、`priceChange("X0.00")` 為 0、`priceChange("+1.50")` 為 1.5、無法解析回 0），驗證：執行並確認失敗（重現 `data!!` 對 null 上月資料的 crash 情境：新函式不存在而編譯失敗）。[after: 1.1]
- [ ] 6.2 「K 線無資料與均線」：實作 `MovingAverage`、`CandleRows`；`CandleStickChartViewModel.getCandleStickData` 改用 try/catch 與 `CandleRows.merge`，空資料時設定 `candleDataUnavailable`；`CandleStickChartFragment` 顯示「無資料」、空清單不呼叫 `last()`、漲跌用 `CandleRows.priceChange`、均線用 `MovingAverage` 且只加入非空資料集；蠟燭、成交量、x 軸標籤與 `originalCandleData` 都由同一份 merge＋sanitize 後的清單產生；`CalculateChartDataUseCase.generateBarData` 改為去逗號後 `toFloat()`（不依裝置語系），驗證：6.1 通過；`grep -n "data!!" app/src/main/java/com/example/mynewsapp/ui/detail/CandleStickChartViewModel.kt` 沒有結果；手動驗證：開啟 6488 詳細頁顯示「無資料」，買賣紀錄與總覽正常。[after: 6.1]

## 7. 收尾

- [ ] 7.1 在 JVM 驗證專案執行全部測試並通過；PR 說明註明：合併前必須在有 Android SDK 的環境執行 `./gradlew testDebugUnitTest assembleDebug` 並通過（雲端無法執行），以及 spectra validate／analyze 未執行，驗證：JVM 測試報告 0 failures；PR 說明含上述兩點。[after: 3.4, 4.2, 5.2, 6.2]
