## 1. 測試先行

- [x] 1.1 （設計：以攔截器送出 X-Api-Key 標頭、記錄時遮蔽金鑰；規格：News API key is sent only as a header to the news host、News API key is not written to logs）先寫 `app/src/test/java/com/example/mynewsapp/api/NewsApiKeyInterceptorTest.kt`（MockWebServer）：主機相符且金鑰非空時請求帶 `X-Api-Key`；金鑰為空白時沒有；主機不符時沒有；終端攔截器回傳固定回應時，完整網址 `https://newsapi.org/...` 也會帶標頭；以 `newsAwareClient` 搭配收集記錄行的 logger，記錄含 `X-Api-Key: ██` 且不含金鑰字串。同時把 `NewsApiRequestTest` 的 `apiKey` 斷言改為「查詢參數名稱不含 `apiKey`」。確認測試在實作前失敗（預期的紅燈是編譯錯誤 `Unresolved reference: NewsApiKeyInterceptor / newsAwareClient`；因為它會讓所有單元測試無法編譯，任務 1.1 到 3.1 要放在同一個 commit，不要分開推送）。驗證：`./gradlew testDebugUnitTest --tests "*NewsApiKeyInterceptorTest" --tests "*NewsApiRequestTest"` 失敗並指出缺少的類別。

## 2. 金鑰來源

- [x] 2.1 （設計：金鑰由 local.properties 提供；規格：News API key comes from local configuration）`app/build.gradle` 讀取 `local.properties` 的 `NEWS_API_KEY`（缺少為空字串，去除前後空白與一組包住值的引號，寫入前跳脫反斜線與雙引號），產生 `BuildConfig.NEWS_API_KEY` 並明確開啟 `buildFeatures { buildConfig true }`。驗證：`./gradlew assembleDebug` 通過；用 `NEWS_API_KEY= "abc" ` 與 `NEWS_API_KEY=abc` 各建置一次，產生的 `BuildConfig.java` 都是 `abc`；沒有該項時為空字串。驗證完要把 `local.properties` 的 `NEWS_API_KEY` 還原（任務 5.2 需要沒有金鑰的環境）。

## 3. 金鑰送出與記錄

- [x] 3.1 （設計：以攔截器送出 X-Api-Key 標頭、記錄時遮蔽金鑰）新增 `NewsApiKeyInterceptor` 與 `newsAwareClient`（預設金鑰為 `BuildConfig.NEWS_API_KEY`，先加金鑰攔截器、再加遮蔽 `X-Api-Key` 的記錄攔截器），兩個 Retrofit 實例都改用它；移除 `NewsAPI` 兩個函式的 `apiKey` 參數與註解中的網址金鑰，刪除 `Constant.kt` 的金鑰常數與 `NewsRepository` 不再使用的 import。驗證：`./gradlew testDebugUnitTest --tests "*NewsApiKeyInterceptorTest" --tests "*NewsApiRequestTest"` 通過，`./gradlew assembleDebug` 通過，且 `git grep` 在目前樹中找不到舊金鑰字串。

## 4. 金鑰為空的提示

- [x] 4.1 （設計：金鑰為空時的行為；規格：Missing key is reported without a request）`NewsViewModel` 與 `NewsViewModelFactory` 增加 `apiKey`（預設 `BuildConfig.NEWS_API_KEY`），`getNews` 在檢查網路前先以 `isBlank()` 檢查，為空白時回傳 `Resource.Error`，訊息為新字串資源 `news_api_key_missing`，不送請求。驗證：`./gradlew assembleDebug` 通過；模擬器以空金鑰建置時，新聞分頁與個股新聞都顯示該訊息、轉圈圈停止，logcat 沒有 `newsapi.org` 請求。

## 5. 文件與驗證

- [x] 5.1 `docs/cloud-setup.md` 第 3 節補充：`echo ... > local.properties` 會覆寫整個檔案，要使用新聞功能時需另外以 `>>` 附加 `NEWS_API_KEY=<金鑰>`，沒有金鑰時新聞分頁顯示設定提示。文件要說明金鑰只附加一次、由人手動或從環境機密提供，不要寫進共用的 setup script（以免進入 shell 歷史或腳本）。驗證：文件內容審閱，指令範例實際執行後 `local.properties` 同時保有 `sdk.dir` 與 `NEWS_API_KEY`。
- [x] 5.2 先在另一個乾淨的 worktree（基底分支）記錄 `./gradlew lintDebug` 的基準，再把 `local.properties` 的 `NEWS_API_KEY` 移除，在沒有金鑰的環境執行 `./gradlew testDebugUnitTest assembleDebug lintDebug`，lint 沒有比基準新增的問題。驗證：指令通過。
- [x] 5.3 在 `local.properties` 放入金鑰後建置並安裝到 Pixel 8 模擬器：新聞分頁與個股新聞載入文章，logcat 的 `newsapi.org` 請求標頭顯示 `X-Api-Key: ██`，且網址不含 `apiKey`。驗證：新聞載入與 logcat 對照都符合。
