## Context

- 新聞分頁（`NewsFragment`、`NewsViewModel`）呼叫 `NewsAPI.getHeadlines`，即 `top-headlines?country=tw&category=business`。該端點對台灣沒有資料，實測 `totalResults` 為 0，畫面空白。
- 同一個 `NewsAPI` 介面已有 `searchForNews`（`v2/everything`），`NewsRepository.searchNews` 包裝了它，但沒有任何畫面呼叫。
- 個股詳情頁 `CandleStickChartFragment` 的導覽參數已有 `stockName`、`stockNo`，工具列選單有聊天室與新增兩個動作。
- 文章點擊由 `NewsFragment` 直接以 Custom Tabs 開啟原文；`news.xml` 的 `newsArticleFragment` 目前沒有被導向。
- iOS 版從個股詳情頁進入新聞清單，查詢是 `v2/everything?qInTitle=<股票名稱>`。

## Goals / Non-Goals

**Goals:**

- 新聞分頁顯示台股新聞，個股詳情頁可開啟該股票的相關新聞。
- 查詢參數的決定邏輯是不依賴 Android 的純 Kotlin，可用 JVM 單元測試驗證。
- 空結果與錯誤都有明確的畫面狀態，轉圈圈不會卡住。

**Non-Goals:**

- 不移動 `Constant.kt` 內的 NewsAPI 金鑰。
- 不做分頁載入、快取或離線顯示。
- 不新增文章內頁；點擊文章的行為不變。
- 不處理 NewsAPI 免費方案的額度與用量限制，超過額度時只顯示回應訊息。

## Decisions

### 以 NewsQuery 表示新聞查詢

新增 `NewsQuery`（sealed class，放在 `repository` 套件，避免 repository 依賴 `ui`），有兩種值：`TaiwanStock` 與 `Stock(name)`。它只負責把查詢轉成 API 參數（`q`、`qInTitle`、`language`、`sortBy`），不碰網路與 Android。呼叫端一律用工廠函式 `NewsQuery.forStock(name: String?)`：名稱為 null 或去除前後空白後為空時回傳 `TaiwanStock`，否則回傳 `Stock(去除空白的名稱)`，避免送出空的 `qInTitle`。

理由：參數組合是這個功能最容易出錯的地方（哪個端點用哪個參數），放在純函式裡可以用單元測試固定下來，不需要 Retrofit 或模擬器。

### 沿用 everything 端點並調整 NewsAPI.searchForNews

`searchForNews` 的參數改為 `q`、`qInTitle`、`language`、`sortBy`、`page`、`apiKey`，其中 `q`、`qInTitle`、`language` 預設為 null。Retrofit 對 null 的 `@Query` 參數不會送出，所以兩種查詢共用同一個方法。`sortBy` 預設為 `publishedAt`。原本預設的 `stockName = "台積電"` 移除，因為沒有呼叫者依賴它。`getHeadlines` 保留不動。

### 個股新聞用 qInTitle，台股分頁用 q

個股新聞用 `qInTitle=<股票名稱>`，與 iOS 一致，結果的標題一定含股票名稱，相關度較高。台股分頁用 `q=台股` 加 `language=zh`，只取中文文章。個股新聞不加 `language`，與 iOS 一致。兩種查詢都加 `sortBy=publishedAt`，這是與 iOS 的刻意差異（iOS 沒有送排序參數），目的是讓最新的文章排前面。

### 同一個 NewsFragment 服務兩個入口

`NewsFragment` 讀取可選的 `stockName` 參數：沒有或為空就查 `TaiwanStock`，有就查 `Stock(stockName)`。參數以 Bundle 鍵 `stockName` 讀取，不用 Safe Args：Safe Args 的 Args 類別以 Fragment 類別名稱命名，同一個 `NewsFragment` 在兩張導覽圖有不同的參數清單，會產生同名但內容不同的 `NewsFragmentArgs`，所以不宣告參數，改用 Bundle 鍵。

`stock.xml` 新增目的地 `stockNewsFragment`（類別為 `NewsFragment`，標題「新聞」），詳情頁導向它並帶入 `stockName`。`news.xml` 的 `newsFragment` 不帶參數，行為是台股新聞。

`NewsViewModelFactory` 增加 `query` 參數，預設為 `NewsQuery.TaiwanStock`；`NewsViewModel` 以 `query` 取代寫死的頭條查詢，並把 `getHeadlines` 改名為 `getNews`。詳情頁進入時，`NewsFragment` 把工具列標題設為「<股票名稱> 新聞」（字串資源），沒有股票名稱時沿用導覽圖標題「新聞」。

### 工具列加「新聞」文字動作

`candle_stick_chart_fragment_option_menu.xml` 新增 `go_to_news`，標題重用既有字串資源 `title_businessNewsList`（「新聞」），導覽圖標題也重用它；`showAsAction` 為 `ifRoom`，沒有圖示，因為專案目前沒有新聞圖示資源。同一列已有兩個 `always` 的圖示，這個動作可能被收進溢出選單，這是可接受的。

### 空狀態與錯誤處理

`fragment_news.xml` 的 `SwipeRefreshLayout` 改為充滿畫面（原本是 `wrap_content`，清單隱藏後高度會變 0，無法下拉）；`RecyclerView` 永遠保持可見（沒有資料時就是空的），新增置中的 `emptyView` 文字疊在它上面。結果為成功但沒有可顯示的文章時顯示「沒有相關新聞」，有文章時隱藏。`Resource.Error` 時關閉 `swipeRefresh` 的轉圈圈，並沿用 Snackbar 顯示訊息。

### 回應錯誤不可崩潰

`NewsViewModel.getNews` 在呼叫 Retrofit 時捕捉 `IOException`（逾時、DNS 失敗、連上 Wi-Fi 但沒有網際網路）與 `JsonDataException`（回應格式不符），轉成 `Resource.Error` 並顯示字串資源訊息；`CancellationException` 不捕捉。`isNetworkAvailable` 只檢查有沒有連線，所以不能取代這個保護。

API 回傳非 2xx 時，或回傳 2xx 但本文為空時，訊息由純函式 `newsErrorMessage(code, errorBody)` 產生（以 Moshi 解析錯誤本文，不用 `org.json`；2xx 無本文時傳入 null 本文，得到「HTTP <code>」）：優先取錯誤本文 JSON 的 `message` 欄位（NewsAPI 格式為 `{status, code, message}`），沒有就用「HTTP <code>」。不使用 `response.message()`，因為 HTTP/2 下它常為空字串，會讓 429、401、426 顯示成空白的 Snackbar。

### 文章欄位可為空並過濾

`everything` 可能回傳含 null 的文章。`Article.title`、`url`、`publishedAt`、`source` 與 `Source.name` 改為可為空；純函式 `usableArticles(list)` 去掉標題或網址為空（含空白）的文章，以及標題為「[Removed]」的占位文章，其餘保持 API 回傳的順序。`NewsAdapter` 在 `publishedAt` 為空或無法解析時留空白、`title` 為空時顯示空字串，`NewsArticleFragment` 以 `article.url?.let` 載入網址，都不丟例外；清單以 `url` 為 `DiffUtil` 的 key，過濾後 url 不會是空。旋轉時 `LiveData` 會重送最後一個錯誤，Snackbar 可能再出現一次，這是可接受的。

## Implementation Contract

**Behavior**

- 點底部導覽的「新聞」：畫面載入後顯示以 `q=台股`、`language=zh`、`sortBy=publishedAt` 查到的文章，依發布時間由新到舊。
- 在個股詳情頁點工具列「新聞」：進入新聞清單，顯示以 `qInTitle=<股票名稱>`、`sortBy=publishedAt` 查到的文章；返回鍵回到詳情頁。
- 下拉重新整理會重新送出同一個查詢。
- 點文章：以 Custom Tabs 開啟原文，與現在相同。

**Interface / data shape**

- `NewsQuery.TaiwanStock`、`NewsQuery.Stock(name: String)`，以及轉換出的參數 `q: String?`、`qInTitle: String?`、`language: String?`、`sortBy: String`。
- `NewsAPI.searchForNews(q, qInTitle, language, sortBy, page, apiKey)`，回傳 `Response<NewsResponse>`。
- `NewsRepository.searchNews(query: NewsQuery, page: Int)`。
- `NewsViewModel(newsRepository, application, query)`，函式 `getNews(page: Int = 1)`。
- 導覽參數 Bundle 鍵：`stockName`（字串，可省略）。

**Failure modes**

- 沒有網路：顯示 Snackbar「No Internet Connection」，轉圈圈關閉，清單維持原狀。
- 逾時、DNS 失敗或連線中斷（`IOException`）與回應格式錯誤（`JsonDataException`）：顯示 Snackbar，轉圈圈關閉，不崩潰。
- API 回傳非成功：顯示 Snackbar，訊息為錯誤本文的 `message`，沒有時為「HTTP <code>」，轉圈圈關閉。
- 部分文章缺標題或網址、或為「[Removed]」：略過那幾篇，其餘照常顯示。
- API 成功但沒有文章：顯示「沒有相關新聞」。
- 股票名稱為空白：退回台股查詢，不送空的 `qInTitle`。

**Acceptance criteria**

- `NewsQueryTest` 驗證（透過 `NewsQuery.forStock`）：`TaiwanStock` 的參數為 `q=台股`、`language=zh`、`qInTitle=null`、`sortBy=publishedAt`；`Stock("台積電")` 為 `qInTitle=台積電`、`q=null`、`language=null`、`sortBy=publishedAt`；`forStock(null)`、`forStock("")`、`forStock("  ")` 都回傳 `TaiwanStock`（物件相等）；名稱前後的空白會被去除。
- `NewsApiRequestTest` 以 MockWebServer 驗證實際送出的請求：以 `requestUrl.encodedPath`、`queryParameter(...)` 與 `queryParameterNames` 斷言（不比對含百分比編碼的原始路徑字串）：個股查詢路徑為 `/v2/everything`，`qInTitle=台積電`、`sortBy=publishedAt`，且參數名稱不含 `q` 與 `language`；台股查詢含 `q=台股`、`language=zh`、`sortBy=publishedAt`，且參數名稱不含 `qInTitle`。
- `NewsArticleFilterTest` 驗證：標題為 null、空白，或網址為 null、空白的文章被去除；標題為「[Removed]」被去除；其餘保持原順序。
- `NewsArticleFilterTest` 另驗證錯誤訊息：錯誤本文 `{"status":"error","code":"rateLimited","message":"too many"}` 取得 `too many`；本文為空或不是 JSON 時為 `HTTP 429`。
- `./gradlew testDebugUnitTest assembleDebug lintDebug` 通過。
- 模擬器手動驗證：新聞分頁有台股文章；打開台積電的詳情頁，點「新聞」看到標題含「台積電」的文章；關閉網路後下拉，轉圈圈停止並顯示錯誤；空狀態以暫時寫死的不存在股票名稱驗證（驗證完還原），並確認空狀態下仍可下拉重新整理；「新聞」動作可能在溢出選單中。

**Scope boundaries**

- In scope：新聞分頁查詢、個股新聞入口、空狀態、錯誤時關閉轉圈圈。
- Out of scope：API 金鑰存放、分頁載入、快取、文章內頁、NewsAPI 額度處理。

## Risks / Trade-offs

- NewsAPI 免費方案每日請求有限，且 `everything` 端點的內容品質不一；個股名稱是短詞（如「環球晶」）時，結果可能包含不相關的簡體中文文章。接受這個限制，與 iOS 行為一致。
- 若把 `Article` 欄位改為可為空，`NewsAdapter` 與其他讀取文章的程式都要處理 null，編譯器會協助找出遺漏。
- 金鑰仍在版控內，每次建置都帶著同一把金鑰；此風險沿用到另一件工作處理。
- 手動以 Bundle 鍵讀取參數少了編譯期檢查；用常數集中鍵名並由導覽呼叫處與 `NewsFragment` 共用，降低拼錯鍵名的風險。
