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

## Decisions

### 範圍

In scope：`ui/tradeimport/` 新套件、`stock.xml` 新增目的地、列表頁加一個入口按鈕、`MainActivity` 處理分享 intent、manifest 的 intent filter、`app/build.gradle` 兩個依賴、字串資源。

Out of scope：`db/` 底下任何檔案、`ListViewModel` 的邏輯、其他分頁、費稅。

替代方案：把入口與流程寫進 `ui/list/`。不採用，因為列表頁由其他 change 同時修改，集中在新套件可減少衝突。

### 平台無關的核心放在 `ui/tradeimport/core`

`core` 套件只依賴 Kotlin 標準函式庫與 `java.time`、`java.text.Normalizer`，不 import 任何 `android.*`、`androidx.*`，讓 `app/src/test` 的 JVM test 直接測。內容：

- `TradeImportModels.kt`：`TextBox(text, x, y, width, height)`，座標沿用 Vision 慣例（正規化 0 到 1，原點左下，y 越大越上面），讓 iOS 測試資料原封不動移植；`TradeSide`（BUY=0、SELL=1，與 `InvestHistory.status` 相同）；`ParsedTrade(stockName, date: LocalDate?, price: Double?, amount: Int?, side, tradeTypeText, isSupportedType)`；`TAIPEI = ZoneId.of("Asia/Taipei")`。
- `TradeScreenshotParser.kt`：逐行移植 iOS 的 `TradeScreenshotParser`。價格用 `Double`（iOS 是 Float），因為 `InvestHistory.price` 是 Double，且手動輸入「112.55」轉出來的 Double 與這裡相同，重複比對才會一致。日期用 `LocalDate`（台北的日曆日）。正規表示式改寫成 Java 語法（`[一-鿿]*(買進|賣出)$` 等）；去空白同時去掉全形空白。
- `StockNameResolver.kt`：移植 iOS 版。正規化用 `Normalizer.Form.NFKC`、移除所有空白（含全形空白）、把長破折號類字元（U+2013、U+2014、U+2015、U+2500、U+30FC）換成「一」；找不到時才把 ASCII 連字號試著換成「一」；同名多檔時不解析。
- `TradeImportSession.kt`：移植 iOS `TradeImportViewModel` 的非 UI 部分（不含費稅）。建構時拿到「已存在紀錄的快照」：`List<ExistingTrade(stockNo, epochMillis, price, amount, side)>`、所有紀錄的毫秒時間戳集合、已在任一清單裡的股票代號集合、可選的清單、預設清單、`isKnownCode`。狀態、勾選、編輯、選清單都在記憶體裡算。`buildImportPlan()` 驗證所有勾選項目，有任何一筆不合法就丟例外、不產生計畫；合法時回傳要寫入的紀錄（含錯開後的毫秒時間戳）與要加入清單的代號。
  - 快照而非逐次查詢：預覽期間資料庫不會被這個流程以外的東西改動（使用者停在預覽頁），快照讓核心保持同步與可測。

替代方案：解析器直接吃 ML Kit 的 `Text` 物件。不採用，因為 ML Kit 類別無法在 JVM test 建構，iOS 的測試資料也無法移植。

### 狀態規則（與 iOS 相同，去掉費稅）

狀態依序判斷：不支援 → 不完整 → 重複 → 可匯入。不完整：代號為空或不在名錄、日期為空、價格為空或不大於 0、股數不在 1 到 32767。股數上限沿用 iOS 的 Int16 上限，雖然 Android 的 `amount` 是 Int，但 Firestore 的紀錄會被 iOS 讀取，維持同一個範圍避免 iOS 端溢位。

重複：代號、台北日曆日、價格、股數、方向都相同；比對對象是既有紀錄與同一批前面的交易。建立時預設只勾選「可匯入」；編輯後狀態改變時，變成可匯入就勾選，變成其他狀態就取消勾選，狀態沒變則保留使用者的選擇。只有「可匯入」與「重複」能被勾選。

替代方案：股數上限改用 Int 上限。不採用，理由如上。

### 寫入的日期

截圖只有日期。寫入時間為該日台北時間 12:00:00.000；若這個毫秒時間戳已被既有紀錄或同一批前面的交易用掉，就加 1 毫秒直到沒被用過。Android 存的就是毫秒 Long，不需要 iOS 的浮點修正。

替代方案：寫入當下時間。不採用，日期會錯；也不寫 00:00，因為時區換算可能跨日。

### 加入清單（Android 的資料模型限制）

預覽頁的清單選項是所有追蹤清單加「不加入清單」。預設選目前列表頁顯示的清單；沒有清單時預設「不加入清單」。

因為 `Stock` 的主鍵只有代號，同一檔股票在本機只能在一個清單。確認匯入時，對每個不重複的已匯入代號：若它已在任何清單（包括選定的清單）就不動它，否則加入選定清單。這和 iOS「選定清單已有就略過」的差別是：已在別的清單時 iOS 會加入，Android 不搬移，避免從原清單消失。紀錄本身不屬於任何清單，仍能從原清單打開。選「不加入清單」時顯示提示「匯入的紀錄無法從列表頁打開，除非股票已在某個清單」。

替代方案：改 `Stock` 主鍵讓同一股票可在多個清單。不採用，需要 Room migration，超出本 change 範圍且會和其他 change 衝突。

### 寫入流程

`TradeImportRepository`（Android 端）負責：

- 讀快照：`NewsRepository.allHistory.first()`、`StockDao.getAllStocks().first()`、`NewsRepository.allFollowingList.first()`，在 IO dispatcher 執行。
- 寫入：對計畫裡每筆紀錄呼叫 `NewsRepository.insertHistory(InvestHistory(0, stockNo, date, price, amount, status))` 與 `FirebaseManager.uploadHistoryToOnlineDB(...)`；對每個要加入的代號呼叫 `NewsRepository.upsert(Stock(stockNo, listId, "0"))` 與 `FirebaseManager.uploadNewStockNoToOnlineDB(stockNo, listName)`。和手動新增一樣，不等待 Firestore 結果；回報「送出寫入」的筆數。
- 費稅 TODO：費稅 change 合併後，在這裡填入費稅欄位，並在 `TradeImportSession` 加入估算。

替代方案：在 `db/` 新增批次寫入的 DAO 方法。不採用，避免動到其他 change 擁有的檔案；筆數少，逐筆寫入即可。

### 文字辨識：ML Kit 中文模型（打包版）

依賴 `com.google.mlkit:text-recognition-chinese:16.0.1`（模型打包在 APK，離線可用，第一次使用不需下載），不用 Play services 的非打包版，因為後者第一次使用要透過 Play services 下載模型，可能失敗。代價是 APK 變大，大小未量測；ML Kit 文件寫每種文字架構約數 MB，實作後以 `./gradlew assembleDebug` 前後的 APK 大小比較確認。

`TradeTextRecognizer` 用 `InputImage.fromFilePath(context, uri)` 讀圖，`TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())` 辨識，取每個 `Text.Line`（相當於 Vision 的一個 observation）。ML Kit 的 `boundingBox` 是像素、原點左上；轉成核心座標：`x = left / w`、`y = 1 - bottom / h`、`width = (right - left) / w`、`height = (bottom - top) / h`。寬高取 `InputImage.width/height`，旋轉 90 或 270 度時互換。一次只辨識一張，辨識完才讀下一張。沒有 `boundingBox` 的行略過。辨識結果不寫 log。

替代方案：Play services 非打包版（`play-services-mlkit-text-recognition-chinese`）。APK 較小但第一次使用需下載模型，離線時失敗。

### 流程與畫面

- 新目的地 `tradeImportFragment` 加在 `res/navigation/stock.xml`，參數 `imageUris`（string array，可為 null）。另加 global action `action_global_tradeImportFragment`。
- 列表頁浮動按鈕展開的按鈕組多一個「截圖匯入」，點了導到 `tradeImportFragment`（不帶圖片）。
- `TradeImportFragment` 在沒有圖片參數且是第一次建立時，開啟 `PickMultipleVisualMedia(ImageOnly)`；使用者取消就返回列表。有圖片後交給 `TradeImportViewModel`（fragment 範圍）辨識，畫面顯示進度；辨識完成後顯示預覽或訊息。旋轉螢幕不重新辨識（ViewModel 保存狀態）。
- 結果：全部圖片沒有任何交易列 → 對話框「找不到可匯入的交易」，按確定返回；讀圖或辨識失敗 → 對話框顯示錯誤，按確定返回，不寫任何紀錄。只要有一張失敗就整批視為失敗，與 iOS 相同。
- 預覽：RecyclerView 每列顯示名稱、代號、方向、股數、價格、日期、狀態文字，可勾選；點列開 `TradeEditDialog`（代號、價格、股數、日期挑選器、買賣切換）。上方是清單選擇（下拉選單），選「不加入清單」時顯示提示。底部「匯入」按鈕在沒有勾選時停用。按下後寫入、Toast 顯示「已匯入 N 筆」並返回列表頁。

替代方案：用新的 Activity 承載匯入流程。不採用，現有畫面都是 Navigation 的 fragment，沿用同一個返回堆疊較一致。

### 分享 intent

`AndroidManifest.xml` 在 `MainActivity` 加兩個 intent filter：`ACTION_SEND` 與 `ACTION_SEND_MULTIPLE`，`mimeType="image/*"`，category DEFAULT。`MainActivity` 在 `onCreate`（`savedInstanceState == null` 時）與 `onNewIntent` 呼叫 `handleShareIntent`：取出 `EXTRA_STREAM` 的 URI（單張或清單），把底部分頁切到 `stock`，再以 global action 開 `tradeImportFragment` 並帶入 URI 字串。處理過的 intent 換成空的 `Intent()`，避免旋轉後重複開啟。URI 的讀取權限由系統授予接收的 Activity，同一 Activity 內的 fragment 讀取不需額外處理。

判斷哪些 URI 要處理的邏輯抽成純函式 `ShareIntentImages.select(action, mimeType, single, multiple)`，用 JVM test 測：非這兩種 action、非 `image/` 類型、沒有 URI 都回傳空清單。

替代方案：另開一個透明的轉送 Activity 接分享。不採用，需要轉送 URI 權限，較複雜；MainActivity 直接接收即可取得讀取權限。

### 測試策略

- `app/src/test`：`TradeScreenshotParserTest`（iOS `TradeScreenshotParserTests` 全部案例）、`StockNameResolverTest`（iOS 案例中不依賴 iOS 真實名錄的部分）、`TradeImportSessionTest`（iOS `TradeImportViewModelTests` 中不含費稅的案例）、`ShareIntentImagesTest`。
- 這個雲端環境無法下載 Android SDK（dl.google.com 被網路政策擋），所以在雲端以獨立的 Kotlin JVM 專案編譯 `core` 與上述測試來執行；完整的 `./gradlew testDebugUnitTest` 與 `assembleDebug` 需在本機或 CI 執行，並寫進 PR 描述。
- ML Kit、Photo Picker、分享選單與畫面以手動驗證，步驟列在 tasks。

替代方案：Robolectric 測辨識與畫面。不採用，雲端無法下載 Android SDK，且 ML Kit 在 Robolectric 下不可用。

## Implementation Contract

行為：
- 入口兩個：列表頁「截圖匯入」（先開 Photo Picker）與分享 intent（直接帶圖片）。兩者進入同一個 `tradeImportFragment`。
- 逐張辨識 → 解析 → 合併成一個預覽；有任何一張讀取或辨識失敗就顯示錯誤並結束；全部沒有交易就顯示「找不到可匯入的交易」並結束；否則顯示預覽。
- 按「匯入」才寫入；寫入後回到列表頁並 Toast 筆數。

介面與資料形狀（`ui/tradeimport/core`，純 Kotlin）：
- `data class TextBox(text: String, x: Double, y: Double, width: Double, height: Double)`，正規化、原點左下。
- `enum class TradeSide(val status: Int) { BUY(0), SELL(1) }`。
- `data class ParsedTrade(stockName: String, date: LocalDate?, price: Double?, amount: Int?, side: TradeSide, tradeTypeText: String, isSupportedType: Boolean)`。
- `object TradeScreenshotParser { fun parse(boxes: List<TextBox>): List<ParsedTrade> }`。
- `data class StockEntry(code: String, name: String)`；`class StockNameResolver(entries: List<StockEntry>) { fun resolve(name: String): String? }`。
- `data class ExistingTrade(stockNo: String, epochMillis: Long, price: Double, amount: Int, side: TradeSide)`。
- `class TradeImportSession(trades, resolver, isKnownCode: (String) -> Boolean, existingTrades: List<ExistingTrade>, usedTimestamps: Set<Long>, stocksInAnyList: Set<String>, lists: List<String>, currentList: String?)`：`items`、`toggleSelection(id)`、`setStockNo/setPrice/setAmount/setDate/setSide(id, value)`、`selectList(name: String?)`、`isImportEnabled`、`showsNoListNotice`、`buildImportPlan(): ImportPlan`。
- `data class ImportPlan(records: List<NewRecord>, stocksToAdd: List<String>, listName: String?)`；`data class NewRecord(stockNo, epochMillis, price, amount, side)`。

失敗模式：
- 有勾選項目不合法時 `buildImportPlan` 丟 `InvalidSelectionException`，畫面顯示錯誤，不寫入。
- 圖片讀不到或 ML Kit 失敗：顯示錯誤，不寫入。
- 名錄讀不到代號：該筆為不完整，使用者可手動輸入名錄內的代號。
- Firestore 上傳失敗不影響本機寫入（與手動新增相同，未登入時不上傳）。

驗收條件：
- `app/src/test` 的 TradeScreenshotParserTest、StockNameResolverTest、TradeImportSessionTest、ShareIntentImagesTest 全部通過。
- tasks 4.2 的手動驗證清單在裝置上通過。

範圍界線：見「範圍」。不改 `db/`、Room schema、`ListViewModel` 邏輯與費稅。

## Risks / Trade-offs

- [ML Kit 的分行方式與 Vision 不同] → 解析器只依位置分行，不依回傳順序；把 Vision 測試資料全部移植。手動驗證時用真實截圖比對，若有差異，把 ML Kit 實際輸出記成新的測試資料再修正解析器。
- [名錄只有上市股票] → 上櫃股票會變成「不完整」，使用者手動輸入代號也會因不在名錄而無法匯入。OTC＋名錄 change 合併後替換 `StockDirectory` 實作即可解決，在 PR 描述註明。
- [已在別的清單的股票不會被加入選定清單] → 預覽頁提示文字說明；紀錄仍可從原清單打開。
- [沒有費稅] → 匯入的紀錄費稅為空，與現在手動新增一致（目前手動新增也不存費稅）。費稅 change 合併後補。
- [雲端無法建置 Android] → 核心邏輯以 JVM 測試驗證，Android 部分在本機建置與手動驗證後才合併。

## Open Questions

- 投資先生整股（現股）列的股數欄位是股還是張：沒有截圖可確認，先沿用 iOS 標示為不支援。
