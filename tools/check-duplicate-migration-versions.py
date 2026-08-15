#!/usr/bin/env python3
"""Report Flyway migrations that share a version number within the same location set.

Background
----------
Flyway refuses to resolve a migration set containing two migrations at the same
version. It does not pick one, and it does not warn -- it throws::

    FlywayException: Found more than one migration with version 20260806.1000
    Offenders:
    -> .../UPGRADE_20260806.1000__footer_layout_property.sql (SQL)
    -> .../UPGRADE_20260806.1000__theme_link_color.sql (SQL)

Nothing in this repo stops two branches from picking the same version. Each one
passes CI on its own -- the collision only exists once both are in the same tree,
so it appears at merge time, in whichever PR merges second.

That happened for real twice. PR #585 collided at 20260728.1000. Then on
2026-08-06 three settings branches independently chose 20260806.1000, and two of
them (#1079, #1081) failed only after their author had already merged main.

The failure is unusually hard to read. DatabaseMigrationTest and
ItemOrderMigrationTest fail in @BeforeAll, so junitlauncher records a
*container-level* failure: every per-class "Tests run:" line still reports
``Failures: 0`` while roughly twenty tests silently never execute, and the build
dies on build.xml's ``<fail if="hasFailingTest"/>`` with no visible failing test.
(TestFailureReportingListener prints the real cause, but it is buried in
several thousand lines of test output.) This check surfaces the same problem in
about a second, before any of that.

What it does
------------
Flyway is configured here with two independent location sets, each with its own
history table, so a version is only required to be unique *within* a set -- an
install migration and an upgrade migration may share a number, and several
legitimately do::

    install   table=flyway_install   NEW_*.sql      + database/install/V*.java
    upgrade   table=flyway_history   UPGRADE_*.sql  + database/upgrade/V*.java

Both SQL and Java migrations are collected per set, because they resolve into
one namespace: a ``V20260719_1004__x.java`` and an
``UPGRADE_20260719.1004__y.sql`` collide exactly as two SQL files would. Version
separators are normalized the way Flyway normalizes them (``_`` and ``.`` are
equivalent), so ``V20260806_1000`` and ``UPGRADE_20260806.1000`` are recognized
as the same version rather than as two different strings.

Repeatable migrations (``DO_*.sql``, ``REPEAT_*.sql``) carry no version and are
skipped.

Modes
-----
Default is REPORT-ONLY: it prints findings and exits 0. Pass ``--strict`` (or set
``STRICT=1``) to exit 1 when any location set has a duplicate version.

Exit codes: 0 = no duplicates (or report-only), 1 = a duplicate found under
--strict, 2 = bad usage, or no migration directory found.

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import argparse
import os
import sys
from collections import defaultdict

# (label, sql prefix, [directories]) -- mirrors DatabaseCommand's two Flyway configs.
LOCATION_SETS = [
    ("install", "NEW_", [
        "src/main/resources/database/install",
        "src/main/java/com/simisinc/platform/infrastructure/database/install",
    ]),
    ("upgrade", "UPGRADE_", [
        "src/main/resources/database/upgrade",
        "src/main/java/com/simisinc/platform/infrastructure/database/upgrade",
    ]),
]


def normalize(version: str) -> str:
    """Flyway treats '_' and '.' as the same version separator, so V20260806_1000
    and UPGRADE_20260806.1000 are one version, not two."""
    return version.replace("_", ".")


def migration_version(filename: str, sql_prefix: str) -> str | None:
    """Return the normalized version of one migration file, or None if the file
    is not a versioned migration (repeatable migrations, helpers, non-migrations)."""
    if filename.endswith(".sql") and filename.startswith(sql_prefix):
        rest = filename[len(sql_prefix):]
    elif filename.endswith(".java") and filename.startswith("V"):
        rest = filename[1:]
    else:
        return None
    if "__" not in rest:
        return None
    version = rest.split("__", 1)[0]
    return normalize(version) if version else None


def collect(root: str, dirs: list[str], sql_prefix: str) -> dict[str, list[str]]:
    """Map normalized version -> [repo-relative paths] across one location set."""
    found: dict[str, list[str]] = defaultdict(list)
    for rel_dir in dirs:
        base = os.path.join(root, rel_dir)
        if not os.path.isdir(base):
            continue
        for dirpath, _dirnames, filenames in os.walk(base):
            for name in sorted(filenames):
                version = migration_version(name, sql_prefix)
                if version is None:
                    continue
                found[version].append(os.path.relpath(os.path.join(dirpath, name), root))
    return found


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("root", nargs="?", default=".")
    ap.add_argument("--strict", action="store_true",
                    default=os.environ.get("STRICT") == "1")
    args = ap.parse_args()

    if not any(os.path.isdir(os.path.join(args.root, d))
               for _label, _prefix, dirs in LOCATION_SETS for d in dirs):
        # Exit 2, not 1: "the gate could not run" is a different signal from "the
        # gate found a duplicate", and CI should be able to tell them apart.
        print("error: no migration directories found under %s (run from the repository root)"
              % args.root, file=sys.stderr)
        return 2

    lines = ["Duplicate Flyway migration version check", ""]
    duplicates: list[tuple[str, str, list[str]]] = []
    for label, sql_prefix, dirs in LOCATION_SETS:
        found = collect(args.root, dirs, sql_prefix)
        for version, paths in sorted(found.items()):
            if len(paths) > 1:
                duplicates.append((label, version, sorted(paths)))
        lines.append("  %-8s %d migration version(s)" % (label, len(found)))
    lines.append("")

    if duplicates:
        for label, version, paths in duplicates:
            lines.append("  DUPLICATE  [%s] version %s used by %d migrations:" % (label, version, len(paths)))
            for path in paths:
                lines.append("               %s" % path)
        lines.append("")
        lines.append("Summary: %d duplicated version(s)." % len(duplicates))
    else:
        lines.append("Summary: no duplicate migration versions.")
    print("\n".join(lines))

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as fh:
            fh.write("## Duplicate Flyway migration versions\n\n")
            if duplicates:
                fh.write("**%d duplicated version(s).** Flyway refuses to resolve a set "
                         "containing two migrations at the same version.\n\n" % len(duplicates))
                fh.write("| Location set | Version | Migrations |\n|---|---|---|\n")
                for label, version, paths in duplicates:
                    fh.write("| %s | `%s` | %s |\n"
                             % (label, version, "<br>".join("`%s`" % p for p in paths)))
            else:
                fh.write("No duplicate migration versions.\n")

    if args.strict and duplicates:
        print()
        print("FAIL: two migrations share a version within one Flyway location set.")
        print("Flyway throws rather than choosing between them, which aborts the")
        print("migration tests in @BeforeAll -- where the per-class \"Tests run\" counts")
        print("still read Failures: 0 and the real cause is easy to miss.")
        print("Renumber the newer migration to an unused version.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
