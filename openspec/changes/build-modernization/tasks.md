# Tasks: build-modernization

## 1. Toolchain alignment

- [x] 1.1 Kotlin 1.9.0 alignment, single Kotlin version source, remove jcenter: in the root `build.gradle`, set `ext.kotlin_version = "1.9.0"`, use `"org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"` on the buildscript classpath, and remove `jcenter()` from `allprojects.repositories`. KSP stays `1.9.0-1.0.13`. Verify: `grep -nE "1\.6\.21|1\.5\.0|jcenter" build.gradle` prints nothing.
- [x] 1.2 Single Kotlin version source, pin JUnit: in `app/build.gradle`, change the stdlib dependency to `"org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"` and `junit:4.+` to `junit:4.13.2`. No other dependency version changes. Verify: `grep -nE "1\.6\.21|4\.\+" app/build.gradle` prints nothing, and when 1.2 is done, `git diff origin/rx -- app/build.gradle` shows only those two lines changed (forced fixes from 4.2 are counted separately). [after: 1.1]

## 2. CI

- [x] 2.1 CI workflow: add `.github/workflows/ci.yml` as described in the design decision (triggers `pull_request` and `push` to `rx`, job `build-and-test`, temurin JDK 17, Gradle cache, `./gradlew assembleDebug testDebugUnitTest --stacktrace`, test results and reports uploaded with `if: always()`, concurrency that cancels only PR runs, read-only permissions). Verify: `python3 -c "import yaml;yaml.safe_load(open('.github/workflows/ci.yml'))"` succeeds and the job id is `build-and-test`.
- [x] 2.2 In `AGENTS.md`, set the `CI 必過檢查` row to `` `build-and-test`（GitHub Actions，`.github/workflows/ci.yml`） ``. Verify: `git diff origin/rx -- AGENTS.md` shows only that row changed.

## 3. Cloud setup document

- [x] 3.1 Cloud setup document: add `docs/cloud-setup.md` with the content listed in the design decision (JDK 17 prerequisite and install, pinned command-line tools, licenses, `platform-tools`, `platforms;android-34`, `build-tools;34.0.0`, `ANDROID_HOME`, `local.properties`, network hosts, a copyable setup-script block). Verify: the JDK 17 commands are the ones run in this session (`/usr/lib/jvm/java-17-openjdk-amd64/bin/java -version` reports 17), and the host list matches the `curl -sIL` redirect chains of the Gradle distribution and plugin portal URLs.

## 4. Review, publish, verify

- [ ] 4.1 Code review the uncommitted changes from 1.x–3.x with `/code-review` (medium or higher), record findings in `docs/reviews/`, fix and re-review until no medium-or-higher findings remain, then commit with the `git-commit` skill, push `feature/1.15/build-modernization`, and open a PR into `rx` without merging. Done when the PR exists and its `build-and-test` run has started. [after: 1.2, 2.1, 2.2, 3.1]
- [ ] 4.2 Forced fixes only: make `./gradlew assembleDebug testDebugUnitTest` pass with JDK 17. Where this session cannot reach the SDK or dependency hosts, the PR's `build-and-test` run is the verification. Fix only errors that block the build, each with the smallest behavior-preserving edit, listed in the PR description. Every fix round goes through code review before it is committed and pushed. Done when `build-and-test` is green on the PR and its uploaded test results contain `TEST-com.example.mynewsapp.ExampleUnitTest.xml` with `tests="1" failures="0" errors="0" skipped="0"`. [after: 4.1]
