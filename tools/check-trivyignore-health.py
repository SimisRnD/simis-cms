#!/usr/bin/env python3
"""Guard the two states that make ``docker/db/.trivyignore`` stop working.

Background
----------
``docker/db/.trivyignore`` holds CVEs that are pending triage for the database image.
Each entry carries an expiry (``CVE-XXXX-NNNN exp:YYYY-MM-DD``), and the file's own
header states the intent: "A lapsed expiry re-fails the publish gate on the next run
-- deliberately, so triage cannot be forgotten silently."

That design assumes two things which have both turned out to be false in practice.

**1. That a lapsed expiry is a triage prompt.** It is not, because the gate it fails is
``Publish container images``, and the deploy step runs at the end of that same workflow.
So a lapsed entry does not prompt anyone -- it stops delivery for everything already
merged, and is discovered as an outage. That has happened three times: 2026-08-27,
2026-09-03, and 2026-09-08, the last of which stranded six merged PRs (#1927-#1932).
Nothing warned beforehand, because nothing outside ``publish-images.yml`` reads this
file at all, and that workflow only reads it as Trivy's ``--ignorefile``.

**2. That triage will arrive to clear the entry.** Each entry's recorded resolution is
"once the alert appears in code scanning, regenerate". ``generate-db-vex.py`` builds the
OpenVEX document from open Trivy alerts, and that feed has been empty since 2026-08-14
(issue #1935). So the step that clears an entry cannot run, and the only available
outcomes are another outage or another extension without triage. Neither is triage.

The generator already refuses to write an empty document -- ``write_document()`` raises
rather than silently dropping every suppression, added for issue #1463. But that guard
only runs when a human runs the generator, and there is no reason to run it when nothing
has changed. The contradiction is therefore real, durable, and observed by nothing.

What this checks
----------------
**Expiry proximity.** Fails when an entry has lapsed or expires within ``--warn-days``
(default 7). This converts the outage into a prompt, which is what the expiry mechanism
was meant to be. The window is deliberately wider than a day: the point is to leave room
to triage, not to catch the failure marginally earlier.

**Feed contradiction.** Fails when the file carries active entries while code scanning
reports zero open Trivy alerts. Those two states cannot both be legitimate -- an entry
exists precisely because a CVE is pending triage, and triage is fed by those alerts. One
of them is wrong, and since 2026-08-14 it has been the feed.

Scope, stated honestly
----------------------
Neither check can tell whether a suppression is *correct*. That is what the OpenVEX
document and its impact statements are for. This only guards the mechanism that is
supposed to force the question to be asked -- it verifies the prompt still works, not
the answer.

The feed check needs the code-scanning API. Pass ``--alert-count`` to supply the number
directly (what the tests do), or ``--check-alert-feed`` to have it fetched via ``gh``.
An API failure is reported as an operational error and exits 2; it is never treated as
"no alerts", because that would turn a broken token into a passing build -- the exact
shape of placebo gate this repository has been bitten by before.
"""

import argparse
import datetime
import os
import re
import subprocess
import sys

IGNORE_PATH = "docker/db/.trivyignore"
REPO = "SimisRnD/simis-cms"

# A CVE id at the start of a line, plus whatever follows it. Commented-out copies of the
# same text sit above each entry as documentation, so anchoring to the line start is what
# separates the active entry from its own explanation.
#
# The remainder is captured loosely on purpose rather than matched as a date. A stricter
# pattern silently skipped any line whose expiry it could not read, which is the worst
# available behaviour: the entry stays active in Trivy's eyes and disappears from this
# report, so a malformed date becomes a suppression nothing tracks. Read the id first,
# judge the expiry second.
ENTRY_RE = re.compile(r"^(CVE-\d{4}-\d+)\b(.*)$")
EXPIRY_RE = re.compile(r"^exp:(\S+)$")


def parse_entries(text):
    """Active entries as (cve, expiry_token_or_None, line_number). Comments are ignored.

    The expiry is returned as the raw token, not a date -- validating it is
    ``expiry_findings``' job, so an unreadable one is reported rather than dropped.
    """
    entries = []
    for lineno, line in enumerate(text.splitlines(), start=1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        match = ENTRY_RE.match(stripped)
        if not match:
            continue
        cve, remainder = match.group(1), match.group(2).strip()
        if not remainder:
            entries.append((cve, None, lineno))
            continue
        expiry = EXPIRY_RE.match(remainder)
        entries.append((cve, expiry.group(1) if expiry else remainder, lineno))
    return entries


def expiry_findings(entries, today, warn_days):
    """Findings for entries that have lapsed, expire soon, or carry no expiry."""
    findings = []
    for cve, expiry, lineno in entries:
        if expiry is None:
            findings.append(
                (cve, lineno, "has no expiry, so it never comes up for triage"))
            continue
        try:
            due = datetime.date.fromisoformat(expiry)
        except ValueError:
            findings.append((cve, lineno, "has an unreadable expiry %r" % expiry))
            continue
        days = (due - today).days
        if days < 0:
            findings.append(
                (cve, lineno,
                 "LAPSED %d day(s) ago (exp:%s) -- the publish gate is failing now" % (-days, expiry)))
        elif days <= warn_days:
            findings.append(
                (cve, lineno,
                 "expires in %d day(s) (exp:%s) -- triage or extend before it stops delivery"
                 % (days, expiry)))
    return findings


def feed_findings(entry_count, alert_count):
    """Finding for the contradiction: pending entries with nothing feeding their triage."""
    if entry_count > 0 and alert_count == 0:
        return [(
            "%d entry(ies) are pending triage, but code scanning reports zero open Trivy "
            "alerts. Both cannot be true: an entry exists because a CVE awaits triage, and "
            "triage is fed by those alerts. See issue #1935." % entry_count)]
    return []


def fetch_open_trivy_alert_count():
    """Open Trivy alert count from code scanning. Raises SystemExit(2) on any failure."""
    result = subprocess.run(
        ["gh", "api", "repos/%s/code-scanning/alerts?state=open&per_page=100" % REPO,
         "--paginate", "--jq", '[.[] | select(.tool.name == "Trivy")] | length'],
        capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit(
            "could not read code scanning alerts: %s\n"
            "Not treating this as zero alerts -- that would turn a broken token into a\n"
            "passing build. Fix the access or drop --check-alert-feed deliberately."
            % result.stderr.strip())
    # --paginate emits one count per page; the total is their sum.
    try:
        return sum(int(line) for line in result.stdout.split() if line.strip())
    except ValueError:
        raise SystemExit("unexpected output from the code scanning API: %r" % result.stdout[:200])


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("repo_root", nargs="?", default=".")
    parser.add_argument("--warn-days", type=int, default=7,
                        help="fail when an entry expires within this many days (default 7)")
    parser.add_argument("--today", help="override today's date as YYYY-MM-DD (for tests)")
    parser.add_argument("--alert-count", type=int,
                        help="open Trivy alert count, supplied rather than fetched")
    parser.add_argument("--check-alert-feed", action="store_true",
                        help="fetch the open Trivy alert count via gh and check the feed")
    parser.add_argument("--strict-feed", action="store_true",
                        help="fail the build on the feed contradiction, not just report it")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    path = os.path.join(args.repo_root, IGNORE_PATH)
    if not os.path.isfile(path):
        print("no %s -- nothing to check" % IGNORE_PATH)
        return 0

    with open(path, encoding="utf-8") as fh:
        entries = parse_entries(fh.read())

    today = (datetime.date.fromisoformat(args.today) if args.today
             else datetime.date.today())

    findings = expiry_findings(entries, today, args.warn_days)

    feed = []
    if args.alert_count is not None:
        feed = feed_findings(len(entries), args.alert_count)
    elif args.check_alert_feed:
        feed = feed_findings(len(entries), fetch_open_trivy_alert_count())

    lines = ["%s: %d active entry(ies)" % (IGNORE_PATH, len(entries)), ""]
    for cve, expiry, lineno in entries:
        lines.append("  line %-4d %s exp:%s" % (lineno, cve, expiry or "(none)"))
    lines.append("")

    if findings:
        lines.append("EXPIRY (%d):" % len(findings))
        lines.extend("  %s (line %d) %s" % (cve, lineno, why) for cve, lineno, why in findings)
    else:
        lines.append("EXPIRY: none lapsed or within %d day(s)." % args.warn_days)
    lines.append("")

    if feed:
        lines.append("FEED (%d):" % len(feed))
        lines.extend("  %s" % why for why in feed)
    elif args.alert_count is not None or args.check_alert_feed:
        lines.append("FEED: alert source is consistent with the pending entries.")
    else:
        lines.append("FEED: not checked (pass --check-alert-feed or --alert-count).")

    report = "\n".join(lines)
    print(report)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as fh:
            fh.write("## Pending-triage CVE health (`docker/db/.trivyignore`)\n\n")
            if findings or feed:
                fh.write("**%d finding(s).** These stop delivery when they mature -- the publish\n"
                         "gate reads this file, and the deploy step runs at the end of that same\n"
                         "workflow.\n\n" % (len(findings) + len(feed)))
                for cve, lineno, why in findings:
                    fh.write("- `%s` (line %d) %s\n" % (cve, lineno, why))
                for why in feed:
                    fh.write("- %s\n" % why)
            else:
                fh.write("%d entry(ies) pending, none expiring within %d day(s).\n"
                         % (len(entries), args.warn_days))

    # Expiry findings always fail: an entry that lapses stops delivery, and that is the
    # outage this exists to pre-empt. The feed contradiction reports by default and fails
    # only under --strict-feed, deliberately -- it is true right now (issue #1935 is open
    # and seven entries are pending), so failing on it would block every unrelated PR until
    # that design change lands. Same staging the jar and JavaScript drift checks used:
    # report first, reconcile, then flip. Add --strict-feed once #1935 closes; a check that
    # can never fail is theatre, and this one is meant to become real.
    blocking = list(findings) + (list(feed) if args.strict_feed else [])
    if blocking:
        print("\nFAIL: %d finding(s)." % len(blocking), file=sys.stderr)
        return 1
    if feed:
        print("\nFeed contradiction reported above; not failing the build "
              "(pass --strict-feed once issue #1935 is fixed).", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
