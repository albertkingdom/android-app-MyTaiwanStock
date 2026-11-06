## Context

- 主題：`Theme.MyNewsApp` 繼承 `Theme.MaterialComponents.Light.NoActionBar`（深色為 `DayNight`），material 1.5.0，appcompat 1.4.1。淺色主題把 `colorPrimary` 設成白色、`colorOnPrimary` 設成黑色，深色反過來；多個版面與 drawable 把 `?attr/colorOnPrimary` 當「一般文字／圖示色」用（包含搜尋圖示 `ic_baseline_search_24.xml`），`?attr/colorPrimaryVariant` 當狀態列與分頁指示色。Material 3 的 `colorPrimary`／`colorOnPrimary` 是真正的品牌色與其對比色，這些用法換主題後會壞。
- 清單切換：`MainActivity.setupAppBarMenu` 在 Toolbar 中放一顆按鈕，點擊開 `ListPopupWindow`，項目來自 `ListViewModel.appBarMenuItemNameList`，最後一項「編輯列表」導向編輯清單畫面；選取時呼叫 `ListViewModel.changeCurrentFollowingList(position)` 並把位置存到 SharedPreferences `sharedPref` 的 `currentList`。按鈕標題來自 `appBarMenuButtonTitle`。`setupAppBarMenu` 每次 `ListFragment.onStart` 都經由 `showMenuSelectorBtn` 重跑。
- `appBarMenuItemNameList` 是 `allFollowingList.map { ... }`，map 內有副作用：清單為空時建立「Default」，非空時呼叫 `changeCurrentFollowingList(lastViewedListIndex)` 啟動價格管線。它只在被觀察時執行，目前由 `MainActivity` 觀察，所以從分享面板冷啟動到匯入畫面時價格管線也會啟動。`lastViewedListIndex` 只在 `ListFragment.onViewCreated` 由 sharedPref 設定；點選清單不會更新它，所以清單 Flow 重新發出時選取會跳回舊值。`changeCurrentFollowingList` 用 `allFollowingList.value?.get(index)!!`，索引超出範圍會丟例外。
- `appBarMenuButtonTitle`（`currentSelectedFollowingListId.map`）唯一的觀察者在 `setupAppBarMenu`；`uploadNewStockNoToOnlineDB`、`deleteStockNoFromOnlineDB` 用它的 `.value` 當雲端清單名稱，為 null 時直接 return。
- `StockDao.getAllFollowingList` 的查詢沒有 ORDER BY。
- 自選股列（`item_stockinfo.xml` + `StockInfoAdapter.onBindViewHolder`）是帶框卡片；漲跌顯示邏輯直接寫在 adapter 裡，沒有單元測試。列支援左右滑動刪除（`ListFragment` 的 `ItemTouchHelper`）。`isShowAsPercent` 是 `ListFragment` 的欄位，view 重建時 adapter 回到金額模式。
- `fragment_list.xml` 的 FAB 展開三顆按鈕：新增清單（實際導向編輯清單畫面）、新增股票、截圖匯入。`SwipeRefreshLayout`、shimmer、無網路畫面都貼齊父層頂端。
- 編輯清單畫面（`EditFollowingListFragment`）已用 Compose Material 3，提供新增、重新命名、刪除；重新命名與刪除都不同步雲端。`AddFollowingListDialogFragment` 不驗證名稱。雲端清單以名稱為鍵。
- `showMenuSelectorBtn`／`hideMenuSelectorBtn` 的呼叫點：`ListFragment.onStart`、`ListFragment.onStop`、`TradeImportFragment.onStart`、`MainActivity.setupOnClickBottomNav`。

## Design Source

- 來源：本次對話中產生並經使用者確認的 mockup（「Watchlist with tabs」與「⋮ menu: manage lists」兩張），不含左右滑動切換的那張（使用者選擇只靠點分頁切換），⋮ 選單不含「調整順序」（使用者排除排序功能）。
- 無 .dc.html，不建立 design cache。
- 關鍵數值：

| 元素 | 數值 |
| ---- | ---- |
| 頂部列 | `MaterialToolbar`，高度 `?attr/actionBarSize`（M3 預設 64dp，取代目前的 50dp），標題「自選股」用 `?attr/textAppearanceTitleLarge`（22sp） |
| 分頁列 | `TabLayout` `tabMode=scrollable`，選取指示為 3dp 底線，分頁文字 14sp |
| 分頁列「+」 | 48dp 觸控區，加號圖示，左側 1dp `colorOutlineVariant` 分隔線 |
| 標題列（名稱／成交價 · 漲跌） | 11sp，`colorOnSurfaceVariant`，高 32dp |
| 列 | 上下 12dp、左右 16dp padding，列間 1dp `MaterialDividerItemDecoration` |
| 股票名稱 | 16sp Medium，`colorOnSurface` |
| 副標（代號 · 時間） | 12sp，`colorOnSurfaceVariant` |
| 價格 | 16sp Medium |
| 漲跌標籤 | 最小寬 72dp，圓角 8dp，左右 8dp、上下 4dp，13sp Medium |
| 新增按鈕 | `ExtendedFloatingActionButton`，文字「新增」，加號圖示，右下 16dp |

## Goals / Non-Goals

**Goals:**

- 全 App 換成 Material 3（material 1.12.0、`Theme.Material3.DayNight.NoActionBar`），淺色與深色模式都可讀。
- 自選股畫面依 mockup 改版：可捲動分頁切換清單、⋮ 清單管理選單、新的股票列、標題列切換、「新增」延伸 FAB。
- 清單選取狀態集中在 `ListViewModel`，分頁與顯示的股票永遠一致，清單變動時選取不跳。
- 把股票列顯示規則、分頁位置夾限、清單名稱驗證抽成純 Kotlin，寫 JVM 單元測試。

**Non-Goals:**

- 不做清單排序（拖曳調整順序、`followingList` 加排序欄位、Room migration）。使用者明確排除，之後另開 change。
- 不支援左右滑動切換清單（`ViewPager2`）。列上的左右滑動刪除維持現狀。
- 不啟用 dynamic color（桌布取色）。
- 其他畫面只做主題遷移與壞掉顏色的修正，不改版面；個股詳情、統計頁的重新設計之後另開 change。Compose 的編輯清單畫面不改（顏色可能與新主題略有差異）。
- 不改 App Widget 的版面與配色。
- 不把「最後檢視的清單」從位置改成存 id。
- 不新增清單重新命名、刪除的雲端同步（現況就沒有）。

## Decisions

### 主題用固定配色，不開 dynamic color

`Theme.MyNewsApp`（values 與 values-night）改繼承 `Theme.Material3.DayNight.NoActionBar`。用 Material Theme Builder 以深藍灰 `#3D5A80` 為 seed 產生完整的淺色與深色色彩角色，寫進 `values/colors.xml` 與 `values-night/colors.xml`（`md_theme_*` 命名），主題為每個角色設一個 item：primary／onPrimary／primaryContainer／onPrimaryContainer、secondary 四個、tertiary 四個、error 四個、`colorSurface`、`colorOnSurface`、`colorSurfaceVariant`、`colorOnSurfaceVariant`、`colorSurfaceContainerLowest`、`colorSurfaceContainerLow`、`colorSurfaceContainer`、`colorSurfaceContainerHigh`、`colorSurfaceContainerHighest`、`colorSurfaceBright`、`colorSurfaceDim`、`colorOutline`、`colorOutlineVariant`、`colorSurfaceInverse`、`colorOnSurfaceInverse`、`colorPrimaryInverse`、`android:colorBackground`、`colorOnBackground`。移除 `colorPrimaryVariant`、`colorSecondaryVariant` 與舊的 `colorSecondary=teal_200`。不呼叫 `DynamicColors.applyToActivitiesIfAvailable`。

理由：漲跌顏色有固定語意（台股漲紅跌綠），桌布取色會讓品牌色每台手機不同，也讓截圖比對無法重現；不設滿角色會落回 M3 預設的紫色系。

替代方案：開 dynamic color — 否決，理由同上。

### 漲跌標籤顏色用獨立色資源，不用主題 error 色

新增 `stock_up_container`／`on_stock_up_container`（紅）、`stock_down_container`／`on_stock_down_container`（綠），淺色與深色各一組；平盤與無價格用 `colorSurfaceContainerHighest` 與 `colorOnSurfaceVariant`。

理由：Material 3 的 `colorError` 語意是錯誤，不是「漲」；跌也沒有對應的主題角色。

### 狀態列與 Toolbar 用 surface 色

主題設 `android:statusBarColor` 為 `?attr/colorSurface`、淺色模式 `android:windowLightStatusBar=true`、深色模式 `false`。`activity_main.xml` 的 `androidx.appcompat.widget.Toolbar` 換成 `com.google.android.material.appbar.MaterialToolbar`（高度 `?attr/actionBarSize`），移除裡面的 `show_list_menu_button`；同一步移除 `MainActivity` 中所有使用它的程式（見「清單選取狀態集中在 ListViewModel」），讓建置在每一步都通過。targetSdk 34 下 `statusBarColor` 有效；升到 35 時需另行處理 edge-to-edge，不在本次範圍。

### 清單選取狀態集中在 ListViewModel

`ListViewModel` 成為選取狀態的唯一來源：

- `init` 時自己讀 `sharedPref`/`currentList` 到 `lastViewedListIndex`（`AndroidViewModel` 有 Application）。移除 `changeLastViewedListIndex` 與 `ListFragment.changeToLastViewedList`。
- 新增 `selectList(position: Int)`：以 `ListTabSelection.selectedPosition(position, lists.size)` 夾限；null 時不做事；否則更新 `lastViewedListIndex`、寫回 `sharedPref`/`currentList`、設 `currentSelectedFollowingListId`、呼叫 `setupGetStockPriceDataPipe`，並把位置放進新的 `selectedListPosition: LiveData<Int>`。`changeCurrentFollowingList` 由 `selectList` 取代。
- `appBarMenuItemNameList` 改名為 `followingListNames`（`LiveData<List<String>>`，不再附加「編輯列表」）；副作用改為：清單為空時建立 Default；非空時呼叫 `keepSelection(lists)`：目前選取的清單（依 id）仍存在時只更新它的位置（前面有清單被新增或刪除），不重新抓價；不存在（被刪除或尚未選取）時呼叫 `selectList(lastViewedListIndex)`，選取同位置的清單（刪除最後一個時夾限到前一個）。重新命名不會重新抓價。
- `MainActivity` 繼續以 Activity 生命週期觀察 `followingListNames`，只為觸發副作用（冷啟動到匯入畫面時管線也會以保存的位置啟動）。`MainActivity` 刪除 `setupAppBarMenu`、`ListPopupWindow`、`saveCurrentViewedList`、`showMenuSelectorBtn`、`hideMenuSelectorBtn`；`ListFragment.onStart`／`onStop`、`TradeImportFragment.onStart`、`MainActivity.setupOnClickBottomNav` 中的呼叫一併刪除。
- `ListFragment` 只從 `selectedListPosition` 決定選取的分頁，不讀 sharedPref；點分頁呼叫 `listViewModel.selectList(position)`。

理由：審查發現點選清單不更新 `lastViewedListIndex`，會讓清單變動時選取跳回舊值；冷啟動時 Fragment 若自己讀 sharedPref，會出現「分頁與股票不一致」。單一來源同時解決兩者。

### 分頁放在 ListFragment，不用 ViewPager2

`fragment_list.xml` 在清單上方加一列：`TabLayout`（`tabMode=scrollable`）＋固定在尾端的「+」`ImageButton`。

- 觀察 `followingListNames`：若分頁數與名稱數相同，只更新每個分頁的文字；否則移除 listener、`removeAllTabs()`、逐一 `addTab(tab, false)`，再依 `selectedListPosition` 的目前值 `selectTab`，最後重新加上 listener。
- 觀察 `selectedListPosition`：`getTabAt(position)` 為 null（分頁尚未建立）時略過；否則在 listener 移除的狀態下 `selectTab`，並 `post { setScrollPosition(position, 0f, true) }` 讓選取分頁捲入可視範圍。
- listener 的 `onTabSelected` 呼叫 `selectList(tab.position)`；`onTabReselected` 不做事。
- 「+」開 `AddFollowingListDialogFragment`。

理由：價格管線是單一清單的，`ViewPager2` 每頁需要各自的資料流，改動大；使用者也選擇保留列上滑動刪除，與滑動切換衝突。

### 分頁位置夾限抽成 ListTabSelection

新增 `ListTabSelection.selectedPosition(savedPosition: Int, listCount: Int): Int?`：`listCount <= 0` 回傳 null，否則回傳 `savedPosition.coerceIn(0, listCount - 1)`。`ListViewModel.selectList` 使用它，修掉 `changeCurrentFollowingList` 索引超出範圍時的例外。

### 目前清單名稱改用函式取得

刪除 `appBarMenuButtonTitle`。新增 `ListViewModel.currentListName(): String?` = `allFollowingList.value?.find { it.followingListId == currentSelectedFollowingListId.value }?.listName`；`uploadNewStockNoToOnlineDB`、`deleteStockNoFromOnlineDB` 改用它。`allFollowingList` 由 `followingListNames`（被 `MainActivity` 觀察）間接保持活躍，所以 `.value` 有值；重新命名後讀到的是新名稱。

### 清單名稱驗證抽成 ListNameValidator

新增純 Kotlin `ListNameValidator.validate(input: String, existingNames: List<String>, renamingFrom: String?): ListNameResult`，`ListNameResult` 為 `Valid(name: String)`（已 trim）、`Blank`、`Duplicate`。規則：trim 後為空 → Blank；trim 後等於某個既有名稱、且該名稱不是 `renamingFrom` → Duplicate；否則 Valid。

`AddFollowingListDialogFragment` 與 ⋮ 的重新命名對話框都用它：對話框在 `setOnShowListener` 中覆寫 positive button 的 click listener，驗證失敗時在 `TextInputLayout.error` 顯示「請輸入清單名稱」或「已有同名清單」且不關閉；成功才寫入並關閉。新增清單對話框的錯誤訊息設在 `dialog_add_following_list.xml` 既有的 `TextInputLayout`（`outlinedTextField`）上。編輯清單畫面（Compose）的重新命名對話框不在本次範圍，維持現狀。

### 清單依 id 排序

`StockDao.getAllFollowingList` 加上 `ORDER BY followingListId`，讓分頁順序與保存的位置在每次查詢一致。目前 SQLite 實際上也是依 rowid 回傳，所以使用者看到的順序不變；`TradeImportRepository.lists()` 讀同一個 Flow，位置對應一致。

### ⋮ 選單由 ListFragment 提供

新增 `res/menu/list_fragment_options_menu.xml`，包含搜尋（沿用 `options_menu.xml` 的 SearchView 設定）與五個 overflow 項目：新增清單、重新命名、刪除此清單、截圖匯入、管理清單。`ListFragment.onCreateOptionsMenu` 改 inflate 這個檔案；`options_menu.xml` 只有 `ListFragment` 使用，刪除。

- 新增清單：`AddFollowingListDialogFragment().show(childFragmentManager, ...)`。
- 重新命名：`MaterialAlertDialogBuilder` + `TextInputLayout`，預填 `currentListName()`；以 `ListNameValidator` 驗證（`renamingFrom` 為目前名稱），通過時呼叫 `ListViewModel.updateFollowingListName(name, id)`。
- 刪除此清單：`MaterialAlertDialogBuilder` 確認，訊息含清單名稱；確定後呼叫 `ListViewModel.deleteFollowingList(id)`（與編輯清單畫面相同，會一併刪除清單內股票）。
- 截圖匯入：沿用目前 FAB 按鈕的導航與「目前目的地仍是清單頁才導航」的防重複判斷。
- 管理清單：`ListFragmentDirections.actionStockListFragmentToEditFollowingListFragment()`。

### 股票列顯示規則抽成 StockRowFormatter

新增純 Kotlin 的 `StockRowFormatter`：

```kotlin
enum class Trend { UP, DOWN, FLAT }
data class StockRowText(val subtitle: String, val price: String, val change: String, val trend: Trend)
object StockRowFormatter {
    fun format(stock: MsgArray, showAsPercent: Boolean, previousCloseLabel: String): StockRowText
}
```

- subtitle：`currentPrice.toFloatOrNull()` 不為 null 時是「`stockNo` · `time.take(5)`」，否則「`stockNo` · `previousCloseLabel`」（呼叫端傳入字串資源「昨收」）。
- price：`MsgArray.displayPrice` 以 `String.format(Locale.US, "%.2f", ...)` 格式化（無千分位），null 時為「-」。
- trend：`displayPrice` 為 null 或 `diff == 0f` 為 FLAT，大於 0 為 UP，小於 0 為 DOWN。
- change：`displayPrice` 為 null 時「-」；FLAT 時固定為「0.00」或「0.00%」（避免「-0.00」）；否則金額模式為 `diff` 帶正負號兩位小數（正數加「+」），百分比模式為 `diffPercent` 同規則加「%」。

`StockInfoAdapter.onBindViewHolder` 只負責把 `StockRowText` 套到 view 與依 trend 上色。

### 新的股票列與標題列

`item_stockinfo.xml` 改為無框的 `ConstraintLayout`：左側名稱與副標，右側價格與漲跌標籤（`TextView` 以圓角 8dp 的 shape background，顏色在 bind 時依 trend 設 `backgroundTintList` 與文字色）。列間分隔用 `com.google.android.material.divider.MaterialDividerItemDecoration`。

顯示模式移到 `ListViewModel.showAsPercent: MutableLiveData<Boolean>`（預設 false）。`ListFragment` 觀察它，呼叫 `stockAdapter.setMode(...)` 並更新標題列的圖示；標題列右側區塊與漲跌標籤的點擊都呼叫 `listViewModel.toggleShowAsPercent()`。view 重建（從個股詳情返回）時，新的 adapter 由觀察立即拿到目前模式。

### 自選股版面的約束

`fragment_list.xml` 由上而下：分頁列（`TabLayout` + 「+」）→ 標題列 → 內容區。內容區（`SwipeRefreshLayout`、shimmer、無網路畫面）三者都約束在標題列下方到父層底部（高度 `0dp`），不再貼齊父層頂端，因此不會蓋住分頁與標題列。RecyclerView 設 `paddingBottom=88dp` 與 `clipToPadding=false`，捲到底時最後一列不被延伸 FAB 擋住。

### 新增按鈕

`fragment_list.xml` 的 `FloatingActionButton` 與 `buttonContainer` 換成一顆 `ExtendedFloatingActionButton`（文字「新增」），點擊導向 `ListFragmentDirections.actionStockListFragmentToAddStockFragment()`。`setupOnClickFab` 中三顆按鈕的程式移除。

### 其他畫面的主題遷移修正

顏色屬性：

- `res/layout`：`item_following_list.xml`、`fragment_chat.xml` 的 `?attr/colorOnPrimary` 改 `?attr/colorOnSurface`；`fragment_statistic.xml` 的 `tabIndicatorColor`、`tabSelectedTextColor` 改 `?attr/colorPrimary`；`item_dividend_detail.xml` 用 `?colorOnPrimary` 的底色改 `?attr/colorOutlineVariant`。
- `res/drawable`：`ic_baseline_search_24.xml` 的 `android:tint` 改 `?attr/colorOnSurfaceVariant`；`history_record_rect.xml` 沒有使用者，刪除。
- 完成後 `app/src/main/res` 與 `app/src/main/java` 都不再出現 `colorOnPrimary`（作為一般前景色）與 `colorPrimaryVariant`。

Style parent 對照表（values 與 values-night 都要改）：

| Style | 舊 parent | 新 parent | 備註 |
| ----- | --------- | --------- | ---- |
| `AddFollowingListDialogTheme` | `ThemeOverlay.MaterialComponents.Dialog.Alert` | `ThemeOverlay.Material3.MaterialAlertDialog` | 刪除黑白 `colorPrimary` 與按鈕文字色覆寫，用主題色 |
| `MyPositiveTextStyle`、`MyNegativeTextStyle` | `Widget.MaterialComponents.Button.TextButton` | 刪除 | 由上一列移除引用後不再需要 |
| `TextInputLayoutStyle` | `Widget.MaterialComponents.TextInputLayout.OutlinedBox`（深色 `.Dense`） | `Widget.Material3.TextInputLayout.OutlinedBox`（深色 `.Dense`） | 移除黑白 `boxStrokeColor`、`hintTextColor` |
| `Widget.App.TextInputLayout` | `Widget.MaterialComponents.TextInputLayout.FilledBox` | `Widget.Material3.TextInputLayout.FilledBox` | |
| `Widget.App.TextInputLayout.Outlined` | `Widget.MaterialComponents.TextInputLayout.OutlinedBox` | `Widget.Material3.TextInputLayout.OutlinedBox` | overlay 中移除黑色 `colorPrimary` |
| `ThemeOverlay.App.TextInputLayout.Outlined` 的 `editTextStyle` | `Widget.MaterialComponents.TextInputEditText.OutlinedBox` | `Widget.Material3.TextInputEditText.OutlinedBox` | |
| `Widget.App.TextInputLayout.Edit` | `Widget.MaterialComponents.TextInputEditText.FilledBox` | `Widget.Material3.TextInputEditText.FilledBox` | |
| `ThemeOverlay.App.TradeEditDialog` | `ThemeOverlay.MaterialComponents.MaterialAlertDialog` | `ThemeOverlay.Material3.MaterialAlertDialog` | 保留 `dialog_action` |
| `MyPreferenceDialogTheme` | `Theme.MaterialComponents.DayNight.Dialog` | `ThemeOverlay.Material3.Dialog.Alert` | 保留 `windowMinWidthMinor`，刪除黑白 `colorPrimary` |
| `MyChipStyle` | `Widget.MaterialComponents.Chip.Choice` | 刪除 | 沒有使用者 |
| `myFloatingActionButtonStyle` | `Widget.MaterialComponents.FloatingActionButton` | 刪除 | 主題中的 `floatingActionButtonStyle` 一併刪除 |
| `Theme.MyNewsApp.AppBarOverlay`、`.PopupOverlay`、`SearchViewStyle` | AppCompat | 保留 parent | `SearchViewStyle` 的 `android:tint` 改 `?attr/colorOnSurfaceVariant`（淺色、深色） |
| `dialog_trade_edit.xml` 的按鈕 style | `Widget.MaterialComponents.Button.OutlinedButton` | `Widget.Material3.Button.OutlinedButton` | |

底部導覽列：移除自訂 `itemIconTint`、`itemTextColor` 與 `android:background`，加 `app:labelVisibilityMode="labeled"`，使用 M3 預設的 active indicator。`res/color/bottom_nav_color.xml` 只有底部導覽列使用，刪除。

## Implementation Contract

**Behavior**

- 所有畫面呈現 Material 3 外觀；淺色、深色模式下沒有與背景同色的文字、圖示或輸入框邊框。狀態列與頂部列同色。
- 自選股畫面：頂部「自選股」標題、搜尋與 ⋮；下方可捲動分頁（每個清單一個）與固定的「+」；分頁下有「名稱｜成交價 · 漲跌」標題列；股票列依 spec 的範例表顯示；右下「新增」；捲到底最後一列不被擋住。
- 選取的分頁與顯示的股票永遠是同一個清單；新增、重新命名、刪除清單後選取依 spec 規則；重開 App 與分享冷啟動回到保存的分頁；位置超出範圍時夾限並回寫，不閃退。
- ⋮ 五個項目的行為依 spec；新增與重新命名拒絕空白與重複名稱。
- 新增股票、滑動刪除股票時，登入使用者的雲端同步以目前清單名稱進行。

**Interface / data shape**

- `ListTabSelection.selectedPosition(savedPosition: Int, listCount: Int): Int?`
- `StockRowFormatter.format(stock: MsgArray, showAsPercent: Boolean, previousCloseLabel: String): StockRowText`，`StockRowText(subtitle, price, change, trend: Trend)`。
- `ListNameValidator.validate(input: String, existingNames: List<String>, renamingFrom: String?): ListNameResult`，`ListNameResult = Valid(name) | Blank | Duplicate`。
- `ListViewModel`：`followingListNames: LiveData<List<String>>`、`selectedListPosition: LiveData<Int>`、`selectList(position: Int)`、`currentListName(): String?`、`showAsPercent: LiveData<Boolean>`、`toggleShowAsPercent()`。刪除 `appBarMenuItemNameList`、`appBarMenuButtonTitle`、`changeLastViewedListIndex`、`changeCurrentFollowingList`。
- `MainActivity`：刪除 `setupAppBarMenu`、`saveCurrentViewedList`、`showMenuSelectorBtn`、`hideMenuSelectorBtn`。
- SharedPreferences `sharedPref` / `currentList` 的語意不變（分頁位置），只由 `ListViewModel` 寫入；`TradeImportFragment` 讀取它的方式不變。
- `StockDao.getAllFollowingList` 依 `followingListId` 升冪排序。

**Failure modes**

- 清單為空：沒有分頁，`followingListNames` 的副作用建立 Default，Flow 重新發出後顯示一個分頁並選取。
- 名稱空白或重複：對話框不關閉並顯示錯誤，不寫資料庫、不上傳。
- 沒有價格：依 `StockRowFormatter` 顯示「-」與中性色，不閃退。
- 未登入：雲端同步函式維持現狀（不上傳）。

**Acceptance criteria**

- `./gradlew testDebugUnitTest` 通過，包含新的 `StockRowFormatterTest`（spec 範例表每一列）、`ListTabSelectionTest`（夾限範例表每一列）、`ListNameValidatorTest`（名稱範例表每一列）。
- `./gradlew connectedDebugAndroidTest` 中新增的 `FollowingListOrderTest` 在 API 33 模擬器通過。
- `./gradlew assembleDebug` 通過。
- 在 API 33 模擬器上依 tasks 的手動步驟驗證並截圖（淺色、深色），截圖存在 `docs/reviews/screenshots/material3-watchlist-redesign/`（不進版控，附在 PR 說明）。

**Scope boundaries**

- In scope：主題與色彩資源、`activity_main.xml`、自選股畫面的版面與程式、`ListViewModel` 的清單選取與名稱、`MainActivity` 中清單下拉相關程式、`TradeImportFragment` 的下拉按鈕呼叫、`AddFollowingListDialogFragment` 的名稱驗證、`StockDao.getAllFollowingList` 排序、為主題遷移必要的其他版面／drawable 顏色屬性與 style parent 修正。
- Out of scope：Non-Goals 列出的項目；其他畫面的版面調整；Widget；清單重新命名與刪除的雲端同步。

## Risks / Trade-offs

- material 1.12.0 會把 appcompat、fragment、activity 等傳遞相依解析到較新版本 → 任務 1.1 同時把 appcompat 明確升到 1.6.1，用 `./gradlew :app:dependencies --configuration debugRuntimeClasspath` 確認實際版本，先只升版並建置，確認後再改主題。`setHasOptionsMenu` 變成 deprecated 只產生警告。
- 第三方元件：MPAndroidChart 的文字色已在程式設定；Shimmer 不受影響；Preference 1.2.0 的開關維持 AppCompat 外觀 → 手動驗證清單逐頁檢查，只修顏色屬性，Preference 外觀不追求 M3。
- Material 3 的對話框、按鈕預設圓角與字體較大，部分版面可能擠壓 → 只在文字被截斷或重疊時調整尺寸，其餘接受新外觀。
- 清單在雲端以名稱為鍵，而重新命名、刪除都不同步雲端（現況）。⋮ 讓這兩個動作更容易觸發：刪除的清單會在下次「登入後下載雲端資料」時回來；重新命名後新增的股票會上傳到新名稱的文件，舊名稱的雲端清單仍存在 → 本次不處理，記錄於此；之後另開 change 處理清單同步。
- Compose 的編輯清單畫面寫死部分顏色（如 `0xFF121212`、預設 color scheme），與新主題可能略有差異 → 本次不改，之後重新設計時處理。
- `MsgArray.time` 若格式不是「HH:mm:ss」，`take(5)` 會顯示原字串前五字 → 可接受，與 API 目前格式一致。
