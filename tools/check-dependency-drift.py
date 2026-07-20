#!/usr/bin/env python3
"""Report drift between pom.xml and the vendored lib/build jars that ship in the WAR.

Background
----------
The production WAR is assembled by Ant, which copies the committed
``lib/build/**/*.jar`` files straight into ``WEB-INF/lib``. Maven / pom.xml plays
no part in that artifact -- the pom builds only a jar and is used for the IDE,
Dependabot, and the SBOM. As a result a version can be bumped in the pom (and the
Dependabot PR merged) while the WAR keeps shipping the older jar. This script makes
that divergence visible so it cannot drift silently.

What it does
------------
For every vendored jar it finds the matching pom dependency (by artifactId) and
compares versions:

  * DRIFT         pom and vendored jar declare different versions  (the thing we care about)
  * VENDORED_ONLY jar is vendored but has no top-level pom dependency (transitive/manual)
  * POM_ONLY      pom declares a shippable dependency with no vendored jar

Modes
-----
Default is REPORT-ONLY: it prints the findings and always exits 0, so it can be
wired into CI without failing any build. Pass ``--strict`` (or set STRICT=1) to
exit 1 when any DRIFT is found that is not in ALLOWLIST -- flip to this once the
one-time reconciliation has zeroed the drift.

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import os
import re
import sys
import glob
import xml.etree.ElementTree as ET

# artifactIds whose pom-vs-vendored divergence is INTENTIONAL and must not fail
# --strict. Empty today because every current pin happens to match. Add entries
# as: "artifactId": "reason (revisit trigger)".
ALLOWLIST: dict[str, str] = {
    # "commons-jexl3": "held at 3.2.1; 3.3+ throws on undefined properties (WorkflowTaskTest)",
}

POM_NS = "{http://maven.apache.org/POM/4.0.0}"
_JAR_RE = re.compile(r"^(.+?)-(\d.*)$")  # split "name-version" at the first -<digit>


def _text(el, tag):
    child = el.find(POM_NS + tag)
    return child.text.strip() if child is not None and child.text else None


def _resolve(value: str, props: dict[str, str]) -> str:
    """Substitute ${prop} tokens, iterating so nested references resolve."""
    for _ in range(10):
        new = re.sub(
            r"\$\{([^}]+)\}",
            lambda m: props.get(m.group(1), m.group(0)),
            value,
        )
        if new == value:
            break
        value = new
    return value


def parse_pom(pom_path: str):
    """Return {artifactId: {'version', 'scope', 'system'}} for declared deps."""
    root = ET.parse(pom_path).getroot()
    props = {}
    pe = root.find(POM_NS + "properties")
    if pe is not None:
        for child in pe:
            tag = child.tag[len(POM_NS):] if child.tag.startswith(POM_NS) else child.tag
            props[tag] = (child.text or "").strip()

    deps = {}
    for dep in root.iter(POM_NS + "dependency"):
        artifact = _text(dep, "artifactId")
        version = _text(dep, "version")
        if not artifact or not version:
            continue
        deps[artifact] = {
            "version": _resolve(version, props),
            "scope": _text(dep, "scope") or "compile",
            "system": _text(dep, "systemPath") is not None,
        }
    return deps


def parse_vendored(lib_dir: str):
    """Return {artifactId: (version, filename)} for every jar under lib/build."""
    out = {}
    for path in sorted(glob.glob(os.path.join(lib_dir, "**", "*.jar"), recursive=True)):
        base = os.path.basename(path)[:-4]
        m = _JAR_RE.match(base)
        if m:
            out[m.group(1)] = (m.group(2), os.path.basename(path))
    return out


def main() -> int:
    repo = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("-") else "."
    strict = "--strict" in sys.argv or os.environ.get("STRICT") == "1"

    pom = parse_pom(os.path.join(repo, "pom.xml"))
    vendored = parse_vendored(os.path.join(repo, "lib", "build"))

    drift, vendored_only, ok = [], [], []
    for artifact, (jar_ver, fname) in sorted(vendored.items()):
        if artifact in pom:
            pom_ver = pom[artifact]["version"]
            (ok if pom_ver == jar_ver else drift).append((artifact, pom_ver, jar_ver, fname))
        else:
            vendored_only.append((artifact, jar_ver, fname))

    # pom deps that ought to ship (not test, not provided) but have no vendored jar
    pom_only = [
        (a, d["version"])
        for a, d in sorted(pom.items())
        if a not in vendored and d["scope"] not in ("test", "provided") and not d["system"]
    ]

    lines = []
    lines.append(f"Dependency drift report  (pom.xml vs vendored lib/build, {len(vendored)} jars)")
    lines.append("=" * 72)
    lines.append("")
    lines.append(f"DRIFT -- pom ahead of / behind the shipped jar ({len(drift)}):")
    if drift:
        w = max(len(a) for a, *_ in drift)
        for a, pv, jv, _ in drift:
            flag = "  [allowlisted]" if a in ALLOWLIST else ""
            lines.append(f"  {a.ljust(w)}  pom {pv:<16} WAR ships {jv}{flag}")
    else:
        lines.append("  (none)")
    lines.append("")
    lines.append(f"VENDORED-ONLY -- shipped but not a top-level pom dependency ({len(vendored_only)}):")
    if vendored_only:
        lines.extend(f"  {a} {v}" for a, v, _ in vendored_only)
    else:
        lines.append("  (none)")
    lines.append("")
    lines.append(f"POM-ONLY -- declared shippable but no vendored jar ({len(pom_only)}):")
    if pom_only:
        lines.extend(f"  {a} {v}" for a, v in pom_only)
    else:
        lines.append("  (none)")
    lines.append("")
    blocking = [a for a, *_ in drift if a not in ALLOWLIST]
    lines.append(f"Summary: {len(drift)} drifted, {len(blocking)} not allowlisted, "
                 f"{len(vendored_only)} vendored-only, {len(pom_only)} pom-only.")
    report = "\n".join(lines)
    print(report)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as fh:
            fh.write("## Dependency drift (pom vs vendored WAR jars)\n\n")
            fh.write(f"**{len(drift)} of {len(drift) + len(ok)} matched libraries drift.** "
                     "Report-only — this check does not fail the build.\n\n")
            if drift:
                fh.write("| Library | pom declares | WAR ships |\n|---|---|---|\n")
                for a, pv, jv, _ in drift:
                    fh.write(f"| `{a}` | {pv} | **{jv}** |\n")

    if strict and blocking:
        print(f"\nFAIL (--strict): {len(blocking)} un-allowlisted drift(s).", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
