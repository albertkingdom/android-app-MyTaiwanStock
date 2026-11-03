## Purpose

The otc-stock-support capability lets over-the-counter stocks be tracked like listed stocks. It selects the correct quote prefix per market for the stock list screen and the home-screen widget, and keeps the stock detail screen usable when candle data is unavailable.

## ADDED Requirements

### Requirement: Query quotes with the market prefix

The system SHALL build each quote query code as "tse_{code}.tw" for a listed stock and "otc_{code}.tw" for an over-the-counter stock, based on the market recorded in the stock list. For a code missing from the current list, the system SHALL use the market recorded in the bundled list, so a followed stock that is absent from a refreshed daily snapshot keeps its prefix. When neither list has the code, the system SHALL use "tse_". Several codes SHALL be joined with "|" in the order given. This SHALL apply to the stock list screen and the home-screen widget.

#### Scenario: Mixed list

- **WHEN** the system queries quotes for 6488 (over-the-counter) and 2330 (listed)
- **THEN** the query SHALL be "otc_6488.tw|tse_2330.tw"

##### Example: prefix selection

| Code | Market in list | Query code |
| --- | --- | --- |
| 2330 | tse | tse_2330.tw |
| 6488 | otc | otc_6488.tw |
| 9999 | unknown | tse_9999.tw |

#### Scenario: Followed stock missing from the refreshed list

- **WHEN** the bundled list records 6488 as over-the-counter and the refreshed list does not contain 6488
- **THEN** the query code for 6488 SHALL be "otc_6488.tw"

---

### Requirement: Widget uses the same market lookup

The widget update job SHALL obtain the code-to-market lookup from the same stock list the app uses (cache, otherwise the bundled list), so a code recorded as over-the-counter in the app is also quoted with "otc_" by the widget. When the lookup has no market for a code, the widget SHALL use "tse_" for that code.

#### Scenario: Widget quotes an over-the-counter stock

- **WHEN** the widget update job runs for the codes 6488 and 2330
- **THEN** the quote query SHALL be "otc_6488.tw|tse_2330.tw"

---

### Requirement: Show no data when candle data is unavailable

When the candle data for a stock is empty, cannot be fetched, or the response has no data rows, the stock detail screen SHALL show the text "無資料" in the chart area, SHALL NOT crash, and SHALL still show the buy/sell records and the overview. When the candle data has fewer rows than a moving average window needs, the chart SHALL NOT crash, SHALL omit that moving average line, and SHALL draw the candles and volume bars that exist. The 5-day line SHALL be drawn only with at least 5 rows and the 10-day line only with at least 10 rows. A row whose shares traded, trade value, open, high, low or close is not a number (for example "--" on a suspended day) SHALL be left out of the chart, and a price change value that is not a number (for example "X0.00" on an ex-dividend day) SHALL be treated as 0. When one of the two requested months fails and the other returns rows, the chart SHALL show the rows that were returned. This SHALL apply to over-the-counter stocks, whose candle data is not supported, and to newly listed securities with few trading days.

#### Scenario: Over-the-counter stock detail

- **WHEN** the user opens the detail screen of 6488 and the candle data request returns no rows
- **THEN** the chart area SHALL show "無資料"
- **AND** the buy/sell records and overview SHALL be displayed normally

#### Scenario: Fewer rows than the moving average needs

- **WHEN** the candle data of a newly listed security has 8 rows
- **THEN** the chart SHALL NOT crash
- **AND** the chart SHALL show 8 candles with the 5-day line and without the 10-day line

##### Example: row counts

| Candle rows | 5-day line | 10-day line | Result |
| --- | --- | --- | --- |
| 0 | no | no | "無資料" |
| 3 | no | no | 3 candles |
| 5 | yes (1 point) | no | 5 candles |
| 9 | yes (5 points) | no | 9 candles |
| 10 | yes (6 points) | yes (1 point) | 10 candles |

#### Scenario: Suspended day in the data

- **WHEN** the candle data has 6 rows and one of them has "--" as its prices
- **THEN** the chart SHALL show 5 candles and SHALL NOT crash
