#!/usr/bin/env python3
"""PreToolUse hook for the Bash tool.

Blocks commands that break the project's git rules (see AGENTS.md):
  - git add of the whole tree (., -A, -u) or of secret files
  - git commit with an attribution line, a subject that is not a Conventional
    Commit or is longer than 72 characters, or on a protected branch
  - gh pr merge without --merge

Exit 0 lets the command run. Exit 2 blocks it and sends stderr back to the agent.
Any internal error exits 0, so a bug here never stops the work.
"""
import json
import os
import re
import shlex
import subprocess
import sys

CONVENTIONAL = re.compile(
    r"^(feat|fix|refactor|chore|docs|test|perf|style|ci|build|revert)(\([^)]+\))?!?: \S"
)
SKIP_SUBJECT = re.compile(r'^(Merge |Revert "|fixup! |squash! |\$\(|`)')

ATTRIBUTION = [
    (re.compile(r"^\s*co-authored-by:", re.I | re.M), "Co-Authored-By"),
    (
        re.compile(
            r"generated (with|by)\b[^\n]*(claude|codex|chatgpt|openai|copilot|gemini|\bai\b|\bllm\b)",
            re.I,
        ),
        "Generated with ...",
    ),
    (re.compile("\U0001F916"), "機器人表情符號"),
    (re.compile(r"noreply@(anthropic|openai)\.com", re.I), "AI 的 noreply 信箱"),
    (re.compile(r"^(change|tasks):", re.I | re.M), "Spectra 追蹤資訊（Change:／Tasks:）"),
]

SECRET = re.compile(r"(^|/)(\.env(\..+)?|.+\.(pem|key|p8|p12)|id_rsa|id_ed25519)$")
SECRET_OK = re.compile(r"(^|/)\.env\.(example|sample|template)$")
WHOLE_TREE = {".", "./", "*", ":/"}
ENV_ASSIGN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
HEREDOC = re.compile(
    r"(?<!<)<<(?!<)-?\s*(['\"]?)(\w+)\1[^\n]*\n(.*?)\n[ \t]*\2[ \t]*(?=\n|\)|$)", re.S
)
HEREDOC_REF = re.compile(r"@@HEREDOC(\d+)@@")
FALLBACK_PROTECTED = {"main", "master", "develop"}


def split_commands(text):
    """Split on unquoted ; | && and newlines, keeping quoted text and $(...) intact."""
    segments, buf = [], []
    quote = None
    depth = 0
    i = 0
    while i < len(text):
        c = text[i]
        if quote:
            buf.append(c)
            if c == "\\" and quote == '"' and i + 1 < len(text):
                buf.append(text[i + 1])
                i += 2
                continue
            if c == quote:
                quote = None
        elif c == "\\" and i + 1 < len(text):
            buf.append(c)
            buf.append(text[i + 1])
            i += 2
            continue
        elif c in "'\"":
            quote = c
            buf.append(c)
        elif text.startswith("$(", i):
            depth += 1
            buf.append("$(")
            i += 2
            continue
        elif c == ")" and depth:
            depth -= 1
            buf.append(c)
        elif depth == 0 and (c in ";\n|" or text.startswith("&&", i)):
            segments.append("".join(buf))
            buf = []
            if text.startswith("&&", i):
                i += 2
                continue
        else:
            buf.append(c)
        i += 1
    segments.append("".join(buf))
    return [s.strip() for s in segments if s.strip()]


def tokenize(segment):
    try:
        argv = shlex.split(segment)
    except ValueError:
        argv = segment.split()
    while argv and ENV_ASSIGN.match(argv[0]):
        argv = argv[1:]
    return argv


def parse_git(argv):
    """Return (subcommand, args, -C path) for a git command, else None."""
    if not argv or os.path.basename(argv[0]) != "git":
        return None
    i = 1
    chdir = None
    while i < len(argv) and argv[i].startswith("-"):
        if argv[i] == "-C" and i + 1 < len(argv):
            chdir = argv[i + 1]
            i += 2
        elif argv[i] in ("-c", "--git-dir", "--work-tree", "--namespace") and i + 1 < len(argv):
            i += 2
        else:
            i += 1
    if i >= len(argv):
        return None
    return argv[i], argv[i + 1:], chdir


def git_out(workdir, *args):
    try:
        result = subprocess.run(
            ["git", "-C", workdir] + list(args),
            capture_output=True,
            text=True,
            timeout=5,
        )
    except Exception:
        return None
    if result.returncode != 0:
        return None
    return result.stdout.strip()


def protected_branches(project_dir):
    """Base and release branches from the facts table in AGENTS.md."""
    found = {}
    try:
        with open(os.path.join(project_dir, "AGENTS.md"), encoding="utf-8") as f:
            for line in f:
                m = re.match(r"^\|\s*(基底分支|發版分支)\s*\|\s*(.+?)\s*\|\s*$", line)
                if not m:
                    continue
                value = m.group(2).strip().strip("`").strip()
                if value and not value.startswith("<") and value not in ("無", "-", "none", "None"):
                    found[m.group(1)] = value
    except OSError:
        pass
    if "基底分支" not in found:
        return set(FALLBACK_PROTECTED) | ({found["發版分支"]} if "發版分支" in found else set())
    return set(found.values())


def check_add(args):
    reasons = []
    paths = []
    after_dashes = False
    for a in args:
        if after_dashes:
            paths.append(a)
        elif a == "--":
            after_dashes = True
        elif a in ("--all", "--update"):
            reasons.append("`git add %s` 會一次加入所有變更" % a)
        elif a.startswith("-") and not a.startswith("--"):
            if "A" in a[1:] or "u" in a[1:]:
                reasons.append("`git add %s` 會一次加入所有變更" % a)
        elif not a.startswith("--"):
            paths.append(a)
    for p in paths:
        if p in WHOLE_TREE:
            reasons.append("`git add %s` 會一次加入整個目錄樹" % p)
        elif SECRET.search(p) and not SECRET_OK.search(p):
            reasons.append("`%s` 看起來是機密檔（.env／金鑰／憑證），不能加入版控" % p)
    if reasons:
        reasons.append("請逐檔 `git add <檔案>`，只加入這次要提交的檔案。")
    return reasons


def commit_messages(args):
    msgs = []
    i = 0
    while i < len(args):
        a = args[i]
        if a in ("-m", "--message") and i + 1 < len(args):
            msgs.append(args[i + 1])
            i += 2
            continue
        if a.startswith("--message="):
            msgs.append(a[len("--message="):])
        elif re.match(r"^-[a-zA-Z]+m$", a) and i + 1 < len(args):
            msgs.append(args[i + 1])
            i += 2
            continue
        elif a.startswith("-m") and len(a) > 2 and not a.startswith("--"):
            msgs.append(a[2:])
        i += 1
    return msgs


def resolve_heredoc(message, bodies):
    m = HEREDOC_REF.search(message)
    if m and int(m.group(1)) < len(bodies):
        return bodies[int(m.group(1))]
    return message


def check_commit(args, bodies, workdir, project_dir):
    reasons = []
    msgs = [resolve_heredoc(m, bodies) for m in commit_messages(args)]
    reads_file = any(a in ("-F", "--file") or a.startswith("--file=") for a in args)
    if not msgs and reads_file and bodies:
        msgs = [bodies[0]]

    if msgs:
        text = "\n\n".join(msgs)
        for pattern, label in ATTRIBUTION:
            if pattern.search(text):
                reasons.append("commit 訊息含 %s，違反 Commit Message Convention" % label)
        subject = msgs[0].strip().split("\n", 1)[0]
        if subject and not SKIP_SUBJECT.match(subject):
            if len(subject) > 72:
                reasons.append("commit 摘要有 %d 個字元，上限 72" % len(subject))
            if not CONVENTIONAL.match(subject):
                reasons.append(
                    "commit 摘要「%s」不是 Conventional Commits 格式（type(scope): summary）" % subject
                )

    branch = git_out(workdir, "branch", "--show-current")
    if branch and git_out(workdir, "rev-parse", "--verify", "-q", "HEAD") is not None:
        root = project_dir or git_out(workdir, "rev-parse", "--show-toplevel") or workdir
        if branch in protected_branches(root):
            reasons.append(
                "目前在受保護的分支 `%s`（基底分支或發版分支），不能直接 commit；"
                "請先切到 feature／fix／chore 分支" % branch
            )
    if reasons:
        reasons.append("commit 訊息請用 git-commit skill 撰寫。")
    return reasons


def check_gh(argv):
    args = argv[1:]
    words = [a for a in args if not a.startswith("-")]
    if not any(words[i] == "pr" and words[i + 1] == "merge" for i in range(len(words) - 1)):
        return []
    flags = [a for a in args if a.startswith("-")]
    if "--squash" in flags or "--rebase" in flags or "-s" in flags or "-r" in flags:
        return ["PR 一律用 Merge commit，不用 squash 或 rebase。請改用 `gh pr merge <N> --merge`"]
    if "--merge" in flags or "-m" in flags:
        return []
    return ["`gh pr merge` 沒指定合併方式。PR 一律用 Merge commit：`gh pr merge <N> --merge`"]


def check_command(cmd, cwd, project_dir=None):
    bodies = []

    def stash(m):
        bodies.append(m.group(3))
        return "<<'%s'\n@@HEREDOC%d@@\n%s" % (m.group(2), len(bodies) - 1, m.group(2))

    sanitized = HEREDOC.sub(stash, cmd)
    reasons = []
    for segment in split_commands(sanitized):
        argv = tokenize(segment)
        if not argv:
            continue
        if argv[0] == "cd" and len(argv) > 1:
            cwd = os.path.normpath(os.path.join(cwd, os.path.expanduser(argv[1])))
            continue
        git = parse_git(argv)
        if git:
            sub, args, chdir = git
            workdir = os.path.join(cwd, os.path.expanduser(chdir)) if chdir else cwd
            if sub == "add":
                reasons += check_add(args)
            elif sub == "commit":
                reasons += check_commit(args, bodies, workdir, project_dir)
        elif os.path.basename(argv[0]) == "gh":
            reasons += check_gh(argv)
    return reasons


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        return 0
    if data.get("tool_name") != "Bash":
        return 0
    command = (data.get("tool_input") or {}).get("command") or ""
    cwd = data.get("cwd") or os.getcwd()
    try:
        reasons = check_command(command, cwd, os.environ.get("CLAUDE_PROJECT_DIR"))
    except Exception:
        return 0
    if not reasons:
        return 0
    sys.stderr.write("已阻擋這個指令：\n")
    for reason in reasons:
        sys.stderr.write("- %s\n" % reason)
    sys.stderr.write("請照上面調整後重新執行，不要改用其他寫法繞過這個檢查。\n")
    return 2


if __name__ == "__main__":
    sys.exit(main())
