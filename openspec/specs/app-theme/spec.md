# app-theme Specification

## Purpose

Defines the Material 3 theme the app uses for every screen, in light and dark mode, so that the View-based screens match the Compose Material 3 screens and stay readable in both modes.

## Requirements

### Requirement: App uses a Material 3 DayNight theme

The app theme SHALL inherit from `Theme.Material3.DayNight.NoActionBar` and SHALL be built with the Material Components library version 1.12.0. The app SHALL NOT enable dynamic color; it SHALL use one fixed light color scheme and one fixed dark color scheme defined in the app's color resources. Both schemes SHALL set every Material 3 color role the theme exposes (primary, secondary, tertiary, error and their container and "on" roles, surface, every surface container level, surface variant, outline, outline variant, inverse roles, and `android:colorBackground`), so that no role falls back to the library's default palette. The theme SHALL NOT set the Material 2 roles `colorPrimaryVariant` and `colorSecondaryVariant`.

#### Scenario: Theme follows the system dark mode setting

- **WHEN** the device is in light mode and the user opens any screen
- **THEN** the screen uses the light color scheme
- **WHEN** the device is in dark mode and the user opens any screen
- **THEN** the screen uses the dark color scheme

#### Scenario: Every color role comes from the app

- **WHEN** a reviewer compares the theme's color items with the Material 3 color role list
- **THEN** each role has an item in both the light and the dark theme, and each item points to an app color resource

#### Scenario: Wallpaper colors do not change the theme

- **WHEN** the app runs on Android 12 or later with any wallpaper
- **THEN** the primary color is the app's fixed primary color, not a color derived from the wallpaper


<!-- @trace
source: material3-watchlist-redesign
updated: 2026-11-06
code:
  - app/src/main/res/values-night/themes.xml
  - app/src/main/res/values/strings.xml
  - app/src/main/res/drawable/history_record_rect.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListNameValidator.kt
  - app/src/main/res/menu/options_menu.xml
  - app/src/main/java/com/example/mynewsapp/ui/tradeimport/TradeImportFragment.kt
  - app/src/main/res/layout/fragment_list.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
  - app/src/main/res/layout/item_dividend_detail.xml
  - app/src/main/java/com/example/mynewsapp/db/StockDao.kt
  - app/src/main/res/color/bottom_nav_color.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListNameDialogs.kt
  - app/src/main/res/layout/menu_list_selector_item.xml
  - app/src/main/res/layout/fragment_statistic.xml
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - app/src/main/res/drawable/change_label_background.xml
  - app/src/main/res/values/colors.xml
  - docs/known-issues.md
  - app/src/main/res/layout/dialog_trade_edit.xml
  - app/src/main/java/com/example/mynewsapp/ui/adapter/StockInfoAdapter.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/EditFollowingListFragment.kt
  - app/src/main/res/drawable/ic_baseline_search_24.xml
  - app/build.gradle
  - app/src/main/res/drawable/ic_swap_horiz_24.xml
  - app/src/main/res/layout/fragment_chat.xml
  - app/src/main/res/layout/item_stockinfo.xml
  - app/src/main/res/layout/item_following_list.xml
  - app/src/main/res/menu/list_fragment_options_menu.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListTabSelection.kt
  - app/src/main/res/layout/fragment_candle_stick_chart.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/StockRowFormatter.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - app/src/main/res/values/themes.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/AddFollowingListDialogFragment.kt
  - app/src/main/res/values-night/colors.xml
  - app/src/main/res/layout/activity_main.xml
tests:
  - app/src/test/java/com/example/mynewsapp/ui/list/StockRowFormatterTest.kt
  - app/src/androidTest/java/com/example/mynewsapp/ui/list/EditFollowingListDeleteTest.kt
  - app/src/androidTest/java/com/example/mynewsapp/db/FollowingListOrderTest.kt
  - app/src/test/java/com/example/mynewsapp/ui/list/ListNameValidatorTest.kt
  - app/src/test/java/com/example/mynewsapp/ui/list/ListTabSelectionTest.kt
-->

---
### Requirement: Status bar matches the surface

The status bar SHALL use the theme's surface color, with dark status bar icons in light mode and light status bar icons in dark mode. The app SHALL NOT show the previous purple status bar.

#### Scenario: Light mode status bar

- **WHEN** the device is in light mode and the stock list is shown
- **THEN** the status bar background equals the top app bar background and its icons are dark

#### Scenario: Dark mode status bar

- **WHEN** the device is in dark mode and the stock list is shown
- **THEN** the status bar background equals the top app bar background and its icons are light


<!-- @trace
source: material3-watchlist-redesign
updated: 2026-11-06
code:
  - app/src/main/res/values-night/themes.xml
  - app/src/main/res/values/strings.xml
  - app/src/main/res/drawable/history_record_rect.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListNameValidator.kt
  - app/src/main/res/menu/options_menu.xml
  - app/src/main/java/com/example/mynewsapp/ui/tradeimport/TradeImportFragment.kt
  - app/src/main/res/layout/fragment_list.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
  - app/src/main/res/layout/item_dividend_detail.xml
  - app/src/main/java/com/example/mynewsapp/db/StockDao.kt
  - app/src/main/res/color/bottom_nav_color.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListNameDialogs.kt
  - app/src/main/res/layout/menu_list_selector_item.xml
  - app/src/main/res/layout/fragment_statistic.xml
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - app/src/main/res/drawable/change_label_background.xml
  - app/src/main/res/values/colors.xml
  - docs/known-issues.md
  - app/src/main/res/layout/dialog_trade_edit.xml
  - app/src/main/java/com/example/mynewsapp/ui/adapter/StockInfoAdapter.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/EditFollowingListFragment.kt
  - app/src/main/res/drawable/ic_baseline_search_24.xml
  - app/build.gradle
  - app/src/main/res/drawable/ic_swap_horiz_24.xml
  - app/src/main/res/layout/fragment_chat.xml
  - app/src/main/res/layout/item_stockinfo.xml
  - app/src/main/res/layout/item_following_list.xml
  - app/src/main/res/menu/list_fragment_options_menu.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListTabSelection.kt
  - app/src/main/res/layout/fragment_candle_stick_chart.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/StockRowFormatter.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - app/src/main/res/values/themes.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/AddFollowingListDialogFragment.kt
  - app/src/main/res/values-night/colors.xml
  - app/src/main/res/layout/activity_main.xml
tests:
  - app/src/test/java/com/example/mynewsapp/ui/list/StockRowFormatterTest.kt
  - app/src/androidTest/java/com/example/mynewsapp/ui/list/EditFollowingListDeleteTest.kt
  - app/src/androidTest/java/com/example/mynewsapp/db/FollowingListOrderTest.kt
  - app/src/test/java/com/example/mynewsapp/ui/list/ListNameValidatorTest.kt
  - app/src/test/java/com/example/mynewsapp/ui/list/ListTabSelectionTest.kt
-->

---
### Requirement: Text stays readable after the theme migration

Every text, icon and input field on every screen SHALL be readable in both light and dark mode after the migration. A view or drawable that used `colorOnPrimary` or `colorPrimaryVariant` as a foreground or background color SHALL instead use a Material 3 role whose meaning matches its use (`colorOnSurface`, `colorOnSurfaceVariant`, `colorPrimary` or `colorSurfaceContainer`).

#### Scenario: No text on a background of the same color

- **WHEN** a tester opens the stock list, stock detail, add trade record, add dividend, fee settings, edit lists, add stock, screenshot import, news, news article, statistics, settings and account screens in light mode and then in dark mode
- **THEN** no text, icon or input border on these screens has the same color as the background behind it


<!-- @trace
source: material3-watchlist-redesign
updated: 2026-11-06
code:
  - app/src/main/res/values-night/themes.xml
  - app/src/main/res/values/strings.xml
  - app/src/main/res/drawable/history_record_rect.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListNameValidator.kt
  - app/src/main/res/menu/options_menu.xml
  - app/src/main/java/com/example/mynewsapp/ui/tradeimport/TradeImportFragment.kt
  - app/src/main/res/layout/fragment_list.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
  - app/src/main/res/layout/item_dividend_detail.xml
  - app/src/main/java/com/example/mynewsapp/db/StockDao.kt
  - app/src/main/res/color/bottom_nav_color.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListNameDialogs.kt
  - app/src/main/res/layout/menu_list_selector_item.xml
  - app/src/main/res/layout/fragment_statistic.xml
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - app/src/main/res/drawable/change_label_background.xml
  - app/src/main/res/values/colors.xml
  - docs/known-issues.md
  - app/src/main/res/layout/dialog_trade_edit.xml
  - app/src/main/java/com/example/mynewsapp/ui/adapter/StockInfoAdapter.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/EditFollowingListFragment.kt
  - app/src/main/res/drawable/ic_baseline_search_24.xml
  - app/build.gradle
  - app/src/main/res/drawable/ic_swap_horiz_24.xml
  - app/src/main/res/layout/fragment_chat.xml
  - app/src/main/res/layout/item_stockinfo.xml
  - app/src/main/res/layout/item_following_list.xml
  - app/src/main/res/menu/list_fragment_options_menu.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListTabSelection.kt
  - app/src/main/res/layout/fragment_candle_stick_chart.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/StockRowFormatter.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - app/src/main/res/values/themes.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/AddFollowingListDialogFragment.kt
  - app/src/main/res/values-night/colors.xml
  - app/src/main/res/layout/activity_main.xml
tests:
  - app/src/test/java/com/example/mynewsapp/ui/list/StockRowFormatterTest.kt
  - app/src/androidTest/java/com/example/mynewsapp/ui/list/EditFollowingListDeleteTest.kt
  - app/src/androidTest/java/com/example/mynewsapp/db/FollowingListOrderTest.kt
  - app/src/test/java/com/example/mynewsapp/ui/list/ListNameValidatorTest.kt
  - app/src/test/java/com/example/mynewsapp/ui/list/ListTabSelectionTest.kt
-->

---
### Requirement: Bottom navigation uses the Material 3 style

The bottom navigation bar SHALL use the Material 3 navigation bar style, SHALL show the label of every tab, and SHALL mark the selected tab with the active indicator.

#### Scenario: Every tab shows its label

- **WHEN** the user opens the app on the stock list tab
- **THEN** the bottom bar shows an icon and a label for each of its tabs, and the stock list tab's icon sits on the active indicator

<!-- @trace
source: material3-watchlist-redesign
updated: 2026-11-06
code:
  - app/src/main/res/values-night/themes.xml
  - app/src/main/res/values/strings.xml
  - app/src/main/res/drawable/history_record_rect.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListNameValidator.kt
  - app/src/main/res/menu/options_menu.xml
  - app/src/main/java/com/example/mynewsapp/ui/tradeimport/TradeImportFragment.kt
  - app/src/main/res/layout/fragment_list.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
  - app/src/main/res/layout/item_dividend_detail.xml
  - app/src/main/java/com/example/mynewsapp/db/StockDao.kt
  - app/src/main/res/color/bottom_nav_color.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListNameDialogs.kt
  - app/src/main/res/layout/menu_list_selector_item.xml
  - app/src/main/res/layout/fragment_statistic.xml
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - app/src/main/res/drawable/change_label_background.xml
  - app/src/main/res/values/colors.xml
  - docs/known-issues.md
  - app/src/main/res/layout/dialog_trade_edit.xml
  - app/src/main/java/com/example/mynewsapp/ui/adapter/StockInfoAdapter.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/EditFollowingListFragment.kt
  - app/src/main/res/drawable/ic_baseline_search_24.xml
  - app/build.gradle
  - app/src/main/res/drawable/ic_swap_horiz_24.xml
  - app/src/main/res/layout/fragment_chat.xml
  - app/src/main/res/layout/item_stockinfo.xml
  - app/src/main/res/layout/item_following_list.xml
  - app/src/main/res/menu/list_fragment_options_menu.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/ListTabSelection.kt
  - app/src/main/res/layout/fragment_candle_stick_chart.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/StockRowFormatter.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - app/src/main/res/values/themes.xml
  - app/src/main/java/com/example/mynewsapp/ui/list/AddFollowingListDialogFragment.kt
  - app/src/main/res/values-night/colors.xml
  - app/src/main/res/layout/activity_main.xml
tests:
  - app/src/test/java/com/example/mynewsapp/ui/list/StockRowFormatterTest.kt
  - app/src/androidTest/java/com/example/mynewsapp/ui/list/EditFollowingListDeleteTest.kt
  - app/src/androidTest/java/com/example/mynewsapp/db/FollowingListOrderTest.kt
  - app/src/test/java/com/example/mynewsapp/ui/list/ListNameValidatorTest.kt
  - app/src/test/java/com/example/mynewsapp/ui/list/ListTabSelectionTest.kt
-->