## Purpose

Defines how the Android app computes, stores and displays the brokerage fee and the securities transaction tax of each buy and sell record, how the user sets the fee discount and minimum fees, and how fees are merged into holdings, average prices and profit.

## ADDED Requirements

### Requirement: Calculate fee and tax with one rule set

The system SHALL compute the fee and the transaction tax of a trade with one calculation that uses decimal arithmetic, and SHALL return no result when the price is not greater than 0, the share count is less than 1, or the fee or the tax would be greater than 2147483647. The trade value SHALL be the price multiplied by the number of shares, without rounding. A price stored as a binary floating-point number SHALL be converted to a decimal through its shortest decimal text, so that a price of 112.55 is exactly 112.55. The fee SHALL be the trade value multiplied by 0.001425 and by the fee discount divided by 10, rounded down to a whole New Taiwan dollar, where a discount of 10 means no discount, and the fee SHALL then be raised to the minimum fee that applies: the whole-lot minimum fee when the trade has 1000 shares or more, and the odd-lot minimum fee when the trade has fewer than 1000 shares. A minimum fee of 0 means that no minimum applies to that kind of trade. The tax SHALL be 0 for a buy. For a sell, the tax SHALL be the trade value multiplied by the tax rate of the chosen category, rounded down to a whole dollar, where the categories are general stock 0.3%, same-day stock trade 0.15%, ETF 0.1% and exempt 0%.

#### Scenario: Buy without discount

- **WHEN** the user buys 1000 shares at a price of 100 with a discount of 10
- **THEN** the fee SHALL be 142 and the tax SHALL be 0

#### Scenario: Fee below the minimum is raised

- **WHEN** the user buys 1000 shares at a price of 20 with a discount of 6 and the whole-lot minimum fee is 20
- **THEN** the calculated fee of 17 SHALL be raised to the whole-lot minimum of 20

#### Scenario: No minimum fee

- **WHEN** the user buys 1000 shares at a price of 20 with a discount of 6 and the whole-lot minimum fee is 0
- **THEN** the fee SHALL be the calculated 17

#### Scenario: Decimal discount without a rounding error

- **WHEN** the discount is 2.8 and the user sells 1000 shares at a price of 1000
- **THEN** the fee SHALL be 399, not 398

#### Scenario: Same-day trade tax

- **WHEN** the user sells 1000 shares at a price of 100 with the same-day stock trade category
- **THEN** the tax SHALL be 150

#### Scenario: Invalid trade has no result

- **WHEN** the price is 0 or negative, or the share count is 0 or negative
- **THEN** the calculation SHALL return no fee and no tax

#### Scenario: Amount that does not fit in a record

- **WHEN** the user sells 30000 shares at a price of 40000000 with a discount of 0.1 and the general stock category
- **THEN** the calculation SHALL return no result, because the tax of 3600000000 is greater than 2147483647

##### Example: fee and tax by trade

| Side | Shares | Price | Discount | Whole-lot minimum | Odd-lot minimum | Category | Fee | Tax |
| ---- | ------ | ----- | -------- | ----------------- | --------------- | -------- | --- | --- |
| Buy | 1000 | 100 | 10 | 20 | 1 | general stock | 142 | 0 |
| Sell | 1000 | 105 | 6 | 20 | 1 | general stock | 89 | 315 |
| Buy | 5 | 112.55 | 6 | 20 | 1 | general stock | 1 | 0 |
| Sell | 10 | 757 | 6 | 20 | 1 | general stock | 6 | 22 |
| Buy | 1 | 2470 | 6 | 20 | 1 | general stock | 2 | 0 |
| Buy | 1000 | 20 | 6 | 20 | 1 | general stock | 20 | 0 |
| Sell | 2000 | 22 | 6 | 20 | 1 | ETF | 37 | 44 |
| Sell | 1000 | 100 | 10 | 20 | 1 | exempt | 142 | 0 |
| Buy | 999 | 1 | 10 | 20 | 1 | general stock | 1 | 0 |
| Buy | 1500 | 1 | 10 | 20 | 1 | general stock | 20 | 0 |
| Buy | 5 | 112.55 | 6 | 20 | 5 | general stock | 5 | 0 |
| Buy | 1000 | 20 | 6 | 30 | 1 | general stock | 30 | 0 |
| Buy | 1000 | 20 | 6 | 0 | 1 | general stock | 17 | 0 |

### Requirement: Store fee, tax and memo with each record

The system SHALL store the fee and the tax as optional whole-dollar numbers and the memo as optional text on each buy and sell record. A record without a stored fee or tax is a record whose fee or tax is unknown, and the system SHALL distinguish an unknown value from a stored value of 0. A record created on the add-record screen SHALL carry the fee and the tax shown in the fields when the record was saved, and the memo the user typed (an empty text when the user typed nothing). The tax of a buy record SHALL be stored as 0. Records synchronized from the online database SHALL keep an unknown fee, tax and memo. The system SHALL NOT upload the fee, the tax or the memo to the online database.

#### Scenario: New record keeps the shown values

- **WHEN** the user saves a sell of 10 shares at 757 with the fee field showing 6 and the tax field showing 22
- **THEN** the stored record SHALL have a fee of 6 and a tax of 22

#### Scenario: Buy stores a tax of zero

- **WHEN** the user saves a buy
- **THEN** the stored record SHALL have a tax of 0

#### Scenario: Existing record has unknown fee

- **WHEN** a record that was saved before this capability is displayed
- **THEN** its fee and tax SHALL be shown as a dash and SHALL NOT be shown as 0

### Requirement: Keep existing data when the database is upgraded

When the app starts with a database of version 6, the system SHALL upgrade it to version 7 by adding the fee, tax and memo columns, SHALL keep every existing record unchanged, and SHALL leave the fee, tax and memo of those records unknown. The system SHALL NOT delete, recreate or reset any table to perform the upgrade.

#### Scenario: Upgrade with existing records

- **GIVEN** a version 6 database with three buy records and two sell records
- **WHEN** the database is upgraded to version 7
- **THEN** all five records SHALL be present with the same id, stock number, price, shares, date and side
- **AND** the fee, tax and memo of all five SHALL be unknown

### Requirement: Enter fee and tax when adding a record by hand

On the add-record screen the system SHALL compute the fee and the tax from the current text of the price and shares fields, using the fee settings, every time the price or the shares change, when the side changes between buy and sell, and when the tax category changes. The fee and the tax SHALL be shown in editable fields that contain only the number, with the unit shown beside the field. When the price or the shares do not give a result, the computed fields SHALL be empty. For a sell, the system SHALL show the tax field and a choice of tax category with general stock as the default; for a buy the system SHALL NOT show the tax field or the category choice. When the user types in the fee field or the tax field, that field SHALL keep the user's value when the price, the shares, the side or the category change afterwards, except that switching to a buy SHALL discard a typed tax. While the user is editing, an empty field SHALL stay empty, and when the field loses focus while it is empty or blank, the system SHALL show the computed value again and SHALL follow the computation again. A value the system writes into a field, including text restored after the screen is recreated, SHALL NOT be treated as typed by the user. Saving or switching the side SHALL first end the editing of the fee and tax fields, as leaving them would. When the user saves, the system SHALL reject a fee, or the tax of a sell, that is empty, negative, not a whole number, or greater than 2147483647, SHALL show an input error on that field and SHALL NOT write a record.

#### Scenario: Fee follows the final text

- **WHEN** the price field shows 100 and the user types the shares one character at a time, ending with 1000
- **THEN** the fee field SHALL show 142 after the last character is typed

#### Scenario: Switching to a sell shows and computes the tax

- **WHEN** the price is 105, the shares are 1000, the discount is 6, the side is a buy, and the user switches to a sell
- **THEN** the tax field and the tax category choice SHALL be shown, and the tax field SHALL show 315

#### Scenario: Manual value is kept

- **WHEN** the user types 50 in the fee field and then changes the shares
- **THEN** the fee field SHALL still show 50

#### Scenario: Retyping after deleting the value

- **WHEN** the fee field shows 142, the user deletes all digits and types 50
- **THEN** the fee field SHALL show 50 and the stored fee SHALL be 50

#### Scenario: Minimum fee from the settings

- **WHEN** the saved odd-lot minimum fee is 5 and the discount is 6, and the user enters a buy of 5 shares at a price of 112.55
- **THEN** the fee field SHALL show 5

#### Scenario: Saving with an empty fee field that still has focus

- **WHEN** the price is 100, the shares are 1000, the user deletes all digits of the fee field and saves without leaving the field
- **THEN** the system SHALL end the editing first and SHALL store the computed fee of 142

#### Scenario: Leaving the field empty

- **WHEN** the user deletes all digits of the fee field and the field loses focus
- **THEN** the fee field SHALL show the computed fee again

#### Scenario: Switching to a buy forgets a typed tax

- **WHEN** the user typed 30 in the tax field of a sell, switches to a buy and then back to a sell
- **THEN** the tax field SHALL show the computed tax

#### Scenario: Invalid fee blocks saving

- **WHEN** the user saves with a fee of 5.5 or 2147483648
- **THEN** the system SHALL show an input error on the fee field and SHALL NOT write a record

### Requirement: Compute holdings and cost with sells and fees

For one stock, the system SHALL compute the holdings as the total shares bought minus the total shares sold, and SHALL show a negative result as it is. The average buy price SHALL be the sum of the buy values and the buy fees divided by the total shares bought, where an unknown fee counts as 0, and SHALL be shown as a dash when there are no buys. The average sell price SHALL be the sum of the sell values divided by the total shares sold, without fees, and SHALL be shown as a dash when there are no sells. Average prices and profit percentages SHALL be shown rounded half up to two decimal places. The market value SHALL be the holdings multiplied by the current price, rounded half up to a whole dollar, shown only when the holdings are greater than 0 and a current price is known, and shown as a dash otherwise. For a buy record, the profit percentage SHALL be the current price minus the fee-inclusive unit cost, divided by the fee-inclusive unit cost, multiplied by 100, where the fee-inclusive unit cost is the buy value plus the fee (0 when unknown), divided by the shares; the color SHALL follow the value rounded to two decimal places: red when positive, green when negative, and the normal text color when it is 0. A sell record SHALL show a dash instead of a profit percentage. When the current price is not a number, the screen SHALL show a dash for the price, the market value and the profit percentages and SHALL NOT crash. The stock detail screen SHALL label the average buy price as including fees, and SHALL update every overview value when the records change, including when no record is left.

#### Scenario: Buy and sell the same shares

- **WHEN** the user bought 1000 shares at 100 and sold 1000 shares at 105
- **THEN** the holdings SHALL be 0 and the market value SHALL be shown as a dash

#### Scenario: Only sell records

- **WHEN** a stock has only a sell record of 400 shares
- **THEN** the holdings SHALL be -400, the market value SHALL be a dash, and the average buy price SHALL be a dash

##### Example: average buy price including fees

- **GIVEN** a buy of 1000 shares at 100 with a fee of 142, and a buy of 1000 shares at 110 with a fee of 156
- **WHEN** the stock detail screen is shown
- **THEN** the average buy price including fees SHALL be 105.149, shown as 105.15

##### Example: rounding of an average price

- **GIVEN** a buy of 1000 shares at 100 with a fee of 145
- **WHEN** the stock detail screen is shown
- **THEN** the average buy price including fees SHALL be 100.145, shown as 100.15

##### Example: profit of a buy record

- **GIVEN** a buy of 1000 shares at 100 with a fee of 142 and a current price of 105
- **WHEN** the record is shown
- **THEN** the profit percentage SHALL be shown as 4.85, in red

### Requirement: Show fee and tax per record and flag missing fee data

The records list of the stock detail screen SHALL show the fee and the tax of each record on a second line of the record row, with a dash for an unknown value. When at least one buy record of the stock has an unknown fee, the stock detail screen SHALL show a note that some buy records have no fee data. Sell records with an unknown fee SHALL NOT cause the note.

#### Scenario: Mixed buy records

- **WHEN** a stock has one buy record with a fee of 142 and one buy record with an unknown fee
- **THEN** the first record SHALL show a fee of 142, the second SHALL show a dash, and the note SHALL be shown

#### Scenario: Old sell record only

- **WHEN** the only record without a fee is a sell record
- **THEN** the note SHALL NOT be shown

### Requirement: Choose the fee discount and minimum fees in settings

The settings screen SHALL show the fee settings as one row whose summary states the saved fee discount and the two minimum fees, for example "2.8 折・最低 20 / 1 元", and "無折扣・最低 20 / 1 元" when the discount is 10. Selecting the row SHALL open a fee settings page with three fields: the fee discount, the whole-lot minimum fee and the odd-lot minimum fee, filled with the current settings. The fee discount SHALL be a plain number from 0.1 to 10 with at most two decimal places, where 10 means no discount. Each minimum fee SHALL be a whole number of dollars from 0 to 2147483647. When the user saves the page, the system SHALL validate the three values together; when any of them is invalid, the system SHALL show an error that names the first invalid value in the order discount, whole-lot minimum fee, odd-lot minimum fee, SHALL NOT save any of the three values, and SHALL keep the page open with what the user typed. When valid, the system SHALL save the three values together and close the page, and the settings row SHALL show the saved values. Reading the settings SHALL NOT write any value. When no valid discount has been saved, the system SHALL read the earlier discount setting, a percentage from 1 to 100, as that percentage divided by 10; without either, the discount SHALL be 10. When no minimum fee has been saved, or a saved minimum fee is not a whole number from 0 to 2147483647 stored as an integer, that minimum fee SHALL be the default: 20 for the whole-lot minimum fee and 1 for the odd-lot minimum fee. The fixed fee amount that an earlier version saved SHALL be ignored, and SHALL NOT be changed or deleted.

#### Scenario: Decimal discount

- **WHEN** the user enters a discount of 2.8 and saves
- **THEN** the system SHALL compute fees with a discount of 2.8 and the row SHALL read "2.8 折・最低 20 / 1 元"

#### Scenario: Discount out of range or with three decimal places

- **WHEN** the user enters a discount of 0, 11, 2.855, "abc" or an empty text and saves
- **THEN** the system SHALL show an error on the discount, SHALL NOT save any value and SHALL keep the page open

#### Scenario: Minimum fee that is not valid

- **WHEN** the user enters, in a minimum fee field, "-1", "20.5", "abc", "" or "2147483648" and saves
- **THEN** the system SHALL show an error that names that minimum fee and SHALL NOT save any value

#### Scenario: Several invalid values

- **WHEN** the user enters a discount of 11 and a whole-lot minimum fee of "-1" and saves
- **THEN** the error SHALL name the discount

#### Scenario: Earlier discount setting

- **WHEN** the earlier discount setting is "60" and no new discount is saved
- **THEN** the discount SHALL be 6 and the earlier setting SHALL remain stored

#### Scenario: Saved minimum fee that is not usable

- **WHEN** the stored odd-lot minimum fee is the text "20", a floating-point number, or -1
- **THEN** the odd-lot minimum fee SHALL be the default of 1

#### Scenario: Nothing saved

- **WHEN** no fee setting was ever saved
- **THEN** the settings row SHALL read "無折扣・最低 20 / 1 元"

#### Scenario: Fixed fee from an earlier version

- **WHEN** the earlier fixed fee is stored as "30" and nothing else was saved, and the user buys 1000 shares at a price of 100
- **THEN** the fee SHALL be 142, not 30, and the earlier fixed fee SHALL remain stored
