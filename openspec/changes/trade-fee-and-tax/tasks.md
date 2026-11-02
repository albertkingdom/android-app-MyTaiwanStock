## 驗證方式說明

- 「JVM 測試」：`./gradlew testDebugUnitTest --tests '<類別>'`。在無法連到 Google Maven 的雲端環境，改用暫時的 JVM Gradle 專案執行，固定 Kotlin 1.9.0（與 app 的 KSP 1.9.0-1.0.13／Compose compiler 1.5.2 一致）、JVM target 17、JUnit 4.13.2、`org.xerial:sqlite-jdbc`，編譯 `app/src/main/java/com/example/mynewsapp/tradecost/`、`db/InvestHistoryMigrationSql.kt` 與 `app/src/test/` 的對應測試。程式只用 Kotlin 1.9 可用的語法與 stdlib。
- 「建置」：`./gradlew assembleDebug`，需要 JDK 17＋Android SDK。
- 「手動」：在模擬器或裝置上操作，依各任務列出的步驟檢查。

## 進度

- 已勾選的任務已在雲端以 JVM 暫時專案驗證（124 個測試通過）。
- 2.1、2.3、3.x、4.1、5.x、6.1 的程式已完成，但驗證需要 Android SDK（建置、`7.json`、`androidTest`、手動 UI），雲端無法執行，留待能建置的環境勾選。

## 1. 費稅與成本的純邏輯（`tradecost`）

- [x] 1.1（Calculate fee and tax with one rule set；決策：純 Kotlin package `tradecost`）新增 `TradeSide`、`TaxCategory`（稅率用字串建 `BigDecimal`）、`FeeAndTax`、`FeeCalculator`，並移植 iOS `FeeCalculatorTests`（規格範例表 13 列、2.8 折 399、當沖稅 150、買進稅 0、價格／股數無效回傳 null、超過 2147483647 回傳 null）。驗證：JVM 測試 `FeeCalculatorTest` 全過。
- [x] 1.2（Choose the fee discount and minimum fees in settings；決策：手續費設定存在 `SharedPreferences`）新增 `FeeSettings`（`parseDiscount`、`parse`、`summaryText`、`fromStored`、`toStored`）與 `FeeSettingsException`，移植 iOS `FeeSettingsTests`，改成 Android 的 key 與舊版百分比規則（"60" → 6、舊 `fee_custom` 不影響、最低手續費只接受 Int），並加 BigDecimal 表示法案例（"10.0" → 無折扣、"2.80" → 「2.8 折」、"2.850" 接受、"10.00" 接受、"1e1" 與 "." 拒絕）。依賴 1.1。驗證：JVM 測試 `FeeSettingsTest` 全過。
- [x] 1.3 新增 `TradeInput`（`parsePrice`、`parseShares`、`parseFeeOrTax`，去頭尾空白、只接受 ASCII 數字）。驗證：JVM 測試 `TradeInputTest`（含 "1e3"、"Infinity"、"."、" 100 "、"５"、"2147483648"、"99999999999"、空白）全過。
- [x] 1.4 新增 `FeeTaxFields`，移植 iOS `FeeTaxFieldsTests`。依賴 1.1、1.3。驗證：JVM 測試 `FeeTaxFieldsTest` 全過。
- [x] 1.5（Compute holdings and cost with sells and fees）新增 `HoldingsCalculator`（`TradeLine`、持股數與費用加總用 `Long`、均價、未知手續費提示、市值、報酬率、`format`、`formatMarketValue`、`Double` 價格轉 `BigDecimal`），移植 iOS `OverViewCalculatorTests`。依賴 1.1。驗證：JVM 測試 `HoldingsCalculatorTest` 全過。
- [x] 1.6（Enter fee and tax when adding a record by hand）新增 `TradeEntryForm`，移植 iOS `MyTaiwanStockTests/Screens/TradeEntry/AddHistoryViewModelTests.swift`（不含 32767 股上限的案例），並涵蓋 spec 情境：逐字輸入、切換賣出算稅、手動值保留、刪除後重打、離開空白欄位回到計算值、存檔前結束編輯會補回計算值、切換買進忘掉手動稅、設定的最低手續費、存檔時的價格／股數／費稅驗證（含 5.5、-1、Infinity）。依賴 1.1–1.4。驗證：JVM 測試 `TradeEntryFormTest` 全過。

## 2. Room 欄位與 migration

- [ ] 2.1（Store fee, tax and memo with each record；決策：Room migration 6 → 7）新增 `db/InvestHistoryMigrationSql.kt`（`V6_TO_V7`），`InvestHistory` 加 `fee`、`tax`、`memo`（可為空、預設 null），`StockDatabase` 版本 7 並註冊 `MIGRATION_6_7`。驗證：建置成功，且 `app/schemas/.../7.json` 產生、包含三個新欄位。
- [x] 2.2（Keep existing data when the database is upgraded）`app/build.gradle` 加 `testImplementation 'org.xerial:sqlite-jdbc'`，新增 JVM 測試 `InvestHistoryMigrationSqlTest`：用 `6.json` 的 `investHistory` createSql（把 `${TABLE_NAME}` 換成 `investHistory`）建 v6 表、3 買 2 賣 → 跑 `V6_TO_V7` → 5 筆原樣、新欄位 null、欄位型別 INTEGER／INTEGER／TEXT。依賴 2.1。驗證：JVM 測試 `InvestHistoryMigrationSqlTest` 全過，且先在 `V6_TO_V7` 為空時確認測試會失敗。
- [ ] 2.3 `app/build.gradle` 加 `androidTestImplementation "androidx.room:room-testing:$room_version"`、`androidx.test:runner`、`androidx.test:core` 與 androidTest assets 指向 `schemas/`，新增 `StockDatabaseMigrationTest`（`MigrationTestHelper(instrumentation, StockDatabase::class.java)`，建立 v6 並插入資料，`runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)` 後檢查資料）。依賴 2.1。驗證：`./gradlew connectedDebugAndroidTest` 在模擬器通過；雲端無法執行，PR 列為合併前必做。

## 3. 手續費設定頁

- [ ] 3.1 新增 `ui/setting/FeeSettingsStore`（`PreferenceManager.getDefaultSharedPreferences`，與舊 `fee_discount` 同一個檔；`load()` 讀 `prefs.all` 交給 `FeeSettings.fromStored`；`save()` 用同一個 editor 寫三個值）。依賴 1.2。驗證：建置成功。
- [ ] 3.2（決策：設定頁）新增 `FeeSettingsFragment` 與 `fragment_fee_settings.xml`（折數 `numberDecimal`、最低手續費 `number`），`navigation/settings.xml` 加 destination 與 action；每次儲存先清掉三個欄位的錯誤。依賴 3.1。驗證：手動：輸入 2.8／30／5 儲存後回到設定頁；折數輸入 11 或 2.855 儲存時折數欄位顯示錯誤且不離開；整股清空儲存時整股欄位顯示錯誤；整股輸入 9999999999 時顯示錯誤；重新進入頁面時欄位是已存的值。負數與小數的最低手續費由 `FeeSettingsTest` 涵蓋（`number` 鍵盤無法輸入）。
- [ ] 3.3 `fragment_setting.xml` 手續費分類改成一列 `fee_settings`，`SettingFragment` 在 `onResume` 更新摘要並導頁。依賴 3.2。驗證：手動：全新安裝時摘要為「無折扣・最低 20 / 1 元」；儲存 2.8 後摘要為「2.8 折・最低 20 / 1 元」；從舊版（`fee_discount`="60"）升級時摘要為「6 折・最低 20 / 1 元」。

## 4. 新增紀錄頁

- [ ] 4.1（Enter fee and tax when adding a record by hand；Store fee, tax and memo with each record；決策：新增紀錄頁）一次改完版面與程式，避免中途無法建置：
  - `fragment_add_history.xml` 移除舊手續費選項，加手續費（`number`）、稅別、交易稅（`number`）、備註欄位與 `NestedScrollView`；刪除 `fee_options.xml`、`fee_discount_option.xml`（設定列已在 3.3 改掉）與不再使用的字串。
  - `AddHistoryViewModel` 改用 `by viewModels { factory }` 取得，包 `TradeEntryForm` 並提供 `StateFlow`。
  - `AddHistoryFragment` 在 `onViewStateRestored` 之後掛 TextWatcher（含 `isRendering` 旗標），掛上後以 ViewModel state 重繪欄位與 chip；焦點離開時結束編輯；存檔與切換買賣前先 `clearFocus()` 並結束編輯；稅別 spinner；存檔時用 `TradeInput` 解析價格與股數、驗證費稅，寫入 `fee`、`tax`、`memo`；Firestore 上傳維持原欄位。
  - 依賴 1.6、2.1、3.1、3.3。
  - 驗證：建置成功；手動：價格 100、股數逐字打到 1000 時手續費顯示 142；切到賣出顯示稅別與交易稅；手動改手續費 50 後改股數仍為 50；清空手續費並離開欄位後回到計算值；清空手續費直接按儲存時存入計算值；輸入價格與股數後旋轉螢幕，再改股數時手續費仍跟著計算；手續費輸入 9999999999 儲存時顯示錯誤、不新增紀錄；正常儲存後在個股詳情看到該筆的手續費與稅。5.5、-1 等鍵盤無法輸入的值由 `TradeEntryFormTest` 涵蓋。

## 5. 個股詳情

- [ ] 5.1（Compute holdings and cost with sells and fees；決策：個股詳情）`CandleStickChartViewModel` 用 `HoldingsCalculator` 產生 `HoldingsSummary` 的 `StateFlow`，初始值為持股 0、其他「-」，空清單也更新。依賴 1.5、2.1。驗證：建置成功；手動：沒有紀錄的股票，總覽顯示持股 0、市值與均價「-」。
- [ ] 5.2 `fragment_candle_stick_chart.xml` 標籤改「買入均價(含費用)」、加未知手續費提示；`CandleStickChartFragment` 顯示 summary，現價（含頁首的股價文字）用 `toBigDecimalOrNull()`，無法解析時顯示「-」不 crash。依賴 5.1。驗證：手動：有一筆升級前的買進紀錄時顯示提示，只有升級前的賣出紀錄時不顯示。
- [ ] 5.3（Show fee and tax per record and flag missing fee data）`item_stock_history.xml` 的 container 與根元素改 `wrap_content`（根元素 `minHeight` 60dp）並加第二行費稅；`StockHistoryAdapter` 用 `HoldingsCalculator.profitPercent`，賣出直接顯示「-」（不套 `%` 格式），顏色依四捨五入到兩位後的正負：正紅、負綠、0 用一般文字色。依賴 1.5、2.1。驗證：手動：新紀錄顯示「手續費 142・稅 0」，升級前紀錄顯示「手續費 -・稅 -」，賣出紀錄報酬率為「-」。

## 6. 收尾驗證

- [ ] 6.1 執行全部 JVM 測試、建置、`androidTest`，提交建置產生的 `7.json`，並更新 `docs/ios-parity-assessment.md` 中費稅與成本兩列的 Android 現況。驗證：`./gradlew testDebugUnitTest assembleDebug` 成功；雲端無法執行的項目（建置、`7.json`、`androidTest`、手動 UI）在 PR 說明列為合併前必做。
