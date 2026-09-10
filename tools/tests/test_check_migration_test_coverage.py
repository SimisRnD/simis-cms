"""check-migration-test-coverage.py: a data-transforming upgrade migration needs a test (#1755)."""

import os
import subprocess
import sys
from pathlib import Path

from conftest import write

TOOL = "check-migration-test-coverage.py"
TOOLS_DIR = Path(__file__).resolve().parent.parent


def run_tool(name, root, *args, base_ref=None):
    """Run the gate against a synthetic repo.

    GITHUB_BASE_REF is scrubbed unless a test asks for it. CI sets it, and the tool would then
    try `origin/<base>...HEAD` inside a tmp repo that has no origin -- so without this the whole
    module passes locally and fails on every CI run. Pass base_ref to exercise that path
    deliberately instead of inheriting it by accident.
    """
    env = dict(os.environ)
    env.pop("GITHUB_BASE_REF", None)
    if base_ref is not None:
        env["GITHUB_BASE_REF"] = base_ref
    return subprocess.run(
        [sys.executable, str(TOOLS_DIR / name), str(root), *args],
        capture_output=True, text=True, env=env,
    )

UPGRADE_DIR = "src/main/resources/database/upgrade/2026"
TEST_DIR = "src/test/java/com/simisinc/platform/infrastructure/database"


def git(repo, *args):
    subprocess.run(["git", *args], cwd=repo, capture_output=True, text=True, check=False)


def commit_baseline(repo):
    """A repo with one commit, so HEAD~1..HEAD is a usable diff range."""
    git(repo, "init", "-q")
    git(repo, "config", "user.email", "t@example.com")
    git(repo, "config", "user.name", "t")
    write(repo, "README.md", "seed\n")
    git(repo, "add", "-A")
    git(repo, "commit", "-qm", "seed")


def commit_change(repo):
    git(repo, "add", "-A")
    git(repo, "commit", "-qm", "change")


# --- the transform classifier ------------------------------------------------------------

def test_pure_ddl_migration_needs_no_test(repo):
    commit_baseline(repo)
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1000__add_table.sql",
          "CREATE TABLE widgets (widget_id BIGSERIAL PRIMARY KEY);\n"
          "ALTER TABLE widgets ADD COLUMN name VARCHAR(255);\n")
    commit_change(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "1 upgrade migration(s) examined" in r.stdout
    assert "0 transform data" in r.stdout


def test_seed_insert_values_needs_no_test(repo):
    # Deliberately excluded: seeds have their own guard in SchemaInstallUpgradeParityTest, and
    # widening this gate to every migration would make it noise people route around.
    commit_baseline(repo)
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1000__seed.sql",
          "INSERT INTO site_properties (property_name, property_value) VALUES ('a.b', 'true');\n")
    commit_change(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "0 transform data" in r.stdout


def test_backfill_update_without_a_test_fails(repo):
    commit_baseline(repo)
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1000__backfill.sql",
          "UPDATE web_pages SET tsv = to_tsvector('simple', title) WHERE tsv IS NULL;\n")
    commit_change(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "1 transform data; 1 of those have no test" in r.stdout
    assert "20260901.1000" in r.stderr
    assert "UPDATE" in r.stderr


def test_delete_and_insert_select_are_transforms(repo):
    commit_baseline(repo)
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1000__d.sql",
          "DELETE FROM site_properties WHERE property_name = 'dead.one';\n")
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1100__i.sql",
          "INSERT INTO role_capabilities (role_id, capability)\n"
          "  SELECT role_id, 'admin:manage' FROM roles WHERE code = 'admin';\n")
    commit_change(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "2 transform data" in r.stdout


# --- what counts as covered --------------------------------------------------------------

def test_a_test_referencing_the_bare_version_covers_it(repo):
    commit_baseline(repo)
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1000__backfill.sql",
          "UPDATE web_pages SET tsv = NULL;\n")
    write(repo, f"{TEST_DIR}/BackfillMigrationTest.java",
          'class BackfillMigrationTest { void t() { harness.applyOnly("20260901.1000"); } }\n')
    commit_change(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "0 of those have no test" in r.stdout


def test_a_test_referencing_only_the_filename_still_covers_it(repo):
    # Regression: `_` is a word character, so a leading \b never matches after "UPGRADE_".
    # With that bug, WebhookMigrationTest -- which cites its three migrations only by filename
    # -- was reported as having no test at all.
    commit_baseline(repo)
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1000__backfill.sql",
          "UPDATE web_pages SET tsv = NULL;\n")
    write(repo, f"{TEST_DIR}/BackfillMigrationTest.java",
          "// exercises UPGRADE_20260901.1000__backfill.sql\nclass BackfillMigrationTest {}\n")
    commit_change(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "0 of those have no test" in r.stdout


def test_a_different_version_does_not_count_as_coverage(repo):
    commit_baseline(repo)
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1000__backfill.sql",
          "UPDATE web_pages SET tsv = NULL;\n")
    write(repo, f"{TEST_DIR}/OtherMigrationTest.java",
          'class OtherMigrationTest { void t() { harness.applyOnly("20260801.1000"); } }\n')
    commit_change(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "20260901.1000" in r.stderr


# --- the comment stripper ----------------------------------------------------------------

def test_an_update_inside_a_comment_is_not_a_transform(repo):
    commit_baseline(repo)
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1000__ddl.sql",
          "-- This replaces the old UPDATE web_pages SET tsv = ... approach.\n"
          "/* We used to DELETE FROM web_pages here. */\n"
          "CREATE TABLE t (id INT);\n")
    commit_change(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "0 transform data" in r.stdout


def test_a_dashdash_inside_a_string_literal_does_not_hide_the_rest(repo):
    # If the stripper treated the `--` in the literal as starting a comment, it would swallow
    # the real UPDATE that follows on the same line and report the file as pure DDL.
    commit_baseline(repo)
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1000__tricky.sql",
          "INSERT INTO notes (body) VALUES ('a -- b'); UPDATE notes SET body = 'x';\n")
    commit_change(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "1 transform data" in r.stdout


# --- failure posture ---------------------------------------------------------------------

def test_an_uncomputable_diff_fails_rather_than_reporting_clean(repo):
    # Issue #399: a shallow clone made a gate silently pass on every run. A gate that cannot
    # see the change must say so, not report success.
    r = run_tool(TOOL, repo)          # not a git repo at all
    assert r.returncode == 2
    assert "could not be computed" in r.stderr


def test_a_missing_base_ref_fails_rather_than_reporting_clean(repo):
    # The CI path: GITHUB_BASE_REF is set but origin/<base> cannot be resolved (a shallow clone,
    # a missing remote). Exit 2, not a clean run -- this is the #399 failure mode, and it is also
    # what caught this module inheriting CI's GITHUB_BASE_REF by accident.
    commit_baseline(repo)
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1000__backfill.sql",
          "UPDATE web_pages SET tsv = NULL;\n")
    commit_change(repo)
    r = run_tool(TOOL, repo, base_ref="main")
    assert r.returncode == 2
    assert "could not be computed" in r.stderr


def test_summary_reports_how_many_were_examined(repo):
    # A parser that matches nothing must not look like a pass -- the failure mode that hid a
    # broken column regex in check-column-length-limits.py.
    commit_baseline(repo)
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260901.1000__ddl.sql", "CREATE TABLE t (id INT);\n")
    commit_change(repo)
    r = run_tool(TOOL, repo)
    assert "1 upgrade migration(s) examined" in r.stdout
