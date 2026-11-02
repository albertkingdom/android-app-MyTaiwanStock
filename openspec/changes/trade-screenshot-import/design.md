## Context

iOS 版的實作是這次移植的依據（albertkingdom/Ios-App-MyTaiwanStock 預設分支）：

- 解析：`MyTaiwanStock/Screens/TradeImport/Services/TradeScreenshotParser.swift`，輸入 Vision 的文字框（正規化座標，原點在左下角，y 越大越上面）。
- 名稱對代號：`Services/StockNameResolver.swift`。
- 預覽狀態與寫入：`ViewModel/TradeImportViewModel.swift`、`Services/DefaultTradeImportStore.swift`。
- 測試資料：`MyTaiwanStockTests/Screens/TradeImport/TradeScreenshotFixtures.swift`（從真實截圖抄下的像素框）、`TradeScreenshotVisionFixtures.swift`（Vision 實際回傳的文字框）。

規格不完整的地方以 iOS 原始碼為準。

Android 現況（`rx` 分支）：

- 手動新增紀錄在 `ui/detail/AddHistoryFragment.kt`：組出 `InvestHistory(0, stockNo, date, price, amount, status)`，呼叫 `NewsRepository.insertHistory` 寫 Room，再呼叫 `FirebaseManager.uploadHistoryToOnlineDB` 上傳（未登入時該函式直接返回）。`date` 是毫秒時間戳，`price` 是 Double，`status` 0 買 1 賣，`amount` 是 Int（Room 欄位 INTEGER）。
- 加入追蹤清單在 `ui/list/AddStockFragment.kt`：`ListViewModel.addToStockList` 呼叫 `NewsRepository.upsert(Stock(stockNo, followingListId, "0"))`，再呼叫 `FirebaseManager.uploadNewStockNoToOnlineDB(stockNo, listName)`。
- `Stock` 的主鍵只有 `stockNo`（`db/Stock.kt`），所以同一檔股票在本機只會屬於一個清單；`upsert` 用 REPLACE，對已在別的清單的股票呼叫會把它搬到新清單。
- 目前顯示的清單 id 在 activity 範圍的 `ListViewModel.currentSelectedFollowingListId`；清單由 `NewsRepository.allFollowingList`（Flow）提供。清單資料以 Room 的 Flowable 驅動，寫入後列表頁會自動更新。
- 股票名錄只有 `res/values/stockNo_suggestions.xml` 的 `countries_array`，每筆是「代號 空白 名稱」，例如 `0050 元大台灣50`、`00757 統一FANG+`。
- 導覽：`res/navigation/nav_graph.xml` include `stock.xml`；`stock.xml` 的起點是 `stockListFragment`。底部分頁的 item id `stock` 對應這個子圖。
- `MainActivity` 是唯一的 Activity（launcher，standard launchMode）。

## Goals / Non-Goals

**Goals:**

- 解析邏輯與 iOS 行為一致，並以移植的 iOS 測試資料在 JVM unit test 驗證。
- 從 Photo Picker 或分享選單進來的圖片，走同一個辨識、預覽、確認、寫入流程。
- 圖片與文字只在裝置上處理。

**Non-Goals:**

- 手續費與交易稅：`feature/1.15/trade-fee-and-tax` 尚未合併進 `rx`，`InvestHistory` 沒有費稅欄位，本 change 不顯示、不估算、不寫入費稅。待辦（費稅 change 合併後另開 change）：預覽與編輯加入費稅估算與手動修改、寫入時填入費稅，對齊 iOS 的「Estimate fee and tax in the preview」。
- 不支援整股、融資、融券、當沖、定期定額：列出但標示不支援。
- 不上傳圖片或辨識文字，不做雲端備份。
- iOS 的待匯入暫存區、過期刪除與前景詢問。
- 上櫃股票名錄（OTC＋股票名錄 change）。
- 改 Room schema。
- 版號調整。

## Decisions

### 範圍

In scope：`ui/tradeimport/` 新套件、`stock.xml` 新增目的地、列表頁加一個入口按鈕、`MainActivity` 處理分享 intent、manifest 的 intent filter、`app/build.gradle` 兩個依賴、字串資源。

Out of scope：`db/` 底下任何檔案、`ListViewModel` 的邏輯、其他分頁、費稅、`versionName`／`versionCode`（版號由同為 1.15 的其他 change 協調調整，本 change 不改，避免衝突）。

替代方案：把入口與流程寫進 `ui/list/`。不採用，因為列表頁由其他 change 同時修改，集中在新套件可減少衝突。

### 平台無關的核心放在 `ui/tradeimport/core`

`core` 套件只依賴 Kotlin 標準函式庫與 `java.time`、`java.text.Normalizer`，不 import 任何 `android.*`、`androidx.*`，讓 `app/src/test` 的 JVM test 直接測。內容：

- `TradeImportModels.kt`：`TextBox(text, x, y, width, height)`，座標沿用 Vision 慣例（正規化 0 到 1，原點左下，y 越大越上面），讓 iOS 測試資料原封不動移植；`TradeSide`（BUY=0、SELL=1，與 `InvestHistory.status` 相同）；`ParsedTrade(stockName, date: LocalDate?, price: Double?, amount: Int?, side, tradeTypeText, isSupportedType)`；`TAIPEI = ZoneId.of("Asia/Taipei")`。
- `TradeScreenshotParser.kt`：逐行移植 iOS 的 `TradeScreenshotParser`，包含略過資料欄右側只有箭頭（V、v、^、∨、∧、⌄、⌃）的行、不存在的日期（例如 02/30）不產生日期。每個框的文字先做 NFKC 正規化（全形數字與全形＋變半形）。這是刻意與 iOS 不同的地方：iOS 會把「１２」當成數字欄位但轉換失敗得到空值，Android 正規化後能正確讀出 12，去空白用 `Char.isWhitespace()`（含全形空白與換行）。正規表示式改寫成 Java 語法。價格用 `Double`（`InvestHistory.price` 是 Double）。日期用 `LocalDate`（台北的日曆日）。測試資料的像素換算維持 iOS 的運算順序（`1 - y1 / h`），讓 0.03 邊界的比較結果與 iOS 相同。
- `TextLineSplitter.kt`：把辨識器的一行拆成多個框（見「文字辨識」）。
- `StockNameResolver.kt`：移植 iOS 版。正規化用 `Normalizer.Form.NFKC`、移除所有空白、把長破折號類字元（U+2013、U+2014、U+2015、U+2500、U+30FC）換成「一」；找不到時才把 ASCII 連字號試著換成「一」；同名多檔或空名稱時不解析。
- `StockDirectoryParser.kt`：把名錄的「代號 名稱」字串在第一個空白切開（名稱本身可含空白，例如 `00739 元大MSCI A股`），轉成 `StockEntry`。
- `ShareIntentImages.kt`：`fun <T> select(action: String?, mimeType: String?, single: T?, multiple: List<T>?): List<T>`，泛型讓 JVM test 不需要 `android.net.Uri`。
- `TradeImportSession.kt`：移植 iOS `TradeImportViewModel` 的非 UI 部分（不含費稅），見「狀態規則」「寫入的日期」「加入清單」。

替代方案：解析器直接吃 ML Kit 的 `Text` 物件。不採用，因為 ML Kit 類別無法在 JVM test 建構，iOS 的測試資料也無法移植。

### 狀態規則（與 iOS 相同，去掉費稅）

狀態依序判斷：不支援 → 不完整 → 重複 → 可匯入。不完整：代號為空或不在名錄、日期為空、價格為空或不大於 0、股數不在 1 到 32767。股數上限沿用 iOS 的 Int16 上限：Firestore 的紀錄會被 iOS 讀取，維持同一個範圍避免 iOS 端溢位。

重複：代號、台北日曆日、價格、股數、方向都相同；比對對象是既有紀錄與同一批前面的交易。價格以 `Float` 比對（兩邊都轉成 Float 再比較）：iOS 上傳到 Firestore 的價格是 Float 轉 Double（例如 112.55 變成 112.55000305175781），同步到 Android 的紀錄若用 Double 直接比會漏判；iOS 本身就以 Float 比對，結果一致。建立時預設只勾選「可匯入」；編輯後狀態改變時，變成可匯入就勾選，變成其他狀態就取消勾選，狀態沒變則保留使用者的選擇。只有「可匯入」與「重複」能被勾選。

時區：手動新增的紀錄是裝置本地時間，重複判斷用台北日曆日。裝置不在 UTC+8 時，晚上手動輸入的紀錄可能落在台北的隔天而不被判為重複；這與 iOS 相同，記錄為已知限制。

編輯輸入規則：代號去頭尾空白並轉大寫（例如 00635U），空字串視為沒有代號；價格與股數去頭尾空白與千分位逗號後解析，無法解析就視為沒有值（交易變成不完整）；日期用日期挑選器，不接受文字輸入。

替代方案：價格以 Double 精確比對。不採用，原因如上。

### 寫入的日期

截圖只有日期。寫入時間為該日台北時間 12:00:00.000；若這個毫秒時間戳已被既有紀錄或同一批前面的交易用掉，就加 1 毫秒直到沒被用過。已用時間戳在按下匯入時重新讀取，不用開預覽時的快照，避免預覽期間 Firestore 同步寫入的紀錄撞到同一個時間戳。

替代方案：寫入當下時間。不採用，日期會錯；也不寫 00:00，因為在台灣以外的時區顯示時容易跨日。

### 加入清單（Android 的資料模型限制）

清單以 `ListChoice(id: Int, name: String)` 傳入核心，因為清單名稱沒有唯一限制，而 `Stock` 需要清單 id；名稱只用於 Firestore 的上傳。預覽頁的選項是所有清單加「不加入清單」。預設：目前列表頁顯示的清單 id 若存在於清單中就選它，否則選第一個清單，沒有任何清單時選「不加入清單」（與 iOS 相同）。清單在開預覽時從 Room 直接讀取（`NewsRepository.allFollowingList.first()`），不依賴 `ListViewModel` 是否已載入；目前清單 id 從 activity 範圍的 `ListViewModel.currentSelectedFollowingListId` 讀，冷啟動時是 0，不會匹配，因此退回第一個清單。

因為 `Stock` 的主鍵只有代號，同一檔股票在本機只能在一個清單。確認匯入時，對每個不重複的已匯入代號：若它已在任何清單（包括選定的清單）就不動它，否則加入選定清單。「已在任何清單的代號」在按下匯入時重新讀取。這和 iOS 的差別是：已在別的清單時 iOS 會加入，Android 不搬移，避免從原清單消失；紀錄本身不屬於任何清單，仍能從原清單打開。選「不加入清單」時顯示提示「匯入的紀錄只能從含有該股票的清單打開」。

列表頁自動建立的「Default」清單沒有上傳到 Firestore（既有行為），對它上傳加入股票不會有效果；本 change 不處理。

替代方案：改 `Stock` 主鍵讓同一股票可在多個清單。不採用，需要 Room migration，超出本 change 範圍且會和其他 change 衝突。

### 寫入流程

`TradeImportRepository`（Android 端）負責：

- 讀快照：`NewsRepository.allHistory.first()`、`StockDao.getAllStocks().first()`、`NewsRepository.allFollowingList.first()`，在 IO dispatcher 執行。
- 寫入：整個「本機寫入＋上傳」是一個在 app 範圍執行的工作，畫面關閉不會取消它。app 範圍是 `ui/tradeimport/TradeImportScope.kt` 的 `object TradeImportScope { val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }`（`MyApplication` 不修改）。工作內依序：對計畫裡每筆紀錄呼叫 `NewsRepository.insertHistory(InvestHistory(0, stockNo, date, price, amount, status))`，緊接著呼叫 `FirebaseManager.uploadHistoryToOnlineDB(...)`；對每個要加入的代號呼叫 `NewsRepository.upsert(Stock(stockNo, listId, "0"))`，緊接著在同一個 scope 另起一個工作呼叫 suspend 的 `FirebaseManager.uploadNewStockNoToOnlineDB(stockNo, listName)`。本機寫入全部完成時完成一個 `CompletableDeferred<Int>`（筆數），ViewModel 只等待這個結果再 Toast 並離開畫面；ViewModel 被清除時等待被取消，但 app 範圍的工作照常完成，上傳不會被略過。本機寫入丟出例外（例如儲存空間不足）時以 `completeExceptionally` 結束這個 deferred，ViewModel 清除「匯入中」狀態並顯示錯誤，已寫入的紀錄保留。和手動新增一樣不等待 Firestore 結果。
- 連點保護：`TradeImportViewModel` 先呼叫 `buildImportPlan`，成功後才設定「匯入中」狀態並啟動寫入；`buildImportPlan` 丟出 `InvalidSelectionException` 時不設定，使用者修正後可以再確認。匯入中按鈕停用並忽略再次確認；同一個預覽只會執行一次寫入，完成後不能再確認。
- 費稅待辦：費稅 change 合併後，在這裡填入費稅欄位，並在 `TradeImportSession` 加入估算。

替代方案：在 fragment 範圍的 `viewModelScope` 內寫入。不採用：返回列表會清掉 ViewModel 並取消寫入，造成寫一半。在 `db/` 新增批次寫入的 DAO 方法也不採用，避免動到其他 change 擁有的檔案。

### 文字辨識：ML Kit 中文模型（打包版）

依賴 `com.google.mlkit:text-recognition-chinese:16.0.1`（模型打包在 APK，離線可用）。APK 增加的大小尚未量測，實作後以 `./gradlew assembleDebug` 前後的 APK 大小比較確認並寫進 PR。

`TradeTextRecognizer`：`InputImage.fromFilePath(context, uri)` 讀圖（它會依 EXIF 旋轉，回傳的框對應轉正後的影像；寬高取轉正後的影像大小：`rotationDegrees` 為 90 或 270 時互換 `InputImage.width/height`，實作時以一張旋轉過的圖手動確認），`TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())` 辨識，用完呼叫 `close()`。讀圖的 `IOException`、`SecurityException`（URI 權限失效）與 `OutOfMemoryError`（超長截圖）都轉成辨識錯誤。一次只辨識一張。

分行粒度：ML Kit 的 `Text.Line` 可能把同一列的多個欄位合併成一行（例如「2026/ 元大台灣50 112.55 -563」），解析器去空白後會得到無法解析的字串。因此不直接用 Line，而是用 Line 底下的 `Text.Element`，交給 core 的 `TextLineSplitter.split(elements)`：`split` 先把同一行的 element 依 `left` 排序（ML Kit 不保證順序），相鄰 element 的水平間距不超過該行高度的 0.5 倍時合併成一個框（文字以一個空白相接），否則分成不同的框。這樣欄位之間的大間距會切開，名稱內的小間距會保留；解析器本身也能處理合併後的「2026/元大台灣50」與「09/30 盤中零股買進」。座標轉換 `toTextBox(text, left, top, right, bottom, width, height)` 也放在 core：`x = left / w`、`y = 1 - bottom / h`、`width = (right - left) / w`、`height = (bottom - top) / h`。

取得 ML Kit 實際輸出：debug 版（`BuildConfig.DEBUG`）的預覽頁選單多一個「複製辨識結果」，把拆框之前的原始輸入（每張圖的寬高與每一行的 `PixelElement` 清單）以 Kotlin 測試資料的格式（可直接作為 `TextLineSplitter.split(...)` 的輸入）複製到剪貼簿，讓測試可以從拆框到解析整段重跑，也能用來調整 0.5 倍行高的門檻，讓開發者用真實截圖產生 JVM 測試資料。release 版沒有這個選單，也不寫任何 log。這是規格「不記錄辨識文字」的唯一例外，由使用者在 debug 版主動觸發，資料只到裝置本機的剪貼簿。

替代方案：Play services 非打包版。APK 較小但第一次使用需下載模型，離線時失敗。直接用 `Text.Line` 不拆分也不採用，原因如上。

### 流程與畫面

- 新目的地 `tradeImportFragment` 加在 `res/navigation/stock.xml`，參數 `imageUris`（string array，可為 null）。另加 global action `action_global_tradeImportFragment`。
- 列表頁浮動按鈕展開的按鈕組多一個「截圖匯入」，點了導到 `tradeImportFragment`（不帶圖片）。
- `TradeImportViewModel`（fragment 範圍，使用 `SavedStateHandle`）保存要辨識的 URI 清單與「挑選器是否已開過」。
- `TradeImportFragment` 第一次進入時：有 `imageUris` 參數就開始辨識；沒有就開 `PickMultipleVisualMedia(ImageOnly)`，取消就返回列表。
- 旋轉螢幕：ViewModel 還在，不重新辨識也不重開挑選器。
- 程序被回收後還原：ViewModel 從 `SavedStateHandle` 取回 URI。已有 URI 就重新辨識（URI 權限失效時會得到辨識錯誤，顯示錯誤並返回列表）；挑選器開過但還沒有 URI 時，Activity Result API 會在 fragment 進入 STARTED 時送回挑選結果，因此在 `onResume` 檢查：挑選器已開過、沒有保存的 URI、也沒有收到結果，就返回列表。預覽中的編輯不保存，還原後重新辨識得到原始結果。
- 結果：所有圖片都沒有任何交易列 → 對話框「找不到可匯入的交易」，按確定返回；只要有交易列（即使全部不支援或不完整）就顯示預覽，與 iOS 相同。讀圖或辨識失敗 → 對話框顯示錯誤，按確定返回，不寫任何紀錄；只要有一張失敗就整批視為失敗，與 iOS 相同。
- 預覽：RecyclerView 每列顯示名稱、代號、方向、股數、價格、日期、狀態文字，可勾選；點列開 `TradeEditDialog`（代號、價格、股數、日期挑選器、買賣切換）。上方是清單選擇（下拉選單），選「不加入清單」時顯示提示。底部「匯入」按鈕在沒有勾選或匯入中時停用。寫入完成後 Toast「已匯入 N 筆」，以 `popBackStack(R.id.stockListFragment, false)` 回到列表頁（從分享進來時堆疊上可能還有其他畫面）。

替代方案：用新的 Activity 承載匯入流程。不採用，現有畫面都是 Navigation 的 fragment，沿用同一個返回堆疊較一致。

### 分享 intent

`AndroidManifest.xml` 在 `MainActivity` 加兩個 intent filter：`ACTION_SEND` 與 `ACTION_SEND_MULTIPLE`，`mimeType="image/*"`，category DEFAULT。`MainActivity` 維持 standard launchMode：從其他 App 分享時，系統會在分享來源 App 的工作堆疊上開一個新的 `MainActivity`；匯入完成後停在這個新實例的列表頁，從列表頁按返回回到分享來源。不改成 `singleTask`，避免影響桌面小工具與啟動器既有的開啟行為。

`MainActivity.onCreate`（`savedInstanceState == null` 時）呼叫 `handleShareIntent`：用 `IntentCompat.getParcelableExtra`／`getParcelableArrayListExtra` 取出 `EXTRA_STREAM`，沒有時改讀 `ClipData` 的 URI；交給 `ShareIntentImages.select` 過濾；有 URI 時把底部分頁切到 `stock`，再以 global action 開 `tradeImportFragment` 並帶入 URI 字串。處理過的 intent 換成空的 `Intent()`，避免旋轉後重複開啟。`onNewIntent` 也呼叫同一個函式（standard launchMode 下很少發生）；若此時返回堆疊上已有 `tradeImportFragment` 且它尚未開始寫入，以 `popUpTo(tradeImportFragment, inclusive = true)` 取代它，舊的匯入不寫入任何紀錄；若它正在寫入，不取代，忽略這次分享並以 Toast 提示「匯入進行中，請稍後再分享」。URI 的讀取權限由系統授予接收的 Activity，同一 Activity 內的 fragment 讀取不需額外處理。

替代方案：`singleTask` launchMode。不採用，原因如上。另開透明的轉送 Activity 也不採用，需要轉送 URI 權限，較複雜。

### 測試策略

- `app/src/test`：`TradeScreenshotParserTest`（iOS `TradeScreenshotParserTests` 全部 24 個案例，加上 NFKC 全形數字與不存在日期的案例）、`TextLineSplitterTest`（拆分與座標換算）、`StockNameResolverTest`（iOS 案例，真實名錄案例改用內嵌資料；上櫃的「環球晶」不移植）、`StockDirectoryParserTest`（第一個空白切開，並讀 `app/src/main/res/values/stockNo_suggestions.xml` 確認 0050、2344、2330、2379、00757 解析得到）、`TradeImportSessionTest`（iOS `TradeImportViewModelTests` 中不含費稅的案例，加上清單預設退回、Float 價格重複、已在清單不加入、毫秒錯開）、`ShareIntentImagesTest`。
- 雲端環境無法下載 Android SDK（dl.google.com 被網路政策擋），所以在雲端以獨立的 Kotlin JVM 專案（Kotlin 1.9.0、JVM 17、JUnit 4.13.2，與建置實際使用的版本一致）編譯 `core` 與上述測試來執行；完整的 `./gradlew testDebugUnitTest` 與 `assembleDebug` 需在本機或 CI 執行。
- Android 端的程式（ML Kit、Photo Picker、Fragment、Navigation、分享）在雲端無法編譯；對應任務在本機建置成功前不勾選，PR 描述寫明。
- ML Kit、Photo Picker、分享選單與畫面以手動驗證，步驟列在 tasks。

替代方案：Robolectric 測辨識與畫面。不採用，雲端無法下載 Android SDK，且 ML Kit 在 Robolectric 下不可用。

## Implementation Contract

行為：
- 入口兩個：列表頁「截圖匯入」（先開 Photo Picker）與分享 intent（直接帶圖片）。兩者進入同一個 `tradeImportFragment`。
- 逐張辨識 → 拆框 → 解析 → 合併成一個預覽；有任何一張讀取或辨識失敗就顯示錯誤並結束；全部沒有交易列就顯示「找不到可匯入的交易」並結束；否則顯示預覽。
- 按「匯入」才寫入；本機寫入完成後回到列表頁並 Toast 筆數；Firestore 上傳在背景完成。

介面與資料形狀（`ui/tradeimport/core`，純 Kotlin）：
- `data class TextBox(text: String, x: Double, y: Double, width: Double, height: Double)`，正規化、原點左下。
- `enum class TradeSide(val status: Int) { BUY(0), SELL(1) }`。
- `data class ParsedTrade(stockName: String, date: LocalDate?, price: Double?, amount: Int?, side: TradeSide, tradeTypeText: String, isSupportedType: Boolean)`。
- `object TradeScreenshotParser { fun parse(boxes: List<TextBox>): List<ParsedTrade> }`。
- `data class PixelElement(text: String, left: Int, top: Int, right: Int, bottom: Int)`；`object TextLineSplitter { fun split(line: List<PixelElement>, imageWidth: Int, imageHeight: Int): List<TextBox> }`。
- `data class StockEntry(code: String, name: String)`；`class StockNameResolver(entries: List<StockEntry>) { fun resolve(name: String): String? }`；`object StockDirectoryParser { fun parse(items: List<String>): List<StockEntry> }`。
- `data class ListChoice(id: Int, name: String)`。
- `data class ExistingTrade(stockNo: String, epochMillis: Long, price: Double, amount: Int, side: TradeSide)`。
- `class TradeImportSession(trades, resolver, isKnownCode: (String) -> Boolean, existingTrades: List<ExistingTrade>, lists: List<ListChoice>, currentListId: Int?)`：`items`、`toggleSelection(id)`、`setStockNo/setPrice/setAmount/setDate/setSide(id, value)`、`selectList(id: Int?)`、`selectedList`、`isImportEnabled`、`showsNoListNotice`、`buildImportPlan(usedTimestamps: Set<Long>, stocksInAnyList: Set<String>): ImportPlan`。
- `data class ImportPlan(records: List<NewRecord>, stocksToAdd: List<String>, list: ListChoice?)`；`data class NewRecord(stockNo, epochMillis, price, amount, side)`。

失敗模式：
- 有勾選項目不合法時 `buildImportPlan` 丟 `InvalidSelectionException`，畫面顯示錯誤，不寫入。
- 圖片讀不到、URI 權限失效、記憶體不足或 ML Kit 失敗：顯示錯誤，不寫入。
- 名錄讀不到代號：該筆為不完整，使用者可手動輸入名錄內的代號。
- 匯入中再次確認：忽略。
- Firestore 上傳失敗不影響本機寫入（與手動新增相同，未登入時不上傳）。

驗收條件：
- `app/src/test` 的 TradeScreenshotParserTest、TextLineSplitterTest、StockNameResolverTest、StockDirectoryParserTest、TradeImportSessionTest、ShareIntentImagesTest 全部通過。
- 本機 `./gradlew assembleDebug testDebugUnitTest` 成功。
- tasks 4.2 的手動驗證清單在裝置上通過。

範圍界線：見「範圍」。不改 `db/`、Room schema、`ListViewModel` 邏輯、費稅與版號。

## Risks / Trade-offs

- [ML Kit 的分行方式與 Vision 不同] → 以 element 依間距重組框；debug 版可複製真實輸出做成測試資料；手動驗證 4.2 以真實截圖確認，若解析錯誤，用複製的資料新增測試後修正。
- [名錄只有上市股票] → 上櫃股票會變成「不完整」，使用者手動輸入代號也會因不在名錄而無法匯入。OTC＋名錄 change 合併後替換 `StockDirectory` 實作即可解決，在 PR 描述註明。
- [已在別的清單的股票不會被加入選定清單] → 預覽頁提示文字說明；紀錄仍可從原清單打開。
- [沒有費稅] → 匯入的紀錄沒有費稅，與現在手動新增一致。費稅 change 合併後補。
- [雲端無法建置 Android] → 核心邏輯以 JVM 測試驗證，Android 任務在本機建置成功前不勾選，本機建置後再做一次 code review 才合併。
- [台灣以外時區] → 重複判斷可能漏判，記錄為已知限制。

## Open Questions

- 投資先生整股（現股）列的股數欄位是股還是張：沒有截圖可確認，先沿用 iOS 標示為不支援。
