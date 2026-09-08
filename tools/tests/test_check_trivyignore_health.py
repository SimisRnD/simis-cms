"""check-trivyignore-health.py: expiry proximity and the alert-feed contradiction."""

from conftest import run_tool, write

TOOL = "check-trivyignore-health.py"
IGNORE = "docker/db/.trivyignore"

HEADER = """# Pending-triage CVEs for the database image scan gate.
#
# A lapsed expiry re-fails the publish gate on the next run -- deliberately, so
# triage cannot be forgotten silently.
#
"""


def write_ignore(repo, body):
    write(repo, IGNORE, HEADER + body)


def test_entries_well_inside_their_window_pass(repo):
    write_ignore(repo, "CVE-2026-66046 exp:2026-10-03\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-08")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "1 active entry" in r.stdout


def test_an_entry_expiring_soon_fails_before_it_stops_delivery(repo):
    """The whole point: turn the outage into a prompt while there is still time."""
    write_ignore(repo, "CVE-2026-66046 exp:2026-10-03\n")
    r = run_tool(TOOL, repo, "--today", "2026-10-01")
    assert r.returncode == 1
    assert "expires in 2 day(s)" in r.stdout


def test_a_lapsed_entry_fails_and_says_the_gate_is_already_failing(repo):
    write_ignore(repo, "CVE-2026-66046 exp:2026-09-10\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-12")
    assert r.returncode == 1
    assert "LAPSED 2 day(s) ago" in r.stdout
    assert "failing now" in r.stdout


def test_the_warning_window_is_configurable(repo):
    write_ignore(repo, "CVE-2026-66046 exp:2026-10-03\n")
    assert run_tool(TOOL, repo, "--today", "2026-09-20").returncode == 0
    assert run_tool(TOOL, repo, "--today", "2026-09-20", "--warn-days", "30").returncode == 1


def test_commented_copies_of_an_entry_are_not_counted(repo):
    """Every real entry has its rationale above it, including a commented copy of the
    entry line itself. Counting those would double every entry and report phantom
    expiries."""
    write_ignore(repo, "# CVE-2026-66046 exp:2026-01-01\n"
                       "#   libexpat1, no fixed version published by Debian.\n"
                       "CVE-2026-66046 exp:2026-10-03\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-08")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "1 active entry" in r.stdout


def test_an_entry_with_no_expiry_is_a_suppression_that_never_comes_up(repo):
    write_ignore(repo, "CVE-2026-66046\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-08")
    assert r.returncode == 1
    assert "has no expiry" in r.stdout


def test_pending_entries_with_zero_open_alerts_is_reported_as_a_contradiction(repo):
    """Issue #1935. An entry exists because a CVE awaits triage; triage is fed by those
    alerts. Zero alerts alongside pending entries means the feed is broken."""
    write_ignore(repo, "CVE-2026-66046 exp:2026-10-03\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-08", "--alert-count", "0", "--strict-feed")
    assert r.returncode == 1
    assert "zero open Trivy alerts" in r.stdout
    assert "#1935" in r.stdout


def test_pending_entries_with_alerts_present_is_consistent(repo):
    write_ignore(repo, "CVE-2026-66046 exp:2026-10-03\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-08", "--alert-count", "12")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "consistent" in r.stdout


def test_zero_alerts_is_fine_when_nothing_is_pending(repo):
    """An empty file and an empty feed agree -- there is nothing awaiting triage."""
    write_ignore(repo, "")
    r = run_tool(TOOL, repo, "--today", "2026-09-08", "--alert-count", "0")
    assert r.returncode == 0, r.stdout + r.stderr


def test_the_feed_is_not_checked_unless_asked(repo):
    """Silence about an unchecked thing is worse than saying it was not checked."""
    write_ignore(repo, "CVE-2026-66046 exp:2026-10-03\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-08")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "not checked" in r.stdout


def test_a_missing_ignore_file_is_not_a_failure(repo):
    r = run_tool(TOOL, repo, "--today", "2026-09-08")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "nothing to check" in r.stdout


def test_an_unreadable_expiry_is_reported_rather_than_ignored(repo):
    write_ignore(repo, "CVE-2026-66046 exp:03-10-2026\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-08")
    assert r.returncode == 1
    assert "unreadable expiry" in r.stdout


def test_unrecognised_trailing_content_is_reported_not_skipped(repo):
    """A stricter parser dropped any line whose expiry it could not read, which left the
    entry active in Trivy and invisible here -- a suppression nothing tracks."""
    write_ignore(repo, "CVE-2026-66046 until next tuesday\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-08")
    assert r.returncode == 1
    assert "1 active entry" in r.stdout
    assert "unreadable expiry" in r.stdout


def test_the_feed_contradiction_reports_without_failing_by_default(repo):
    """Issue #1935 is open and entries are pending, so this is true today. Failing on it
    would block every unrelated PR until that design change lands -- the same staging the
    jar and JavaScript drift checks used."""
    write_ignore(repo, "CVE-2026-66046 exp:2026-10-03\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-08", "--alert-count", "0")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "zero open Trivy alerts" in r.stdout
    assert "not failing the build" in r.stderr


def test_strict_feed_makes_the_contradiction_blocking(repo):
    write_ignore(repo, "CVE-2026-66046 exp:2026-10-03\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-08", "--alert-count", "0", "--strict-feed")
    assert r.returncode == 1
    assert "zero open Trivy alerts" in r.stdout


def test_an_expiry_finding_fails_even_without_strict_feed(repo):
    """Expiry is always blocking -- a lapsed entry stops delivery on its own."""
    write_ignore(repo, "CVE-2026-66046 exp:2026-09-01\n")
    r = run_tool(TOOL, repo, "--today", "2026-09-08", "--alert-count", "12")
    assert r.returncode == 1
    assert "LAPSED" in r.stdout
