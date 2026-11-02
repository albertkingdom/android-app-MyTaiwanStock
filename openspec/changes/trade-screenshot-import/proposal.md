## Why

iOS 版已經能把元大「投資先生」的交易明細截圖辨識成買賣紀錄，也能從系統分享選單把截圖送進 App。Android 版沒有這個功能，使用者只能一筆一筆手動輸入。券商 App 沒有匯出功能，也沒有第三方 API，畫面上的明細是唯一的資料來源。這個 change 把 iOS 的截圖匯入與分享匯入移植到 Android，讓兩個平台的功能對齊（見 docs/ios-parity-assessment.md 的「截圖匯入交易」與「從分享選單匯入圖片」兩列）。

## What Changes

- 列表頁的浮動按鈕選單多一個「截圖匯入」：開啟系統相片挑選器（Photo Picker，可多選），逐張在裝置上辨識文字，把所有辨識到的交易合併成一個預覽清單。
- 文字辨識使用 ML Kit Text Recognition 的中文模型（模型打包在 App 內，裝置端執行）。圖片與辨識文字都不上傳任何伺服器。
- 新增純 Kotlin 的截圖解析器 `TradeScreenshotParser`：輸入與平台無關的文字框清單（文字加位置），依位置找出「上下兩行一組」的交易列，忽略展開的明細區塊、摘要列與表頭。從 iOS 的測試資料（`TradeScreenshotFixtures.swift`、`TradeScreenshotVisionFixtures.swift`）移植 JVM unit test。
- 新增純 Kotlin 的股票名稱對代號解析 `StockNameResolver`（精確比對，不做模糊比對）與預覽狀態邏輯 `TradeImportSession`（可匯入、重複、不完整、不支援四種狀態、勾選規則、寫入日期的毫秒錯開、加入清單的股票），都有 JVM unit test。
- 預覽頁：每筆可勾選或取消、可點開逐筆編輯（代號、價格、股數、日期、買賣方向）；選擇要把匯入的股票加入哪個追蹤清單，或「不加入清單」。按「匯入」才寫入。沒有任何可匯入的交易時顯示「找不到可匯入的交易」。
- 系統分享選單：`MainActivity` 接受 `image/*` 的 `ACTION_SEND` 與 `ACTION_SEND_MULTIPLE`，直接切到列表分頁並開啟同一個匯入流程。Android 可以直接把圖片的 content URI 交給 App，不需要 iOS 的待匯入暫存區。
- 寫入：沿用手動新增紀錄的方式，寫入本機 Room 的 `InvestHistory` 並上傳 Firestore（已登入時），加入清單時同樣寫本機並上傳 Firestore。
- 新增依賴：`com.google.mlkit:text-recognition-chinese`（ML Kit 中文辨識，打包模型）與 `androidx.activity:activity-ktx`（Photo Picker 的 `PickMultipleVisualMedia`）。

## Capabilities

### New Capabilities

- `trade-screenshot-import`: 從投資先生交易明細截圖辨識買賣紀錄、預覽、逐筆確認並寫入。
- `share-intent-import`: 從系統分享選單把圖片交給 App，直接開啟截圖匯入流程。

### Modified Capabilities

(none)

## Impact

- Affected specs: `trade-screenshot-import`（新增）、`share-intent-import`（新增）
- Affected code:
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/core/TradeImportModels.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/core/TradeScreenshotParser.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/core/StockNameResolver.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/core/TradeImportSession.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/TradeTextRecognizer.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/StockDirectory.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/TradeImportRepository.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/TradeImportViewModel.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/TradeImportFragment.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/TradePreviewAdapter.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/TradeEditDialog.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/tradeimport/ShareIntentImages.kt
  - New: app/src/main/res/layout/fragment_trade_import.xml
  - New: app/src/main/res/layout/item_trade_preview.xml
  - New: app/src/main/res/layout/dialog_trade_edit.xml
  - New: app/src/test/java/com/example/mynewsapp/ui/tradeimport/core/TradeScreenshotFixtures.kt
  - New: app/src/test/java/com/example/mynewsapp/ui/tradeimport/core/TradeScreenshotParserTest.kt
  - New: app/src/test/java/com/example/mynewsapp/ui/tradeimport/core/StockNameResolverTest.kt
  - New: app/src/test/java/com/example/mynewsapp/ui/tradeimport/core/TradeImportSessionTest.kt
  - New: app/src/test/java/com/example/mynewsapp/ui/tradeimport/ShareIntentImagesTest.kt
  - Modified: app/build.gradle
  - Modified: app/src/main/AndroidManifest.xml
  - Modified: app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - Modified: app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
  - Modified: app/src/main/res/layout/fragment_list.xml
  - Modified: app/src/main/res/navigation/stock.xml
  - Modified: app/src/main/res/values/strings.xml
  - Removed: (none)
