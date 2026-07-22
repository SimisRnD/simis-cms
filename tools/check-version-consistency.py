#!/usr/bin/env python3
"""Report drift between the pom.xml project version and ApplicationInfo.VERSION.

Background
----------
Three places carry the project version, and only one of them is authoritative:

  * ``src/main/java/com/simisinc/platform/ApplicationInfo.java`` -- the ``VERSION``
    constant. ``build.xml`` loads it in the ``version`` target to stamp the build,
    so this is the version that actually ships.
  * the git release tag, ``v<VERSION>``, cut at release time.
  * ``pom.xml`` -- the project ``<version>``, carrying a ``-SNAPSHOT`` suffix. Maven
    plays no part in producing the WAR, so nothing fails when this one goes stale.

Because no build step reads the pom's own version, it drifts silently. It has done
so more than once: at ``v20231209.10000`` the pom still read ``20231127.10000-SNAPSHOT``,
and by ``v20260719.10000`` it had fallen two and a half years behind at
``20240106.10000-SNAPSHOT``. CONTRIBUTING.md already warns that the pom and the
shipped artifact "can silently drift apart" -- this is that same failure mode
applied to the version field itself.

What it does
------------
Compares the pom's project version, with any ``-SNAPSHOT`` suffix removed, against
``ApplicationInfo.VERSION``, and reports whether they agree.

The git tag is deliberately NOT part of the comparison. ``ApplicationInfo.VERSION``
is legitimately bumped ahead of the newest tag while the next release is still in
development, so requiring tag parity would fail the build during normal work.

Modes
-----
Default is REPORT-ONLY: it prints the finding and exits 0. Pass ``--strict`` (or set
``STRICT=1``) to exit 1 when the two versions disagree.

Exit codes: 0 = consistent (or report-only), 1 = drift under --strict, 2 = bad usage
or a version string that could not be read.

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import os
import re
import sys
import xml.etree.ElementTree as ET

POM_NS = "{http://maven.apache.org/POM/4.0.0}"

APPLICATION_INFO = "src/main/java/com/simisinc/platform/ApplicationInfo.java"
VERSION_RE = re.compile(r'\bString\s+VERSION\s*=\s*"([^"]+)"')

SNAPSHOT_SUFFIX = "-SNAPSHOT"


def _fail(message: str):
    """Exit 2 -- the version could not be read, which is distinct from finding drift.

    ``SystemExit("text")`` would print the text but exit 1, colliding with the
    --strict drift signal and hiding a broken checkout behind a "drift" result.
    """
    print(message, file=sys.stderr)
    raise SystemExit(2)


def read_application_info_version(root_dir: str) -> str:
    """Return the VERSION constant declared in ApplicationInfo.java."""
    path = os.path.join(root_dir, APPLICATION_INFO)
    try:
        with open(path, encoding="utf-8") as fh:
            source = fh.read()
    except OSError as exc:
        _fail(f"ERROR: cannot read {APPLICATION_INFO}: {exc}")

    matches = VERSION_RE.findall(source)
    # The file documents the constant's format in a comment above it, so match on the
    # declaration only and require exactly one -- two would mean the shape changed.
    if len(matches) != 1:
        _fail(
            f"ERROR: expected exactly one VERSION declaration in {APPLICATION_INFO}, "
            f"found {len(matches)}. Has the constant been renamed or duplicated?"
        )
    return matches[0]


def read_pom_version(root_dir: str) -> str:
    """Return the pom's own project <version> (not a parent's, not a dependency's)."""
    path = os.path.join(root_dir, "pom.xml")
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        _fail(f"ERROR: cannot parse pom.xml: {exc}")

    # Direct child of <project> only -- root.iter() would also pick up every
    # <version> under <dependencies>, <plugins>, and <properties>.
    version = root.find(POM_NS + "version")
    if version is None or not (version.text or "").strip():
        _fail("ERROR: pom.xml declares no top-level <version>.")
    return version.text.strip()


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    flags = {a for a in sys.argv[1:] if a.startswith("-")}

    unknown = flags - {"--strict"}
    if unknown or len(args) > 1:
        print(f"usage: {os.path.basename(sys.argv[0])} [ROOT] [--strict]", file=sys.stderr)
        return 2

    root_dir = args[0] if args else "."
    strict = "--strict" in flags or os.environ.get("STRICT") == "1"

    app_version = read_application_info_version(root_dir)
    pom_version = read_pom_version(root_dir)
    pom_base = pom_version[: -len(SNAPSHOT_SUFFIX)] if pom_version.endswith(SNAPSHOT_SUFFIX) else pom_version

    consistent = pom_base == app_version

    lines = [
        "Project version consistency (pom.xml vs ApplicationInfo.java)",
        "",
        f"  ApplicationInfo.VERSION : {app_version}   (authoritative -- build.xml reads this)",
        f"  pom.xml <version>       : {pom_version}",
        "",
    ]
    if consistent:
        lines.append("Summary: consistent.")
    else:
        lines.append(f"Summary: DRIFT -- pom declares {pom_base}, the build ships {app_version}.")
        lines.append(f"Fix: set the pom's project <version> to {app_version}{SNAPSHOT_SUFFIX}.")
    report = "\n".join(lines)
    print(report)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as fh:
            fh.write("## Project version consistency\n\n")
            if consistent:
                fh.write(f"**Consistent** — both declare `{app_version}`.\n\n")
            else:
                fh.write("**Drift.** The pom's project version does not match the version the build ships.\n\n")
                fh.write("| Source | Version |\n|---|---|\n")
                fh.write(f"| `ApplicationInfo.VERSION` (authoritative) | **{app_version}** |\n")
                fh.write(f"| `pom.xml` `<version>` | {pom_version} |\n\n")
                fh.write(f"Set the pom's project version to `{app_version}{SNAPSHOT_SUFFIX}`.\n")

    if strict and not consistent:
        print(
            f"\nFAIL (--strict): pom.xml project version {pom_version} does not match "
            f"ApplicationInfo.VERSION {app_version}.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
