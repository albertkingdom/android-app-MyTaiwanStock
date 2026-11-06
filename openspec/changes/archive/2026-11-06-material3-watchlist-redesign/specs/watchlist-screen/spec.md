## Purpose

Defines the stock list (watchlist) screen: switching between following lists with tabs, managing lists from the overflow menu, and how each stock row shows its price and change.

## ADDED Requirements

### Requirement: Lists are switched with scrollable tabs

The stock list screen SHALL show one tab per following list, in ascending `followingListId` order, in a horizontally scrollable tab row below the top app bar. Tapping a tab SHALL show that list's stocks and SHALL save the tab position as the last viewed list. Swiping horizontally on the stock rows SHALL NOT switch lists. The top app bar SHALL NOT show the previous list dropdown button.

#### Scenario: Tapping a tab switches the list

- **WHEN** the user has lists "Default" and "長期持有" and taps the "長期持有" tab
- **THEN** the screen shows the stocks of "長期持有" and the "長期持有" tab is marked as selected

#### Scenario: Last viewed list is restored

- **WHEN** the user selected the second tab, leaves the app and opens it again
- **THEN** the second tab is selected and its stocks are shown

#### Scenario: Selected tab and shown stocks always match

- **WHEN** the saved last viewed list is the third tab, the app is cold-started from the share sheet into the screenshot import screen, and the user then returns to the stock list
- **THEN** the third tab is selected and the stocks shown are the third list's stocks

#### Scenario: Changing the lists keeps the selection

- **WHEN** the user selected the second tab and then adds a list or renames the second list
- **THEN** the second tab stays selected and its stocks stay shown

#### Scenario: Swiping a row still deletes the stock

- **WHEN** the user swipes a stock row left or right
- **THEN** the stock is removed from the current list and the selected tab does not change

### Requirement: Selected tab stays within the list count

When the saved last viewed position is not a valid tab position, the screen SHALL select the closest valid position: position 0 when the saved position is negative, and the last tab when the saved position is past the end, and SHALL save the selected position as the last viewed list. The screen SHALL NOT crash when the saved position is out of range.

#### Scenario: Saved position past the end after a list was deleted

- **WHEN** the saved position is 2 and only 2 lists remain
- **THEN** the second tab (position 1) is selected

##### Example: Clamping the saved position

| Saved position | List count | Selected position |
| -------------- | ---------- | ----------------- |
| 0              | 3          | 0                 |
| 2              | 3          | 2                 |
| 3              | 3          | 2                 |
| -1             | 3          | 0                 |
| 0              | 0          | none (no tab)     |

### Requirement: Tab row adds a list

The tab row SHALL end with an add button that stays visible while the tabs scroll. Tapping it SHALL open the add-list dialog. When the selected tab is outside the visible part of the tab row, the tab row SHALL scroll it into view.

#### Scenario: Adding a list from the tab row

- **WHEN** the user taps the add button at the end of the tab row and confirms the name "高股息"
- **THEN** a tab named "高股息" appears after the existing tabs

### Requirement: Overflow menu manages lists

The top app bar of the stock list screen SHALL have a search action and an overflow menu with these items in this order: 新增清單, 重新命名, 刪除此清單, 截圖匯入, 管理清單.

- 新增清單 SHALL open the existing add-list dialog.
- 重新命名 SHALL open a dialog prefilled with the selected list's name; confirming a valid list name SHALL rename the selected list.
- 刪除此清單 SHALL ask for confirmation showing the list name; confirming SHALL delete the selected list and its stocks, the same way the edit lists screen deletes a list.
- 截圖匯入 SHALL open the trade screenshot import screen, and a second tap before navigation finishes SHALL NOT open a second import screen.
- 管理清單 SHALL open the existing edit lists screen.

#### Scenario: Renaming the selected list

- **WHEN** the selected tab is "Default", the user chooses 重新命名, enters "核心持股" and confirms
- **THEN** the selected tab's title becomes "核心持股" and stays selected


#### Scenario: Deleting the selected list

- **WHEN** the tabs are "Default", "長期持有", "高股息", "長期持有" is selected, and the user confirms 刪除此清單
- **THEN** "長期持有" and its stocks are deleted, and the tab at the same position ("高股息") is selected

#### Scenario: Deleting the only list

- **WHEN** only one list exists and the user confirms 刪除此清單
- **THEN** the list is deleted and an empty list named "Default" is created and selected

### Requirement: List names are validated

The add-list dialog and the 重新命名 dialog opened from the overflow menu SHALL reject a name that is blank after trimming, or that equals the name of another existing list after trimming. On rejection the dialog SHALL stay open, show an error message under the name field, and SHALL NOT write anything. Renaming a list to its own current name SHALL be accepted. Accepted names SHALL be saved trimmed.

#### Scenario: Blank name is rejected

- **WHEN** the user clears the name in the rename dialog and confirms
- **THEN** the dialog stays open, shows an error message and the list name does not change

#### Scenario: Duplicate name is rejected

- **WHEN** lists "Default" and "長期持有" exist and the user enters "長期持有" in the add-list dialog and confirms
- **THEN** the dialog stays open, shows an error message and no list is added

##### Example: Validating a list name

| Entered name | Existing names          | List being renamed | Result    |
| ------------ | ----------------------- | ------------------ | --------- |
| "高股息"     | Default, 長期持有       | none (add)         | accepted  |
| "  高股息 "  | Default, 長期持有       | none (add)         | accepted, saved as "高股息" |
| "   "        | Default, 長期持有       | none (add)         | blank     |
| "長期持有"   | Default, 長期持有       | none (add)         | duplicate |
| "長期持有"   | Default, 長期持有       | 長期持有           | accepted  |
| "Default"    | Default, 長期持有       | 長期持有           | duplicate |

### Requirement: Cloud sync uses the selected list

Adding a stock and swipe-deleting a stock SHALL keep uploading the change to the online database under the name of the currently selected list, including right after the list was renamed.

#### Scenario: Adding a stock while signed in

- **WHEN** a signed-in user selects the "長期持有" tab and adds stock 2330
- **THEN** the app calls the online database upload for stock 2330 with the list name "長期持有"

### Requirement: Stock row shows price and change

Each stock row SHALL show the stock name and a subtitle on the left, and the price and a change label on the right. Rows SHALL be separated by divider lines, not drawn as bordered cards. The last row SHALL NOT be covered by the add button when the list is scrolled to the end.

- The subtitle SHALL be "<stock number> · <HH:mm of the quote time>" when the quote has a last trade price, and "<stock number> · 昨收" when it does not.
- The price SHALL be the last trade price, or the previous close when there is no last trade price, formatted with two decimals; it SHALL be "-" when neither exists.
- The change label SHALL be a filled label with a red background when the change is above zero, a green background when it is below zero, and a neutral background when it is zero or unknown.
- In amount mode the change label SHALL show the signed change with two decimals ("+" for gains); in percent mode it SHALL show the signed percent change with two decimals and a "%" suffix. When neither price exists the label SHALL show "-".

#### Scenario: A rising stock

- **WHEN** the quote for 2330 has last trade 1085 and previous close 1070 at 13:30:00 in amount mode
- **THEN** the row shows "2330 · 13:30", price "1085.00" and a red label "+15.00"

##### Example: Row text and label color

| Last trade | Previous close | Time     | Mode    | Subtitle      | Price   | Label    | Label color |
| ---------- | -------------- | -------- | ------- | ------------- | ------- | -------- | ----------- |
| 1085       | 1070           | 13:30:00 | amount  | 2330 · 13:30  | 1085.00 | +15.00   | red         |
| 1085       | 1070           | 13:30:00 | percent | 2330 · 13:30  | 1085.00 | +1.40%   | red         |
| 195.30     | 196.50         | 13:30:00 | amount  | 0050 · 13:30  | 195.30  | -1.20    | green       |
| -          | 127.50         | 13:19:51 | amount  | 2344 · 昨收   | 127.50  | 0.00     | neutral     |
| -          | 127.50         | 13:19:51 | percent | 2344 · 昨收   | 127.50  | 0.00%    | neutral     |
| -          | -              | 13:19:51 | amount  | 2344 · 昨收   | -       | -        | neutral     |
| 195.30     | 196.50         | 13:30:00 | percent | 0050 · 13:30  | 195.30  | -0.61%   | green       |
| 1070.01    | 1070           | 13:30:00 | percent | 2330 · 13:30  | 1070.01 | +0.00%   | red         |
| 1070       | 1070           | 13:30:00 | amount  | 2330 · 13:30  | 1070.00 | 0.00     | neutral     |
| 12345.5    | 12000          | 13:30:00 | amount  | 9999 · 13:30  | 12345.50 | +345.50 | red         |

Prices and changes use "." as the decimal separator and no thousands separator, whatever the device language. A change of exactly 0 (FLAT) is always shown as "0.00" or "0.00%", never with a sign; a change above 0 that rounds to 0.00 is shown with "+" and a red label.

### Requirement: Header row toggles amount and percent

The list SHALL have a header row above the first stock row with a name label on the left and a price and change label with a toggle control on the right. Tapping the toggle control or any change label SHALL switch every row between amount mode and percent mode. The chosen mode SHALL be kept when the user leaves the stock list for another screen in the app and comes back.

#### Scenario: Switching to percent mode

- **WHEN** the rows are in amount mode and the user taps the toggle control in the header row
- **THEN** every row's change label shows a percent value

#### Scenario: Mode is kept after visiting a stock

- **WHEN** the rows are in percent mode, the user opens a stock's detail screen and goes back
- **THEN** every row's change label still shows a percent value, and one tap on the toggle control switches to amount mode

### Requirement: Add button adds a stock

The stock list screen SHALL show an extended floating action button labeled "新增" that opens the add stock screen. The screen SHALL NOT show the previous expanding button group for adding a list, adding a stock and importing a screenshot.

#### Scenario: Adding a stock

- **WHEN** the user taps the "新增" button
- **THEN** the add stock screen opens
