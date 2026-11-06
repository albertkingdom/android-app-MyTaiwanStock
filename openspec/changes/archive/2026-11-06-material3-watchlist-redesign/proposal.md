## Why

App 的外觀仍是 2021 年左右的 Material Design 2（`Theme.MaterialComponents`、material 1.5.0）：紫色狀態列、全大寫的清單按鈕、黑色 FAB、帶框卡片列，看起來過時。自選股是最常用的畫面，清單切換藏在標題列的下拉選單裡，漲跌只是角落的小字，資訊層次不清楚。專案已經引入 Compose Material 3，但 View 端仍是 Material 2，兩邊風格不一致。

## What Changes

- App 主題從 `Theme.MaterialComponents` 改為 `Theme.Material3.DayNight`，material 函式庫升到 1.12；淺色與深色模式都要可讀。
- 所有畫面跟著換上 Material 3 的顏色、字體、按鈕、對話框、輸入框與底部導覽列；只修正因主題屬性語意改變而壞掉的顏色或樣式，不改其他畫面的版面。
- 自選股畫面重新設計：
  - 用可橫向捲動的分頁（tabs）切換清單，取代標題列的下拉選單；分頁列尾端有「+」可新增清單。切換只靠點分頁，不支援左右滑動切換，列上的左右滑動刪除維持不變。
  - 標題列的 ⋮ 選單提供：新增清單、重新命名、刪除此清單、截圖匯入、管理清單（開啟既有的編輯清單畫面）。取代下拉選單中的「編輯列表」。
  - 股票列改為以分隔線區隔的清單列：左側股票名稱與「代號 · 時間」（沒有成交價時顯示「代號 · 昨收」），右側較大的價格與實心的漲跌標籤（漲紅、跌綠、平盤中性色）。
  - 清單上方加一行小標題列，並提供價格／百分比切換。
  - FAB 改為帶文字的「新增」，只用來新增股票；原本 FAB 展開的三個按鈕（新增清單、新增股票、截圖匯入）移除。
- 新增清單與重新命名都拒絕空白與重複的名稱。
- 清單選取狀態集中在 `ListViewModel`：選取的分頁與顯示的股票永遠一致，新增、重新命名、刪除清單後選取不會跳回舊分頁；新增或刪除股票的雲端同步繼續使用目前清單名稱。
- 底部導覽列改為 Material 3 樣式，所有分頁都顯示標籤，選取中的分頁有膠囊底色。

## Non-Goals

（寫在 design.md 的 Goals / Non-Goals。）

## Capabilities

### New Capabilities

- `app-theme`: App 使用的 Material 3 主題、淺色／深色模式配色與狀態列外觀
- `watchlist-screen`: 自選股畫面的清單分頁切換、⋮ 清單管理選單、股票列的價格與漲跌顯示、新增股票按鈕

### Modified Capabilities

(none)

## Impact

- Affected specs: `app-theme`（新增）、`watchlist-screen`（新增）
- Affected code:
  - New:
    - app/src/main/java/com/example/mynewsapp/ui/list/StockRowFormatter.kt
    - app/src/main/java/com/example/mynewsapp/ui/list/ListTabSelection.kt
    - app/src/main/java/com/example/mynewsapp/ui/list/ListNameValidator.kt
    - app/src/test/java/com/example/mynewsapp/ui/list/ListNameValidatorTest.kt
    - app/src/androidTest/java/com/example/mynewsapp/db/FollowingListOrderTest.kt
    - app/src/main/res/menu/list_fragment_options_menu.xml
    - app/src/test/java/com/example/mynewsapp/ui/list/StockRowFormatterTest.kt
    - app/src/test/java/com/example/mynewsapp/ui/list/ListTabSelectionTest.kt
  - Modified:
    - app/build.gradle
    - app/src/main/res/values/themes.xml
    - app/src/main/res/values-night/themes.xml
    - app/src/main/res/values/colors.xml
    - app/src/main/res/values-night/colors.xml
    - app/src/main/res/layout/activity_main.xml
    - app/src/main/res/layout/fragment_list.xml
    - app/src/main/res/layout/item_stockinfo.xml
    - app/src/main/res/layout/fragment_statistic.xml
    - app/src/main/res/layout/item_following_list.xml
    - app/src/main/res/layout/item_dividend_detail.xml
    - app/src/main/res/layout/fragment_chat.xml
    - app/src/main/res/layout/dialog_trade_edit.xml
    - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
    - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
    - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
    - app/src/main/java/com/example/mynewsapp/ui/adapter/StockInfoAdapter.kt
    - app/src/main/java/com/example/mynewsapp/db/StockDao.kt
    - app/src/main/java/com/example/mynewsapp/ui/list/AddFollowingListDialogFragment.kt
    - app/src/main/java/com/example/mynewsapp/ui/tradeimport/TradeImportFragment.kt
    - app/src/main/res/drawable/ic_baseline_search_24.xml
    - app/src/main/res/layout/dialog_add_following_list.xml
  - Removed:
    - app/src/main/res/layout/menu_list_selector_item.xml
    - app/src/main/res/menu/options_menu.xml
    - app/src/main/res/color/bottom_nav_color.xml
    - app/src/main/res/drawable/history_record_rect.xml
