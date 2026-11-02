# Android 對齊 iOS 版功能評估

日期：2026-11-02
參考：`~/Documents/ios-projects/Ios-App-MyTaiwanStock`（以原始碼 `MyTaiwanStock/Screens/` 為準，iOS 的 `openspec/specs/` 只涵蓋部分功能）
排除：iCloud／Firebase 雲端備份

## 功能對照

| 功能 | iOS | Android 現況 | 差距 | Android 對應做法 |
|---|---|---|---|---|
| 追蹤清單（多清單、新增／刪除股票） | 有 | 有 | 小 | 沿用 |
| 即時股價 | 有，上市＋上櫃 | 有，只查上市（寫死 `tse_`） | **中** | 依市場帶 `tse_`／`otc_` 前綴 |
| 股票名錄 | 官方 open data 建清單、快取＋內建備援、背景更新、精確比對代號 | 無內建名錄與背景更新 | **大** | assets 內建 JSON＋WorkManager 定期更新 |
| K 線圖 | 有，往左捲載入更多歷史 | 有，無載入更多 | 中 | MPAndroidChart `OnChartGestureListener` |
| 個股詳情總覽（持股、均價、損益） | 有 | 有，均價含手續費（`trade-fee-and-tax`） | 小 | 沿用 |
| 買賣紀錄（手動新增、備註） | 有，含手續費、稅、備註 | 有，含手續費、稅、備註（資料庫版本 7） | 小 | 沿用 |
| 手續費與交易稅 | 統一計算規則、每筆存費稅、設定折數與整股／零股最低手續費 | 有，`tradecost/FeeCalculator`＋手續費設定頁；不上傳 Firestore | 小 | 沿用 |
| 成本計算（含賣出、費用） | 有 `OverViewCalculator` | 有，`tradecost/HoldingsCalculator`（統計頁圓餅圖仍用 `CalculateAssetUseCase`，只算股數） | 小 | 沿用 |
| 股利（新增、明細） | 有 | 有 | 小 | 沿用 |
| 統計（圓餅圖、股利統計） | 有 | 有 | 小 | 沿用 |
| 截圖匯入交易（投資先生） | 裝置端 Vision OCR、解析、預覽、逐筆編輯、選清單 | 無 | **大** | ML Kit 中文文字辨識（裝置端）＋移植解析器 |
| 從分享選單匯入圖片 | Share Extension，待匯入暫存、過期刪除 | 無 | 大 | `ACTION_SEND(_MULTIPLE)` intent filter，直接進匯入流程（不需暫存區） |
| 即時報價常駐顯示 | Live Activity／Dynamic Island | 無 | 中 | 前景服務的進行中通知；Android 16 用 Live Updates |
| 桌面小工具 | WidgetKit，支援上櫃 | 有（RemoteViews），只支援上市 | 中 | 先補上櫃；之後可改 Glance |
| 新聞 | 有 | 有 | 小 | 沿用 |
| 聊天室 | Firebase，未讀數 | 有，無未讀數 | 小 | 視需要補未讀 |
| 登入 | Google、Apple | Google（舊版 `GoogleSignIn` API，已棄用） | 中 | 改 Credential Manager；Apple 登入不做 |
| 初次使用導覽 | 有 | 無 | 小 | ViewPager2 導覽頁 |
| 設定頁 | 帳號、手續費 | 帳號、手續費 | 小 | 沿用 |
| 本機資料匯入（登入後） | 有 | 無 | 依需求 | 與雲端同步有關，先確認是否屬於排除範圍 |
| 雙語（中／英） | 有 | 待確認 | 小 | `values-en` |

## 開工前要先處理的問題

1. **目前無法建置**：toolchain 要求 JDK 17，本機只有 8、21、23。`./gradlew assembleDebug` 失敗在 `compileDebugJavaWithJavac`。需安裝 JDK 17（`brew install openjdk@17`）或改 toolchain 為 21。
2. **建置設定過舊且不一致**：Kotlin plugin 1.6.21 配 KSP 1.9.0、`jcenter()`、舊版 Navigation／Material、Compose 依賴混雜。建議第一個 change 先升級並確認可建置。
3. **基底分支 `rx` 上有未提交的 WIP**（`FetchStockWorker`、log、註解掉的程式），需決定保留到分支或捨棄。
4. **沒有測試、沒有 CI**：只有範例測試。費稅計算、截圖解析這類純邏輯要從 iOS 的測試案例移植。
5. Package 名稱仍是 `com.example.mynewsapp`（applicationId 已是正式值），不影響上架，可不動。

## 建議的 change 順序

1. `build-modernization`：JDK／Kotlin／AGP 升級、移除 jcenter、加 GitHub Actions CI（build＋unit test）
2. `otc-stock-support` ＋ `stock-list-refresh`：名錄與上櫃報價（其他功能都依賴正確的代號與市場）
3. `trade-fee-and-tax`：Room migration、計算規則、設定頁、成本重算
4. `trade-screenshot-import` ＋ `share-intent-import`
5. `kline-load-more`、`ongoing-quote-notification`、widget 上櫃支援
6. `onboarding`、登入 API 更新、雙語

每個 change 依 `AGENTS.md` 走 Spectra 流程與計畫審查。iOS 沒寫進 spec 的功能（K 線載入更多、導覽、聊天未讀、成本計算細節）要在 proposal 裡從原始碼補寫需求。

## 關於「Claude Code 的雲端 API token」

不能拿來給 App 用。Claude Code 登入用的是訂閱帳號的 OAuth token，只能用於 Claude Code 本身；嵌進 APK 或給 App 的後端呼叫，都違反使用條款，而且 APK 可被反組譯，token 會外洩。

上面的功能都不需要 LLM：截圖辨識在 iOS 用的是裝置端 Vision，Android 對應 ML Kit，免費且不上傳。若之後真要在 App 裡用 Claude（例如解析辨識失敗的截圖），要另外申請 Anthropic API key，放在自己的後端（例如 Firebase Cloud Functions）代呼叫，不放在 App 裡。

如果指的是讓開發工作在雲端跑：Claude Code 可以開雲端 session，但前提是 repo 已推上 GitHub，且 Android 建置在雲端環境需要 JDK 17 與 Android SDK，目前還沒設定。
