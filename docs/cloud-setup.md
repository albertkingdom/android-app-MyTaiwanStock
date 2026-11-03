# 雲端環境建置設定

在 Claude Code 雲端 session（Ubuntu 24.04、root）或其他 Linux 機器上，用以下步驟建立可以執行 `./gradlew assembleDebug testDebugUnitTest` 的環境。最後一節的腳本可以直接貼進雲端環境的 setup script。

## 需要的網路主機

環境的網路政策至少要允許下列主機。轉址鏈是 2026-11-02 用 `curl -sIL` 實測的結果。

| 主機 | 用途 |
|---|---|
| `dl.google.com` | Android SDK 套件（command-line tools、platform、build-tools），以及 Gradle 的 `google()` repository（`dl.google.com/dl/android/maven2`） |
| `services.gradle.org` → `github.com` → `release-assets.githubusercontent.com` | Gradle wrapper 下載 distribution（`gradle-8.7-bin.zip`） |
| `plugins.gradle.org` → `repo.maven.apache.org` | Gradle plugin（KSP）的 artifact |
| `repo.maven.apache.org` | `mavenCentral()` |
| `jitpack.io` | MPAndroidChart |
| `archive.ubuntu.com`、`security.ubuntu.com`（arm64 為 `ports.ubuntu.com`） | apt 安裝 JDK 17 與 unzip（Ubuntu 24.04 預設的 apt 來源） |

2026-11-02 的雲端 session 實測結果：

- 連得到：`services.gradle.org`、`github.com`、`release-assets.githubusercontent.com`、`plugins.gradle.org`、`repo.maven.apache.org`、`maven.google.com`、`archive.ubuntu.com`。
- 被擋（proxy 回 403）：`dl.google.com`、`jitpack.io`。

在預設網路政策下，JDK 17 裝得起來，但 Android SDK 和 MPAndroidChart 下載不到，無法在本機建置。這種情況下改以 PR 上的 CI（`build-and-test`）驗證，或請環境擁有者把上面兩個主機加進 Allowed domains（https://code.claude.com/docs/en/cloud-environments#network-access）。

## 1. JDK 17 與 unzip

`app/build.gradle` 設定了 `kotlin { jvmToolchain(17) }`，但 `settings.gradle` 沒有 toolchain resolver（foojay）。所以機器上必須先裝好 JDK 17，否則 Gradle 會出現 "No matching toolchains found"，而且不會自動下載。

雲端 session 每個指令都在新的 shell 執行，前一個指令 export 的變數不會留下來。所以下面每段指令都自己設定需要的變數，不使用 `cd`，可以分開執行。

```bash
DEBIAN_FRONTEND=noninteractive apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jdk-headless unzip curl ca-certificates
```

確認版本（路徑依 CPU 架構，amd64 或 arm64）：

```bash
/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture)/bin/java -version
```

雲端映像檔預設的 `java` 是 21。執行 Gradle 時一定要把 `JAVA_HOME` 指向 17（見第 4 節）：本專案用 Kotlin 1.9.0，官方從 1.9.20 才支援 JDK 21，用 21 跑 Gradle 時 kapt 或 Kotlin daemon 可能出錯。

## 2. Android SDK

這裡固定 command-line tools 的版本（`11076708`），安裝 compileSdk 34 所需的套件，裝在 `/opt/android-sdk`。

注意：2026-11-02 的雲端 session 擋住 `dl.google.com`，本節指令當時無法實際執行（第 1 節有執行過）。指令依 Android 官方的 command-line tools 安裝方式撰寫，第一次在放行的環境執行時，請確認結果並回頭更新本文件。

下載並解開 command-line tools：

```bash
export ANDROID_HOME=/opt/android-sdk && mkdir -p "$ANDROID_HOME/cmdline-tools" && curl -sSfL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && unzip -q -o /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" && rm -rf "$ANDROID_HOME/cmdline-tools/latest" && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" && rm /tmp/cmdline-tools.zip
```

接受 license，再安裝套件：

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture) && set +o pipefail && yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null
```

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture) && /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

`yes | sdkmanager --licenses` 結束時，`yes` 會收到 SIGPIPE。開了 `pipefail` 的話，整條管線會因此回傳非零值，所以這一步先關掉 `pipefail`。這樣管線的結果就是 `sdkmanager` 本身的結果，它真的失敗時仍然看得到。

## 3. local.properties

`local.properties` 已列在 `.gitignore`，不要 commit。在專案根目錄執行：

```bash
echo "sdk.dir=/opt/android-sdk" > local.properties
```

## 4. 建置與測試

在專案根目錄執行：

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture) ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug testDebugUnitTest
```

## setup script

可以整段貼進雲端環境的 setup script。重複執行也安全：檔案會覆寫，不會一直附加。這段只安裝工具；`local.properties` 要在 repo clone 之後產生（見第 3 節）。

```bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y openjdk-17-jdk-headless unzip curl ca-certificates
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture)"
ANDROID_HOME=/opt/android-sdk
export JAVA_HOME ANDROID_HOME
mkdir -p "$ANDROID_HOME/cmdline-tools"
curl -sSfL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q -o /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools"
rm -rf "$ANDROID_HOME/cmdline-tools/latest"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
rm /tmp/cmdline-tools.zip
set +o pipefail
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null
set -o pipefail
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
cat > /etc/profile.d/android.sh <<PROFILE
export JAVA_HOME=$JAVA_HOME
export ANDROID_HOME=$ANDROID_HOME
export PATH=\$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
PROFILE
mkdir -p "$HOME/.gradle"
touch "$HOME/.gradle/gradle.properties"
sed -i '/^org.gradle.java.home=/d' "$HOME/.gradle/gradle.properties"
echo "org.gradle.java.home=$JAVA_HOME" >> "$HOME/.gradle/gradle.properties"
```

雲端 session 的指令 shell 不是 login shell，也不是互動 shell，`/etc/profile.d` 和 `/etc/bash.bashrc` 都不會載入。所以腳本另外用 Gradle 自己的設定來指定 JDK：使用者層級 `~/.gradle/gradle.properties` 的 `org.gradle.java.home`。不論 shell 有沒有載入環境變數，Gradle 都會用 JDK 17 執行。SDK 位置則由第 3 節的 `local.properties` 提供，所以 clone 之後一定要產生它。

## CI

GitHub Actions 的 `build-and-test` job（`.github/workflows/ci.yml`）使用 temurin JDK 17。GitHub 的 ubuntu-latest 映像檔已經內建 Android SDK、設好 `ANDROID_HOME`，也接受了 license，所以 CI 不需要上面的 SDK 步驟。
