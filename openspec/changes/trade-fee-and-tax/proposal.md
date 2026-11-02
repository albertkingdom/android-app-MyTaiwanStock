## Why

iOS 版每筆買賣紀錄都存手續費與證交稅，用同一套規則計算，成本與均價也把費用算進去；Android 版的 `InvestHistory` 沒有費稅與備註欄，新增頁的手續費選項只是畫面，不會存，個股詳情的均價也不含費用。這個 change 把 iOS 的費稅功能移植到 Android（差距評估見 `docs/ios-parity-assessment.md`）。

## What Changes

- 新增純 Kotlin 的費稅計算（`FeeCalculator`）、手續費設定（`FeeSettings`）、費稅欄位狀態（`FeeTaxFields`）與持股成本計算（`HoldingsCalculator`，對應 iOS `OverViewCalculator`），放在新 package `com.example.mynewsapp.tradecost`，不依賴 Android framework，並移植 iOS `MyTaiwanStockTests/TradeCost/` 的測試案例。
- Room：`InvestHistory` 新增可為空的 `fee`、`tax`（整數元）與 `memo`（文字，預設空字串），資料庫版本 6 → 7，migration 只加欄位，舊紀錄保留，費稅為未知（null）。
- 新增紀錄頁：移除舊的手續費選項（折扣下拉、盤中零股 1 元、自訂元），改成可編輯的手續費與交易稅欄位（預設帶入計算值）、賣出時的稅別選擇、備註欄位；儲存時寫入顯示的費稅值。
- 設定頁：手續費改成一列摘要（例如「2.8 折・最低 20 / 1 元」），點進去是新的手續費設定頁，可輸入折數與整股／零股最低手續費，三個值一起驗證、一起儲存。舊版的折扣（`fee_discount`，百分比）讀成對應折數；舊版自訂手續費（`fee_custom`）不再使用、不刪除。
- 個股詳情：持股數＝買進股數－賣出股數；買入均價含買進手續費並標示「含費用」，四捨五入到兩位；沒有買進時顯示「-」；持股為 0 或負數時市值顯示「-」；每筆紀錄第二行顯示手續費與交易稅，未知顯示「-」；有買進紀錄沒有手續費資料時顯示提示；買進紀錄的報酬率以含手續費的成本計算，賣出紀錄不顯示報酬率。
- 不上傳費稅到 Firestore（雲端備份不在範圍內），既有上傳欄位不變。

### iOS 與 Android 對應

| iOS | Android |
|---|---|
| `UserDefaults` 的折數（Double）與最低手續費 | `SharedPreferences`：折數存成文字（避免二進位小數誤差），最低手續費存成 Int |
| 舊版折扣選項（位置＋1） | 舊版 `fee_discount`（10～100 的百分比字串）÷ 10 |
| 舊版固定金額（`feeMode`、`userDefinedFeeInDollars`） | 舊版 `fee_custom`（忽略、不刪） |
| Core Data `fee`／`tax`（Int32，可為 nil） | Room `fee`／`tax`（`Int?`） |
| 備註另開 `MemoViewController` | 同一頁的多行備註欄位 |
| 股數上限 32767（Int16） | Android 的 `amount` 是 Int，不設 32767 上限；費稅超過 2147483647 時不給計算值，儲存時擋下 |
| Firestore 上傳費稅 | 不做（雲端備份不在範圍內） |

## Capabilities

### New Capabilities

- `trade-fee-and-tax`: 計算、儲存與顯示每筆買賣的手續費與交易稅，手續費設定，以及含費用的持股成本計算。

### Modified Capabilities

(none)

## Impact

- Affected specs: `trade-fee-and-tax`（新增）
- Affected code:
  - New: app/src/main/java/com/example/mynewsapp/tradecost/（FeeCalculator.kt、FeeSettings.kt、FeeTaxFields.kt、HoldingsCalculator.kt、TradeInput.kt、TradeEntryForm.kt）
  - New: app/src/main/java/com/example/mynewsapp/db/InvestHistoryMigrationSql.kt
  - New: app/src/main/java/com/example/mynewsapp/ui/setting/FeeSettingsFragment.kt、FeeSettingsStore.kt
  - New: app/src/main/res/layout/fragment_fee_settings.xml
  - New: app/src/test/java/com/example/mynewsapp/tradecost/（各計算的 JVM 測試）、app/src/test/java/com/example/mynewsapp/db/InvestHistoryMigrationSqlTest.kt
  - New: app/src/androidTest/java/com/example/mynewsapp/db/StockDatabaseMigrationTest.kt
  - Modified: app/src/main/java/com/example/mynewsapp/db/InvestHistory.kt、StockDatabase.kt
  - Modified: app/src/main/java/com/example/mynewsapp/ui/detail/AddHistoryFragment.kt、AddHistoryViewModel.kt、CandleStickChartFragment.kt、CandleStickChartViewModel.kt
  - Modified: app/src/main/java/com/example/mynewsapp/ui/adapter/StockHistoryAdapter.kt
  - Modified: app/src/main/java/com/example/mynewsapp/ui/setting/SettingFragment.kt
  - Modified: app/src/main/res/layout/fragment_add_history.xml、fragment_candle_stick_chart.xml、item_stock_history.xml
  - Modified: app/src/main/res/xml/fragment_setting.xml、app/src/main/res/navigation/settings.xml、app/src/main/res/values/strings.xml
  - Removed: app/src/main/res/values/fee_options.xml、fee_discount_option.xml（舊手續費選項，只有新增頁與舊設定列使用）
  - Modified: app/build.gradle（androidTest 的 room-testing 與 schema assets、JVM 測試的 sqlite-jdbc）
  - Generated on build: app/schemas/com.example.mynewsapp.db.StockDatabase/7.json
