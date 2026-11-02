---
name: git-commit
description: Use this skill whenever the user wants to create a git commit, write a commit message, stage files, or run `git commit`. Enforces conventional commits format and clean developer-authored messages — no AI tool names, no Co-Authored-By attribution.
---

# Git Commit

Write a clean, developer-authored commit message following the Conventional Commits format.

## Commit Message Format

```
<type>(<scope>): <short summary>

<optional body — explain WHY, not what>
```

**Types:** `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `perf`, `style`, `ci`

**Rules:**
- Language: always English, regardless of the language the user writes in
- Summary line: imperative mood, no period, ≤72 chars
- Body: explain motivation and context, not mechanics
- Scope: optional, name of the module/area changed

## What NOT to include

Never add any of the following to commit messages or pull request descriptions:
- `Co-Authored-By: ...` or any AI tool name
- `Generated with ...` or similar attribution
- Any mention of AI tools, LLMs, or assistants
- Spectra tracking details such as change names, task counts, `Change: ...`, `Tasks: N/M complete`

The commit must read as if written entirely by the developer.

## Process

1. Run `git diff --staged` (and `git diff` if nothing staged) to understand the changes
2. Run `git log --oneline -5` to match the project's commit style
3. Stage appropriate files with `git add <files>` — always specific files, never `git add -A`, `git add .` or `git add -u`
4. Write the commit message
5. Commit with `git commit -m "$(cat <<'EOF' ... EOF)"`
6. Verify with `git show --stat --format='%h %s' HEAD`

## Examples

```
feat(auth): add JWT refresh token rotation
```

```
refactor(app): singleton pattern for RAG components

Re-initializing the embeddings client and vector store on every request
added unnecessary latency. Components are now initialized once and
reused across requests.
```

```
fix(parser): replace manual JSON parsing with structured output
```

```
chore: add project-level skills to version control
```
