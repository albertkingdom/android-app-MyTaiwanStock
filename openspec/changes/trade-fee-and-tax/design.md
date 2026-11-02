## Context

- iOS 參考：`MyTaiwanStock/TradeCost/`（`FeeCalculator`、`FeeSettings`、`FeeTaxFields`、`OverViewCalculator`）、`Screens/TradeEntry/`、`Screens/Settings/ViewController/FeeSettingsViewController.swift`、測試 `MyTaiwanStockTests/TradeCost/`，以及 iOS spec `openspec/specs/trade-fee-and-tax/spec.md`。規則以 iOS 原始碼為準。
- Android 現況：
  - `db/InvestHistory.kt`：`id, stockNo, date, price: Double, amount: Int, status: Int`（0 買、1 賣），資料庫版本 6（`db/StockDatabase.kt`，schema 匯出在 `app/schemas/`）。
  - `ui/detail/AddHistoryFragment.kt`：手續費選項（折扣下拉、盤中零股、自訂）只有畫面，不存；儲存時呼叫 `FirebaseManager.uploadHistoryToOnlineDB`。
  - `ui/detail/CandleStickChartViewModel.kt`：均價不含費用；紀錄清單為空時直接 `return@collect`，不會把均價清成「-」。
  - `ui/adapter/StockHistoryAdapter.kt`：報酬率對買賣都算，用 `Double`。
  - `res/xml/fragment_setting.xml`：`fee_discount`（ListPreference，值 "10"～"100" 的百分比）與 `fee_custom`（EditTextPreference，固定金額）。
  - `ui/list/ListViewModel.kt` 用具名參數建立 `InvestHistory`（雲端同步下來的紀錄），新欄位有預設值就不必改它。
- 這個雲端環境無法連到 `dl.google.com`（Google Maven、Android SDK）與 `jitpack.io`，`./gradlew` 不能在這裡建置。純 Kotlin 的計算與 migration SQL 測試另用一個暫時的 JVM Gradle 專案（Kotlin JVM plugin＋Maven Central）編譯 `tradecost/`、`db/InvestHistoryMigrationSql.kt` 與對應測試來跑；完整建置與 `androidTest` 要在有 Android SDK 的環境跑。

## Goals / Non-Goals

**Goals:**

- 費稅計算、設定解析、欄位狀態、持股成本的規則與 iOS 一致，並以移植的 JVM 測試鎖住。
- 舊資料升級後完整保留，費稅與備註為未知。
- 新增頁、設定頁、個股詳情的行為與 iOS spec 一致（Android 對應見 proposal）。

**Non-Goals:**

- Firestore 上傳費稅、截圖匯入、編輯既有紀錄、統計頁計算、建置工具升級。

## Decisions

### 純 Kotlin package `tradecost`

`com.example.mynewsapp.tradecost` 不 import 任何 `android.*`／`androidx.*`，讓它能在 JVM 測試、也能被之後的截圖匯入 change 重用。內容：

- `TradeSide`（`BUY`=0、`SELL`=1，`fromStatus(Int)`）、`TaxCategory`（`STOCK` 0.003、`DAY_TRADE` 0.0015、`ETF` 0.001、`EXEMPT` 0，一律用字串建 `BigDecimal("0.003")`，不用 `BigDecimal(0.003)`）、`FeeAndTax(fee: Int, tax: Int)`。
- `FeeCalculator.calculate(price: BigDecimal, shares: Int, side, settings, taxCategory): FeeAndTax?`：成交金額＝價格×股數；手續費＝金額×0.001425×折數÷10 無條件捨去，再取 `max(手續費, 最低手續費)`，1000 股（含）以上用整股最低、以下用零股最低；稅＝買進 0，賣出為金額×稅率無條件捨去。價格 ≤ 0、股數 < 1、或費稅超過 `Int.MAX_VALUE`（2147483647）時回傳 null。
- `FeeSettings(discount: BigDecimal, wholeLotMinimumFee: Int, oddLotMinimumFee: Int)` 與 `parseDiscount`、`parse`（依序驗證折數、整股、零股，第一個錯的丟 `FeeSettingsException(reason)`）、`summaryText`、`fromStored(Map<String, *>)`、`toStored(): Map<String, Any>`。
- `BigDecimal` 的 `equals` 會比較 scale（`10.0 != 10`），所以：`parseDiscount` 先檢查只含 ASCII 數字與最多一個小數點（擋掉 "1e1"），範圍用 `compareTo`（0.1～10），小數位檢查用 `stripTrailingZeros().scale() <= 2`（與 iOS「×100 為整數」相同，"2.850" 接受、"2.855" 拒絕），回傳值一律 `stripTrailingZeros()`；`summaryText` 的「無折扣」判斷用 `compareTo(10) == 0`，數字顯示與儲存都用 `toPlainString()`（避免 "1E+1"）。`FeeSettings` 覆寫 `equals`／`hashCode`／`toString`，折數一律用 `stripTrailingZeros()` 後的值。`parseDiscount` 也要求至少一個數字，"." 丟 `FeeSettingsException(INVALID_DISCOUNT)` 而不是 `NumberFormatException`。
- `FeeTaxFields`：iOS 同名 struct 的移植（`editFee`／`editTax`／`endEditing*`／`refresh(estimate, side)`）。
- `TradeInput`：`parsePrice`（去頭尾空白；只接受 ASCII 數字與最多一個小數點，且至少一個數字，"." 回傳 null；結果必須 > 0 才算有效價格由呼叫端判斷）、`parseShares`（ASCII 數字、1～`Int.MAX_VALUE`）、`parseFeeOrTax`（去頭尾空白後只接受 ASCII 數字、0～2147483647）。Kotlin 的 `toIntOrNull` 會接受全形數字，所以先檢查 ASCII。
- `TradeEntryForm`：新增頁的狀態（買賣、稅別、價格與股數文字、`FeeTaxFields`），對應 iOS `AddHistoryViewModel` 的計算部分；ViewModel 包住它，Fragment 只負責畫面。
- `HoldingsCalculator`（iOS `OverViewCalculator`）：`TradeLine(side, shares, price: BigDecimal, fee: Int?)`、`amount`、`averageBuyPrice()`（含買進手續費，未知當 0）、`averageSellPrice()`（不含費用）、`hasBuyWithUnknownFee`、`marketValue(currentPrice)`（持股 > 0 才有值）、`profitPercent(line, currentPrice)`（只有買進）、`format`（兩位、HALF_UP、null 顯示「-」）、`formatMarketValue`（HALF_UP 到整數元、`toPlainString()`，null 顯示「-」）。持股數與手續費加總用 `Long` 累加，避免 Int 溢位；`amount` 對外是 `Long`。

**為什麼用 `BigDecimal`**：iOS 用 `Decimal` 是為了讓 2.8 折、112.55 元這類數字不因二進位誤差少一元（spec 的「1,000,000×0.001425×0.28 剛好 399」案例）。除法用 `MathContext.DECIMAL128`，測試用 `compareTo` 比較數值。`InvestHistory.price` 是 `Double`，用 `BigDecimal.valueOf(double)`（走 `Double.toString` 的最短表示）轉成 112.55，而不是 `BigDecimal(double)` 的 112.5500000000000113…。

替代方案：直接用 `Double` 並在最後四捨五入。否決，因為 2.8 折案例會算出 398。

### 手續費設定存在 `SharedPreferences`

- 新 key：`fee_discount_decimal`（String，例如 "2.8"；存文字而非 Float，避免二進位誤差）、`fee_minimum_whole_lot`（Int）、`fee_minimum_odd_lot`（Int）。三個值用同一個 `SharedPreferences.Editor` 一起 `apply()`。
- 讀取規則（`FeeSettings.fromStored(prefs.all)`，不寫入任何值）：
  - 折數：`fee_discount_decimal` 是 `String` 且通過 `parseDiscount` 就用它；否則若舊 key `fee_discount` 是 1～100 的整數字串，折數＝該值÷10（舊版是百分比，"60" 代表 6 折）；都沒有就是 10（無折扣）。
  - 最低手續費：值必須是 `Int` 且在 0～2147483647，否則用預設（整股 20、零股 1）。字串 "20"、Float、Long、Boolean 都視為無效。
  - 舊 key `fee_discount`、`fee_custom` 不刪、不改。
- 包一層 `ui/setting/FeeSettingsStore`（`load()`、`save(settings)`）處理 `PreferenceManager.getDefaultSharedPreferences`（與舊 `fee_discount` 同一個檔），邏輯本身在 `FeeSettings` 用 `Map` 測。

替代方案：用 `EditTextPreference` 三個獨立欄位。否決，因為 spec 要求三個值一起驗證、有一個錯就全部不存並停在頁面上，獨立的 preference 各自存檔做不到。

### 設定頁

`fragment_setting.xml` 的手續費分類只留一列 `Preference`（key `fee_settings`），`SettingFragment` 在 `onResume` 用 `FeeSettingsStore.load().summaryText` 更新摘要，點擊導到新的 `FeeSettingsFragment`（`navigation/settings.xml` 新增 destination 與 action）。`FeeSettingsFragment` 是一般 `Fragment`：三個 `TextInputLayout`（折數 `numberDecimal`、兩個最低手續費 `number`）、說明文字、選單上的「儲存」。儲存時 `FeeSettings.parse`：成功就 `save` 並 `popBackStack()`；失敗就把錯誤訊息顯示在對應欄位的 `error`，不存、不離開；每次儲存前先清掉三個欄位之前的錯誤。

### Room migration 6 → 7

- `InvestHistory` 新增 `fee: Int? = null`、`tax: Int? = null`、`memo: String? = null`。三個都可為空、沒有 SQL 預設值，舊紀錄就是 null，與 iOS 的「未知」一致，也避免 Room 對 `DEFAULT` 值的 schema 驗證差異。
- SQL 放在純 Kotlin 的 `db/InvestHistoryMigrationSql.kt`（`V6_TO_V7: List<String>`：三個 `ALTER TABLE investHistory ADD COLUMN ...`），`StockDatabase.MIGRATION_6_7` 逐一執行；資料庫版本改 7，`addMigrations(MIGRATION_6_7)`。
- 測試分兩層：
  - JVM：`InvestHistoryMigrationSqlTest` 用 `org.xerial:sqlite-jdbc`（`testImplementation`）以 `6.json` 的 `investHistory` 建表 SQL 建立 v6 表、塞 3 買 2 賣，跑 `V6_TO_V7`，檢查 5 筆資料原樣保留、新欄位為 null、新欄位存在且型別為 INTEGER／INTEGER／TEXT。
  - 裝置：`androidTest` 的 `StockDatabaseMigrationTest` 用 `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), StockDatabase::class.java)` 建立 v6、插入資料，再 `runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)`，驗證 migration 後的表與 Room entity 一致且資料保留。需要 `androidTestImplementation "androidx.room:room-testing:$room_version"`、`androidx.test:runner`、`androidx.test:core` 與 `sourceSets.androidTest.assets.srcDirs += files("$projectDir/schemas")`。`7.json` 由 kapt 在建置時產生，要在能建置的環境產生後一併 commit；PR 說明把「commit `7.json` 並跑過 `androidTest`」列為合併前必做。

### 新增紀錄頁

- 版面：手續費與交易稅欄位都用 `inputType="number"`（對應 iOS 只允許數字），鍵盤無法輸入小數點與負號，這兩種值只在 `TradeEntryForm` 的 JVM 測試驗證。移除 `fee_option_spinner`、`fee_discount_spinner`、`fee_one_dollar`、`fee_custom`；新增手續費 `TextInputLayout`（`number`，後綴「元」）、稅別列（`AppCompatSpinner`，一般股票／現股當沖／ETF／免稅，預設一般股票）、交易稅 `TextInputLayout`（後綴「元」）、備註 `TextInputLayout`（多行）。稅別與交易稅兩列在買進時 `GONE`。整頁包進 `NestedScrollView`，避免鍵盤擋住。
- 狀態：`AddHistoryViewModel` 改用 `by viewModels { factory }` 取得（目前是 `AddHistoryViewModel(repository)` 直接建立，旋轉會重建），持有 `TradeEntryForm` 與 `FeeSettings`（建立時由 `FeeSettingsStore.load()` 取得），對外提供 `StateFlow<TradeEntryUiState>`（feeText、taxText、side、taxCategory）。
- 旋轉或 view 重建時，系統會在 `onViewCreated` 之後還原 EditText、chip、spinner 的狀態，這些還原事件不能被當成使用者輸入。Fragment 在 `onViewStateRestored` 才掛 TextWatcher 與 listener，掛上後立刻以 ViewModel state 重繪費稅欄位、chip 與 spinner（價格、股數、備註以還原的文字為準，掛上後呼叫一次 `changeTrade` 同步）。chip 與 spinner 的 listener 同樣看 `isRendering`，程式設定選取狀態時不觸發 `selectSide`／`selectTaxCategory`。選擇的日期移進 ViewModel（原本是 Fragment 欄位，旋轉後會遺失）。程序被系統回收後 ViewModel 重建，買賣、稅別與手動旗標回到預設，費稅欄位以計算值重繪；不使用 `SavedStateHandle`。
- 程式設定文字會觸發 `TextWatcher`，若直接呼叫 `editFee` 會把計算值誤判成手動輸入。Fragment 在把 state 寫回欄位時設 `isRendering = true`，watcher 看到旗標就忽略；而且只有文字不同才 `setText`，保留游標。
- 離開欄位用 `OnFocusChangeListener`（失去焦點時呼叫 `endEditingFee`／`endEditingTax`）。點選單的「儲存」或點買賣 chip 不會移走焦點，所以 iOS 的 `view.endEditing(true)` 在 Android 改成：存檔與切換買賣前先呼叫 `endEditingFee`、`endEditingTax`，再 `clearFocus()` 並收鍵盤。
- 儲存：價格與股數改用 `TradeInput.parsePrice`／`parseShares` 解析（與計算同一個定義，擋掉 "Infinity"、"1e50"、負數），價格存成 `parsed.toDouble()`，再交給 `CheckUserInputHistoryDataUseCase` 檢查股號、股數、價格、日期；股數無法解析時的錯誤訊息改為「請輸入 1 到 2147483647 的整數」，價格為「請輸入大於 0 的數字」；手續費用 `TradeInput.parseFeeOrTax`，賣出的稅也檢查，買進的稅固定存 0；不合法時在欄位顯示「請輸入 0 到 2147483647 的整數」，不寫入。合法時寫入 `InvestHistory(fee, tax, memo)`；Firestore 上傳維持原本的欄位。

### 個股詳情

- `CandleStickChartFragment` 目前在頁首以 `args.stockPrice.toFloat()` 顯示股價，現價為 "-" 時會 `NumberFormatException`；改成 `toBigDecimalOrNull()`，無法解析時頁首顯示「-」、市值與報酬率顯示「-」。
- `CandleStickChartViewModel` 收到紀錄清單（包含空清單）就用 `HoldingsCalculator` 產生 `HoldingsSummary`（持股數、市值文字、買入均價文字、賣出均價文字、是否顯示未知手續費提示），以 `StateFlow` 提供，初始值為持股 0、其他「-」；現價由 Fragment 用 `args.stockPrice.toBigDecimalOrNull()` 傳入。修正空清單時不更新的問題。
- 版面：「買入均價」改成「買入均價(含費用)」；總覽下方新增提示 `TextView`「部分買進紀錄沒有手續費資料，均價可能偏低」，預設 `GONE`。
- `item_stock_history.xml`：`content_container` 與根元素都改 `wrap_content`（根元素 `minHeight` 60dp），第二行顯示「手續費 6・稅 22」，未知為「手續費 -・稅 -」。
- `StockHistoryAdapter`：報酬率改用 `HoldingsCalculator.profitPercent`；賣出與沒有值時直接顯示「-」（不套 `stockhistory_revenue` 的 `%` 格式）；顏色依四捨五入到兩位後的值（與 iOS 相同）：正值紅色、負值綠色、0 用一般文字色。

## Implementation Contract

**行為**：見 spec `trade-fee-and-tax` 的七個 requirement；計算規則與 iOS `TradeCost/` 相同，Android 差異列在 proposal 的對應表。

**介面與資料形狀**（package `com.example.mynewsapp.tradecost`，純 Kotlin）：

```kotlin
enum class TradeSide(val status: Int) { BUY(0), SELL(1); companion object { fun fromStatus(status: Int): TradeSide } }
enum class TaxCategory(val rate: BigDecimal, val title: String) { STOCK, DAY_TRADE, ETF, EXEMPT }
data class FeeAndTax(val fee: Int, val tax: Int)
object FeeCalculator { fun calculate(price: BigDecimal, shares: Int, side: TradeSide, settings: FeeSettings, taxCategory: TaxCategory): FeeAndTax? }
class FeeSettings(val discount: BigDecimal = 10, val wholeLotMinimumFee: Int = 20, val oddLotMinimumFee: Int = 1) {
    val summaryText: String
    fun toStored(): Map<String, Any>               // fee_discount_decimal: String, fee_minimum_whole_lot: Int, fee_minimum_odd_lot: Int
    companion object {
        fun fromStored(values: Map<String, *>): FeeSettings
        fun parseDiscount(text: String?): BigDecimal                       // throws FeeSettingsException(INVALID_DISCOUNT)
        fun parse(discountText: String?, wholeLotMinimumText: String?, oddLotMinimumText: String?): FeeSettings
    }
}
class FeeSettingsException(val reason: Reason) : Exception()   // Reason: INVALID_DISCOUNT, INVALID_WHOLE_LOT_MINIMUM_FEE, INVALID_ODD_LOT_MINIMUM_FEE，各帶中文訊息
object TradeInput { fun parsePrice(text: String?): BigDecimal?; fun parseShares(text: String?): Int?; fun parseFeeOrTax(text: String?): Int? }
class FeeTaxFields { feeText; taxText; feeIsManual; taxIsManual; fee: Int?; tax: Int?; editFee(); editTax(); endEditingFee(); endEditingTax(); refresh(estimate: FeeAndTax?, side) }
class TradeEntryForm(settings: () -> FeeSettings) {
    side; taxCategory; feeText; taxText
    fun changeTrade(priceText: String?, sharesText: String?); fun selectSide(side); fun selectTaxCategory(category)
    fun editFee(text); fun editTax(text); fun endEditingFee(); fun endEditingTax()
    fun feeAndTaxToSave(): FeeTaxResult        // Valid(fee, tax) 或 Invalid(feeInvalid: Boolean, taxInvalid: Boolean)；買進 tax 固定 0
}
data class TradeLine(val side: TradeSide, val shares: Int, val price: BigDecimal, val fee: Int?)
class HoldingsCalculator(lines: List<TradeLine>) {
    val amount: Long; val hasBuyWithUnknownFee: Boolean
    fun averageBuyPrice(): BigDecimal?; fun averageSellPrice(): BigDecimal?; fun marketValue(currentPrice: BigDecimal?): BigDecimal?
    companion object { fun profitPercent(line: TradeLine, currentPrice: BigDecimal?): BigDecimal?; fun format(value: BigDecimal?): String; fun formatMarketValue(value: BigDecimal?): String; fun priceOf(value: Double): BigDecimal }
}
```

資料庫：`investHistory` 新增 `fee INTEGER`、`tax INTEGER`、`memo TEXT`（都可為 NULL、無預設值），版本 7。

**失敗模式**：
- 計算無結果（價格／股數無效、超出 Int）→ 未手動輸入的欄位顯示空白；儲存時費稅驗證失敗、欄位顯示錯誤、不寫入。
- 設定頁任一值無效 → 對應欄位顯示錯誤訊息，三個值都不寫入，頁面不關閉。
- 偏好設定裡的值型別或範圍不對 → 讀成預設值，不覆寫。
- 現價無法解析（例如 "-"）→ 市值與報酬率顯示「-」。

**驗收標準**：tasks.md 每個任務的驗證項目通過；JVM 測試涵蓋 iOS `MyTaiwanStockTests/TradeCost/` 的所有案例（換成 Android 的 key 與型別）。

**範圍邊界**：不改 `ui/list/`、`api/`、`firebase/`、統計頁與 `CalculateAssetUseCase`；`app/build.gradle` 只加測試相依與 androidTest assets。

## Risks / Trade-offs

- [這裡無法執行 `./gradlew` 與模擬器] → 純邏輯與 migration SQL 用 JVM 暫時專案跑測試；PR 說明列出還沒在這裡驗證的項目（Android 編譯、`7.json` 產生、`androidTest`、手動 UI 驗證），由能建置的環境補跑。
- [`7.json` 未 commit 時 `MigrationTestHelper` 找不到 schema] → 第一次建置會由 kapt 產生，PR 說明提醒要 commit。
- [舊版 `fee_discount` 讀成折數可能與使用者當初理解不同] → 舊設定頁標題是「手續費預設折扣(%)」，"60" 就是 6 折，與現行台灣券商「x 折」說法一致；新設定頁會顯示讀到的值，使用者可以直接改。
- [`build-modernization` 同時改 `app/build.gradle`] → 只加三行測試相依與一行 `sourceSets`，衝突時手動合併。
- [版號：分支用 1.15，`versionName` 仍是 1.12（`versionCode` 13）] → 本 change 不改版號，避免與平行 session 衝突；由使用者在發版前決定，PR 說明註明。
- [Android 沒有 iOS 的 32767 股上限] → `amount` 是 Int，不加人為上限；費稅超過 Int 範圍時計算回傳 null、欄位空白，儲存時被費稅驗證擋下。

## Migration Plan

1. 使用者升級後第一次開 App，Room 執行 `MIGRATION_6_7`，只新增三個可為空的欄位，不重建表、不刪資料。
2. 回滾：若要退回版本 6 的 App，Room 會因版本降級失敗；目前沒有 `fallbackToDestructiveMigration`，所以不提供降級，與既有 migration 一致。

## Open Questions

（無）
