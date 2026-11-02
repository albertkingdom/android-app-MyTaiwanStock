#!/usr/bin/env python3
"""Tests for the hooks in this directory.

Run from the project root:  python3 .claude/hooks/test_hooks.py

Each test feeds the hook the same JSON Claude Code sends and checks the exit
code (0 = allowed, 2 = blocked) and the message sent back on stderr.
"""
import json
import os
import py_compile
import shutil
import subprocess
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
GUARD = os.path.join(HERE, "guard_bash.py")
REMINDER = os.path.join(HERE, "review_doc_reminder.py")
SETTINGS = os.path.join(HERE, "..", "settings.json")

FILLED_TABLE = """# 專案開發規範

| 項目 | 值 |
|---|---|
| 基底分支 | `mvvm` |
| 發版分支 | `release` |
"""
UNFILLED_TABLE = """# 專案開發規範

| 項目 | 值 |
|---|---|
| 基底分支 | `<填寫，例如 main>` |
| 發版分支 | `<填寫，沒有就寫「無」>` |
"""


def git(repo, *args):
    subprocess.run(
        ["git", "-C", repo, "-c", "user.name=t", "-c", "user.email=t@example.com"] + list(args),
        check=True,
        capture_output=True,
    )


def make_repo(root, name, table, branch, commits=True):
    repo = os.path.join(root, name)
    os.makedirs(repo)
    subprocess.run(["git", "init", "-q", "-b", branch, repo], check=True, capture_output=True)
    with open(os.path.join(repo, "AGENTS.md"), "w", encoding="utf-8") as f:
        f.write(table)
    if commits:
        git(repo, "commit", "-q", "--allow-empty", "-m", "chore: init")
    return repo


def run_hook(script, payload, project_dir=None):
    env = dict(os.environ)
    env.pop("CLAUDE_PROJECT_DIR", None)
    if project_dir:
        env["CLAUDE_PROJECT_DIR"] = project_dir
    result = subprocess.run(
        ["python3", script],
        input=payload if isinstance(payload, str) else json.dumps(payload),
        capture_output=True,
        text=True,
        env=env,
    )
    return result.returncode, result.stderr


def heredoc_commit(message):
    return "git commit -m \"$(cat <<'EOF'\n%s\nEOF\n)\"" % message


class GuardBashTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = tempfile.mkdtemp()
        cls.feature = make_repo(cls.root, "feature", FILLED_TABLE, "feature/x")
        cls.on_base = make_repo(cls.root, "on_base", FILLED_TABLE, "mvvm")
        cls.on_release = make_repo(cls.root, "on_release", FILLED_TABLE, "release")
        cls.main_with_base = make_repo(cls.root, "main_with_base", FILLED_TABLE, "main")
        cls.unfilled_main = make_repo(cls.root, "unfilled_main", UNFILLED_TABLE, "main")
        cls.empty_main = make_repo(cls.root, "empty_main", UNFILLED_TABLE, "main", commits=False)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.root, ignore_errors=True)

    def run_guard(self, command, repo=None, tool="Bash"):
        repo = repo or self.feature
        payload = {"tool_name": tool, "tool_input": {"command": command}, "cwd": repo}
        return run_hook(GUARD, payload, project_dir=repo)

    def assertAllowed(self, command, repo=None):
        code, err = self.run_guard(command, repo)
        self.assertEqual(code, 0, "should be allowed: %r\n%s" % (command, err))

    def assertBlocked(self, command, repo=None, contains=None):
        code, err = self.run_guard(command, repo)
        self.assertEqual(code, 2, "should be blocked: %r" % command)
        if contains:
            self.assertIn(contains, err)

    # --- git add ---------------------------------------------------------

    def test_add_specific_files_is_allowed(self):
        for cmd in [
            "git add src/a.swift README.md",
            "git add -p",
            "git add -- docs/a.md",
            "git add .env.example",
            "git -C sub add file.txt",
            "git status",
            "ls -la",
            'echo "git add ."',
        ]:
            self.assertAllowed(cmd)

    def test_add_whole_tree_is_blocked(self):
        for cmd in [
            "git add .",
            "git add ./",
            "git add -A",
            "git add -u",
            "git add --all",
            "git add -Av",
            "cd sub && git add -A",
            "git add a.swift && git add .",
        ]:
            self.assertBlocked(cmd, contains="逐檔")

    def test_add_secret_files_is_blocked(self):
        for cmd in [
            "git add .env",
            "git add fastlane/.env",
            "git add .env.production",
            "git add cert.pem",
            "git add AuthKey_ABC.p8",
            "git add id_rsa",
        ]:
            self.assertBlocked(cmd, contains="機密")

    # --- git commit: message --------------------------------------------

    def test_good_commit_messages_are_allowed(self):
        for cmd in [
            'git commit -m "feat(chart): read prices with separators"',
            "git add a b && git commit -m \"fix: handle empty list\"",
            heredoc_commit("fix(chart): skip days without trades\n\nThe API returns -- on those days."),
            heredoc_commit("docs: explain why git add . is blocked\n\nAlso covers the Claude API client."),
            "git commit -m \"Merge branch 'feature/x' into mvvm\"",
            "git commit --amend --no-edit",
            "git commit -F - <<'EOF'\nfeat: add parser\nEOF",
        ]:
            self.assertAllowed(cmd)

    def test_non_conventional_subject_is_blocked(self):
        self.assertBlocked('git commit -m "update stuff"', contains="Conventional")
        self.assertBlocked('git commit -am "update stuff"', contains="Conventional")
        self.assertBlocked(heredoc_commit("Fixed the thing"), contains="Conventional")

    def test_long_subject_is_blocked(self):
        subject = "feat(chart): " + "x" * 70
        self.assertBlocked('git commit -m "%s"' % subject, contains="72")

    def test_attribution_is_blocked(self):
        for message in [
            "feat: add parser\n\nCo-Authored-By: Someone <a@b.c>",
            "feat: add parser\n\nGenerated with Claude Code",
            "feat: add parser\n\n\U0001F916 Generated by an assistant",
            "feat: add parser\n\nsent from noreply@anthropic.com",
            "feat: add parser\n\nChange: some-change",
            "feat: add parser\n\nTasks: 3/4 complete",
        ]:
            self.assertBlocked(heredoc_commit(message), contains="commit 訊息含")

    def test_attribution_in_second_message_flag_is_blocked(self):
        self.assertBlocked(
            'git commit -m "feat: x" -m "Co-Authored-By: Bot <noreply@anthropic.com>"',
            contains="Co-Authored-By",
        )

    def test_attribution_in_file_message_is_blocked(self):
        self.assertBlocked(
            "git commit -F - <<'EOF'\nfeat: x\n\nCo-Authored-By: Bot <b@c.d>\nEOF",
            contains="Co-Authored-By",
        )

    # --- git commit: branch ---------------------------------------------

    def test_commit_on_base_or_release_branch_is_blocked(self):
        self.assertBlocked('git commit -m "feat: x"', self.on_base, contains="mvvm")
        self.assertBlocked('git commit -m "feat: x"', self.on_release, contains="release")

    def test_commit_on_main_depends_on_facts_table(self):
        self.assertAllowed('git commit -m "feat: x"', self.main_with_base)
        self.assertBlocked('git commit -m "feat: x"', self.unfilled_main, contains="main")

    def test_first_commit_on_main_is_allowed(self):
        self.assertAllowed('git commit -m "chore: set up project rules and skills"', self.empty_main)

    def test_git_dash_c_and_cd_target_the_right_repo(self):
        self.assertBlocked('git -C %s commit -m "feat: x"' % self.on_base, self.feature, contains="mvvm")
        self.assertBlocked('cd %s && git commit -m "feat: x"' % self.on_base, self.feature, contains="mvvm")

    # --- gh pr merge -----------------------------------------------------

    def test_gh_pr_merge_needs_merge_commit(self):
        for cmd in ["gh pr merge 5 --merge", "gh pr merge 5 -m --delete-branch", "gh pr view 5", "gh pr create --title x"]:
            self.assertAllowed(cmd)
        for cmd in ["gh pr merge 5", "gh pr merge 5 --squash", "gh pr merge 5 --rebase", "gh -R a/b pr merge 5 --squash"]:
            self.assertBlocked(cmd, contains="Merge commit")

    # --- robustness ------------------------------------------------------

    def test_other_tools_and_bad_input_fail_open(self):
        code, _ = self.run_guard("git add .", tool="Edit")
        self.assertEqual(code, 0)
        self.assertEqual(run_hook(GUARD, "not json")[0], 0)
        self.assertEqual(run_hook(GUARD, "")[0], 0)
        self.assertEqual(run_hook(GUARD, {"tool_name": "Bash"})[0], 0)

    def test_block_message_tells_how_to_continue(self):
        _, err = self.run_guard("git add -A")
        self.assertIn("已阻擋", err)
        self.assertIn("不要改用其他寫法繞過", err)


class ReviewReminderTests(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp()
        os.makedirs(os.path.join(self.root, "docs", "reviews"))

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def write_review(self, name, text):
        with open(os.path.join(self.root, "docs", "reviews", name), "w", encoding="utf-8") as f:
            f.write(text)

    def run_reminder(self, **payload):
        return run_hook(REMINDER, payload, project_dir=self.root)

    def test_finished_review_is_reported(self):
        self.write_review("2026-01-01-a.md", "## 修復 Todo\n- [x] 一\n- [X] 二 — 不修，影響低\n")
        code, err = self.run_reminder()
        self.assertEqual(code, 2)
        self.assertIn("2026-01-01-a.md", err)

    def test_open_review_is_left_alone(self):
        self.write_review("a.md", "- [x] 一\n- [ ] 二\n")
        self.assertEqual(self.run_reminder()[0], 0)

    def test_file_without_checklist_is_left_alone(self):
        self.write_review("a.md", "# 只有說明，沒有 checklist\n")
        self.assertEqual(self.run_reminder()[0], 0)

    def test_no_reviews_directory_is_fine(self):
        shutil.rmtree(os.path.join(self.root, "docs"))
        self.assertEqual(self.run_reminder()[0], 0)

    def test_second_stop_is_allowed_so_it_cannot_loop(self):
        self.write_review("a.md", "- [x] 一\n")
        self.assertEqual(self.run_reminder(stop_hook_active=True)[0], 0)

    def test_only_finished_files_are_listed(self):
        self.write_review("done.md", "- [x] 一\n")
        self.write_review("open.md", "- [ ] 一\n")
        _, err = self.run_reminder()
        self.assertIn("done.md", err)
        self.assertNotIn("open.md", err)


class SettingsTests(unittest.TestCase):
    def setUp(self):
        with open(SETTINGS, encoding="utf-8") as f:
            self.settings = json.load(f)

    def test_every_hook_command_points_at_an_existing_script(self):
        commands = []
        for entries in self.settings["hooks"].values():
            for entry in entries:
                commands += [h["command"] for h in entry["hooks"]]
        self.assertTrue(commands)
        for command in commands:
            script = command.split("$CLAUDE_PROJECT_DIR/")[1].rstrip('"')
            path = os.path.join(HERE, "..", "..", script)
            self.assertTrue(os.path.exists(path), command)
            py_compile.compile(path, doraise=True)

    def test_pushing_and_pr_actions_ask_for_confirmation(self):
        ask = self.settings["permissions"]["ask"]
        for rule in ["Bash(git push:*)", "Bash(gh pr create:*)", "Bash(gh pr merge:*)", "Bash(git commit --amend:*)"]:
            self.assertIn(rule, ask)

    def test_attribution_stays_off(self):
        self.assertEqual(self.settings["attribution"]["commit"], "")
        self.assertEqual(self.settings["attribution"]["pr"], "")


if __name__ == "__main__":
    unittest.main(verbosity=2)
