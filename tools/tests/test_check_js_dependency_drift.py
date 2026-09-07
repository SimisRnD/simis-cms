"""check-js-dependency-drift.py: package.json versions vs vendored javascript/ directory names."""

import json

from conftest import run_tool, write

TOOL = "check-js-dependency-drift.py"
JS = "src/main/webapp/javascript"


def make_package_json(repo, deps):
    write(repo, "package.json", json.dumps({"name": "t", "dependencies": deps}))


def vendor(repo, dirname):
    """Create a vendored library directory by writing a file into it."""
    write(repo, f"{JS}/{dirname}/lib.min.js", "// vendored")


def test_matching_versions_pass_strict(repo):
    make_package_json(repo, {"widget": "1.2.3"})
    vendor(repo, "widget-1.2.3")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_version_drift_fails_strict(repo):
    make_package_json(repo, {"widget": "2.0.0"})
    vendor(repo, "widget-1.2.3")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "DRIFT" in r.stdout
    assert "WAR ships 1.2.3" in r.stdout


def test_drift_reported_but_exit_zero_without_strict(repo):
    make_package_json(repo, {"widget": "2.0.0"})
    vendor(repo, "widget-1.2.3")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "DRIFT" in r.stdout


def test_alias_surfaces_drift_a_rename_would_hide(repo):
    """The reason ALIASES exists.

    prismjs ships in a directory called prism-*. Without the alias the two never
    match, the drift lands in VENDORED-ONLY -- which reads as "vendored on purpose,
    nothing to see" -- and a version gap hides in the one bucket nobody reads. This
    is the real 2026-09-07 case: prismjs 1.30.0 declared, prism-1.29.0 shipping,
    with a published advisory against 1.29.0 that Dependabot never raised because
    it only ever saw the patched number in package.json.
    """
    make_package_json(repo, {"prismjs": "1.30.0"})
    vendor(repo, "prism-1.29.0")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "prismjs" in r.stdout
    assert "1.29.0" in r.stdout
    assert "VENDORED-ONLY -- shipped but not declared (0)" in r.stdout, \
        "the alias should have matched it, not left it unmatched"


def test_vendored_without_a_declaration_is_not_a_failure(repo):
    """Several libraries are vendored deliberately with no npm entry. Informational."""
    make_package_json(repo, {})
    vendor(repo, "handrolled-1.0.0")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "VENDORED-ONLY" in r.stdout
    assert "handrolled" in r.stdout


def test_declared_without_a_vendored_copy_is_not_a_failure(repo):
    make_package_json(repo, {"consumed-elsewhere": "1.0.0"})
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "DECLARED-ONLY" in r.stdout


def test_allowlisted_drift_does_not_fail_strict(repo):
    """foundation-datepicker is vendored under a date stamp, not a version."""
    make_package_json(repo, {"foundation-datepicker": "1.5.6"})
    vendor(repo, "foundation-datepicker-20180424")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "[allowlisted]" in r.stdout


def test_range_prefixes_are_stripped_before_comparing(repo):
    """Pins are exact today, but a ^ or ~ must not read as a mismatch."""
    make_package_json(repo, {"widget": "^1.2.3"})
    vendor(repo, "widget-1.2.3")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_a_multi_dash_name_splits_at_the_version_not_the_first_dash(repo):
    """add-to-calendar-0.1.0 must parse as (add-to-calendar, 0.1.0)."""
    make_package_json(repo, {"add-to-calendar": "0.1.0"})
    vendor(repo, "add-to-calendar-0.1.0")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "DRIFT -- package.json ahead of / behind the shipped copy (0)" in r.stdout
