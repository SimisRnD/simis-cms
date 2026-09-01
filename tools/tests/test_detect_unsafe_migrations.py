"""detect_unsafe_migrations.py: destructive-SQL + Java-field-removal correlation.

Unlike the other five tools/tests/*.py files, this one is git-based (issue #399):
it exercises the tool through real commits in a synthetic tmp_path repo, since the
Java-removal side of the check reads `git diff`, not the working tree.

Migration files below are written directly under
src/main/resources/database/upgrade/ (not a dated subdirectory like the real repo's
upgrade/2026/) to match check_migrations()'s current glob, which is not recursive.
Real migrations one level deeper are a separate, pre-existing bug from the one this
file covers (the shallow-clone / git-diff fix) and are out of scope here.
"""

import subprocess
from pathlib import Path

from conftest import run_tool, write

TOOL = "detect_unsafe_migrations.py"

MIGRATION_DIR = "src/main/resources/database/upgrade"
JAVA_FILE = "src/main/java/com/simisinc/platform/domain/model/Order.java"

JAVA_BEFORE = (
    "package com.simisinc.platform.domain.model;\n\n"
    "public class Order {\n"
    "  private String statusFlag;\n\n"
    "  public String getStatusFlag() {\n"
    "    return statusFlag;\n"
    "  }\n\n"
    "  public void setStatusFlag(String statusFlag) {\n"
    "    this.statusFlag = statusFlag;\n"
    "  }\n"
    "}\n"
)

JAVA_AFTER = (
    "package com.simisinc.platform.domain.model;\n\n"
    "public class Order {\n"
    "}\n"
)


def _git(root: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", *args], cwd=root, capture_output=True, text=True, check=True,
    )


def _init_repo(root: Path) -> None:
    _git(root, "init", "-q")
    _git(root, "config", "user.email", "test@example.com")
    _git(root, "config", "user.name", "Test")


def _commit_all(root: Path, message: str) -> None:
    _git(root, "add", "-A")
    _git(root, "commit", "-q", "-m", message)


def test_destructive_migration_with_matching_java_removal_is_flagged(repo, monkeypatch):
    # These three tests exercise the HEAD~1..HEAD fallback path (no PR base ref),
    # which is only reached when GITHUB_BASE_REF is unset. GitHub Actions sets it
    # for every job in a pull_request-triggered run -- including this test suite's
    # own tools-tests.yml job -- so without clearing it here, running these tests
    # in CI silently exercises the origin/<base>...HEAD path instead (and fails,
    # since these synthetic repos have no "origin" remote at all).
    monkeypatch.delenv("GITHUB_BASE_REF", raising=False)
    _init_repo(repo)
    write(repo, JAVA_FILE, JAVA_BEFORE)
    _commit_all(repo, "initial state")

    write(
        repo,
        f"{MIGRATION_DIR}/UPGRADE_20260801.1000__drop_status_flag.sql",
        "ALTER TABLE orders DROP COLUMN status_flag;\n",
    )
    write(repo, JAVA_FILE, JAVA_AFTER)
    _commit_all(repo, "drop status_flag column and field")

    r = run_tool(TOOL, repo)
    assert r.returncode == 1, r.stdout + r.stderr
    assert "UNSAFE MIGRATIONS DETECTED" in r.stderr
    assert "DROP COLUMN" in r.stderr


def test_destructive_migration_without_java_removal_is_not_flagged(repo, monkeypatch):
    monkeypatch.delenv("GITHUB_BASE_REF", raising=False)
    _init_repo(repo)
    write(repo, JAVA_FILE, JAVA_BEFORE)
    _commit_all(repo, "initial state")

    write(
        repo,
        f"{MIGRATION_DIR}/UPGRADE_20260801.1000__drop_status_flag.sql",
        "ALTER TABLE orders DROP COLUMN status_flag;\n",
    )
    _commit_all(repo, "drop status_flag column only, no Java change")

    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_java_removal_without_destructive_migration_is_not_flagged(repo, monkeypatch):
    monkeypatch.delenv("GITHUB_BASE_REF", raising=False)
    _init_repo(repo)
    write(repo, JAVA_FILE, JAVA_BEFORE)
    write(
        repo,
        f"{MIGRATION_DIR}/UPGRADE_20260801.1000__add_status_enum.sql",
        "ALTER TABLE orders ADD COLUMN status_enum VARCHAR(50);\n",
    )
    _commit_all(repo, "initial state")

    write(repo, JAVA_FILE, JAVA_AFTER)
    _commit_all(repo, "remove statusFlag field, no destructive migration")

    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr


def test_multi_commit_pr_via_github_base_ref_is_still_flagged(repo, monkeypatch):
    """Regression test for the shallow-clone bug (issue #399).

    HEAD~1..HEAD alone misses a removal that happened in an earlier commit of a
    multi-commit PR. With GITHUB_BASE_REF set -- as GitHub Actions sets it on
    pull_request events -- the tool should diff against the PR's base branch
    (origin/<base>...HEAD) instead, which covers the whole PR.
    """
    _init_repo(repo)
    write(repo, JAVA_FILE, JAVA_BEFORE)
    _commit_all(repo, "initial state")
    _git(repo, "branch", "-m", "main")
    # Simulate what a real CI checkout with fetch-depth: 0 leaves behind: a real
    # origin/<base> remote-tracking ref, reachable for a merge-base diff.
    _git(repo, "remote", "add", "origin", str(repo))
    _git(repo, "fetch", "origin", "-q")

    _git(repo, "checkout", "-q", "-b", "feature")

    # Commit 1 of the PR: the Java field removal.
    write(repo, JAVA_FILE, JAVA_AFTER)
    _commit_all(repo, "remove statusFlag field")

    # Commit 2 of the PR: the destructive migration, plus an unrelated file touch.
    write(
        repo,
        f"{MIGRATION_DIR}/UPGRADE_20260801.1000__drop_status_flag.sql",
        "ALTER TABLE orders DROP COLUMN status_flag;\n",
    )
    write(repo, "README.md", "unrelated change\n")
    _commit_all(repo, "drop status_flag column")

    monkeypatch.setenv("GITHUB_BASE_REF", "main")
    r = run_tool(TOOL, repo)
    assert r.returncode == 1, r.stdout + r.stderr

    # Sanity check that this genuinely exercises the multi-commit case: the old,
    # buggy HEAD~1..HEAD-only diff would NOT contain the Java removal, since it
    # happened in the PR's first commit, not its last.
    last_commit_diff = _git(repo, "diff", "HEAD~1..HEAD", "--", "src/main/java")
    assert "statusFlag" not in last_commit_diff.stdout


def test_missing_migrations_directory_exits_two(repo):
    # This one was not merely mislabelled, it was inverted: a missing upgrade/ directory
    # printed a warning to stderr and then returned 0, so renaming the directory turned
    # the gate off without failing a single build. Exit 2 is the documented "script
    # error" code, and is distinct from both 0 (checked, nothing unsafe) and 1 (found
    # something unsafe).
    r = run_tool(TOOL, repo)
    assert r.returncode == 2, r.stdout + r.stderr
    assert "error:" in r.stderr
    assert "migrations directory not found" in r.stderr
