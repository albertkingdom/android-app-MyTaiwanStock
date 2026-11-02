<!-- SPECTRA:START v1.3.0 -->

# Spectra Instructions

This project uses Spectra for Spec-Driven Development(SDD). Specs live in `openspec/specs/`, change proposals in `openspec/changes/`.

## Skills

Each `/spectra-*` skill carries its own trigger description; these are the groups:

- Shape and plan → `/spectra-discuss`, `/spectra-propose`
- Continue tasks for an identified change → `/spectra-apply`
- Update requirements or plans for an identified change → `/spectra-ingest`
- Quality gate → `/spectra-verify`, `/spectra-review`, `/spectra-analyze`, `/spectra-audit`, `/spectra-drift`, `/spectra-debug`
- Finish → `/spectra-archive`, `/spectra-commit`

Explicit skill invocation takes precedence. Apply existing authorization within its unchanged scope.

## Workflow

discuss? → propose → apply ⇄ ingest → verify / review → archive

- `discuss` is optional — skip if requirements are clear
- Requirements change mid-work? Plan mode → `ingest` → resume `apply`

## Parked Changes

Changes can be parked（暫存）— temporarily moved out of `openspec/changes/`. Parked changes won't appear in `spectra list` but can be found with `spectra list --parked`. To restore: `spectra unpark <name>`. The `/spectra-apply` and `/spectra-ingest` skills disclose parking and restore when the named operation is already explicitly requested; respect a known refusal, otherwise ask for missing authorization.

<!-- SPECTRA:END -->

@AGENTS.md

# Claude Code 專屬

- **計畫審查**：用 Agent 工具開審查者，`model: "opus"`，唯讀。
- **Code review**：commit 前執行 `/code-review`（強度 medium 起）。不要啟動 `ultra`，那是雲端計費，只能由使用者觸發。
- **Spectra**：用 `/spectra-*` 斜線 skill（discuss → propose → apply ⇄ ingest → archive）。
- **提問**：需要使用者確認時用純文字提問，不用 AskUserQuestion。
- **Hook**：`.claude/hooks/` 會擋 `git add .`、帶署名或格式不對的 commit、在基底分支 commit、沒指定 `--merge` 的 `gh pr merge`。被擋時照訊息調整，不要用 `bash -c`、`eval` 之類的寫法繞過。改 hook 後執行 `python3 .claude/hooks/test_hooks.py`。
