---
name: android-test
description: 在 Android 專案寫或執行測試時使用：修 bug 前先寫重現測試、為新邏輯補單元測試、寫 Room／Compose／Espresso 的 instrumented test、把 spec 的範例表變成測試、測試在 CI／模擬器上失敗要排查、或改了畫面與生命週期後要在模擬器上手動驗證。涵蓋測試放哪一層、怎麼只跑一個測試、在模擬器上手動驗證畫面與生命週期，以及常見的坑。只用於 Android（Gradle）專案。
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

# 只跑一個 package 底下所有的 instrumented test
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.example.app.db
```

結果在：

- JVM：`app/build/test-results/testDebugUnitTest/TEST-*.xml`
- instrumented：`app/build/outputs/androidTest-results/connected/debug/*.xml`

`-q` 模式下 Gradle 不印通過的測試，看 XML 的 `tests`、`failures`、`errors` 確認真的跑了、跑了幾個。

## 6. 不讓 instrumented test 拖慢開發

instrumented test 要模擬器、要安裝 APK，一次動輒數分鐘。讓它只在值得的時候跑：

- **把邏輯抽成純 Kotlin，用 JVM 測試涵蓋。** 判斷、計算、格式化、狀態轉換都不需要裝置；instrumented test 只留給「接線」與真正依賴 Android 的行為。這是最有效的一招。
- **只跑相關的 package 或類別。** 測試放在跟原始碼對應的 package（改 `ui/statistics/` 就跑 `ui.statistics`），用 `-Pandroid.testInstrumentationRunnerArguments.package=<package>`；只想跑一個類別時用 `.class=<完整類別名稱>`。不要每次跑全部。新增測試放進同一個 package，就不用另外登記。
- **只在改動碰到該碰的地方時跑**：資料庫（DAO、migration、Entity）、Compose 畫面、Activity／Fragment 生命週期與 intent。`AGENTS.md` 只放一句提醒，不列對照表，要跑哪個 package 照上一點對應原始碼的位置挑。
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

## 8. 手動驗證（模擬器）

instrumented test 抓得到的是「寫成斷言的行為」；畫面長什麼樣、使用者實際操作一輪會不會壞，要自己在模擬器上走一遍。這一節是 instrumented test 之外的最後一道確認，不取代測試。下面的指令都在 `emulator-5554`（Android 13）上實際跑過；角括號 `<…>` 是要換成專案值的地方。

### 何時需要

- 改到畫面（Fragment 的 view、圖表、列表）、生命週期（旋轉、返回、切 tab、退到背景再回來）、ViewModel 與畫面之間的資料流。
- 修正「在模擬器上才看得到」的問題（顏色、版面、閃爍、標籤格式）。
- 純邏輯、純資料、純文件的改動不需要。

### 前置

1. **只用模擬器，不要動實體手機。** 開發機常同時接著手機：每個 adb 指令都加 `-s emulator-5554`（`adb devices` 看序號），Gradle 用 `ANDROID_SERIAL=emulator-5554`。`installDebug` 會印 `Installed on 1 device.`，不是 1 就停下來看。
2. **用 `installDebug` 安裝，不要用 `connectedDebugAndroidTest`。** connected 測試跑完會解除安裝 App、清掉資料。
   ```bash
   ANDROID_SERIAL=emulator-5554 ./gradlew installDebug   # JDK 前綴照 AGENTS.md
   ```
3. **資料被清掉就用 UI 重建。** 先前的 instrumented test、模擬器重啟（用 `-no-snapshot-save` 啟動的話，重啟前的安裝與資料都會消失）、重裝，都會讓 App 回到空白。要有資料才看得到的畫面，先把資料建好（見「建立測試資料」）。
4. **模擬器要有網路，而且要能解析網域。** App 呼叫的 API 主機解析不了，畫面就沒有資料（見「常見的坑」的 DNS）。<!--ps--> **本專案：** 股價從 `mis.twse.com.tw` 查；沒有股價，自選股頁是空的、統計頁沒有扇形。<!--/ps-->

### 啟動與操作（以下都實際跑過）

在 zsh 裡用函式，不要把 `adb -s emulator-5554` 放進變數（不會被拆成多個參數）：

```bash
a() { adb -s emulator-5554 "$@"; }
OUT=<repo 外的資料夾>; mkdir -p "$OUT"

# 啟動：指定 MainActivity，不要用 monkey 或點 launcher 圖示（debug 版可能有第二個 launcher，見「常見的坑」）
# 列出這個 App 的所有 launcher activity：
a shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER <applicationId>
a shell am start -S -n <applicationId>/<MainActivity 的完整類別名稱>   # -S：先強制停止
# 等 splash 與第一次載入：冷啟動、剛安裝後要 5～10 秒；splash 期間 dump 看不到 App 的內容。
```
<!--ps-->**本專案：** applicationId 是 `com.albertkingdom.mystockapp`，Activity 類別在 namespace `com.example.mynewsapp` 底下，兩者不同：`a shell am start -S -n com.albertkingdom.mystockapp/com.example.mynewsapp.ui.MainActivity`。`query-activities` 會列出 `MainActivity` 與 `leakcanary.internal.activity.LeakLauncherActivity` 兩個。<!--/ps-->

```bash
# 截圖（放 repo 外，再用看圖工具打開）
a exec-out screencap -p > "$OUT/01-screen.png"

# 找元件座標：dump 出來解析 resource-id / text / content-desc 與 bounds，點中心
a shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
a exec-out cat /sdcard/ui.xml | python3 -c "
import sys,re
x=sys.stdin.read()
for m in re.finditer(r'<node [^>]*>',x):
    n=m.group(0); g=lambda k:(re.search(r' '+k+r'=\"([^\"]*)\"',n) or [0,''])[1]
    if g('text') or g('content-desc') or g('resource-id'):
        print(g('bounds'), 'click' if g('clickable')=='true' else '', g('resource-id').split('/')[-1], repr(g('text')), repr(g('content-desc')))
"

a shell input tap <x> <y>            # 座標是 bounds 的中心
a shell input text 12345             # 只試過數字
a shell input keyevent KEYCODE_BACK  # 收鍵盤、返回上一頁；在最上層畫面按 BACK 會離開 App
a shell input keyevent KEYCODE_HOME  # 退到背景
```

- 每個動作後等 2～5 秒再截圖或 dump；模擬器忙的時候要更久。
- 圖表（自繪的 View）在 dump 裡只有一個節點，看不到圖形與標籤，**要看截圖**。
- 表單、列表的文字（數量、金額）在 dump 裡有，可以直接比對，不必靠眼睛。

#### 旋轉

```bash
a shell settings get system accelerometer_rotation   # 先記下原值
a shell settings get system user_rotation            # 先記下原值
a shell settings put system accelerometer_rotation 0
a shell settings put system user_rotation 1          # 橫向；截圖寬高對調
a shell settings put system user_rotation 0          # 回直向
a shell settings put system accelerometer_rotation 1 # 還原成原值，別留著鎖定
```

#### 退到背景再回來

```bash
a shell input keyevent KEYCODE_HOME
a shell am start -n <applicationId>/<MainActivity 的完整類別名稱>
# 會印 "Warning: Activity not started, its current task has been brought to the front"，這是正常的：同一個 task 被帶到前景。
```

#### 查 crash 與 ANR

```bash
a logcat -c                                   # 操作前先清
# …操作…
a logcat -d -s AndroidRuntime:E               # 空的 = 沒有 crash
a logcat -d | grep -c "FATAL EXCEPTION"       # 要是 0
a logcat -d | grep -E "ANR in|has died"       # 要是空的
a logcat -d | grep -c "UnknownHostException"  # 網路是否正常
```

### 建立測試資料（用 UI）

先用 `uiautomator dump` 找到各個欄位與按鈕的 resource-id、文字或 content-desc，再依序點與輸入。表單的必填欄位（例如日期）沒填，儲存不會成功，錯誤訊息會出現在 dump 的 `text` 裡；日期選擇器的確定鈕在 dump 裡是 `button1`。沒有 resource-id 的欄位，用 content-desc 找。<!--ps-->

**本專案的步驟（統計頁要有資料）：**

1. 自選股頁 → `addStockButton`（新增）→ 點搜尋框輸入代號（`0050`）→ `star_icon` 加入；清除搜尋（`search_close_btn`）再輸入 `0056` → `star_icon`。按兩次 BACK 回自選股頁，等幾秒，列表出現價格才表示股價查得到。
2. 點股票（`stockName`）→ 右上 `addButton`（＋）→ 「新增買/賣」→ `amount_edit` 輸入股數、`price_edit` 輸入價格，**成交日期必填**：`date_edit` → 日期選擇器的 OK（`button1`，預設今天）→ `finishButton`（完成）。手續費會自動帶出（例：1000 股 × 90 帶出 128）。
3. 股利：同一個 ＋ → 「新增股利」→ 「現金股利」（沒有 id，用 content-desc 找）輸入金額 → 日期 → 完成。
4. 資產頁：底部導覽「資產」（`statisticFragment`，content-desc 為「資產」）。「庫存」與「股利」兩個分頁各有資料才算建好。

這次建的資料：0050 買 1000 股＠90（股價 92.50，市值 92,500）、0056 買 2000 股＠48（股價 49.91，市值 99,820）；現金股利 0050 2000、0056 1500。
<!--/ps-->

### 判讀

- **標籤與數字用手算驗證。** 預期值不要來自畫面：先用手算（例如各項金額 ÷ 總金額），再和畫面上的標籤比對，連格式（單位、空格）一起看。<!--ps--> **本專案（統計頁）：** 92,500 ÷ (92,500 + 99,820) = 48.1%，99,820 ÷ 192,320 = 51.9%，圖上要看到 `48.1 %`、`51.9 %`（數字與 % 之間有空格）；分頁用 dump 比對（`1000 股`、`NT$92,500`、配發現金 `2000`）。<!--/ps-->
- **分頁、列表的數字用 dump 比對**，不必靠眼睛。
- **顏色是否變了，用像素取樣比較，不要憑眼睛。** 取兩個色塊裡的固定點，比較前後兩張截圖的 RGB：
  ```bash
  python3 -c "
  import sys; from PIL import Image
  for p in sys.argv[1:]:
      im=Image.open(p).convert('RGB'); print(p, '#%02x%02x%02x'%im.getpixel((x1,y1)), '#%02x%02x%02x'%im.getpixel((x2,y2)))" a.png b.png
  ```
  `(x1,y1)`、`(x2,y2)` 換成畫面上各色塊裡的點；本機有 Pillow（`python3 -c "import PIL"` 不報錯）才能用。
- **「預期」要先弄清楚再下結論。** 拿不準某個行為是不是這次改出來的，就把基底分支（上一個 commit 或基底分支）的 debug 版裝上去，用同樣步驟量一次再比較。<!--ps--> **本專案：** 統計頁圓餅圖的顏色是隨機的，在 view 重建時重新抽：切 tab 再回來、從股利細節返回、旋轉都會變色；只有「退到背景再回來」（view 沒重建）會保持。重構統計頁時，就是用基底版量一次，確認顏色行為與重構前一致。像素取樣點（1080 寬直向）：`(700,650)` 在 0050 扇形、`(380,420)` 在 0056 扇形。<!--/ps-->
- logcat 的 crash、ANR、`UnknownHostException` 見上面；沒有就在回報寫出「0 個 FATAL EXCEPTION」。<!--ps--> **本專案：** 統計頁橫向時圓餅圖版面很小、標籤擠在一起，在基底版也是如此（版面問題，與該次改動無關）；看到時照實記錄，不要當成回歸。<!--/ps-->

### 常見的坑

- **打開的是別的 launcher，不是 App**：debug 版若有第二個 launcher（例如 LeakCanary 的 Leaks 畫面），用 `monkey` 或 launcher 圖示會打開那一個。一律用 `am start -n <applicationId>/<MainActivity 的完整類別名稱>`；`cmd package resolve-activity` 在有多個 launcher 時只會回 `ResolverActivity`，不能用，改用上面的 `query-activities` 列出全部。<!--ps--> **本專案：** debug 版有 LeakCanary 的第二個 launcher（`leakcanary.internal.activity.LeakLauncherActivity`），用 `monkey` 會打開 LeakCanary 的 Leaks 畫面（「0 Distinct Leaks」）。<!--/ps-->
- **權限對話框吃掉點擊**：第一次進 App 可能跳出權限對話框（例如通知權限）。對話框在的時候點別處不會有效果。先處理掉再繼續；測試資料用不到的權限選不允許即可。<!--ps--> **本專案：** LeakCanary 會要求通知權限（`permission_deny_button`、「Don’t allow」）；這次前幾次加股票的點擊就是被這個對話框吃掉。<!--/ps-->
- **畫面是空的，但其實已經建了資料**：先看是不是資料來源（網路、價格）沒回來，再看 DNS。
- **DNS 壞了（模擬器指到 VPN 或 Tailscale 的 DNS）**：症狀是 logcat 一直有 `UnknownHostException: Unable to resolve host "<App 呼叫的網域>"`，畫面沒有資料；`a shell ping -c1 8.8.8.8` 通，`a shell ping -c1 <App 呼叫的網域>` 卻是 `unknown host`。查原因：`ps aux | grep netsimd` 看到 `--host-dns=100.100.100.100,...`（Tailscale MagicDNS），而 Tailscale 沒連線時這個位址不通（Mac 本身的 DNS 是好的）。處理二選一：(1) 把 Tailscale 連上線；(2) 關掉模擬器，用明確的 DNS 重開：
  ```bash
  adb -s emulator-5554 emu kill          # 約 3 秒結束
  nohup ~/Library/Android/sdk/emulator/emulator -avd <AVD 名稱> -no-snapshot-save -no-boot-anim -dns-server 8.8.8.8,8.8.4.4 > <log 檔> 2>&1 &
  # 等 adb -s emulator-5554 shell getprop sys.boot_completed 回 1（約 25 秒），再等幾秒讓網路就緒，
  # 用 ping <App 呼叫的網域> 確認；ps 看到 netsimd --host-dns=8.8.8.8,8.8.4.4 才算換掉。
  ```
  重開後，重開前裝的 App 與資料都沒了（`-no-snapshot-save`），要重新 `installDebug` 與建資料。重開模擬器會關掉使用者正在用的那一台，先問。<!--ps--> **本專案：** 要 ping 的網域是 `mis.twse.com.tw`；本機模擬器的 AVD 是 `Pixel_8_API_33`。<!--/ps-->
- **ANR 對話框（「… isn't responding」）**：這次出現在模擬器剛重開、host 負載很高（load average 約 10）時，截圖上看得到對話框，logcat 沒有 App 的例外。先看 `a shell dumpsys cpuinfo | head` 與 host 的 `uptime`；負載高就等幾分鐘、再做一次，不要急著判定是 App 的問題（這次對話框後來自己消失，App 回到上一個畫面，資料還在）。要確認是不是 App 造成，在負載低的時候用同樣步驟重做。
- **BACK 按太多次**：在最上層畫面再按 BACK 會離開 App 回到桌面；回到桌面後 dump 出來的是 launcher，不是 App。每步先 `a shell dumpsys activity activities | grep topResumedActivity` 看目前在哪個 Activity。
- **dump 抓不到畫面**：splash 期間、對話框或選單展開時，dump 只有對話框的元件。先截圖確認畫面，再 dump。
- **沒有 root**：`adb root` 會回 `adbd cannot run as root in production builds`（Play 版映像），不能改 hosts 或 DNS 設定，只能重開模擬器帶參數。

### 回報要附的證據

- 在哪一台、哪個版本：`emulator-5554`（AVD 名稱、API 等級）、分支與 commit、debug 版是不是目前分支裝的。
- 逐項的「預期 → 實際」，每項附截圖的完整路徑（放 repo 外）。預期的數字寫出手算過程。
- 做過的操作順序（旋轉、返回、切 tab、退到背景…），以及每項有沒有通過。
- logcat 結果：`FATAL EXCEPTION` 數量、有無 ANR、`UnknownHostException` 數量。
- 與基底版的比較（若有）：同樣步驟在基底版量到什麼。
- 沒驗證到的（例如沒測橫向的版面、沒測某個分頁）要明說，不要寫成通過。
- 環境動過什麼：有沒有重開模擬器、改過旋轉設定並已還原、App 資料是否重建過。

## 9. 回報

完成時回報：

- 新增或修改的測試檔與測試名稱。
- 修改前的失敗訊息（證明有重現）、故意改壞時失敗的測試，以及修改後的通過結果與測試數量。
- 跑了哪些指令；instrumented test 用哪台裝置或模擬器（API 等級）。
- 既有、與本次無關的失敗（例如 `ExampleInstrumentedTest`），註明它在基底分支上也失敗。
- 手動驗證的證據（見第 8 節）。
