# stock-news Specification

## Purpose

The news screens show Taiwan stock market news in the News tab and stock specific news from the stock detail page.

## Requirements

### Requirement: News tab shows Taiwan stock market news

The News tab SHALL request articles from the NewsAPI `v2/everything` endpoint with `q=台股`, `language=zh` and `sortBy=publishedAt`, and SHALL show the returned articles in the order the API returns them, without re-sorting.

#### Scenario: News tab opens with articles available

- **WHEN** the user opens the News tab and the API returns one or more articles
- **THEN** the screen lists those articles in the API order and the loading indicator is hidden

#### Scenario: Pull to refresh

- **WHEN** the user pulls down on the News tab list
- **THEN** the app sends the same query again and replaces the list with the new result


<!-- @trace
source: stock-news
updated: 2026-11-04
code:
  - .agents/skills/spectra-drift/SKILL.md
  - .agents/skills/spectra-commit/SKILL.md
  - .agents/skills/spectra-archive/SKILL.md
  - .spectra.yaml
  - app/build.gradle
  - app/src/main/java/com/example/mynewsapp/ui/list/FetchStockWorker.kt
  - .agents/skills/git-commit/SKILL.md
  - .agents/skills/wrapup/SKILL.md
  - .agents/skills/spectra-audit/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - .agents/skills/spectra-discuss/SKILL.md
  - AGENTS.md
  - .agents/skills/spectra-debug/SKILL.md
  - CLAUDE.md
  - .agents/skills/spectra-verify/SKILL.md
  - .agents/skills/spectra-propose/SKILL.md
  - .agents/skills/spectra-review/SKILL.md
  - .agents/skills/spectra-apply/SKILL.md
  - docs/ios-parity-assessment.md
  - .agents/skills/spectra-ingest/SKILL.md
  - .agents/skills/spectra-analyze/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
-->

---
### Requirement: Stock detail page opens news for that stock

The stock detail page SHALL provide a "新聞" toolbar action that opens a news list for the stock being viewed. The list SHALL request `v2/everything` with `qInTitle` set to the stock name and `sortBy=publishedAt`, and SHALL NOT send `q` or `language`.

#### Scenario: Open news from a stock detail page

- **GIVEN** the user is viewing the detail page of stock "台積電" (2330)
- **WHEN** the user taps the "新聞" toolbar action
- **THEN** the app shows a news list whose request has `qInTitle=台積電`
- **AND** the system back action returns to the detail page of 2330

#### Scenario: Stock name is blank

- **WHEN** the news list is opened with a blank stock name
- **THEN** the app uses the Taiwan stock market query instead of sending an empty `qInTitle`

##### Example: Query parameters

| Query | q | qInTitle | language | sortBy |
| ----- | - | -------- | -------- | ------ |
| Taiwan stock market | 台股 | (not sent) | zh | publishedAt |
| Stock "台積電" | (not sent) | 台積電 | (not sent) | publishedAt |
| Stock "  環球晶 " | (not sent) | 環球晶 | (not sent) | publishedAt |
| Stock "" or no name | 台股 | (not sent) | zh | publishedAt |


<!-- @trace
source: stock-news
updated: 2026-11-04
code:
  - .agents/skills/spectra-drift/SKILL.md
  - .agents/skills/spectra-commit/SKILL.md
  - .agents/skills/spectra-archive/SKILL.md
  - .spectra.yaml
  - app/build.gradle
  - app/src/main/java/com/example/mynewsapp/ui/list/FetchStockWorker.kt
  - .agents/skills/git-commit/SKILL.md
  - .agents/skills/wrapup/SKILL.md
  - .agents/skills/spectra-audit/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - .agents/skills/spectra-discuss/SKILL.md
  - AGENTS.md
  - .agents/skills/spectra-debug/SKILL.md
  - CLAUDE.md
  - .agents/skills/spectra-verify/SKILL.md
  - .agents/skills/spectra-propose/SKILL.md
  - .agents/skills/spectra-review/SKILL.md
  - .agents/skills/spectra-apply/SKILL.md
  - docs/ios-parity-assessment.md
  - .agents/skills/spectra-ingest/SKILL.md
  - .agents/skills/spectra-analyze/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
-->

---
### Requirement: News list shows empty and error states

The news list SHALL show the text "沒有相關新聞" over an empty list when a request succeeds with no displayable articles, SHALL keep pull-to-refresh available while that text is showing, and SHALL stop the pull-to-refresh indicator and show the error message when a request fails.

#### Scenario: No articles

- **WHEN** the API responds successfully with zero articles
- **THEN** the screen shows "沒有相關新聞" and the loading indicator is hidden

#### Scenario: Pull to refresh while empty

- **GIVEN** the empty text is showing
- **WHEN** the user pulls down on the screen
- **THEN** the pull-to-refresh indicator appears and the app sends the query again

#### Scenario: Request fails

- **WHEN** the device has no network connection, the connection times out or drops, the response cannot be parsed, or the API responds with an error status
- **THEN** the app does not crash
- **AND** the pull-to-refresh indicator stops
- **AND** a Snackbar shows a non-empty message
- **AND** the list keeps its previous content

##### Example: API error message

| Response | Snackbar message (shown as is, without a prefix) |
| -------- | ---------------- |
| 429 with body `{"status":"error","code":"rateLimited","message":"too many"}` | too many |
| 429 with an empty body | HTTP 429 |

#### Scenario: Articles with missing fields

- **GIVEN** the API returns three articles, one with a null title, one with a null url, and one with the title "[Removed]"
- **WHEN** the list is shown
- **THEN** only articles that have both a title and a url, and whose title is not "[Removed]", are listed
- **AND** their order is the API order

#### Scenario: Articles replace the empty state

- **GIVEN** the empty text is showing
- **WHEN** the user pulls down to refresh and the new request returns one or more articles
- **THEN** the empty text is hidden and the articles are listed


<!-- @trace
source: stock-news
updated: 2026-11-04
code:
  - .agents/skills/spectra-drift/SKILL.md
  - .agents/skills/spectra-commit/SKILL.md
  - .agents/skills/spectra-archive/SKILL.md
  - .spectra.yaml
  - app/build.gradle
  - app/src/main/java/com/example/mynewsapp/ui/list/FetchStockWorker.kt
  - .agents/skills/git-commit/SKILL.md
  - .agents/skills/wrapup/SKILL.md
  - .agents/skills/spectra-audit/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - .agents/skills/spectra-discuss/SKILL.md
  - AGENTS.md
  - .agents/skills/spectra-debug/SKILL.md
  - CLAUDE.md
  - .agents/skills/spectra-verify/SKILL.md
  - .agents/skills/spectra-propose/SKILL.md
  - .agents/skills/spectra-review/SKILL.md
  - .agents/skills/spectra-apply/SKILL.md
  - docs/ios-parity-assessment.md
  - .agents/skills/spectra-ingest/SKILL.md
  - .agents/skills/spectra-analyze/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
-->

---
### Requirement: Opening an article is unchanged

Tapping an article in any news list SHALL open the article URL in a Custom Tab.

#### Scenario: Tap an article

- **WHEN** the user taps an article row
- **THEN** the article URL opens in a Custom Tab

<!-- @trace
source: stock-news
updated: 2026-11-04
code:
  - .agents/skills/spectra-drift/SKILL.md
  - .agents/skills/spectra-commit/SKILL.md
  - .agents/skills/spectra-archive/SKILL.md
  - .spectra.yaml
  - app/build.gradle
  - app/src/main/java/com/example/mynewsapp/ui/list/FetchStockWorker.kt
  - .agents/skills/git-commit/SKILL.md
  - .agents/skills/wrapup/SKILL.md
  - .agents/skills/spectra-audit/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - .agents/skills/spectra-discuss/SKILL.md
  - AGENTS.md
  - .agents/skills/spectra-debug/SKILL.md
  - CLAUDE.md
  - .agents/skills/spectra-verify/SKILL.md
  - .agents/skills/spectra-propose/SKILL.md
  - .agents/skills/spectra-review/SKILL.md
  - .agents/skills/spectra-apply/SKILL.md
  - docs/ios-parity-assessment.md
  - .agents/skills/spectra-ingest/SKILL.md
  - .agents/skills/spectra-analyze/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
-->