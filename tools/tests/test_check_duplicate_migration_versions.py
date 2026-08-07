"""check-duplicate-migration-versions.py: two migrations sharing one Flyway version."""

from conftest import run_tool, write

TOOL = "check-duplicate-migration-versions.py"

UPGRADE_DIR = "src/main/resources/database/upgrade/2026"
INSTALL_DIR = "src/main/resources/database/install"
UPGRADE_JAVA_DIR = "src/main/java/com/simisinc/platform/infrastructure/database/upgrade"


def test_distinct_versions_pass_strict(repo):
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260806.1000__footer.sql", "select 1;")
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260806.1100__theme.sql", "select 1;")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "no duplicate migration versions" in r.stdout


def test_duplicate_sql_versions_fail_strict(repo):
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260806.1000__footer.sql", "select 1;")
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260806.1000__theme.sql", "select 1;")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "DUPLICATE" in r.stdout
    assert "20260806.1000" in r.stdout


def test_duplicate_reported_but_exit_zero_without_strict(repo):
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260806.1000__footer.sql", "select 1;")
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260806.1000__theme.sql", "select 1;")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "DUPLICATE" in r.stdout


def test_same_version_across_install_and_upgrade_is_allowed(repo):
    # Separate Flyway configs (flyway_install vs flyway_history), separate namespaces.
    write(repo, f"{INSTALL_DIR}/NEW_10000__new_database.sql", "select 1;")
    write(repo, f"{UPGRADE_DIR}/UPGRADE_10000__something.sql", "select 1;")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_java_migration_collides_with_sql_migration(repo):
    # V20260719_1004 and UPGRADE_20260719.1004 are the same version to Flyway:
    # '_' and '.' are interchangeable version separators.
    write(repo, f"{UPGRADE_JAVA_DIR}/V20260719_1004__reencrypt.java", "class X {}")
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260719.1004__other.sql", "select 1;")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "20260719.1004" in r.stdout


def test_two_java_migrations_at_one_version_fail_strict(repo):
    write(repo, f"{UPGRADE_JAVA_DIR}/V20260719_1004__one.java", "class X {}")
    write(repo, f"{UPGRADE_JAVA_DIR}/V20260719_1004__two.java", "class Y {}")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "20260719.1004" in r.stdout


def test_repeatable_migrations_are_ignored(repo):
    # Repeatable migrations carry no version, so two of them never collide.
    write(repo, f"{UPGRADE_DIR}/REPEAT_something.sql", "select 1;")
    write(repo, f"{UPGRADE_DIR}/REPEAT_another.sql", "select 1;")
    write(repo, f"{INSTALL_DIR}/DO_seed.sql", "select 1;")
    write(repo, f"{INSTALL_DIR}/DO_more.sql", "select 1;")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_non_migration_files_are_ignored(repo):
    write(repo, f"{UPGRADE_DIR}/UPGRADE_20260806.1000__real.sql", "select 1;")
    write(repo, f"{UPGRADE_DIR}/README.md", "notes")
    write(repo, f"{UPGRADE_DIR}/UPGRADE_no_double_underscore.sql", "select 1;")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_missing_migration_directories_exit_two(repo):
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 2
    assert "no migration directories found" in (r.stdout + r.stderr)
