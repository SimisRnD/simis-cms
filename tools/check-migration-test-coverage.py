#!/usr/bin/env python3
"""Require an executing test for every data-transforming upgrade migration in the change.

Issue #1755: `DatabaseMigrationTest` runs the INSTALL track on every build, but the UPGRADE
track is not run at all -- a fresh install baselines it to a high version, so a new
`UPGRADE_*` file is absorbed into the baseline and its SQL never executes. A migration author
therefore gets no signal: the suite is equally green whether the new file is correct,
syntactically broken, or silently a no-op. That is not hypothetical -- the repair migration in
#1754 passed a 4,164-test suite without ever being run, and its real defects were only found by
applying it to a container by hand.

Running all 169 historical migrations is deliberately NOT what this gate does, and #1755
explains why: upgrades are written against the schema as it was at the time, not against a
modern fresh install, so replaying them onto one fails for legitimate reasons (adding a column
that now already exists, and so on). That needs a decision about what "the starting schema"
means and is tracked separately.

What this gate does instead is close the forward-looking hole, on the class of migration where
a silent no-op does real damage: a backfill. A `CREATE TABLE` that fails is loud on the next
deploy; an `UPDATE` that matches zero rows is silent forever, and nobody finds out until the
data is wrong in production. So a changed migration containing an `UPDATE`, a `DELETE`, or an
`INSERT ... SELECT` must be exercised by a test.

`MigrationTestHarness` (issue #1755, added in PR #1759) makes that a fixture and one call, so
the cost of compliance is a few lines. `src/main/resources/database/README.md` has the worked
example.

Deliberately NOT required for pure DDL or for `INSERT ... VALUES` seed rows. Seeds have their
own guard in `SchemaInstallUpgradeParityTest`, and widening this gate to every migration would
make it noise that people learn to route around. That is a stated choice, not an oversight.

Usage:
  python3 tools/check-migration-test-coverage.py            # changed migrations (CI)
  python3 tools/check-migration-test-coverage.py --all      # audit the whole upgrade track
  python3 tools/check-migration-test-coverage.py <root> ... # explicit repo root, for tools/tests
"""

import os
import re
import subprocess
import sys

UPGRADE_DIR = os.path.join('src', 'main', 'resources', 'database', 'upgrade')
TEST_DIR = os.path.join('src', 'test')
HARNESS = 'src/test/java/com/simisinc/platform/infrastructure/database/MigrationTestHarness.java'
README = 'src/main/resources/database/README.md'

# A backfill: rewrites data that is already there, and fails silently when it matches nothing.
# A migration version as it appears in a test: bare and quoted ("20260801.1000"), or embedded in
# a filename (UPGRADE_20260729.1003). NOT \b...\b -- `_` is a word character, so a leading \b never
# matches after "UPGRADE_", and WebhookMigrationTest, which cites its three migrations only by
# filename, would have been reported as having no test at all.
VERSION_IN_TEXT = re.compile(r'(?<![\d.])(\d{8}\.\d{3,4})(?!\d)')

TRANSFORM_PATTERNS = [
    ('UPDATE', re.compile(r'\bUPDATE\s+[\w."]+\s+SET\b', re.IGNORECASE)),
    ('DELETE', re.compile(r'\bDELETE\s+FROM\b', re.IGNORECASE)),
    ('INSERT ... SELECT', re.compile(r'\bINSERT\s+INTO\b[\s\S]{0,400}?\bSELECT\b', re.IGNORECASE)),
]


def strip_sql_comments(sql):
    """Remove -- line comments and /* */ blocks, without being fooled by string literals.

    A `--` inside '...' is data, not a comment. Getting this wrong in the permissive direction
    would hide a real UPDATE inside what looks like a comment; getting it wrong in the strict
    direction would invent one. Both are worse than the few lines it takes to scan properly.
    """
    out = []
    i, n = 0, len(sql)
    in_line_comment = in_block_comment = in_string = False
    while i < n:
        ch = sql[i]
        nxt = sql[i + 1] if i + 1 < n else ''
        if in_line_comment:
            if ch == '\n':
                in_line_comment = False
                out.append(ch)
        elif in_block_comment:
            if ch == '*' and nxt == '/':
                in_block_comment = False
                i += 1
        elif in_string:
            out.append(ch)
            if ch == "'":
                if nxt == "'":       # '' is an escaped quote, still inside the string
                    out.append(nxt)
                    i += 1
                else:
                    in_string = False
        elif ch == '-' and nxt == '-':
            in_line_comment = True
            i += 1
        elif ch == '/' and nxt == '*':
            in_block_comment = True
            i += 1
        elif ch == "'":
            in_string = True
            out.append(ch)
        else:
            out.append(ch)
        i += 1
    return ''.join(out)


def version_of(path):
    """UPGRADE_20260901.1100__name.sql -> 20260901.1100"""
    name = os.path.basename(path)
    match = re.match(r'UPGRADE_([0-9.]+)__', name)
    return match.group(1) if match else None


def transforms_in(sql):
    body = strip_sql_comments(sql)
    return [label for label, pattern in TRANSFORM_PATTERNS if pattern.search(body)]


def changed_migrations(repo_root):
    """Upgrade migrations added or modified in the change under review.

    Same range and same failure posture as tools/detect_unsafe_migrations.py: prefer a
    merge-base diff against the PR base so the WHOLE PR is covered, fall back to HEAD~1..HEAD.
    Needs fetch-depth: 0 in CI to resolve origin/<base> -- see .github/workflows/ant.yml and
    issue #399, where a shallow clone made a gate silently pass on every run.

    Returns (paths, ok). ok is False when the diff could not be computed, so the caller can
    say so out loud rather than reporting a clean run it did not actually perform.
    """
    base_ref = os.environ.get('GITHUB_BASE_REF')
    diff_range = f'origin/{base_ref}...HEAD' if base_ref else 'HEAD~1..HEAD'
    try:
        output = subprocess.check_output(
            ['git', 'diff', '--name-only', '--diff-filter=AM', diff_range, '--', UPGRADE_DIR],
            cwd=repo_root, text=True, stderr=subprocess.DEVNULL,
        )
    except (subprocess.CalledProcessError, FileNotFoundError, OSError) as exc:
        print(f"warning: could not compute git diff for {diff_range}: {exc}", file=sys.stderr)
        return [], False
    paths = [line.strip() for line in output.splitlines()
             if line.strip().endswith('.sql') and os.path.basename(line.strip()).startswith('UPGRADE_')]
    return paths, True


def all_migrations(repo_root):
    paths = []
    for root, _dirs, files in os.walk(os.path.join(repo_root, UPGRADE_DIR)):
        for name in sorted(files):
            if name.startswith('UPGRADE_') and name.endswith('.sql'):
                paths.append(os.path.relpath(os.path.join(root, name), repo_root))
    return sorted(paths)


def versions_referenced_by_tests(repo_root):
    """Every migration version mentioned anywhere under src/test.

    Deliberately a mention rather than proof of execution: a version string in a test file is
    there because someone wrote a test about that migration. Demanding proof of execution would
    mean parsing Java to see which harness calls run, which is a lot of machinery for a gate
    whose job is to make sure somebody looked.
    """
    seen = set()
    for root, _dirs, files in os.walk(os.path.join(repo_root, TEST_DIR)):
        for name in files:
            if not name.endswith(('.java', '.sql')):
                continue
            try:
                with open(os.path.join(root, name), encoding='utf-8', errors='replace') as handle:
                    seen.update(VERSION_IN_TEXT.findall(handle.read()))
            except OSError:
                continue
    return seen


def main():
    # A positional root, like the other tools/ gates, so tools/tests can drive the real CLI
    # against a synthetic tree instead of the live repo (see tools/tests/conftest.py).
    positional = [a for a in sys.argv[1:] if not a.startswith('-')]
    repo_root = positional[0] if positional else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    audit_all = '--all' in sys.argv

    if audit_all:
        paths, diff_ok = all_migrations(repo_root), True
    else:
        paths, diff_ok = changed_migrations(repo_root)

    if not diff_ok:
        print("check-migration-test-coverage: FAILED -- the diff could not be computed, so no "
              "migration was examined. This is reported as a failure on purpose: a gate that "
              "cannot see the change must not report a clean run (issue #399).", file=sys.stderr)
        return 2

    tested = versions_referenced_by_tests(repo_root)
    transforming, uncovered = [], []

    for path in paths:
        full = os.path.join(repo_root, path)
        if not os.path.exists(full):
            continue
        with open(full, encoding='utf-8', errors='replace') as handle:
            kinds = transforms_in(handle.read())
        if not kinds:
            continue
        version = version_of(path)
        transforming.append((path, version, kinds))
        if version and version not in tested:
            uncovered.append((path, version, kinds))

    scope = "the whole upgrade track" if audit_all else "the change under review"
    print(f"Summary: {len(paths)} upgrade migration(s) examined in {scope}; "
          f"{len(transforming)} transform data; {len(uncovered)} of those have no test.")

    if audit_all:
        for path, version, kinds in transforming:
            mark = 'no test' if version not in tested else 'tested '
            print(f"  [{mark}] {version}  {', '.join(kinds):20s}  {path}")
        return 0

    if not uncovered:
        return 0

    print("\nThese migrations rewrite existing data and nothing executes them:", file=sys.stderr)
    for path, version, kinds in uncovered:
        print(f"  {path}\n      version {version}, contains {', '.join(kinds)}", file=sys.stderr)
    print(f"\nAn UPDATE that matches zero rows is silent forever. Add a test that applies the\n"
          f"migration and asserts what it changed -- {HARNESS}\n"
          f"makes that a fixture and one applyOnly(\"<version>\") call, and {README}\n"
          f"has the worked example. Reference the version string in the test so this gate can\n"
          f"see it.", file=sys.stderr)
    return 1


if __name__ == '__main__':
    sys.exit(main())
