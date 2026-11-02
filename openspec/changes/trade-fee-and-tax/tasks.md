## 驗證方式說明

- 「JVM 測試」：`./gradlew testDebugUnitTest --tests '<類別>'`。在無法連到 Google Maven 的雲端環境，改用暫時的 JVM Gradle 專案（Kotlin JVM plugin＋Maven Central）編譯 `app/src/main/java/com/example/mynewsapp/tradecost/`、`db/InvestHistoryMigrationSql.kt` 與 `app/src/test/` 的對應測試執行，結果相同。
- 「建置」：`./gradlew assembleDebug`，需要 JDK 17＋Android SDK。
- 「手動」：在模擬器或裝置上操作，依各任務列出的步驟檢查。

## 1. 費稅與成本的純邏輯（`tradecost`）

- [ ] 1.1（Calculate fee and tax with one rule set；決策：純 Kotlin package `tradecost`）新增 `TradeSide`、`TaxCategory`、`FeeAndTax`、`FeeCalculator`，並移植 iOS `FeeCalculatorTests`（規格範例表 13 列、2.8 折 399、當沖稅 150、買進稅 0、價格／股數無效回傳 null、超過 2147483647 回傳 null）。驗證：JVM 測試 `FeeCalculatorTest` 全過。
- [ ] 1.2（Choose the fee discount and minimum fees in settings；決策：手續費設定存在 `SharedPreferences`）新增 `FeeSettings`（`parseDiscount`、`parse`、`summaryText`、`fromStored`、`toStored`）與 `FeeSettingsException`，移植 iOS `FeeSettingsTests`，改成 Android 的 key 與舊版百分比規則（"60" → 6、舊 `fee_custom` 不影響、最低手續費只接受 Int）。驗證：JVM 測試 `FeeSettingsTest` 全過。
- [ ] 1.3 新增 `TradeInput`（`parsePrice`、`parseShares`、`parseFeeOrTax`，只接受 ASCII 數字）。驗證：JVM 測試 `TradeInputTest`（含 "1e3"、"５"、"2147483648"、空白）全過。
- [ ] 1.4 新增 `FeeTaxFields`，移植 iOS `FeeTaxFieldsTests`。驗證：JVM 測試 `FeeTaxFieldsTest` 全過。
- [ ] 1.5（Compute holdings and cost with sells and fees）新增 `HoldingsCalculator`（`TradeLine`、持股數、均價、未知手續費提示、市值、報酬率、`format`、`Double` 價格轉 `BigDecimal`），移植 iOS `OverViewCalculatorTests`。驗證：JVM 測試 `HoldingsCalculatorTest` 全過。
- [ ] 1.6（Enter fee and tax when adding a record by hand）新增 `TradeEntryForm`（新增頁的計算狀態），測試 spec「Enter fee and tax」的情境：逐字輸入股數、切換賣出算稅、手動值保留、離開空白欄位回到計算值、切換買進忘掉手動稅、儲存時的費稅驗證結果。驗證：JVM 測試 `TradeEntryFormTest` 全過。

## 2. Room 欄位與 migration

- [ ] 2.1（Store fee, tax and memo with each record；決策：Room migration 6 → 7）新增 `db/InvestHistoryMigrationSql.kt`（`V6_TO_V7`），`InvestHistory` 加 `fee`、`tax`、`memo`（可為空、預設 null），`StockDatabase` 版本 7 並註冊 `MIGRATION_6_7`。依賴 1 無。驗證：建置成功，且 `app/schemas/.../7.json` 產生、包含三個新欄位。
- [ ] 2.2（Keep existing data when the database is upgraded）`app/build.gradle` 加 `testImplementation 'org.xerial:sqlite-jdbc'`，新增 JVM 測試 `InvestHistoryMigrationSqlTest`（v6 表 3 買 2 賣 → 跑 `V6_TO_V7` → 5 筆原樣、新欄位 null、欄位型別正確）。依賴 2.1。驗證：JVM 測試 `InvestHistoryMigrationSqlTest` 全過，且先在 `V6_TO_V7` 為空時確認測試會失敗。
- [ ] 2.3 `app/build.gradle` 加 `androidTestImplementation room-testing` 與 androidTest assets 指向 `schemas/`，新增 `StockDatabaseMigrationTest`（`MigrationTestHelper` 6 → 7 並驗證）。依賴 2.1。驗證：`./gradlew connectedDebugAndroidTest --tests '*StockDatabaseMigrationTest'` 在模擬器通過。

## 3. 手續費設定頁

- [ ] 3.1 新增 `ui/setting/FeeSettingsStore`（`load()` 讀 `prefs.all` 交給 `FeeSettings.fromStored`；`save()` 用同一個 editor 寫三個值）。依賴 1.2。驗證：建置成功。
- [ ] 3.2（決策：設定頁）新增 `FeeSettingsFragment` 與 `fragment_fee_settings.xml`，`navigation/settings.xml` 加 destination 與 action。依賴 3.1。驗證：手動：輸入 2.8／30／5 儲存後回到設定頁；輸入 11 或 2.855 儲存時折數欄位顯示錯誤且不離開；整股填 -1 時整股欄位顯示錯誤；重新進入頁面時欄位是已存的值。
- [ ] 3.3 `fragment_setting.xml` 手續費分類改成一列 `fee_settings`，`SettingFragment` 在 `onResume` 更新摘要並導頁；刪除 `fee_discount_option.xml`。依賴 3.2。驗證：手動：全新安裝時摘要為「無折扣・最低 20 / 1 元」；儲存 2.8 後摘要為「2.8 折・最低 20 / 1 元」；從舊版（`fee_discount`="60"）升級時摘要為「6 折・最低 20 / 1 元」。

## 4. 新增紀錄頁

- [ ] 4.1 改 `fragment_add_history.xml`：移除舊手續費選項，加手續費、稅別、交易稅、備註欄位與 `NestedScrollView`；刪除 `fee_options.xml` 與不再使用的字串。依賴無。驗證：建置成功。
- [ ] 4.2（Enter fee and tax when adding a record by hand；Store fee, tax and memo with each record；決策：新增紀錄頁）`AddHistoryViewModel` 包 `TradeEntryForm` 並提供 `StateFlow`；`AddHistoryFragment` 接 TextWatcher（含 `isRendering` 旗標）、焦點離開、買賣 chip、稅別 spinner，儲存時驗證並寫入 `fee`、`tax`、`memo`，Firestore 上傳維持原欄位。依賴 1.6、2.1、3.1、4.1。驗證：手動：價格 100、股數逐字打到 1000 時手續費顯示 142；切到賣出顯示稅別與交易稅；手動改手續費 50 後改股數仍為 50；清空手續費並離開欄位後回到計算值；手續費填 5.5 儲存時顯示錯誤、不新增紀錄；正常儲存後在個股詳情看到該筆的手續費與稅。

## 5. 個股詳情

- [ ] 5.1（Compute holdings and cost with sells and fees；決策：個股詳情）`CandleStickChartViewModel` 用 `HoldingsCalculator` 產生 `HoldingsSummary` 的 `StateFlow`，空清單也更新。依賴 1.5、2.1。驗證：建置成功；手動：刪光紀錄（或新股票無紀錄）時總覽全部顯示「0」或「-」。
- [ ] 5.2 `fragment_candle_stick_chart.xml` 標籤改「買入均價(含費用)」、加未知手續費提示；`CandleStickChartFragment` 顯示 summary。依賴 5.1。驗證：手動：有一筆升級前的買進紀錄時顯示提示，只有升級前的賣出紀錄時不顯示。
- [ ] 5.3（Show fee and tax per record and flag missing fee data）`item_stock_history.xml` 加第二行費稅，`StockHistoryAdapter` 用 `HoldingsCalculator.profitPercent`、賣出顯示「-」、正紅負綠。依賴 1.5、2.1。驗證：手動：新紀錄顯示「手續費 142・稅 0」，升級前紀錄顯示「手續費 -・稅 -」，賣出紀錄報酬率為「-」。

## 6. 收尾驗證

- [ ] 6.1 執行全部 JVM 測試、建置、`androidTest`，並更新 `docs/ios-parity-assessment.md` 中費稅與成本兩列的 Android 現況。驗證：`./gradlew testDebugUnitTest assembleDebug` 成功；無法在雲端執行的項目於 PR 說明列出。
