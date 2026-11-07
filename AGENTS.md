<!-- SPECTRA:START v1.3.0 -->

# Spectra Instructions

This project uses Spectra for Spec-Driven Development(SDD). Specs live in `openspec/specs/`, change proposals in `openspec/changes/`.

## Use `$spectra-*` skills when:

- An explicitly requested Spectra decision needs structure → `$spectra-discuss`
- User explicitly requests a Spectra change proposal → `$spectra-propose`
- Continue tasks for an identified change → `$spectra-apply`
- Update requirements or plans for an identified change → `$spectra-ingest`
- Check implementation matches artifacts → `$spectra-verify`
- Review implementation quality and logic issues → `$spectra-review`
- Analyze artifact consistency before coding → `$spectra-analyze`
- Audit security sharp edges → `$spectra-audit`
- Check stale changes before resuming → `$spectra-drift`
- Debug a concrete bug systematically → `$spectra-debug`
- Implementation is done → `$spectra-archive`
- Commit only files related to a specific change → `$spectra-commit`

Explicit skill invocation takes precedence. Apply existing authorization within its unchanged scope.

## Workflow

discuss? → propose → apply ⇄ ingest → verify / review → archive

- `discuss` is optional — skip if requirements are clear
- Requirements change mid-work? Plan mode (`/plan`) → `ingest` → resume `apply`

## Parked Changes

Changes can be parked（暫存）— temporarily moved out of `openspec/changes/`. Parked changes won't appear in `spectra list` but can be found with `spectra list --parked`. To restore: `spectra unpark <name>`. The `$spectra-apply` and `$spectra-ingest` skills disclose parking and restore when the named operation is already explicitly requested; respect a known refusal, otherwise ask for missing authorization.

<!-- SPECTRA:END -->

# 專案開發規範

本檔是 Claude Code 與 Codex 共用的規範。`spectra init`／`spectra update` 會在本檔寫入並重寫一段 Spectra 區塊，自己的規則一律寫在區塊之外。注意：本檔的文字裡不要寫出 Spectra 區塊的起迄標記，Spectra 是用字串比對找區塊，會把說明文字當成區塊切掉。

## 專案事實

skill 與下列規則都以這張表為準。新專案的第一件事是填完它。

| 項目 | 值 |
|---|---|
| 基底分支 | `rx` |
| 發版分支 | 無 |
| 建置指令 | `./gradlew assembleDebug`（需 JDK 17，見「平台專屬規則」） |
| 測試指令 | `./gradlew testDebugUnitTest` |
| CI 必過檢查 | `build-and-test`（GitHub Actions，`.github/workflows/ci.yml`） |
| 版號來源 | `app/build.gradle` 的 `versionName`（並同步遞增 `versionCode`） |

## Spectra 計畫審查

Spectra 規劃（propose，或之後修改 proposal、design、tasks、delta spec）完成後，**開始實作（apply）之前**，必須由獨立的審查者審查計畫，不等使用者開口要求：

1. 審查者是另開的唯讀 agent／subagent，用最強的可用模型，不修改任何檔案。交給它 change 名稱、artifact 的路徑與相關程式碼位置，要求檢查：計畫與現有程式碼是否一致、遺漏的邊界與失敗情境、任務的順序與相依、每個任務的驗證方式能否實際執行、規格的 SHALL 與情境是否可測。
2. 審查結果依嚴重度記錄成 `docs/reviews/{YYYY-MM-DD}-{change 名稱}-plan.md`，記錄方式與 Code Review 相同。
3. 高、中嚴重度的發現要修正 artifact（需要使用者決定的先問使用者），修完再審，直到沒有高、中嚴重度；低嚴重度由使用者決定修或不修。
4. 計畫審查沒完成前，不得開始 apply，也不得對計畫宣稱「可以實作」。

## 分支規則

- 新需求在 `feature/{需求名稱}` 分支上實作。
- Bug 修正在 `fix/{問題名稱}` 分支上實作。
- 雜務（文件、設定、歸檔）在 `chore/{名稱}` 分支上進行。
- 專案若有版號規則，寫在「平台專屬規則」。

## PR Merge 規則

- PR 一律用 **Merge commit**（`gh pr merge <N> --merge`），不用 squash 或 rebase，保留分支上的 commit 歷史。
- 合併前確認「CI 必過檢查」通過。
- feature／fix／chore 分支合併進基底分支；發版時再把基底分支 merge 進發版分支（同樣用 merge commit）。
- 上一個 PR 合併後，才從最新的基底分支切下一個分支，避免把未合併的 commit 帶進新分支。

## Bug Fix Workflow

修 bug（含 crash report、tester 回報）必須依序：

寫測試前先讀 `android-test` skill（測試放哪一層、怎麼只跑一個測試、常見的坑）。

1. 先寫一個能重現問題的測試，並執行確認它在修正前會失敗或 crash，且失敗原因是該 bug：斷言不成立或重現了 crash。編譯錯誤、打錯名稱、找不到元件都不算；測試需要的新類別或函式，先以「修正前的行為」補上讓測試能編譯，再看它失敗
2. 再修改程式碼
3. 重新執行該測試（以及同檔案的其他測試）確認通過
4. 把修正的關鍵改回去或改壞，確認該測試會失敗，再還原；仍然通過代表測試沒測到 bug，要補強測試

回報與修正的 commit 內文都要記錄測試證據：修正前的失敗訊息、第 4 步改壞時失敗的測試、修正後通過的結果與測試數量。缺少任何一項，不算修復完成。

例外：無法寫自動化測試重現時（例如純 UI、系統行為），改為記錄原因與手動驗證步驟，不必提供上面三項；純排版（margin、間距）調整不必先寫失敗測試，記錄「純排版，未寫測試」即可。

## Code Review Workflow

### 執行時機

實作完成、使用者確認功能沒問題後，**在 commit 之前**必須先對尚未 commit 的改動執行 code review，不等使用者開口要求：

1. 流程順序：實作 → 手動／自動測試 → code review → 修復 review 發現 → commit → push → 開 PR
2. 修復之後必須再次 code review，重複「review → 修復」直到沒有嚴重度中（含）以上的發現為止；低嚴重度的項目由使用者決定修或不修（不修需註明原因）
3. 純文件、純設定（不影響行為）的改動可以略過，但需在回報中說明略過
4. 尚未執行 code review 時，不得 commit，也不得提議 push 或開 PR
5. 若發現 commit 時漏審，立即補審整個分支相對基底分支的差異，修復後才能 push
6. review 範圍包含測試：斷言是否具體、程式寫錯時測試是否仍會通過、測試資料是否有清理；測試不足算一項發現

### 記錄方式

Code review（含 subagent review）的結果必須記錄成 Markdown：

1. 存放於 `docs/reviews/{YYYY-MM-DD}-{主題}.md`，內容包含背景、依嚴重度分類的發現（附檔案位置與失敗情境）、已確認無問題的項目
2. 文件末尾將發現拆成「修復 Todo」checklist（`- [ ]`），每項對應一個可獨立驗證的修正
3. 逐項修復（仍須遵守 Bug Fix Workflow），每修完一項立即在文件中打勾（`- [x]`）
4. 決定不修的項目也要打勾，並註明原因
5. Review 文件是工作中的追蹤紀錄，不加入版控；所有項目都打勾後即刪除該文件

## Commit Message Convention

- Commit 時使用 `git-commit` skill 撰寫訊息：Conventional Commits、英文、不提任何 AI 工具、不加署名。
- 不得提及 Spectra 的 task／change 名稱等內部追蹤資訊（例如 `Change: ...`、`Tasks: N/M complete`）。

## 收尾

使用者說「收尾」時，依 `wrapup` skill 執行：只在本機操作，不 push、不開 PR、不合併。

## 平台專屬規則

### Android

#### 建置環境

- Gradle toolchain 要求 JDK 17（`compileOptions` 為 `VERSION_17`）。本機沒有 JDK 17 時建置會失敗在 `compileDebugJavaWithJavac`，先安裝 JDK 17（例如 `brew install openjdk@17`）。
- 執行 Gradle 時以 JDK 17 啟動，與 CI（`build-and-test`，temurin 17）一致。不要用 Android Studio 內建的 JBR：新版內建的是 JDK 21，而本專案的 Kotlin 1.9.0 要到 1.9.20 才正式支援 JDK 21。

  ```bash
  JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug
  ```

- 在 Android Studio 裡建置時，把 Settings › Build, Execution, Deployment › Build Tools › Gradle 的 Gradle JDK 設為 JDK 17。
- 雲端（Linux）環境的設定見 `docs/cloud-setup.md`。

- `local.properties`、`app/google-services.json` 以外的金鑰、keystore（`*.jks`、`*.keystore`）不進版控。

#### 分支名稱帶版號

覆蓋上方「分支規則」的命名，改成：

- 新需求：`feature/{版號}/{需求名稱}`，例如 `feature/1.15/trade-fee-and-tax`
- Bug 修正：`fix/{版號}/{問題名稱}`

版號採下一個預計發布的 App 版本，寫法同 `versionName`（`1.15`）。

- 新需求：下一個尚未發布的版本。上一版上架後改用下一版，並把 `versionName` 改為該版、`versionCode` 加 1
- 修正尚未上架的版本：沿用該版號，只遞增 `versionCode`
- 修正已上架版本的緊急 bug：用修訂版號，例如 `fix/1.14.1/...`，`versionName` 改為 `1.14.1`

#### 與 iOS 版對齊

- 功能對照的參考專案：`~/Documents/ios-projects/Ios-App-MyTaiwanStock`。它的 `openspec/specs/` 只涵蓋部分功能，對照時以 iOS 原始碼（`MyTaiwanStock/Screens/`）為準。
- iOS 專屬功能改用 Android 對應機制，在 proposal 寫明對應關係：
  - Live Activity／Dynamic Island → 前景服務的進行中通知（Android 16 起可用 Live Updates）
  - Share Extension → `ACTION_SEND`／`ACTION_SEND_MULTIPLE` 的 intent filter
  - Vision 文字辨識 → ML Kit Text Recognition（中文模型，裝置端，不上傳）
  - WidgetKit → App Widget（優先 Glance）
  - BGTaskScheduler → WorkManager
  - Sign in with Apple → 不實作，只保留 Google 登入
- 雲端備份（iCloud／Firebase 備份）暫不納入。

#### 測試

- 寫或執行測試前先讀 `android-test` skill。
- 純邏輯（費稅計算、截圖解析、清單解析）寫在 `app/src/test`（JVM unit test），不依賴 Android framework。
- 需要裝置的測試放 `app/src/androidTest`，執行 `./gradlew connectedDebugAndroidTest`（需模擬器）。
- 改動碰到資料庫（DAO、migration）、Compose 畫面或 Activity／Fragment 生命週期時，code review 之前要在模擬器上跑對應的 instrumented test，只跑相關的類別：

  ```bash
  ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<完整類別名稱>
  ```

  | 改到的地方 | 要跑的測試（`com.example.mynewsapp` 底下） |
  |---|---|
  | `db/`（DAO、migration、Entity） | `db.FollowingListOrderTest`、`db.StockDatabaseMigrationTest` |
  | Compose 畫面（例如 `EditFollowingListFragment`） | `ui.list.EditFollowingListDeleteTest` |
  | Activity／Fragment 生命週期、觀察者、分享 intent 冷啟動 | `ui.list.ListFragmentObserverLeakTest` |
  | 統計頁（`ui/statistics/`）的 Fragment、`StatisticViewModel`、圓餅圖 | `ui.statistics.StatisticFragmentLeakTest`、`ui.statistics.PieChartRenderingLeakTest`、`ui.statistics.StatisticViewModelPriceTest` |

  原因：這些行為無法在 JVM 上測，而 CI（`build-and-test`）只跑 `testDebugUnitTest`，不跑 instrumented test，沒人手動跑就不會被驗證。
- 沒有對應測試，或無法執行（沒有模擬器、在沒有裝置的環境）時，回報中如實寫「未執行」或「無對應測試」與原因，不要宣稱通過。改到的 Compose 畫面沒有測試時，評估是否補一個，優先補曾經壞過的行為。
- `connectedDebugAndroidTest` 跑完會解除安裝 App，模擬器上的 App 資料會被清掉；不要在自己的手機上跑，除非接受資料被清掉。
- `ExampleInstrumentedTest`（範本產生）在 `rx` 上就已失敗：它預期套件名稱是 `com.example.mynewsapp`，但 applicationId 是 `com.albertkingdom.mystockapp`。跑全部 instrumented test 時這個失敗與改動無關。
