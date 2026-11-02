## Purpose

The stock-list-refresh capability keeps the app's list of tradable Taiwan stock numbers current. It builds the list from official open data for listed and over-the-counter securities, stores it locally with a bundled fallback, refreshes it in the background, and uses it to validate and search stock numbers.

## ADDED Requirements

### Requirement: Build the list from official open data

The system SHALL build the stock list from two official sources: the Taiwan Stock Exchange daily trading list (fields Code and Name) for listed securities and ETFs, and the Taipei Exchange daily close quotes (fields SecuritiesCompanyCode and CompanyName) for over-the-counter securities. Each entry SHALL carry a code, a name and a market, where the market is "tse" for the first source and "otc" for the second. Leading and trailing whitespace SHALL be removed from codes and names, and a row whose code or name is empty after trimming SHALL be dropped. From the over-the-counter source the system SHALL keep an entry only when its code has at most 5 characters or starts with "00", and SHALL exclude every other entry, including warrants whose 6-character codes start with 70, 71, 72 or 73. When the same code appears in both sources, the system SHALL keep the Taiwan Stock Exchange entry. The merged list SHALL be sorted by code.

#### Scenario: Over-the-counter warrants are excluded

- **WHEN** the over-the-counter source contains the codes 710000, 700019, 73000U, 72400U, 6488, 00679B, 006201 and 00411A
- **THEN** the resulting list SHALL contain 6488, 00679B, 006201 and 00411A with market "otc"
- **AND** the resulting list SHALL NOT contain 710000, 700019, 73000U or 72400U

##### Example: filtering rule

| Code | Source | Kept | Reason |
| --- | --- | --- | --- |
| 6488 | Taipei Exchange | yes | 4 characters |
| 00679B | Taipei Exchange | yes | starts with 00 |
| 710000 | Taipei Exchange | no | 6 characters, starts with 71 |
| 73000U | Taipei Exchange | no | 6 characters, starts with 73 |
| 00981A | Taiwan Stock Exchange | yes | all entries kept |

#### Scenario: Code present in both sources

- **WHEN** the code 1234 appears in both sources
- **THEN** the system SHALL keep exactly one entry for 1234, with market "tse"

#### Scenario: Payload is not a JSON array of rows

- **WHEN** a source returns text that is not a JSON array, or a row lacks the code or name field
- **THEN** parsing that source SHALL fail as a whole

---

### Requirement: Load the list from cache with a bundled fallback

The system SHALL load the stock list from the local cache file when it exists, can be decoded and contains at least one entry, and SHALL otherwise load the bundled fallback list shipped inside the app as an asset. The list provided to the rest of the app SHALL NOT be empty when the bundled list is valid. The cache and the bundled list SHALL use the same JSON shape: an object with an "entries" array whose items have "code", "name" and "market". When a code appears more than once, the market lookup SHALL use its first entry.

#### Scenario: Valid cache

- **WHEN** the cache file exists and decodes to a non-empty list
- **THEN** the system SHALL provide the cached list

#### Scenario: Missing, corrupted or empty cache

- **WHEN** the cache file does not exist, cannot be decoded, or decodes to an empty list
- **THEN** the system SHALL provide the bundled fallback list

#### Scenario: Bundled list content

- **WHEN** the app has no cache
- **THEN** the provided list SHALL contain "6488 環球晶" with market "otc", 00929 with market "tse", and 2330 with market "tse"

---

### Requirement: Refresh the list in the background when stale

The system SHALL check whether a refresh is needed each time the main activity starts (app launch and return to the foreground). A refresh SHALL start when no valid cached list exists (the cache file is missing, cannot be decoded, or is empty) or when at least 7 days have passed since the last successful refresh. The refresh SHALL run in a background WorkManager job that requires a network connection, and the staleness decision, file reads and downloads SHALL NOT run on the main thread. Each source request SHALL fail when connecting takes more than 30 seconds, when no data arrives for 30 seconds, or when the whole download takes more than 5 minutes, and cancelling the refresh SHALL cancel the request. After an attempt that adopted no market or could not write the cache, the system SHALL NOT start another refresh until 24 hours have passed since that attempt, unless the recorded attempt time is later than the current time. A successful refresh SHALL clear the recorded failed attempt. A recorded last refresh time later than the current time SHALL make the refresh due.

#### Scenario: Refresh timing

- **WHEN** the main activity starts
- **THEN** the system SHALL decide to refresh or skip according to the time since the last successful refresh

##### Example: refresh decision

| Last successful refresh | Cache exists | Refresh |
| --- | --- | --- |
| 6 days ago | yes | no |
| 7 days ago | yes | yes |
| just now | no | yes |
| never | no | yes |
| 1 day ago | corrupted | yes |
| 30 days in the future | yes | yes |

#### Scenario: Back-off after a failed attempt

- **WHEN** a refresh attempt adopted no market 23 hours ago
- **THEN** the system SHALL NOT start a refresh
- **AND** when 24 hours have passed since that attempt the refresh SHALL be due again

---

### Requirement: Protect the list from bad refresh results

The system SHALL evaluate the two markets independently during a refresh. For each market, the system SHALL adopt the fetched entries only when the fetch and decoding succeeded, at least one entry remains after filtering, and the number of entries after filtering is at least 80 percent of the number of entries that market currently has. For a market that is not adopted, the system SHALL keep its existing entries. The system SHALL write the cache atomically (a reader never observes a partially written file) and SHALL record the refresh time only when at least one market was adopted and the cache was written. A network error, timeout, non-200 HTTP status or decoding failure SHALL leave the existing list unchanged and SHALL NOT show an error to the user. When writing the cache fails, the in-memory list SHALL remain unchanged and both markets SHALL be reported as keeping old data.

#### Scenario: One market returns too few entries

- **WHEN** the over-the-counter market currently has 1007 entries and the fetch returns 700 entries
- **AND** the listed market fetch returns 1390 entries while it currently has 1380
- **THEN** the system SHALL keep the existing over-the-counter entries
- **AND** the system SHALL adopt the new listed entries

##### Example: 80 percent guard

| Current entries | Fetched entries | Adopted |
| --- | --- | --- |
| 1007 | 700 | no (700 is below 805.6) |
| 1007 | 806 | yes |
| 1380 | 1380 | yes |
| 1380 | 0 | no |

#### Scenario: Both markets fail

- **WHEN** the listed source times out and the over-the-counter source returns text that is not JSON
- **THEN** the system SHALL keep the existing list
- **AND** the system SHALL NOT record a refresh time
- **AND** the system SHALL NOT create a cache file
- **AND** the system SHALL NOT show an error to the user

#### Scenario: One market throws an unexpected error

- **WHEN** fetching the over-the-counter source throws an unexpected runtime error and the listed source returns a normal number of entries
- **THEN** the system SHALL adopt the listed entries and keep the existing over-the-counter entries

#### Scenario: Cache cannot be written

- **WHEN** both markets are adopted but writing the cache file fails
- **THEN** the in-memory list and the existing cache file SHALL remain unchanged
- **AND** both markets SHALL be reported as keeping old data
- **AND** the system SHALL NOT record a refresh time

---

### Requirement: Run at most one refresh at a time

The system SHALL run at most one refresh at any time within the app process. When a refresh is requested while another is in progress, the system SHALL return the in-progress refresh's result and SHALL NOT start a second download. The background job SHALL be enqueued as unique work that keeps an already enqueued or running job. Replacing the list SHALL be atomic for readers: after a successful refresh, every later read from any thread SHALL return the new list, and no read SHALL observe a mix of old and new entries.

#### Scenario: Two triggers at launch

- **WHEN** a refresh is requested twice while the first one is still downloading
- **THEN** the system SHALL download each source only once
- **AND** both requests SHALL return the same result

#### Scenario: Read after refresh

- **WHEN** a refresh has just adopted new entries
- **THEN** the next stock search SHALL return results from the new list

---

### Requirement: Validate stock numbers by exact match

The system SHALL accept a stock number as valid only when it is exactly equal to the code of an entry in the stock list. The system SHALL NOT accept a partial code. The add-stock dialog SHALL take the text before the first space, after trimming surrounding whitespace and converting letters to upper case, as the entered code; it SHALL add a stock only when that code is valid, and SHALL otherwise keep the dialog open and show the error "查無此股票代號". The dialog input SHALL accept letters so codes such as 00981A can be typed.

#### Scenario: Partial code is rejected

- **WHEN** the stock number validator is given "23"
- **THEN** the system SHALL reject it as an invalid stock number

##### Example: validation results

| Input | Result |
| --- | --- |
| 2330 | valid |
| 6488 | valid |
| 23 | invalid |
| (empty) | invalid |

---

### Requirement: Provide search strings for adding stocks

The system SHALL provide the list as strings in the form "code name", for example "6488 環球晶", and the add-stock search SHALL match the user's text as a substring of these strings. The search SHALL read the current list each time it filters, so a list replaced while the add-stock screen is open is used by the next search.

#### Scenario: Search by name

- **WHEN** the user types "環球" in the add-stock search
- **THEN** the results SHALL include "6488 環球晶"
