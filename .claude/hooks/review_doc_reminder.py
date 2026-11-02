#!/usr/bin/env python3
"""Stop hook: remind the agent to delete finished review documents.

AGENTS.md says a file under docs/reviews/ is deleted once every item in it is
ticked. If such a file is still there when the agent is about to stop, exit 2
sends the reminder back and the agent keeps going once. The second stop is
allowed (stop_hook_active), so this can never loop.
"""
import glob
import json
import os
import re
import sys

UNCHECKED = re.compile(r"^\s*- \[ \]", re.M)
CHECKED = re.compile(r"^\s*- \[[xX]\]", re.M)


def finished_reviews(project_dir):
    done = []
    for path in sorted(glob.glob(os.path.join(project_dir, "docs", "reviews", "*.md"))):
        try:
            with open(path, encoding="utf-8") as f:
                text = f.read()
        except OSError:
            continue
        if CHECKED.search(text) and not UNCHECKED.search(text):
            done.append(os.path.relpath(path, project_dir))
    return done


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        data = {}
    if data.get("stop_hook_active"):
        return 0
    project_dir = os.environ.get("CLAUDE_PROJECT_DIR") or data.get("cwd") or os.getcwd()
    try:
        done = finished_reviews(project_dir)
    except Exception:
        return 0
    if not done:
        return 0
    sys.stderr.write("下列 review 文件的項目已全部打勾，依 AGENTS.md 的 Code Review Workflow 應該刪除（它不進版控）：\n")
    for path in done:
        sys.stderr.write("- %s\n" % path)
    sys.stderr.write("請刪除它們；如果還有事要先跟使用者確認，說明原因後再結束。\n")
    return 2


if __name__ == "__main__":
    sys.exit(main())
