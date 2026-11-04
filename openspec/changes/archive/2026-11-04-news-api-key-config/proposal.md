## Problem

NewsAPI 金鑰以字串常數寫在 `Constant.kt`，已進版控；每個新聞請求都把它放在 `apiKey` 查詢參數，而 `HttpLoggingInterceptor` 以 BODY 等級記錄完整網址，所以金鑰也會被印進 logcat。任何能看到 repository、bug report 或測試機 logcat 的人都能取得金鑰。

## Root Cause

金鑰被當作原始碼的一部分管理：沒有每台機器各自的設定來源，請求又把它放在會被記錄的網址裡。專案已有 `local.properties`（不進版控）可放機器專屬設定，但建置沒有讀取新聞金鑰。

## Proposed Solution

- 建置從 `local.properties` 的 `NEWS_API_KEY` 讀取金鑰，沒有設定時為空字串，並以 `BuildConfig.NEWS_API_KEY` 提供給程式。
- 移除 `Constant.kt` 的金鑰常數，並移除 `NewsAPI` 的 `apiKey` 查詢參數；金鑰改由一個 OkHttp 攔截器放進 `X-Api-Key` 標頭（NewsAPI 支援），只對 `newsapi.org` 主機、且金鑰不為空時加上。
- 兩個 Retrofit 實例（`RetrofitInstance`、`RetrofitInstanceForGeneralUse`）都加上這個攔截器；後者同時用於證交所主機，所以必須依主機過濾。
- 兩個 `HttpLoggingInterceptor` 遮蔽 `X-Api-Key` 標頭，記錄中不出現金鑰。
- 金鑰為空時，新聞畫面不送請求，顯示明確的提示訊息。

## Non-Goals (optional)

- 不替使用者換金鑰：舊金鑰已在 git 歷史中，需要使用者到 NewsAPI 後台作廢並重新申請，再寫入自己的 `local.properties`。
- 不改寫 git 歷史。
- 不設定 CI 的 secret：CI 只建置與跑 JVM 單元測試，不呼叫 NewsAPI，空金鑰即可建置與測試通過。
- 不移除目前沒有呼叫者的 `getHeadlines`。
- 不改變其他 API（證交所、K 線）的請求與記錄行為。

## Success Criteria

- 專案中不再有 NewsAPI 金鑰的字串常數；`git grep` 搜尋舊金鑰在目前樹中沒有結果。
- 新聞請求的網址不含 `apiKey`，改以 `X-Api-Key` 標頭送出；對 `newsapi.org` 以外的主機不加這個標頭。
- 以空金鑰建置時，新聞畫面顯示提示且不送請求；`local.properties` 設有金鑰時，新聞照常載入。
- logcat 的請求記錄中，`X-Api-Key` 標頭顯示為遮蔽值。
- `./gradlew testDebugUnitTest assembleDebug lintDebug` 在沒有 `NEWS_API_KEY` 的環境（如 CI）通過。

## Impact

- Affected code:
  - New: `app/src/main/java/com/example/mynewsapp/api/NewsApiKeyInterceptor.kt`
  - New: `app/src/test/java/com/example/mynewsapp/api/NewsApiKeyInterceptorTest.kt`
  - Modified: `app/build.gradle`
  - Modified: `docs/cloud-setup.md`
  - Modified: `app/src/main/java/com/example/mynewsapp/util/Constant.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/api/NewsAPI.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/api/RetrofitInstance.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/api/RetrofitInstanceForGeneralUse.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/repository/NewsRepository.kt`
  - Modified: `app/src/main/java/com/example/mynewsapp/ui/news/NewsViewModel.kt`
  - Modified: `app/src/main/res/values/strings.xml`
  - Modified: `app/src/test/java/com/example/mynewsapp/api/NewsApiRequestTest.kt`
