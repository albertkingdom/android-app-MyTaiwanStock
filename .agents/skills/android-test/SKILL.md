---
name: android-test
description: 在 Android 專案寫或執行測試時使用：修 bug 前先寫重現測試、為新邏輯補單元測試、寫 Room／Compose／Espresso 的 instrumented test、把 spec 的範例表變成測試、或測試在 CI／模擬器上失敗要排查。涵蓋測試放哪一層、怎麼只跑一個測試、以及常見的坑。只用於 Android（Gradle）專案。
---

# Android 測試

寫測試或跑測試前讀這份。流程規則（先寫會失敗的測試再修、code review）以 `AGENTS.md` 為準；這裡只補 Android 的做法與坑。

**專案事實一律讀 `AGENTS.md`**：「專案事實」表的測試指令、建置指令，以及「平台專屬規則」的 JDK 版本與模擬器要求。不要自己猜指令或 JDK 版本。

## 1. 選測試層級

能往下放就往下放：越下層越快、越穩定，也能在 CI 跑。

| 要測的東西 | 放哪 | 工具 |
|---|---|---|
| 純邏輯：計算、格式化、解析、驗證、狀態夾限 | `app/src/test`（JVM） | JUnit；不依賴 Android framework |
| 只用到 `Locale`、字串、資料類別的邏輯 | `app/src/test` | 同上 |
| Room DAO 的查詢、排序、交易 | `app/src/androidTest` | `Room.inMemoryDatabaseBuilder` |
| Room migration | `app/src/androidTest` | `MigrationTestHelper` |
| Compose 畫面的互動 | `app/src/androidTest` | `createAndroidComposeRule<Activity>()`，需要 `androidx.compose.ui:ui-test-junit4`（版本對齊專案的 compose ui）與 `debugImplementation ui-test-manifest` |
| View 畫面、Snackbar、Toast 以外的 View | `app/src/androidTest` | Espresso |
| Activity／Fragment 生命週期、intent | `app/src/androidTest` | `InstrumentationRegistry`、`ActivityScenario` |

**先抽純邏輯再測。** UI 或 ViewModel 裡的判斷（價格怎麼顯示、位置怎麼夾限、名稱怎麼驗證）先抽成不依賴 Android 的 `object` 或函式，用 JVM 測試涵蓋所有情境；instrumented test 只驗證接線與互動。

## 2. 把 spec 的範例變成測試

spec 裡的 `##### Example` 表格是約定好的規格：

- 每一列寫成一個測試（或一個參數化案例），值照抄，不要自己換「更好的」值。
- 測試名稱描述該列的情境，例如 `` `a saved position past the end selects the last tab` ``。
- 表格沒涵蓋、但你認為重要的邊界，先回報或補進 spec，不要只寫在測試裡。

## 3. 先失敗，再修

1. 寫測試，執行，**確認因為正確的原因失敗**：斷言不成立或重現了 crash。編譯錯誤、打錯名稱、找不到元件都不算，因為測試還沒真正跑到要驗證的行為。測試需要的新類別或函式，先補一個「修正前的行為」版本讓測試能編譯，再看它因斷言失敗。
2. 再改程式。
3. 重跑該測試與同檔其他測試，全部通過才算完成。

無法用自動化測試重現（純外觀、系統行為），在回報中寫明原因與手動驗證步驟。

## 4. 確認測試本身是對的

測試通過只代表「程式符合測試的斷言」，不代表測試有測到該測的東西。寫完、標為完成之前逐項確認：

1. **先看到它因正確的原因失敗。** 修正前執行一次，失敗訊息要是斷言不成立或重現了 crash，不是編譯錯、打錯名稱、找不到元件。
2. **故意把程式改壞，確認它會變紅。** 修好之後，把關鍵的那行改回去或改壞（`>` 改 `>=`、刪掉一個條件、回傳固定值），重跑測試，必須失敗；確認後還原。仍然通過就代表測試沒涵蓋那個行為，補斷言或補案例。至少對 bug 修正的那一行做一次。
3. **預期值不能來自受測的程式。** 預期值照抄 spec 範例表，或用手算（例如 15 ÷ 1070 = 1.40%）。不要先跑程式、把輸出貼進斷言，那只是把現況（含 bug）固定下來。
4. **斷言要具體，檢查使用者看得到的結果。** 只斷言「沒有 crash」「不是 null」「Activity 沒有結束」幾乎抓不到東西。斷言實際的值、畫面上的文字、資料庫的內容，以及操作後其他東西沒被影響（例如刪掉一列後下一列仍在）。
5. **單獨跑、整組跑、重複跑。** 單獨通過但整組失敗，代表測試之間共用狀態；用 `./gradlew testDebugUnitTest --tests '<類別>' --rerun` 連跑幾次（`--rerun-tasks` 會連編譯一起重跑，很慢），偶爾失敗的就是 flaky（通常是時間或動畫），要改成等待條件而不是放寬斷言。
6. **把測試也交給 code review。** 請 reviewer 檢查：斷言是否足夠、程式錯了測試是否仍會通過、清理是否可靠。

回報時寫出第 1、2 點的結果：修正前的失敗訊息，以及改壞後確實失敗的是哪個測試。

需要系統性檢查時，可用 PIT（pitest）自動產生小幅改壞的版本，列出沒有任何測試抓到的改動。Android 的 JVM 測試用 `pl.droidsonroids.pitest` Gradle plugin；它很慢，只對少數核心邏輯類別跑，不放進 CI。

## 5. 執行測試

指令前綴用 `AGENTS.md` 指定的 JDK（例如 `JAVA_HOME=... ./gradlew`）。

```bash
# 全部 JVM 測試
./gradlew testDebugUnitTest

# 只跑一個 JVM 測試類別（支援萬用字元）
./gradlew testDebugUnitTest --tests '*PriceFormatterTest'

# 全部 instrumented test（需要已開機的模擬器或手機）
./gradlew connectedDebugAndroidTest

# 只跑一個 instrumented test 類別（用完整類別名稱）
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.app.db.SomeDaoTest
```

結果在：

- JVM：`app/build/test-results/testDebugUnitTest/TEST-*.xml`
- instrumented：`app/build/outputs/androidTest-results/connected/debug/*.xml`

`-q` 模式下 Gradle 不印通過的測試，看 XML 的 `tests`、`failures`、`errors` 確認真的跑了、跑了幾個。

## 6. 不讓 instrumented test 拖慢開發

instrumented test 要模擬器、要安裝 APK，一次動輒數分鐘。讓它只在值得的時候跑：

- **把邏輯抽成純 Kotlin，用 JVM 測試涵蓋。** 判斷、計算、格式化、狀態轉換都不需要裝置；instrumented test 只留給「接線」與真正依賴 Android 的行為。這是最有效的一招。
- **只跑相關的類別。** 用 `-Pandroid.testInstrumentationRunnerArguments.class=<完整類別名稱>`，不要每次跑全部。
- **只在改動碰到該碰的地方時跑**：資料庫（DAO、migration、Entity）、Compose 畫面、Activity／Fragment 生命週期與 intent。專案可以在 `AGENTS.md` 列一張「改到哪裡 → 跑哪個測試」的對照表。
- **Robolectric 是選項，不是預設。** 它在 JVM 上模擬 Android framework，可以跑部分 View、Activity、資源相關的測試，速度接近單元測試。代價是多一個依賴與設定、它是模擬不是真機（部分行為、Compose 動畫、廠商差異測不到），版本也要跟著 SDK 升級。
- **交給 CI 或排程跑全部。** 在 CI 加一個用模擬器跑 `connectedDebugAndroidTest` 的 job（例如 `reactivecircus/android-emulator-runner`），或每晚排程跑一次；本機只跑相關類別。
- **讓模擬器保持開著。** 冷開機比跑測試還久；開發期間不要關。
- **instrumented test 少而準。** 優先寫給曾經壞過、或 JVM 測不到的關鍵行為（migration、crash 重現、生命週期洩漏），不要用它測一般邏輯。

## 7. 常見的坑

**`connectedDebugAndroidTest` 會清掉 App 資料。** 它安裝測試用 APK，跑完會解除安裝 App，模擬器上手動建立的清單、資料都會消失。要在模擬器上手動驗證的資料，跑完 instrumented test 後重建；不要在使用者的手機上跑 instrumented test，除非使用者同意資料被清掉。

**用到 App 真實資料庫的測試要自己清理。**
- 測試資料用每次執行都不同的名稱（例如加上 `System.currentTimeMillis()`），避免上次失敗留下的資料讓這次找到兩筆。
- 在 `@After` 直接透過 repository／DAO 刪除，用 `runBlocking` 等它完成；不要經由 ViewModel 的 `viewModelScope`，Activity 關閉時協程會被取消。
- 只測 DAO 時用 in-memory 資料庫，就沒有清理問題。

**數字格式跟著裝置語系。** `String.format("%.2f", x)` 在德文等語系會用逗號當小數點。顯示用的數字指定 `Locale.US`（或專案規定的語系），並加一個測試：暫時 `Locale.setDefault(Locale.GERMANY)`，在 `try/finally` 裡還原原本的預設值，否則後面的測試都在德文語系下跑。

**Compose 測試不會等 View 的動畫。** Snackbar、Toast 等 View 是動畫進場，`composeRule.waitForIdle()` 不等它們。用 `composeRule.waitUntil(timeoutMillis) { runCatching { onView(...).check(matches(isDisplayed())) }.isSuccess }` 等到出現。

**Compose 列表要有 key。** `LazyColumn` 的 `items(list)` 沒給 `key`，刪除一列後，下一列會沿用被刪那列的 `remember` 狀態（例如滑動刪除的狀態）。測刪除時順便斷言「下一列仍在畫面上」，能抓到這個問題。

**`ExampleInstrumentedTest` 可能一直失敗。** Android Studio 範本產生的 `useAppContext()` 斷言套件名稱等於 namespace；如果 `applicationId` 與 namespace 不同，它永遠失敗。跑全部 instrumented test 時先確認這個失敗是不是早就存在（在基底分支上也失敗），不要誤以為是自己造成的；要修就把預期值改成 `applicationId`。

**測試間共用狀態。** `Locale`、`SharedPreferences`、單例、資料庫在同一個測試程序裡共用。改了就在 `@After` 或 `finally` 還原。

**模擬器很慢時的時間值。** 冷啟動可能超過 10 秒。`waitUntil` 給足 timeout（10 秒起跳），不要用固定的 `Thread.sleep`。

## 8. 回報

完成時回報：

- 新增或修改的測試檔與測試名稱。
- 修改前的失敗訊息（證明有重現）、故意改壞時失敗的測試，以及修改後的通過結果與測試數量。
- 跑了哪些指令；instrumented test 用哪台裝置或模擬器（API 等級）。
- 既有、與本次無關的失敗（例如 `ExampleInstrumentedTest`），註明它在基底分支上也失敗。
