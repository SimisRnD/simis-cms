"""check-version-consistency.py: pom <version> vs ApplicationInfo.VERSION."""

from conftest import make_application_info, make_pom, run_tool

TOOL = "check-version-consistency.py"


def test_matching_versions_pass_strict(repo):
    make_pom(repo, version="20260724.1-SNAPSHOT")
    make_application_info(repo, version="20260724.1")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_drifted_versions_fail_strict(repo):
    make_pom(repo, version="20240101.1-SNAPSHOT")
    make_application_info(repo, version="20260724.1")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1


def test_drift_reported_but_exit_zero_without_strict(repo):
    make_pom(repo, version="20240101.1-SNAPSHOT")
    make_application_info(repo, version="20260724.1")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
