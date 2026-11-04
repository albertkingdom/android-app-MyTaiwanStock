## 1. 純邏輯與測試

- [x] 1.1 （設計：以 NewsQuery 表示新聞查詢；規格：Stock detail page opens news for that stock）在 `repository` 套件新增 `NewsQuery`（`TaiwanStock`、`Stock(name)`）、參數轉換與 `NewsQuery.forStock(name: String?)`：台股為 `q=台股`、`language=zh`、`sortBy=publishedAt`；個股為 `qInTitle=<去除前後空白的名稱>`、`sortBy=publishedAt`；null、空字串、空白名稱回傳 `TaiwanStock`。先寫 `app/src/test/java/com/example/mynewsapp/repository/NewsQueryTest.kt`（涵蓋 spec 範例表各列與前後空白），確認實作前失敗，再實作到通過。驗證：`./gradlew testDebugUnitTest --tests "*NewsQueryTest"`。
- [x] 1.2 （設計：文章欄位可為空並過濾、回應錯誤不可崩潰）新增純函式 `usableArticles(list)`（去掉標題或網址為 null 或空白、標題為「[Removed]」的文章，保持順序）與 `newsErrorMessage(code, errorBody)`（取 JSON 的 `message`，否則「HTTP <code>」），同時把 `Article.title`、`url`、`publishedAt`、`source` 與 `Source.name` 改為可為空，並在同一個任務加固讀取它們的地方：`NewsAdapter` 在 `publishedAt` 為空或無法解析時留空白、`title` 為空時顯示空字串，`NewsArticleFragment` 以 `article.url?.let` 載入網址。`newsErrorMessage` 用 Moshi 解析錯誤本文（小型 `@JsonClass` 類型），不用 `org.json`，因為 JVM 單元測試沒有該實作。先寫 `NewsArticleFilterTest`（含錯誤訊息的兩個範例與 spec 的缺欄位情境），確認失敗再實作到通過。驗證：`./gradlew testDebugUnitTest --tests "*NewsArticleFilterTest"`。

## 2. API 與 Repository

- [x] 2.1 （設計：沿用 everything 端點並調整 NewsAPI.searchForNews；規格：News tab shows Taiwan stock market news）一次完成 `NewsAPI.searchForNews` 改為 `q`、`qInTitle`、`language`、`sortBy`（預設 `publishedAt`）、`page`、`apiKey`（前三者預設 null，移除預設「台積電」），並把 `NewsRepository.searchNews` 改為接收 `NewsQuery` 與 `page`，讓專案保持可編譯。先寫 `app/src/test/java/com/example/mynewsapp/api/NewsApiRequestTest.kt`，以 MockWebServer 驗證兩種查詢實際送出的路徑與參數（個股不含 `q`、`language`，台股不含 `qInTitle`），確認失敗再實作到通過。驗證：`./gradlew testDebugUnitTest --tests "*NewsApiRequestTest"` 並 `./gradlew assembleDebug` 通過。
- [x] 2.2 以 `curl -G` 搭配 `--data-urlencode` 對真實 NewsAPI 以台股與「台積電」兩組參數各呼叫一次（金鑰取自 `Constant.kt`，不印出），確認 `status` 為 `ok` 且文章數大於 0。驗證：兩次的 `totalResults` 皆大於 0。

## 3. 新聞畫面

- [x] 3.1 （設計：回應錯誤不可崩潰、空狀態與錯誤處理）`NewsViewModel` 與 `NewsViewModelFactory` 增加 `query` 參數（預設 `NewsQuery.TaiwanStock`，所以 `NewsFragment` 在此任務完成前仍可編譯），以 `getNews(page)` 取代 `getHeadlines`（同時把 `NewsFragment` 下拉重新整理的呼叫改為 `getNews()`，讓專案保持可編譯）並呼叫 `searchNews`；捕捉 `IOException` 與 `JsonDataException`（不捕捉 `CancellationException`）轉成 `Resource.Error`，非 2xx 或 2xx 但本文為 null 時以 `newsErrorMessage` 產生訊息（Snackbar 不再加「An error occured:」前綴，直接顯示該訊息），成功時以 `usableArticles` 過濾；沒有網路仍回傳 `Resource.Error("No Internet Connection")`。驗證：`./gradlew assembleDebug` 通過，模擬器開新聞分頁看到台股文章。
- [x] 3.2 （設計：空狀態與錯誤處理；規格：News list shows empty and error states）`fragment_news.xml` 的 `SwipeRefreshLayout` 改為充滿畫面、`RecyclerView` 永遠可見，新增置中的 `emptyView`（字串資源 `news_empty`「沒有相關新聞」）；`NewsFragment` 在成功且過濾後為空時顯示，有文章時隱藏，Loading 與 Error 不改變 `emptyView` 的顯示狀態；`Resource.Error` 時關閉 `swipeRefresh` 並顯示 Snackbar。驗證：模擬器關閉網路後下拉，轉圈圈停止並出現非空的錯誤訊息，不崩潰。
- [x] 3.3 （設計：同一個 NewsFragment 服務兩個入口）`NewsFragment` 從 Bundle 鍵 `stockName`（鍵名放共用常數）讀取可選的名稱，以 `NewsQuery.forStock` 組成查詢傳給 `NewsViewModelFactory`；有名稱時把工具列標題設為「<股票名稱> 新聞」（字串資源）。驗證：`./gradlew assembleDebug` 通過；新聞分頁（沒有參數）仍查台股。

## 4. 個股詳情頁入口

- [x] 4.1 在 `stock.xml` 新增目的地 `stockNewsFragment`（類別 `NewsFragment`，標題重用字串資源 `title_businessNewsList`）與 `candleStickChartFragment` 到它的動作，不宣告導覽參數。驗證：`./gradlew assembleDebug` 通過（導覽圖與 Safe Args 產生無錯誤）。
- [x] 4.2 （設計：工具列加「新聞」文字動作；規格：Stock detail page opens news for that stock）在 `candle_stick_chart_fragment_option_menu.xml` 新增 `go_to_news`（標題重用 `title_businessNewsList`，`showAsAction=ifRoom`），`CandleStickChartFragment.onOptionsItemSelected` 以 `args.stockName` 帶入 Bundle 並導向 `stockNewsFragment`。驗證：在模擬器打開台積電詳情頁，點「新聞」（可能在溢出選單）看到標題含「台積電」的文章與標題「台積電 新聞」，按返回回到詳情頁。

## 5. 驗證與收尾

- [x] 5.1 先在另一個乾淨的 worktree（基底分支）記錄 `./gradlew lintDebug` 的基準結果，再用暫時寫死的不存在股票名稱驗證空狀態：顯示「沒有相關新聞」，且空狀態下可下拉重新整理（驗證完還原）。驗證：空狀態畫面截圖，且下拉後會重新送出請求。
- [x] 5.2 （規格：Opening an article is unchanged）執行 `./gradlew testDebugUnitTest assembleDebug lintDebug`，lint 沒有比基準新增的問題，並在 Pixel 8 模擬器走完 design 的手動驗證清單（含點文章以 Custom Tab 開啟原文，行為與現在相同）。驗證：指令全數通過，手動清單逐項通過，保存新聞分頁與個股新聞的截圖。
