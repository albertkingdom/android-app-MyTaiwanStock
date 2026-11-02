# Proposal: build-modernization

## Why

目前專案無法穩定建置，後續所有對齊 iOS 的 change（手續費與稅、上櫃與股票名錄、截圖匯入）都卡在這裡：

- 根目錄 `build.gradle` 宣告 Kotlin Gradle plugin 1.6.21，但 KSP plugin 是 `1.9.0-1.0.13`，Compose compiler extension 是 `1.5.2`，這兩者都要求 Kotlin 1.9.0。宣告的 Kotlin 版本和 plugin 需要的不一致。實際用到哪個 Kotlin 取決於 Gradle 的版本衝突解析，從檔案內容無法確定，建置結果不可重現。
- `allprojects.repositories` 還列著 `jcenter()`。它已唯讀，而且即將關閉。
- `app/build.gradle` 把 `kotlin-stdlib:1.6.21` 寫死，和 plugin 版本分開；JUnit 用動態版本 `junit:4.+`。不同日期建置可能解析到不同的 artifact。
- 沒有 CI，PR 合併前沒有任何檢查。`AGENTS.md` 的「CI 必過檢查」寫著「無」。
- 雲端 session 沒有記錄下來的 JDK 17 和 Android SDK 安裝步驟。

## What Changes

- Kotlin Gradle plugin 升到 1.9.0，也就是 KSP `1.9.0-1.0.13` 和 Compose compiler `1.5.2` 對應的版本；`kotlin-stdlib` 跟著同一個版本。
- 移除 `jcenter()`。原本沒有被引用的 `ext.kotlin_version = "1.5.0"` 改為 `"1.9.0"`，當成 plugin 和 stdlib 共用的唯一版本來源。
- JUnit 固定為 `4.13.2`。
- 只修正會讓 `./gradlew assembleDebug testDebugUnitTest` 在 JDK 17 下失敗的錯誤。
- 新增 `.github/workflows/ci.yml`：job 名稱 `build-and-test`，在 `pull_request` 和 push 到 `rx` 時執行，使用 JDK 17，執行 `./gradlew assembleDebug testDebugUnitTest`。
- `AGENTS.md` 的「CI 必過檢查」改為 `build-and-test`。
- 新增 `docs/cloud-setup.md`，寫下安裝 JDK 17 和 Android SDK、設定 `ANDROID_HOME`、產生 `local.properties` 的實際指令。

## Capabilities

### New Capabilities

（無，僅建置與工具設定）

### Modified Capabilities

（無）

## Impact

- 受影響的 spec：無（只動建置與工具，App 的行為不變）。
- 受影響的檔案：build.gradle、app/build.gradle、.github/workflows/ci.yml（新增）、AGENTS.md（facts 表一列）、docs/cloud-setup.md（新增）。
- 平行進行中的 change（手續費與稅、上櫃與股票名錄、截圖匯入）在本 change 合併進 `rx` 後 rebase。
