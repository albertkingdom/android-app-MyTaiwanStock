---
name: wrapup
description: 收尾：實作完成後，一次完成 code review 關卡、分批 commit，review 通過才 spectra archive 並同步主 spec。當使用者說「收尾」「收工」「結案」「完成收尾」「幫我收一下」「wrap up」「archive 並 commit」或類似意思時使用。只在本機操作：不 push、不開 PR、不合併；code review 通過前不可歸檔、不可開 PR。
---

# Spectra 收尾（code review 關卡 → 分批 commit → 通過才 archive 並同步主 spec）

使用者說出「收尾」之類的關鍵字，代表授權你**在本機**完成下列流程。流程結束就停，不要接著 push、開 PR 或合併。只在使用者真的要收尾時使用；如果只是在討論「收尾是什麼」，直接回答，不要執行。

**專案事實一律讀 `AGENTS.md` 的「專案事實」表**（基底分支、發版分支、測試指令、CI 必過檢查）。表裡還有 `<填寫>` 的項目時，停下請使用者補，不要猜。

## 安全邊界（任何一條不成立就停下回報）

- **不在基底分支或發版分支上 commit。** 目前分支是其中之一時，停下並說明。
- **只在本機**：不 `git push`、不 `gh pr create`、不 merge、不刪分支、不 `--force`、不 `--amend` 已存在的 commit，除非使用者另外明確要求。
- **code review 沒完成或還有中（含）以上嚴重度的問題時，不得 archive，也不得建議或執行開 PR。**
- **逐檔 `git add <file>`**，永遠不用 `git add .`、`-A`、`-u`。
- **不提交機密**：不 stage `.env`、`.env.*`（`.env.example` 例外）、`*.pem`、`*.key`、含金鑰的暫存檔。提交前掃一次 `git diff --cached --name-only`。
- **commit 訊息一律用 `git-commit` skill**，並遵守 `AGENTS.md` 的 Commit Message Convention：不提 AI 工具、不加署名、不寫 Spectra 追蹤資訊。
- 需要確認時用純文字提問並等待回答。
- 任一步驟失敗：停止、貼出錯誤、不要往下做。

## 步驟

### 1. 前置檢查

```bash
git branch --show-current
git status --porcelain
spectra list
```

### 2. 決定要收尾哪個 change

依序嘗試，找到就用，並宣告「Wrapping up: <name>」：

1. 使用者訊息中提到的名稱。
2. 目前分支名稱與 `spectra list` 的 active change 對得上的那個。
3. 本次對話中剛 apply 的 change。
4. 都不明確：用純文字列出 active change 請使用者選，不要猜。

**沒有對應的 active change**（例如純文件或雜務分支）：略過步驟 3、4 的 Spectra 部分與步驟 8，只做 code review 與 commit，並在報告中說明。

一次只收一個 change；若這個分支還有其他完成的 change，收完後在報告裡提醒再說一次「收尾」。

### 3. 完成度檢查

```bash
spectra status --change <name> --json      # 每個 artifact 都要是 done
grep -n "^- \[ \]" openspec/changes/<name>/tasks.md
```

（change 目錄在 `openspec/changes/` 或 `docs/spectra/changes/`，依專案的 `spec_dir`。）

- 還沒勾的任務只有「提交／push／開 PR／取得 code review」這類收尾本身的關卡：允許繼續，並在報告中如實寫出剩下哪些。
- 還有其他實作任務未完成：停下，列出清單，由使用者決定。
- **不要自動使用 `--mark-tasks-complete`**，除非使用者明說。

### 4. 品質關卡

```bash
spectra validate <name>
spectra analyze <name> --json     # 只看 Critical / Warning，Suggestion 忽略
```

- 執行「專案事實」的測試指令。不能執行就如實寫「未執行」，**不要宣稱通過**。
- 不得為了收尾而呼叫需金鑰或會花錢的外部 API。
- 讀 `AGENTS.md` 有沒有列出「改了某檔案就要同步某文件」的規則；有的話檢查，沒同步就停下提醒。

### 5. Code review 關卡（commit 之前）

依 `AGENTS.md` 的 Code Review Workflow：

1. 審查範圍是尚未 commit 的改動；若改動已經 commit，則是「基底分支..HEAD」的差異。純文件、純設定的改動可以略過，但要在報告中說明略過。
2. 用目前工具的 code review 功能（Claude Code 見 `CLAUDE.md`），強度 `medium`。**不要啟動 `ultra`，也不要用 `--fix`**。
3. 把結果記錄到 `docs/reviews/{YYYY-MM-DD}-{主題}.md`（格式見 Code Review Workflow），逐項驗證、分級。
4. 有中（含）以上的問題：停下，報告「尚未歸檔，尚不可開 PR：<原因>」，等使用者決定。修復要遵守 Bug Fix Workflow，修完再審，直到沒有中以上的問題。低嚴重度的項目由使用者決定修或不修。
5. 通過後才進入下一步。review 文件的項目全部打勾後刪除。

### 6. 分批 commit

先看 `git status --porcelain`，依序各自 commit（空的批次略過）：

| 批次 | 檔案 | type |
|---|---|---|
| 1. 程式與測試 | 原始碼、測試、設定檔、建置檔 | 依 proposal 類型：`feat`／`fix`／`refactor`／`perf` |
| 2. 文件 | `README.md`、`docs/`（不含 review 文件） | `docs` |
| 3. Spectra 進行中的產物 | `openspec/changes/<name>/**`（或 `docs/spectra/changes/<name>/**`；proposal、design、tasks、delta specs，還沒 archive） | `docs(spectra)` |

- 程式與它的測試放同一個 commit，確保該 commit 自己是綠的。
- **bug 修正另開 `fix` commit**：change 裡順手修的 bug，以及步驟 5 code review 後才修的 bug，修正和它的測試另開一個 `fix` commit，不要併進批次 1 的 `feat`／`refactor` commit。同一個檔案混有修正與其他改動、分不開時，先提醒使用者；使用者同意合併成一個 commit 時，不論 type，該 commit 內文都要附測試證據。
- **bug 修正的測試證據**：type 為 `fix` 的 commit，內文要有 `AGENTS.md` Bug Fix Workflow 要求的測試證據（或例外時的原因與手動驗證步驟）。本次對話中沒有這些資料（例如在新對話收尾），停下請使用者提供或重跑，不要編造。步驟 5 code review 之後才修的 bug 一樣適用。
- 另外用 `git log <基底分支>..HEAD --format='%h %s%n%b'` 檢查分支上已經存在的 commit：`fix` commit，以及內文提到修正 bug 的其他 commit，缺測試證據的列進結束報告。
- 不屬於這個 change 的 dirty 檔案**不要 commit**，在報告中列出，或問使用者要不要另開 commit。
- 同一個檔案混有不相關的變更時，不要自動用 `git add -p`，提醒使用者。
- 使用者觸發「收尾」就是對這些本機 commit 的授權，不需逐批詢問；有無關檔案或疑似機密時才停下確認。

### 7. 確認 review 通過

步驟 5 必須已經通過（或已說明略過），才能繼續。review 之後若又有修正的 commit，先重跑 `spectra validate <name>`。

### 8. Archive 並同步主 spec

```bash
rm -f .spectra/touched/<name>.json          # 追蹤檔，archive 前清掉
spectra archive <name> --preview            # 唯讀預覽；失敗代表 delta spec 有問題，停下
spectra archive <name>                      # 套用 delta specs 到主 spec 並搬到 archive
```

- **「同步主 spec」就是 `spectra archive` 本身**（會輸出 `Specs applied: <capability>`）。不要加 `--skip-specs`，除非 change 沒有 delta spec 或使用者要求。
- 驗證：archive 目錄存在；每個 delta capability 在主 spec 都已建立或更新。否則停下回報。
- 主 spec 檔裡 Spectra 自動附加的 `@trace` 註解區塊是正常的。

### 9. 提交 archive 產物

archive 產生的變更逐檔 `git add`，一個 commit：`chore(spectra): archive <summary> and sync main specs`，列出同步的 capability。

### 10. 結束報告（繁體中文）

包含：

- archive 狀態：已歸檔（位置、同步的 capability 與數量），或「未歸檔」與原因
- 每個 commit 的 hash 與標題（依序）
- **code review 結果與 PR 狀態**：「通過，已歸檔，可開 PR」或「尚未歸檔，尚不可開 PR：<原因>」，以及各級問題數量
- 未完成的任務、**沒有執行的測試**、無關的 dirty 檔案
- 缺少測試證據的 commit（`fix`，或內文提到修正 bug 的 commit；見步驟 6）
- 其他「任務全完成但還沒 archive」的 active change（只列出，不要自動處理）
- 下一步（**不會自動做**）：review 通過並歸檔後，push 並開 PR；沒通過就先處理問題，再說一次「收尾」

## 範例觸發語

「收尾」「收尾吧」「收工」「結案」「幫我收尾」「這個 feature 可以收尾了」「archive 然後 commit」「wrap up」
