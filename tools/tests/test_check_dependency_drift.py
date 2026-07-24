"""check-dependency-drift.py: pom versions vs vendored lib/build jar filenames."""

from conftest import make_pom, run_tool, write

TOOL = "check-dependency-drift.py"


def test_matching_versions_pass_strict(repo):
    make_pom(repo, deps=[("org.example", "widget", "1.2.3")])
    write(repo, "lib/build/widget-1.2.3.jar", "jar bytes")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_version_drift_fails_strict(repo):
    make_pom(repo, deps=[("org.example", "widget", "2.0.0")])
    write(repo, "lib/build/widget-1.2.3.jar", "jar bytes")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "DRIFT" in r.stdout


def test_drift_reported_but_exit_zero_without_strict(repo):
    make_pom(repo, deps=[("org.example", "widget", "2.0.0")])
    write(repo, "lib/build/widget-1.2.3.jar", "jar bytes")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "DRIFT" in r.stdout
