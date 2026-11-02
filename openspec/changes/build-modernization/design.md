# Design: build-modernization

## Context

Current build configuration on `rx` (root `build.gradle`, `app/build.gradle`):

| Item | Current value |
|---|---|
| Android Gradle Plugin | 8.5.1 (needs Gradle ≥ 8.7, JDK 17) |
| Gradle wrapper | 8.7 |
| Kotlin Gradle plugin | 1.6.21 |
| `ext.kotlin_version` | "1.5.0" (not referenced anywhere) |
| KSP plugin | 1.9.0-1.0.13 (used only for the Moshi codegen) |
| Compose compiler extension | 1.5.2 |
| `kotlin-stdlib` dependency | 1.6.21, hard-coded |
| JVM target | `compileOptions` 17, `kotlin { jvmToolchain(17) }` |
| Repositories | google(), mavenCentral(), jitpack.io (MPAndroidChart), jcenter() |
| Unit tests | only `ExampleUnitTest` |
| CI | none |

This cloud session can install JDK 17 from apt, but the network policy denies `dl.google.com` (Android SDK packages and Gradle's `google()` repository) and `jitpack.io`. The Android SDK cannot be installed here, so a full local build cannot run in this session.

## Goals / Non-Goals

**Goals**

- `./gradlew assembleDebug testDebugUnitTest` passes with JDK 17 on a clean checkout, on GitHub Actions and in any environment that allows the hosts listed in D7.
- Every PR and every push to `rx` runs that command in a job named `build-and-test`.
- A cloud session can install the toolchain by copying commands from `docs/cloud-setup.md`.

**Non-Goals**

- No feature work. No change to app behavior, UI, data, or app logic beyond what compiling requires.
- No upgrade of AGP (stays 8.5.1), Gradle (stays 8.7), compileSdk/targetSdk (stay 34), Room, Firebase, Compose, or other libraries, unless the build fails without it.
- No kapt → KSP migration for Room, no version catalog, no move to the `plugins {}` DSL.
- No change to `versionName`/`versionCode`. The first feature change that ships in 1.15 bumps them.
- No instrumented tests (`connectedDebugAndroidTest`) in CI, because they need an emulator.

## Decisions

### Kotlin 1.9.0 alignment

Set Kotlin to 1.9.0. KSP versions are tied to a single Kotlin version (`1.9.0-1.0.13` → Kotlin 1.9.0), and Compose compiler `1.5.2` supports only Kotlin 1.9.0, so 1.9.0 aligns all three without touching anything else.

Alternatives: a newer Kotlin (1.9.2x or 2.x) would force KSP and Compose compiler upgrades too, and 2.x adds the Compose compiler Gradle plugin; rejected as beyond minimal scope. Downgrading KSP to a 1.6.21 build was also rejected, because Compose compiler 1.5.2 would still need Kotlin 1.9.0.

### Single Kotlin version source

Set `ext.kotlin_version = "1.9.0"` in the root `buildscript` and use it for both the `kotlin-gradle-plugin` classpath entry and the `kotlin-stdlib` dependency, so the two cannot drift again. Alternative: drop the explicit stdlib dependency (KGP adds it automatically). Rejected to keep the dependency list unchanged.

### Remove jcenter

Every remaining dependency resolves from google(), mavenCentral() or jitpack.io. If removing jcenter breaks resolution, the fix is to repoint the failing artifact to Maven Central at the same version, not to restore jcenter. Alternative: keep jcenter as a fallback. Rejected because it is read-only and scheduled to shut down.

### Pin JUnit

`4.+` resolves to 4.13.2, the last 4.x release, so pinning `junit:4.13.2` keeps the same artifact and makes resolution reproducible. Alternative: JUnit 5. Rejected as a behavior change for tests.

### Forced fixes only

After the changes above, any new build error gets the smallest edit that keeps behavior identical. Warnings are left alone. Each forced fix is listed in the PR description. Alternative: fix warnings and deprecations proactively. Rejected as scope creep that would conflict with the parallel feature branches.

### CI workflow

`.github/workflows/ci.yml`:

- triggers: `pull_request` (any target branch) and `push` to `rx`;
- job id and name `build-and-test`, `runs-on: ubuntu-latest`, `timeout-minutes: 30`;
- steps: `actions/checkout@v4`, `actions/setup-java@v4` (temurin 17), `gradle/actions/setup-gradle@v4` (cache and wrapper validation), then `./gradlew assembleDebug testDebugUnitTest --stacktrace`;
- always (`if: always()`) upload `app/build/test-results/testDebugUnitTest/` and `app/build/reports/tests/` with `actions/upload-artifact@v4`, so a green run still shows which tests ran;
- `concurrency` with `cancel-in-progress: true`, grouped by workflow plus ref for `pull_request` and by workflow plus commit SHA for `push`: a new push to a PR cancels that PR's older run, while each push to `rx` has its own group, so its run is never cancelled or replaced in the queue;
- `permissions: contents: read`.

The GitHub-hosted Ubuntu image has the Android SDK installed, `ANDROID_HOME` set, and SDK licenses accepted. AGP installs missing packages (platform 34, build-tools 34.0.0) itself. `app/google-services.json` is in version control, so the google-services plugin needs no secret. Alternative: a self-managed SDK install step such as `android-actions/setup-android`. Rejected because the image already provides the SDK.

### Cloud setup document

`docs/cloud-setup.md` records:

- JDK 17: `apt-get install -y openjdk-17-jdk-headless unzip`, with `JAVA_HOME` derived from the machine architecture (`/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture)`). JDK 17 must exist before Gradle runs: `settings.gradle` has no toolchain resolver (foojay), so `jvmToolchain(17)` only finds an installed JDK 17 and otherwise fails with "No matching toolchains found";
- Android SDK: the pinned command-line tools archive `commandlinetools-linux-11076708_latest.zip` unpacked into `$ANDROID_HOME/cmdline-tools/latest`, licenses accepted, then `platform-tools`, `platforms;android-34`, `build-tools;34.0.0`;
- `local.properties` with `sdk.dir=$ANDROID_HOME` (already in `.gitignore`);
- the network hosts the environment must allow, taken from redirect chains measured in this session with `curl -sIL`: `dl.google.com` (SDK packages, and Gradle's `google()` repository), `services.gradle.org` → `github.com` → `release-assets.githubusercontent.com` (Gradle distribution), `plugins.gradle.org` → `repo.maven.apache.org` (plugin artifacts), `repo.maven.apache.org` (`mavenCentral()`), `jitpack.io` (MPAndroidChart), plus the apt hosts `archive.ubuntu.com` and `security.ubuntu.com` (`ports.ubuntu.com` on arm64);
- each step block is self-contained (exports its own variables, uses absolute paths, no `cd`), because a cloud session runs every command in a fresh shell;
- what this session could and could not reach;
- one script block that a cloud environment setup script can copy as is.

Alternative: a committed SessionStart hook. Rejected; the task asks for a document that an environment setup script can reuse.

## Implementation Contract

- **Behavior**: the app's runtime behavior is unchanged. The only observable differences are in the build: the declared Kotlin version is 1.9.0, there is no jcenter, JUnit is 4.13.2, and CI exists.
- **Interfaces**: the CI check name `build-and-test` is the merge gate referenced from `AGENTS.md`. The command it runs is exactly `./gradlew assembleDebug testDebugUnitTest` (plus `--stacktrace`).
- **Data shapes**: none. No Room schema, preference, or network contract changes.
- **Failure modes**: dependency resolution failure after jcenter removal → repoint the artifact, never restore jcenter. A compile error from Kotlin 1.9 → minimal behavior-preserving source edit. A Gradle plugin incompatible with AGP 8.5.1 → latest patch of the same minor line. jitpack.io outage → CI fails, accepted.
- **Acceptance criteria**: the PR's `build-and-test` run is green, and its uploaded test results contain `TEST-com.example.mynewsapp.ExampleUnitTest.xml` with `tests="1" failures="0" errors="0" skipped="0"`. `grep -nE "1\.6\.21|jcenter" build.gradle app/build.gradle` prints nothing. `AGENTS.md` names `build-and-test`. `docs/cloud-setup.md` exists with the D7 content.
- **Scope boundaries**: may edit root `build.gradle`, `app/build.gradle`, `.github/workflows/ci.yml`, the `CI 必過檢查` row and the Android build-environment section of `AGENTS.md` (Gradle runs on JDK 17 instead of Android Studio's bundled JBR, to match CI), `docs/cloud-setup.md`, and source files only for forced fixes. Must not edit anything else under `app/src`, resources, version numbers, or signing.

## Risks / Trade-offs

- [Risk] Kotlin 1.9.0 predates Gradle 8.7 and may print a compatibility warning → the build must pass; a warning alone is accepted.
- [Risk] The Kotlin 1.9 compiler is stricter than 1.6.21 (for example, non-exhaustive `when` statements over sealed, enum or Boolean subjects became errors in 1.7) → forced, minimal fixes listed in the PR. Gradle may already be resolving KGP 1.9.0 through KSP's dependency, in which case nothing changes.
- [Risk] Gradle plugins `firebase-crashlytics-gradle:2.9.0` or `google-services:4.3.13` are incompatible with AGP 8.5.1 → move only the failing one to the latest patch of its minor line (for example `google-services` 4.3.15, `firebase-crashlytics-gradle` 2.9.9) and record it.
- [Risk] This session cannot reach `dl.google.com` or `jitpack.io`, so no local build here → GitHub Actions is the verification path; `docs/cloud-setup.md` lists the hosts to allow.
- [Risk] Each CI fix round needs a commit → every round goes through code review before commit and push, as `AGENTS.md` requires.

## Migration Plan

Merge into `rx` with a merge commit. Parallel feature branches rebase onto the new `rx`. Rollback is a revert of the merge commit.

## Open Questions

None.
