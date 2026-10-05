"""check-trufflehog-pin.py: the `version` input vs the pinned action version.

The tool exists because pinning the action by SHA leaves the scanner on `latest`, and
Dependabot updates the `uses:` ref without touching the `with:` input that controls it.
Both halves are covered here: a missing input, and an input left behind by a SHA bump.
"""

from conftest import run_tool, write

TOOL = "check-trufflehog-pin.py"

SHA = "4dd8831c5f12599465d4d45c3c447b4018a34c85"


def workflow(uses_comment="# v3.97.9", version_line='          version: "3.97.9"\n', extra=""):
    """A minimal workflow carrying one TruffleHog step."""
    return (
        "name: Scan\n"
        "on:\n"
        "  pull_request:\n"
        "jobs:\n"
        "  secret-scan:\n"
        "    runs-on: ubuntu-latest\n"
        "    steps:\n"
        "      - uses: actions/checkout@abc123\n"
        f"      - uses: trufflesecurity/trufflehog@{SHA} {uses_comment}\n"
        "        with:\n"
        "          path: ./\n"
        f"{version_line}"
        "          extra_args: --json\n"
        f"{extra}"
    )


def test_matching_pin_passes_strict(repo):
    write(repo, ".github/workflows/scan.yml", workflow())
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_missing_version_input_fails_strict(repo):
    write(repo, ".github/workflows/scan.yml", workflow(version_line=""))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "floats to `latest`" in r.stdout
    # The fix line should name the version to add, read off the SHA comment.
    assert 'add `version: "3.97.9"`' in r.stdout


def test_version_left_behind_by_a_sha_bump_fails_strict(repo):
    """Dependabot rewrites the uses: ref and its comment; the input stays put."""
    write(repo, ".github/workflows/scan.yml", workflow(uses_comment="# v3.98.0"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "input declares 3.97.9, action pins 3.98.0" in r.stdout


def test_drift_reported_but_exit_zero_without_strict(repo):
    write(repo, ".github/workflows/scan.yml", workflow(uses_comment="# v3.98.0"))
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "findings               : 1" in r.stdout


def test_uncommented_sha_fails_strict(repo):
    """With no version comment there is nothing to check the input against."""
    write(repo, ".github/workflows/scan.yml", workflow(uses_comment=""))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "no `# vX.Y.Z` comment" in r.stdout


def test_version_input_of_a_later_step_is_not_credited(repo):
    """A version input further down the job belongs to that step, not the TruffleHog one."""
    later_step = (
        "      - uses: some/other-action@def456\n"
        "        with:\n"
        '          version: "3.97.9"\n'
    )
    write(repo, ".github/workflows/scan.yml", workflow(version_line="", extra=later_step))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "floats to `latest`" in r.stdout


def test_unquoted_version_value_is_accepted(repo):
    write(repo, ".github/workflows/scan.yml", workflow(version_line="          version: 3.97.9\n"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_every_trufflehog_step_is_checked(repo):
    """Two workflows, one pinned and one not -- the unpinned one must still be reported."""
    write(repo, ".github/workflows/a.yml", workflow())
    write(repo, ".github/workflows/b.yml", workflow(version_line=""))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "TruffleHog steps found : 2" in r.stdout
    assert "findings               : 1" in r.stdout
    assert "b.yml" in r.stdout


def test_yaml_extension_is_also_discovered(repo):
    write(repo, ".github/workflows/scan.yaml", workflow(version_line=""))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1


def test_no_trufflehog_step_at_all_exits_two(repo):
    """Silence would be wrong: either the scan was removed or the matching went stale."""
    write(repo, ".github/workflows/scan.yml", "name: Nothing\njobs: {}\n")
    r = run_tool(TOOL, repo)
    assert r.returncode == 2
    assert "no trufflesecurity/trufflehog step found" in r.stderr


def test_bad_usage_exits_two(repo):
    write(repo, ".github/workflows/scan.yml", workflow())
    r = run_tool(TOOL, repo, "--nonsense")
    assert r.returncode == 2
