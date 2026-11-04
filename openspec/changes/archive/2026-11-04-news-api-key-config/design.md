## Context

- `Constant.kt` 以字串常數保存 NewsAPI 金鑰；`NewsAPI` 的 `searchForNews` 與 `getHeadlines` 以 `apiKey` 查詢參數送出它。
- `RetrofitInstance` 與 `RetrofitInstanceForGeneralUse` 各自建立 OkHttp 用戶端，並加上 BODY 等級的 `HttpLoggingInterceptor`，完整網址（含金鑰）會被印進 logcat。`RetrofitInstanceForGeneralUse` 同時用於證交所的股價與 K 線 API。
- `local.properties` 已在 `.gitignore`，目前只放 `sdk.dir`。建置使用 AGP 8.5.1；`gradle.properties` 以 `android.defaults.buildfeatures.buildconfig=true` 全域開啟 `BuildConfig`（程式已在用 `BuildConfig.DEBUG`），但這個全域屬性在新版 AGP 會移除，所以仍在 `app/build.gradle` 明確開啟。
- `docs/cloud-setup.md` 第 3 節以 `echo "sdk.dir=..." > local.properties` 產生雲端環境的檔案，這會覆寫整個檔案。
- CI（`build-and-test`）只建置並跑 JVM 單元測試，不呼叫 NewsAPI，沒有 `local.properties`。

## Goals / Non-Goals

**Goals:**

- 金鑰不再出現在版控的原始碼。
- 金鑰不會出現在請求網址，也不會出現在 logcat。
- 沒有設定金鑰的環境（CI、新環境）仍能建置、測試通過，新聞畫面給出明確提示。

**Non-Goals:**

- 不替使用者換金鑰，也不改寫 git 歷史；舊金鑰需要使用者到 NewsAPI 後台作廢。
- 不設定 CI 的 secret。
- 不移除沒有呼叫者的 `getHeadlines`。
- 不改變證交所與 K 線 API 的請求與記錄。

## Decisions

### 金鑰由 local.properties 提供

`app/build.gradle` 在設定階段讀取專案根目錄的 `local.properties`，取 `NEWS_API_KEY`，沒有或檔案不存在時為空字串；值先去除前後空白，並去掉一組包住整個值的單引號或雙引號，再把反斜線與雙引號跳脫，以 `buildConfigField "String", "NEWS_API_KEY", ...` 產生 `BuildConfig.NEWS_API_KEY`，並明確開啟 `buildFeatures { buildConfig true }`。「空」一律指去除空白後為空，程式各處都用 `isBlank()` 判斷。

理由：`local.properties` 是每台機器各自的檔案，已被忽略，不需要新機制；空字串預設讓 CI 不必設定任何 secret。

### 以攔截器送出 X-Api-Key 標頭

新增 `NewsApiKeyInterceptor(apiKey, host = "newsapi.org")`（OkHttp `Interceptor`）：金鑰為空白，或請求網址的主機不等於 `host` 時原樣放行；否則加上 `X-Api-Key` 標頭。`NewsAPI` 的 `searchForNews` 與 `getHeadlines` 移除 `apiKey` 參數，網址不再帶金鑰。

兩個 Retrofit 實例共用同一個用戶端工廠 `newsAwareClient(apiKey, host, logger)`（與攔截器放在同一檔案）：它建立 `OkHttpClient`，先加入金鑰攔截器，再加入 BODY 等級、遮蔽 `X-Api-Key` 的 `HttpLoggingInterceptor`。工廠的預設值是 `BuildConfig.NEWS_API_KEY`、`newsapi.org` 與 Android 預設記錄；測試可以傳入自己的金鑰、主機與收集記錄的 logger。

理由：NewsAPI 支援 `X-Api-Key` 標頭；標頭可以被記錄攔截器遮蔽，查詢參數則會在網址中。依主機過濾是因為 `RetrofitInstanceForGeneralUse` 也會對證交所主機發請求，不能把金鑰送給它們。共用工廠讓「兩個實例的接線與攔截器順序正確」可以被單元測試驗證，而不是只靠手動檢查。

### 記錄時遮蔽金鑰

`newsAwareClient` 內的 `HttpLoggingInterceptor` 呼叫 `redactHeader("X-Api-Key")`。因為金鑰攔截器先於記錄攔截器加入（應用層攔截器依加入順序執行），記錄攔截器看到的請求已有該標頭，會把值印成遮蔽字樣（okhttp 5.0.0-alpha.2 的遮蔽字串為「██」）。

### 金鑰為空時的行為

`NewsViewModel.getNews` 在檢查網路之前先檢查金鑰：`NewsViewModel` 與 `NewsViewModelFactory` 增加參數 `apiKey: String = BuildConfig.NEWS_API_KEY`，為空白時不送請求，直接回傳 `Resource.Error`，訊息為字串資源 `news_api_key_missing`（「尚未設定新聞 API 金鑰，請在 local.properties 加入 NEWS_API_KEY」）。新聞分頁與個股新聞共用同一個 ViewModel，所以兩者行為一致。

## Implementation Contract

**Behavior**

- `local.properties` 設有 `NEWS_API_KEY=<值>` 時，新聞分頁與個股新聞照常載入。
- 沒有設定時，兩個新聞畫面都顯示「尚未設定新聞 API 金鑰…」的 Snackbar，轉圈圈停止，沒有任何請求送往 `newsapi.org`。
- 新聞請求網址為 `https://newsapi.org/v2/everything?...`，不含 `apiKey`；金鑰在 `X-Api-Key` 標頭。
- logcat 中該請求的標頭行顯示 `X-Api-Key: ██`，不出現金鑰。

**Interface / data shape**

- `BuildConfig.NEWS_API_KEY: String`（空字串表示未設定）。
- `NewsApiKeyInterceptor(apiKey: String, host: String = "newsapi.org")` 與 `newsAwareClient(apiKey: String = BuildConfig.NEWS_API_KEY, host: String = "newsapi.org", logger: HttpLoggingInterceptor.Logger = DEFAULT): OkHttpClient`。
- `NewsViewModel(newsRepository, application, query, apiKey)` 與對應的 `NewsViewModelFactory` 參數，`apiKey` 預設為 `BuildConfig.NEWS_API_KEY`。
- 標頭名稱 `X-Api-Key`。
- `NewsAPI.searchForNews(q, qInTitle, language, sortBy, page)` 與 `getHeadlines(urlString, country, category, page)`，皆無 `apiKey` 參數。
- 字串資源 `news_api_key_missing`。

**Failure modes**

- 金鑰為空白：不送請求，顯示提示（不是 401 錯誤）。
- 金鑰存在但無效：NewsAPI 回 401，沿用既有的錯誤訊息處理，顯示 API 回傳的 `message`。
- 其他主機（證交所）：不加標頭，行為不變。
- 金鑰含有標頭無法攜帶的字元（例如貼上時混入的全形引號）：攔截器丟出 `IOException`，由既有的錯誤處理顯示訊息，不會崩潰。

**Acceptance criteria**

- `NewsApiKeyInterceptorTest`（MockWebServer）驗證：主機相符且金鑰非空時請求帶 `X-Api-Key`；金鑰為空白時沒有該標頭；主機不符時沒有該標頭；以回傳固定回應的終端攔截器驗證完整網址 `https://newsapi.org/...` 也會帶標頭。
- 同一測試檔以 `newsAwareClient`（傳入收集記錄行的 logger）驗證真實接線：記錄中含 `X-Api-Key: ██`，且不含金鑰字串。
- `NewsApiRequestTest` 驗證請求網址的查詢參數名稱不含 `apiKey`。
- `git grep` 在目前樹中找不到舊金鑰字串。
- 產生的 `BuildConfig.java` 內，`local.properties` 的 `NEWS_API_KEY= "abc" ` 與 `NEWS_API_KEY=abc` 得到相同的值（人工檢查產生的檔案）。
- `./gradlew testDebugUnitTest assembleDebug lintDebug` 在沒有 `local.properties` 金鑰的環境通過，lint 沒有比基準新增的問題。
- 模擬器手動驗證：設有金鑰時新聞分頁載入文章，logcat 的請求標頭顯示遮蔽值；移除金鑰後重建，新聞分頁顯示提示且 logcat 沒有 `newsapi.org` 請求（金鑰為空的 ViewModel 行為依賴 `AndroidViewModel`，以手動驗證為主）。

**Scope boundaries**

- In scope：金鑰來源、標頭送出、記錄遮蔽、金鑰為空的提示、對應測試、`docs/cloud-setup.md` 的說明。
- Out of scope：換金鑰、改寫 git 歷史、CI secret、移除 `getHeadlines`、其他 API 的記錄。

## Risks / Trade-offs

- 舊金鑰仍在 git 歷史中，直到使用者作廢它都等同外洩；這個變更只保證之後不再外洩。
- 沒有金鑰的新環境新聞功能不能用，要靠提示文字讓開發者知道怎麼設定；`docs/cloud-setup.md` 會補充雲端環境的設定方式。
- 金鑰會被編進 APK（`BuildConfig`），任何拿到 APK 的人仍可取出；這個方法只防止它進版控與 logcat，不是保密機制。若要完全保密，需要由自己的後端代為呼叫 NewsAPI，不在這次範圍。
- 其他請求（證交所）仍以 BODY 等級記錄，這是既有行為，這個變更不調整。
