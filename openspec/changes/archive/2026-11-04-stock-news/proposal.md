## Why

新聞分頁呼叫 NewsAPI 的 `top-headlines?country=tw&category=business`，該端點對台灣沒有資料（回傳 `totalResults` 為 0），所以分頁永遠空白。iOS 版的新聞是從個股詳情頁進入、以股票名稱查詢相關新聞，Android 版沒有這個入口。

## What Changes

- 新聞分頁改查台股新聞：呼叫 `v2/everything`，以 `q=台股`、`language=zh`、`sortBy=publishedAt` 取得文章。
- 個股詳情頁工具列新增「新聞」動作，開啟該股票的新聞清單：呼叫 `v2/everything`，以 `qInTitle=<股票名稱>`、`sortBy=publishedAt` 取得文章，對應 iOS 版的 `NewsAPIClient.fetchNews(queryTitle:)`。
- 新聞清單沒有文章時顯示「沒有相關新聞」；載入失敗（沒有網路、逾時、回應格式錯誤、API 錯誤）時關閉下拉重新整理的轉圈圈並顯示錯誤訊息，不會崩潰。
- 新聞文章的欄位改為可為空，標題或網址缺漏與「[Removed]」的文章不顯示。
- 點擊文章的行為不變：以 Custom Tabs 開啟原文，不新增文章內頁。

## Non-Goals (optional)

- 不把寫在 `Constant.kt` 的 NewsAPI 金鑰移出版控；這是另一件事，需先決定金鑰的存放與 CI 設定方式。
- 不做分頁載入（下拉到底載入下一頁）；維持現有只取第一頁的行為。
- 不新增新聞快取或離線顯示。
- 不改 `getHeadlines`（保留，但不再被新聞畫面使用）。
- 與 iOS 的刻意差異：Android 兩種查詢都加 `sortBy=publishedAt`，iOS 沒有送排序參數。

## Capabilities

### New Capabilities

- `stock-news`: 新聞分頁的台股新聞，以及個股詳情頁以股票名稱查詢的相關新聞。

### Modified Capabilities

(none)

## Impact

- Affected specs: `stock-news`（新增）
- Affected code:
  - New: `app/src/main/java/com/example/mynewsapp/repository/NewsQuery.kt`
  - New: `app/src/main/java/com/example/mynewsapp/repository/NewsArticleFilter.kt`
  - New: `app/src/test/java/com/example/mynewsapp/repository/NewsQueryTest.kt`
  - New: `app/src/test/java/com/example/mynewsapp/repository/NewsArticleFilterTest.kt`
  - New: `app/src/test/java/com/example/mynewsapp/api/NewsApiRequestTest.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/api/NewsAPI.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/repository/NewsRepository.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/model/Article.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/model/Source.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/ui/adapter/NewsAdapter.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/ui/news/NewsArticleFragment.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/ui/news/NewsViewModel.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/ui/news/NewsFragment.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/ui/detail/CandleStickChartFragment.kt`
  - Modified: `app/src/main/res/layout/fragment_news.xml`
  - Modified: `app/src/main/res/menu/candle_stick_chart_fragment_option_menu.xml`
  - Modified: `app/src/main/res/navigation/stock.xml`
  - Modified: `app/src/main/res/values/strings.xml`
