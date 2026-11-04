# news-api-key Specification

## Purpose

The news API key is supplied by each developer's local configuration and is never stored in the repository, written to a request URL or written to a log.

## Requirements

### Requirement: News API key comes from local configuration

The app SHALL read the news API key from the `NEWS_API_KEY` entry of the untracked `local.properties` file at build time, SHALL trim surrounding whitespace and one pair of surrounding quotes from the value, SHALL treat a missing entry or file as an empty key, and SHALL NOT contain the key as a literal in any tracked source file.

#### Scenario: Key is configured

- **GIVEN** `local.properties` contains `NEWS_API_KEY=abc123`
- **WHEN** the app is built
- **THEN** `BuildConfig.NEWS_API_KEY` equals `abc123`

#### Scenario: Key value has whitespace or quotes

- **GIVEN** `local.properties` contains `NEWS_API_KEY= "abc123" `
- **WHEN** the app is built
- **THEN** `BuildConfig.NEWS_API_KEY` equals `abc123`

#### Scenario: Key is not configured

- **GIVEN** `local.properties` does not exist or has no `NEWS_API_KEY` entry
- **WHEN** the app is built and its unit tests are run
- **THEN** the build and the tests succeed and `BuildConfig.NEWS_API_KEY` is an empty string


<!-- @trace
source: news-api-key-config
updated: 2026-11-04
code:
  - .agents/skills/spectra-ingest/SKILL.md
  - .agents/skills/spectra-propose/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - .agents/skills/wrapup/SKILL.md
  - .spectra.yaml
  - .agents/skills/git-commit/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - .agents/skills/spectra-audit/SKILL.md
  - .agents/skills/spectra-archive/SKILL.md
  - .agents/skills/spectra-discuss/SKILL.md
  - .agents/skills/spectra-review/SKILL.md
  - .agents/skills/spectra-analyze/SKILL.md
  - .agents/skills/spectra-debug/SKILL.md
  - .agents/skills/spectra-drift/SKILL.md
  - .agents/skills/spectra-verify/SKILL.md
  - app/build.gradle
  - app/src/main/java/com/example/mynewsapp/ui/list/FetchStockWorker.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
  - CLAUDE.md
  - docs/ios-parity-assessment.md
  - .agents/skills/spectra-commit/SKILL.md
  - AGENTS.md
  - .agents/skills/spectra-apply/SKILL.md
-->

---
### Requirement: News API key is sent only as a header to the news host

Requests to `newsapi.org` SHALL carry the key in the `X-Api-Key` header when the key is not blank (the same blank test is used everywhere in the app), SHALL NOT carry it as a query parameter, and requests to any other host SHALL NOT carry the header.

#### Scenario: Request to the news host

- **GIVEN** the key is `abc123`
- **WHEN** the app sends a request to `https://newsapi.org/v2/everything?qInTitle=台積電`
- **THEN** the request has the header `X-Api-Key: abc123`
- **AND** the request URL has no `apiKey` query parameter

#### Scenario: Request to another host

- **GIVEN** the key is `abc123`
- **WHEN** the app sends a request to `https://mis.twse.com.tw/stock/api/getStockInfo.jsp`
- **THEN** the request has no `X-Api-Key` header

#### Scenario: Key with characters a header cannot carry

- **GIVEN** the key contains curly quotes
- **WHEN** the app sends a request to the news host
- **THEN** the request fails with an I/O error and the app does not crash

#### Scenario: Blank key

- **GIVEN** the key is empty or only whitespace
- **WHEN** the app sends a request to the news host
- **THEN** the request has no `X-Api-Key` header


<!-- @trace
source: news-api-key-config
updated: 2026-11-04
code:
  - .agents/skills/spectra-ingest/SKILL.md
  - .agents/skills/spectra-propose/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - .agents/skills/wrapup/SKILL.md
  - .spectra.yaml
  - .agents/skills/git-commit/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - .agents/skills/spectra-audit/SKILL.md
  - .agents/skills/spectra-archive/SKILL.md
  - .agents/skills/spectra-discuss/SKILL.md
  - .agents/skills/spectra-review/SKILL.md
  - .agents/skills/spectra-analyze/SKILL.md
  - .agents/skills/spectra-debug/SKILL.md
  - .agents/skills/spectra-drift/SKILL.md
  - .agents/skills/spectra-verify/SKILL.md
  - app/build.gradle
  - app/src/main/java/com/example/mynewsapp/ui/list/FetchStockWorker.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
  - CLAUDE.md
  - docs/ios-parity-assessment.md
  - .agents/skills/spectra-commit/SKILL.md
  - AGENTS.md
  - .agents/skills/spectra-apply/SKILL.md
-->

---
### Requirement: News API key is not written to logs

HTTP request logging SHALL replace the value of the `X-Api-Key` header with a redaction marker, so the key does not appear in any log line.

#### Scenario: Logged request

- **GIVEN** the key is `abc123`
- **WHEN** a request to the news host is logged at BODY level
- **THEN** the log contains a line `X-Api-Key: ██`
- **AND** no log line contains `abc123`


<!-- @trace
source: news-api-key-config
updated: 2026-11-04
code:
  - .agents/skills/spectra-ingest/SKILL.md
  - .agents/skills/spectra-propose/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - .agents/skills/wrapup/SKILL.md
  - .spectra.yaml
  - .agents/skills/git-commit/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - .agents/skills/spectra-audit/SKILL.md
  - .agents/skills/spectra-archive/SKILL.md
  - .agents/skills/spectra-discuss/SKILL.md
  - .agents/skills/spectra-review/SKILL.md
  - .agents/skills/spectra-analyze/SKILL.md
  - .agents/skills/spectra-debug/SKILL.md
  - .agents/skills/spectra-drift/SKILL.md
  - .agents/skills/spectra-verify/SKILL.md
  - app/build.gradle
  - app/src/main/java/com/example/mynewsapp/ui/list/FetchStockWorker.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
  - CLAUDE.md
  - docs/ios-parity-assessment.md
  - .agents/skills/spectra-commit/SKILL.md
  - AGENTS.md
  - .agents/skills/spectra-apply/SKILL.md
-->

---
### Requirement: Missing key is reported without a request

When the key is blank, the news screens SHALL NOT send a request to the news host and SHALL show the message "尚未設定新聞 API 金鑰，請在 local.properties 加入 NEWS_API_KEY".

#### Scenario: Open news without a key

- **GIVEN** the key is an empty string
- **WHEN** the user opens the News tab or the news of a stock
- **THEN** a Snackbar shows the missing key message
- **AND** the pull-to-refresh indicator is not showing
- **AND** no request to `newsapi.org` is sent

<!-- @trace
source: news-api-key-config
updated: 2026-11-04
code:
  - .agents/skills/spectra-ingest/SKILL.md
  - .agents/skills/spectra-propose/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/MainActivity.kt
  - .agents/skills/wrapup/SKILL.md
  - .spectra.yaml
  - .agents/skills/git-commit/SKILL.md
  - app/src/main/java/com/example/mynewsapp/ui/list/ListViewModel.kt
  - .agents/skills/spectra-audit/SKILL.md
  - .agents/skills/spectra-archive/SKILL.md
  - .agents/skills/spectra-discuss/SKILL.md
  - .agents/skills/spectra-review/SKILL.md
  - .agents/skills/spectra-analyze/SKILL.md
  - .agents/skills/spectra-debug/SKILL.md
  - .agents/skills/spectra-drift/SKILL.md
  - .agents/skills/spectra-verify/SKILL.md
  - app/build.gradle
  - app/src/main/java/com/example/mynewsapp/ui/list/FetchStockWorker.kt
  - app/src/main/java/com/example/mynewsapp/ui/list/ListFragment.kt
  - CLAUDE.md
  - docs/ios-parity-assessment.md
  - .agents/skills/spectra-commit/SKILL.md
  - AGENTS.md
  - .agents/skills/spectra-apply/SKILL.md
-->