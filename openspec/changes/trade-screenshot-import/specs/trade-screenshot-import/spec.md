## Purpose

Lets a user turn screenshots of the Yuanta "Investor Mr." (投資先生) trade detail screen into buy/sell records on Android, recognized on the device. The broker app offers no export and no API, so the on-screen detail list is the only data source.

## ADDED Requirements

### Requirement: Recognize trades from screenshots on device

The system SHALL let the user pick one or more images with the system photo picker, or receive images shared through the system share sheet, and SHALL recognize the text in each image on the device with ML Kit Text Recognition using the bundled Chinese model, one image at a time. The system SHALL NOT upload the images or the recognized text to any server and SHALL NOT write the recognized text to logs. The only exception is a debug-build-only action in the preview that copies the recognized text boxes, formatted as test data, to the device clipboard when the developer selects it; release builds SHALL NOT offer this action. Each recognized line SHALL be split into separate text boxes wherever the horizontal gap between neighbouring words is more than 0.5 times the line height.

#### Scenario: Multiple screenshots are merged into one preview

- **WHEN** the user selects two screenshots and both contain trade rows
- **THEN** the system SHALL recognize each image and present all recognized trades together in a single preview list

#### Scenario: Screenshot without trades

- **WHEN** the recognized text of every selected image contains no trade row
- **THEN** the system SHALL show a message that no importable trade was found
- **AND** the system SHALL NOT write any record

#### Scenario: Image cannot be loaded or recognized

- **WHEN** a selected image cannot be loaded, its read permission is no longer valid, it is too large to decode, or the recognition fails
- **THEN** the system SHALL show an error message
- **AND** the system SHALL NOT write any record

#### Scenario: Only unsupported or incomplete rows

- **WHEN** every recognized trade row is unsupported or incomplete
- **THEN** the system SHALL show the preview with those rows and SHALL NOT show the no-trade message

#### Scenario: Process restored during import

- **WHEN** the app process is recreated while the import screen is shown and the selected images are still readable
- **THEN** the system SHALL recognize the same images again and show the preview
- **AND** when the images can no longer be read the system SHALL show an error and return to the stock list without writing any record

#### Scenario: Columns of one line split into boxes

- **WHEN** the recognizer returns "2026/", "元大台灣50" and "112.55" as words of one line separated by gaps wider than half the line height
- **THEN** the parser SHALL receive them as three separate text boxes

#### Scenario: Picker cancelled

- **WHEN** the user closes the photo picker without selecting an image
- **THEN** the system SHALL return to the stock list without writing any record

### Requirement: Parse two-line trade rows by position

The system SHALL parse each trade as a two-line group located by the position of the recognized text boxes and SHALL NOT rely on the order in which text lines are returned. Positions SHALL be normalized to the range 0 to 1 with the origin at the lower left corner, so a larger y value is higher on the screen. The lower line is located by a type text ending in "買進" or "賣出". The upper line SHALL lie above the lower line, within 0.5 to 2.5 times the height of the type text box, and SHALL contain a year fragment matching four digits followed by a slash. The upper line carries the year fragment, stock name, price and net amount; the lower line carries the month/day, trade type, share count and profit or loss. The trade direction SHALL be derived from the trade type text. When the upper line cannot be found, the trade SHALL have no stock name, price or date and SHALL NOT borrow text from any other row. Each box text SHALL be normalized with Unicode NFKC before parsing, and whitespace SHALL be removed using the Unicode whitespace definition. Thousands separators SHALL be removed before numbers are converted. A line made only of arrow characters (V, v, ^, ∨, ∧, ⌄, ⌃) to the right of the data columns SHALL be skipped while searching for the upper line. A month/day that does not exist in the year, such as 02/30, SHALL produce no date. A missing year SHALL leave the date empty and SHALL keep the other fields.

#### Scenario: Collapsed odd-lot buy row

- **WHEN** the recognized boxes contain "2026/", "元大台灣50", "112.55", "-563" on the upper line and "09/30", "盤中零股買進", "5", "--" on the lower line
- **THEN** the system SHALL produce one trade with stock name 元大台灣50, direction buy, price 112.55, shares 5 and date 2026-09-30

##### Example: parsed trades from the sample screenshots

| Stock name | Trade type | Price | Shares | Date | Direction |
| --- | --- | --- | --- | --- | --- |
| 元大台灣50 | 盤中零股買進 | 112.55 | 5 | 2026-09-30 | buy |
| 華邦電 | 盤中零股買進 | 179.5 | 3 | 2026-10-01 | buy |
| 台積電 | 盤中零股買進 | 2470.0 | 1 | 2026-09-22 | buy |
| 瑞昱 | 盤中零股賣出 | 757.0 | 10 | 2026-09-23 | sell |

#### Scenario: Thousands separator

- **WHEN** the price text is "1,125.50" and the share text is "1,000"
- **THEN** the trade SHALL have price 1125.5 and shares 1000

#### Scenario: Top row cut off by the screenshot

- **WHEN** the first trade in a screenshot shows only its lower line and the nearest text above it belongs to the summary rows
- **THEN** the trade SHALL have an empty stock name and no price and no date
- **AND** its share count SHALL still be read from its lower line

#### Scenario: Merged text box

- **WHEN** the recognizer returns "2026/ 元大台灣50" and "09/30 盤中零股買進" as single text boxes
- **THEN** the system SHALL still extract the year, the stock name, the month/day and the trade type

#### Scenario: Row arrow between two lines

- **WHEN** a "V" box lies to the right of the profit or loss column between the upper and the lower line of the last trade
- **THEN** the trade SHALL still take its stock name, price and year from the upper line

#### Scenario: Full-width digits

- **WHEN** the share text is "１０"
- **THEN** the trade SHALL have shares 10

#### Scenario: Impossible date

- **WHEN** the year is "2026/" and the month/day is "02/30"
- **THEN** the trade SHALL have no date

#### Scenario: Upper line search stops at another trade

- **WHEN** two lower lines lie directly above each other with no year fragment between them
- **THEN** the second trade SHALL have no stock name, price or date

### Requirement: Ignore expanded detail block

The system SHALL ignore the expanded detail block of a trade row and the summary and header rows of the screen. Text containing the labels 委託書號, 持有成本, 價金, 手續費, 交易稅, 配息總額, 報酬率, 幣別, 沖抵明細, 總應收付, 總損益, 總價金, 總手續費 or 總交易稅, together with every text box on the same row, SHALL NOT produce a trade and SHALL NOT change the parsed result of any trade. The table header texts 日期, 名稱, 價格/股數 and 應收付/損益 SHALL also be ignored.

#### Scenario: Expanded detail text

- **WHEN** a screenshot shows the 瑞昱 sell row expanded with 委託書號, 持有成本, 價金, 手續費 and 交易稅 rows below it
- **THEN** the system SHALL produce exactly one trade for 瑞昱 with shares 10 and price 757

#### Scenario: Summary and header only

- **WHEN** the recognized text contains only the summary rows and the table header
- **THEN** the system SHALL produce no trade

### Requirement: Reassemble stock name fragments

The parser SHALL concatenate adjacent letter, digit, plus-sign and dash fragments in the stock name column, left to right, when the gap between them is at most 0.03 of the image width and the fragment does not extend more than 0.03 beyond the right edge of the trade type box. Numeric fragments SHALL be joined to the name only while no number has been taken for the price. A fragment outside these limits SHALL NOT be appended to the name.

#### Scenario: Latin and plus-sign fragments

- **WHEN** the name column contains adjacent boxes holding "統一", "FANG" and "+"
- **THEN** the parsed name SHALL be "統一FANG+"

#### Scenario: Numeric name suffix

- **WHEN** the name column contains adjacent boxes "元大台灣" and "50", followed by a price box "112"
- **THEN** the parsed name SHALL be "元大台灣50" and the price SHALL be 112

#### Scenario: Unrelated plus sign

- **WHEN** a "+" box lies more than 0.03 of the image width to the right of the name
- **THEN** the parser SHALL NOT append it to the name

### Requirement: Read shares only from the share column

When the upper line has a price, the parser SHALL read the share count only from a whole number on the lower line that lies to the right of the trade type box and whose horizontal center is not beyond the share column edge. The share column edge SHALL be the midpoint between the right edge of the price box and the left edge of the next number on the upper line, or the right edge of the price box when there is no next number. Text in the profit or loss column SHALL NOT supply a missing share count.

#### Scenario: Missing shares with a numeric profit or loss

- **WHEN** the recognized text has price "144.20" but no share count, and "11" in the profit or loss column
- **THEN** the trade SHALL have no share count

#### Scenario: Wide share count under a narrow price

- **WHEN** the price is "9" and the share text "1,000" is wider than the price box
- **THEN** the trade SHALL have shares 1000

### Requirement: Restrict scope to supported trade types

The system SHALL support the trade types 盤中零股買進, 盤中零股賣出, 盤後零股買進 and 盤後零股賣出. A row whose type text ends in "買進" or "賣出" but is not one of these, such as 現股買進, 融資買進, 融券賣出, 當沖買進 or 定期定額買進, SHALL still be listed in the preview, SHALL be marked unsupported, SHALL NOT be selectable, and SHALL NOT stop the other trades from being processed.

#### Scenario: Unsupported type in a batch

- **WHEN** a screenshot contains one 盤中零股買進 row and one 融資買進 row
- **THEN** the preview SHALL list the odd-lot row as importable
- **AND** the preview SHALL list the 融資買進 row as unsupported and not selectable

### Requirement: Resolve stock number from stock name

The system SHALL resolve the stock number from the stock name by exact match against the stock directory. Before matching, both names SHALL be normalized by Unicode NFKC normalization, removal of all whitespace including full-width spaces, and replacement of the dash characters U+2013, U+2014, U+2015, U+2500 and U+30FC with "一". When no entry matches and the name contains an ASCII hyphen, the system SHALL retry once with each ASCII hyphen replaced by "一". The system SHALL NOT use fuzzy matching. When the name matches no entry, or more than one entry, the stock number SHALL stay unresolved. An empty name SHALL stay unresolved. A stock number entered manually SHALL be accepted only when it equals the code of an entry in the stock directory.

#### Scenario: Name found

- **WHEN** the recognized stock name is "華邦電"
- **THEN** the system SHALL resolve the stock number 2344

##### Example: name to stock number

| Recognized name | Directory name | Result |
| --- | --- | --- |
| 元大台灣50 | 元大台灣50 | 0050 |
| 元大 台灣50 | 元大台灣50 | 0050 |
| 元大台灣５０ | 元大台灣50 | 0050 |
| 元大MSCIA股 | 元大MSCI A股 (test entry) | 00850 (test entry) |
| 統—FANG+ | 統一FANG+ | 00757 |
| 統-FANG+ | 統一FANG+ | 00757 |
| 台積 | 台積電 | unresolved |
| 重複名 | two entries named 重複名 | unresolved |

#### Scenario: Manually entered stock number not in the directory

- **WHEN** the user enters "23" as the stock number of an unresolved trade
- **THEN** the trade SHALL stay incomplete and not selectable

### Requirement: Mandatory preview with per-trade confirmation

The system SHALL present every recognized trade in a preview before anything is written and SHALL NOT provide a way to import without the preview. The user SHALL be able to select or deselect each trade and to edit its stock number, price, shares, date and direction. A trade SHALL be incomplete when its stock number is missing or not in the stock directory, its date is missing, its price is missing or not greater than 0, or its shares are missing or not an integer from 1 to 32767. Only importable and duplicate trades SHALL be selectable. When a trade matches more than one status, the first applicable status in this order SHALL be shown: unsupported, incomplete, duplicate, importable. When the preview opens, exactly the importable trades SHALL be selected. After an edit changes a trade's status, the trade SHALL be selected when it became importable and deselected otherwise; a trade whose status did not change SHALL keep its selection. The import action SHALL be disabled while no trade is selected. In the editor, the stock number SHALL be trimmed and upper-cased and an empty stock number SHALL mean no stock number; the price and the shares SHALL be trimmed and stripped of thousands separators, and a value that cannot be parsed SHALL mean no value; the date SHALL be chosen with a date picker.

#### Scenario: Incomplete trade becomes selectable after editing

- **WHEN** a trade has no date and the user enters a valid date
- **THEN** the trade SHALL become importable and selected

##### Example: share count validation

| Shares | Status |
| --- | --- |
| 0 | incomplete |
| 1 | importable |
| 32767 | importable |
| 32768 | incomplete |

#### Scenario: Letter stock number is upper-cased

- **WHEN** the user enters " 00635u " as the stock number and 00635U is in the stock directory
- **THEN** the trade SHALL have stock number 00635U

#### Scenario: Nothing selected

- **WHEN** the user has deselected every trade
- **THEN** the import action SHALL be disabled

### Requirement: Detect duplicates

The system SHALL treat two trades as duplicates when stock number, calendar date in the Asia/Taipei time zone, price compared as a single-precision float, shares and direction are all equal. Each previewed trade SHALL be compared with the existing local buy/sell records and with the earlier trades in the same preview. A duplicate SHALL be deselected by default and the user SHALL be able to select it manually.

##### Example: duplicate key comparison

| Existing record | New trade | Duplicate |
| --- | --- | --- |
| 0050, 2026-09-30, 112.55, 5, buy | 0050, 2026-09-30, 112.55, 5, buy | yes |
| 0050, 2026-09-30, 112.55, 5, buy | 0050, 2026-09-30, 112.55, 5, sell | no |
| 0050, 2026-09-30, 112.55, 5, buy | 0050, 2026-09-30, 112.55, 6, buy | no |
| 0050, 2026-09-30, 112.55, 5, buy | 0050, 2026-10-01, 112.55, 5, buy | no |

#### Scenario: Record synced from iOS

- **WHEN** an existing record for 0050 on 2026-09-30 has price 112.55000305175781, which is how iOS stores 112.55, and the new trade has price 112.55 with the same shares and direction
- **THEN** the new trade SHALL be marked duplicate

#### Scenario: Same trade twice in one preview

- **WHEN** the preview contains the same trade twice
- **THEN** the first SHALL be importable and selected and the second SHALL be duplicate and deselected

#### Scenario: Legitimate repeated trade

- **WHEN** the user selects a duplicate trade and confirms the import
- **THEN** the system SHALL write it as a new record

### Requirement: Write through the existing record flow

When the user confirms the import, the system SHALL first validate every selected trade and SHALL write nothing when any selected trade is not importable or duplicate with a valid stock number, date, price and share count. After validation passes, the system SHALL write each selected trade the same way as manual entry: a local `InvestHistory` record with the stock number, date, price, shares and direction (0 buy, 1 sell), and an upload to the online database when the user is signed in. Shares SHALL be stored without unit conversion. The stored date SHALL be 12:00:00.000 on the trade's calendar date in the Asia/Taipei time zone; when that millisecond timestamp is already used by an existing local record or an earlier trade in the same import, the system SHALL add one millisecond at a time until it is unused. The used timestamps SHALL be read again when the user confirms. The local records SHALL be written completely before the import screen closes, and leaving the screen SHALL NOT cancel the local writes or the uploads. While an import is being written, the import action SHALL be disabled and a second confirmation SHALL be ignored, so one preview is written at most once. The system SHALL report the number of records submitted for writing. Fee and tax SHALL NOT be written by this capability.

#### Scenario: Only selected trades are written

- **WHEN** the preview contains three trades, two selected, and the user confirms
- **THEN** the system SHALL write exactly two records and report 2

#### Scenario: Same-day trades get distinct timestamps

- **WHEN** the user imports one trade for 0050 and one for 2344, both dated 2026-09-30
- **THEN** the stored dates SHALL be 2026-09-30 12:00:00.000 and 2026-09-30 12:00:00.001 Asia/Taipei

#### Scenario: Import tapped twice

- **WHEN** the user taps the import action twice quickly
- **THEN** the system SHALL write each selected trade exactly once

#### Scenario: Invalid selected trade blocks the whole import

- **WHEN** a selected trade has an invalid share count and the user confirms
- **THEN** the system SHALL NOT write any record

### Requirement: Choose target list for imported stocks

The preview SHALL show a list selector with every stock list plus a "do not add to a list" option. Lists SHALL be identified by their id. The default SHALL be the list currently shown on the stock list screen when it exists, otherwise the first list, and "do not add to a list" when no list exists. When the import is confirmed, for each distinct stock number among the written trades, the system SHALL add it to the selected list only when it is not already in any list at the moment of confirmation, and SHALL upload the addition to the online database the same way as adding a stock manually. Stock numbers of trades that were not written SHALL NOT be added. When "do not add to a list" is selected, no list SHALL change and the preview SHALL show a note that the records can only be opened from a list that contains the stock.

##### Example: stocks added to the selected list

| Written trades | Stocks already in any list | Stocks added |
| --- | --- | --- |
| 0050 buy, 2344 buy | 0050 | 2344 |
| 2379 sell, 2379 buy | none | 2379 (once) |
| 2344 buy (2330 deselected) | 2344 | none |

#### Scenario: Current list unknown

- **WHEN** the import opens before the stock list screen has chosen a current list and two lists exist
- **THEN** the list selector SHALL show the first list as selected

#### Scenario: Do not add to a list

- **WHEN** the user selects "do not add to a list" and confirms
- **THEN** the system SHALL write the records and SHALL NOT change any list

### Requirement: Return to the stock list after import

When the import completes, the system SHALL return to the stock list screen, removing every screen above it in the stock tab, and show how many records were submitted. A stock added to the list currently shown SHALL appear without leaving and re-entering the screen.

#### Scenario: New stock visible immediately

- **WHEN** the user imports a trade for 2379 into the list currently shown and the import completes
- **THEN** the stock list screen SHALL show 2379
