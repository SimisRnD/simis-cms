#!/usr/bin/env python3
"""Report drift between package.json and the vendored JavaScript that ships in the WAR.

Background
----------
This is the JavaScript sibling of ``check-dependency-drift.py``, and it exists for
the same reason that one does. The production WAR is assembled by Ant, which copies
``src/main/webapp/javascript/**`` straight into the artifact. npm plays no part in
building it -- ``package.json`` has no ``scripts`` block at all. It exists to give
Dependabot and the SBOM something to read.

That split has the failure mode the jar checker already names: a version can be
bumped in ``package.json`` (and the Dependabot PR merged) while the WAR keeps
shipping the older vendored copy, and nothing says so. The jar side has been
guarded since the drift checker landed. The JavaScript side -- the half that runs
in a visitor's browser -- was not.

It is not hypothetical. Both of these were live on 2026-09-07, undetected:

    mermaid     package.json 10.9.8   ->  vendored mermaid-10.9.6
    js-cookie   package.json 3.0.7    ->  vendored js-cookie-3.0.5

The mermaid one produced a Dependabot alert (GHSA-55q2-fjhq-7xh7) describing a
dependency tree that is not deployed. Dependabot read the lockfile, saw
mermaid 10.9.8 pulling dompurify 3.4.12, and reported on that. The bundle actually
served embeds DOMPurify 3.4.2 -- ten patch releases OLDER than the version being
flagged. Remediating the alert as filed would have changed nothing in the WAR.

Scope, stated honestly
----------------------
This compares DECLARED versions against VENDORED DIRECTORY versions. It cannot see
inside a vendored bundle. A vendored file may embed its own pinned copies of other
libraries -- mermaid bundles DOMPurify, TinyMCE bundles a good deal -- and those
inner versions are invisible to both ``package.json`` and this check. That is a
real remaining gap and it is the one that produced the alert above; closing it
means parsing minified bundles for license banners, which is fragile enough that
it deserves its own tool rather than being bolted on here.

What this catches is the outer layer: the version we claim to ship versus the
version on disk. Both 2026-09-07 drifts are of that kind.

Findings
--------
  * DRIFT          package.json and the vendored directory disagree  (the thing we care about)
  * VENDORED-ONLY  shipped but not declared in package.json          (manual vendor, or renamed)
  * DECLARED-ONLY  declared in package.json but not vendored         (unused, or loaded elsewhere)

VENDORED-ONLY is informational, not a fault: several directories here are vendored
deliberately without an npm entry. DECLARED-ONLY is likewise expected for packages
consumed some other way. Only DRIFT is a finding.

Modes
-----
Default is REPORT-ONLY: it prints the findings and always exits 0, matching the jar
checker so this can be wired into CI without failing any build while the two known
drifts are reconciled. Pass ``--strict`` (or set STRICT=1) to exit 1 on any DRIFT
not in ALLOWLIST -- flip CI to that once the count is zero, or the check becomes
the theatre the provenance script warns about.

This is a read-only reporter. It changes no files.

Usage: check-js-dependency-drift.py [repo-root] [--strict]
"""
from __future__ import annotations

import json
import os
import re
import sys

# Vendored directory name -> npm package name, where the two differ.
#
# This map is load-bearing, not cosmetic. Without it a renamed directory simply
# fails to match and lands in VENDORED-ONLY, which reads as "vendored deliberately,
# nothing to see" -- so a real drift hides in the one bucket nobody reads. That is
# not hypothetical either: prism-1.29.0 against prismjs 1.30.0 sat unnoticed in
# exactly that gap until these aliases were added.
#
# Keep it exhaustive. An unmapped rename is a silent hole in this check.
ALIASES: dict[str, str] = {
    "ace": "ace-builds",
    "chartjs": "chart.js",
    "foundation": "foundation-sites",
    "jspreadsheet": "jspreadsheet-ce",
    "masonry": "masonry-layout",
    "prism": "prismjs",
    "spectrum": "spectrum-colorpicker",
    "superset-embedded-sdk": "embedded-sdk",
}

# Package names whose declared-vs-vendored divergence is INTENTIONAL and must not
# fail --strict. Add entries as: "name": "reason (revisit trigger)".
#
# The genuine version drifts are deliberately NOT listed here: they are real, they
# should be reconciled by re-vendoring rather than excused, and allowlisting them
# would hide exactly what this tool was written to show.
ALLOWLIST: dict[str, str] = {
    "foundation-datepicker": (
        "the vendored directory is date-stamped (foundation-datepicker-20180424), not "
        "semver, so its version cannot be read from the name and every run reports it "
        "as drift against package.json's 1.5.6. This is a naming-convention artifact, "
        "not a version mismatch -- but note the cost: because the name carries no "
        "version, nothing here or anywhere else can tell which release actually ships. "
        "Revisit: re-vendor under a semver directory name and delete this entry."
    ),
}

JS_DIR = os.path.join("src", "main", "webapp", "javascript")

# "name-1.2.3" / "name-with-dashes-10.9.6" -- split at the LAST dash before a digit
_DIR_RE = re.compile(r"^(.*?)-(\d[\w.\-]*)$")


def parse_declared(package_json: str) -> dict[str, str]:
    """Return {name: version} for every runtime dependency in package.json.

    Scoped names (@scope/pkg) are keyed on the bare package name, because that is
    what the vendored directory is named after.
    """
    with open(package_json, encoding="utf-8") as fh:
        data = json.load(fh)
    out: dict[str, str] = {}
    for name, spec in (data.get("dependencies") or {}).items():
        # strip any range prefix; these are pinned exactly today, but do not assume it
        out[name.split("/")[-1].lower()] = str(spec).lstrip("^~>=< ")
    return out


def parse_vendored(js_dir: str) -> dict[str, tuple[str, str]]:
    """Return {name: (version, dirname)} for every versioned directory under javascript/."""
    out: dict[str, tuple[str, str]] = {}
    if not os.path.isdir(js_dir):
        return out
    for entry in sorted(os.listdir(js_dir)):
        if not os.path.isdir(os.path.join(js_dir, entry)):
            continue
        m = _DIR_RE.match(entry)
        if m:
            name = m.group(1).lower()
            out[ALIASES.get(name, name)] = (m.group(2), entry)
    return out


def main() -> int:
    repo = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("-") else "."
    strict = "--strict" in sys.argv or os.environ.get("STRICT") == "1"

    declared = parse_declared(os.path.join(repo, "package.json"))
    vendored = parse_vendored(os.path.join(repo, JS_DIR))

    drift, vendored_only, ok = [], [], []
    for name, (ver, dirname) in sorted(vendored.items()):
        if name in declared:
            (ok if declared[name] == ver else drift).append((name, declared[name], ver, dirname))
        else:
            vendored_only.append((name, ver, dirname))

    declared_only = [(n, v) for n, v in sorted(declared.items()) if n not in vendored]

    lines = []
    lines.append(f"JavaScript drift report  (package.json vs vendored {JS_DIR}, "
                 f"{len(vendored)} directories)")
    lines.append("=" * 72)
    lines.append("")
    lines.append(f"DRIFT -- package.json ahead of / behind the shipped copy ({len(drift)}):")
    if drift:
        w = max(len(n) for n, *_ in drift)
        for n, dv, vv, _ in drift:
            flag = "  [allowlisted]" if n in ALLOWLIST else ""
            lines.append(f"  {n.ljust(w)}  package.json {dv:<12} WAR ships {vv}{flag}")
    else:
        lines.append("  (none)")
    lines.append("")
    lines.append(f"VENDORED-ONLY -- shipped but not declared ({len(vendored_only)}):")
    lines.extend(f"  {n} {v}" for n, v, _ in vendored_only) if vendored_only else lines.append("  (none)")
    lines.append("")
    lines.append(f"DECLARED-ONLY -- declared but not vendored ({len(declared_only)}):")
    lines.extend(f"  {n} {v}" for n, v in declared_only) if declared_only else lines.append("  (none)")
    lines.append("")
    blocking = [n for n, *_ in drift if n not in ALLOWLIST]
    lines.append(f"Summary: {len(drift)} drifted, {len(blocking)} not allowlisted, "
                 f"{len(vendored_only)} vendored-only, {len(declared_only)} declared-only.")
    report = "\n".join(lines)
    print(report)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as fh:
            fh.write("## JavaScript drift (package.json vs vendored WAR assets)\n\n")
            fh.write(f"**{len(drift)} of {len(drift) + len(ok)} matched libraries drift.** "
                     "Report-only — this check does not fail the build.\n\n")
            if drift:
                fh.write("| Library | package.json declares | WAR ships |\n|---|---|---|\n")
                for n, dv, vv, _ in drift:
                    fh.write(f"| `{n}` | {dv} | **{vv}** |\n")
                fh.write("\nA drift here means Dependabot is reasoning about a version "
                         "the site does not serve.\n")

    if strict and blocking:
        print(f"\nFAIL (--strict): {len(blocking)} un-allowlisted drift(s).", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
