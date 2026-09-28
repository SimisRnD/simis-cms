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
For every vendored jar it finds the matching pom dependency (by artifactId, and by the
groupId the jar records in its META-INF/maven metadata wherever two groups share an
artifactId -- jackson 2.x and 3.x both ship jackson-core) and compares versions:

  * DRIFT         pom and vendored jar declare different versions  (the thing we care about)
  * AMBIGUOUS     the jar shares its artifactId and records no groupId, so it cannot be matched
  * VENDORED_ONLY jar is vendored but has no top-level pom dependency (transitive/manual)
  * POM_ONLY      pom declares a shippable dependency with no vendored jar

Modes
-----
Default is REPORT-ONLY: it prints the findings and always exits 0, so it can be
wired into CI without failing any build. Pass ``--strict`` (or set STRICT=1) to
exit 1 when any DRIFT is found that is not in ALLOWLIST, or any AMBIGUOUS jar -- flip to this once the
one-time reconciliation has zeroed the drift.

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import os
import re
import sys
import glob
from collections import Counter
import zipfile
import xml.etree.ElementTree as ET

# artifactIds whose pom-vs-vendored divergence is INTENTIONAL and must not fail
# --strict. Empty today because every current pin happens to match. Add entries
# as: "artifactId": "reason (revisit trigger)".
ALLOWLIST: dict[str, str] = {
    "flexmark-all": (
        "vendored 0.64.0-lib is the FAT/uber jar that bundles the flexmark.ext.* modules; "
        "the pom's plain flexmark-all 0.64.8 is a thin aggregate, so a straight version swap "
        "drops those modules and breaks the compile. "
        "Revisit: a matching 0.64.x '-lib' fat jar, or a proper per-module re-vendor."
    ),
    "jackson-coreutils": (
        "abandoned com.github.fge lib held at 1.0; 1.8 breaks JsonLoader.fromString "
        "(FormDataJSONCommandTest + ecommerce JsonTest). "
        "Revisit: replace the fge dependency, or correct the pom back to 1.0."
    ),
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


def _properties(root) -> dict[str, str]:
    props = {}
    pe = root.find(POM_NS + "properties")
    if pe is not None:
        for child in pe:
            tag = child.tag[len(POM_NS):] if child.tag.startswith(POM_NS) else child.tag
            props[tag] = (child.text or "").strip()
    return props


def pom_dependencies(pom_path: str):
    """Every declared dependency, one dict per distinct (groupId, artifactId).

    Each is {'group', 'artifact', 'version', 'scope', 'system'}; a later declaration of the same
    coordinates replaces an earlier one. Unlike parse_pom(), two groups that publish the same
    artifactId -- jackson 2.x (com.fasterxml.jackson.*) and 3.x (tools.jackson.*) both ship
    jackson-core and jackson-databind -- stay two entries.
    """
    root = ET.parse(pom_path).getroot()
    props = _properties(root)
    deps = {}
    for dep in root.iter(POM_NS + "dependency"):
        artifact = _text(dep, "artifactId")
        version = _text(dep, "version")
        if not artifact or not version:
            continue
        group = _text(dep, "groupId")
        group = _resolve(group, props) if group else None
        deps[(group, artifact)] = {
            "group": group,
            "artifact": artifact,
            "version": _resolve(version, props),
            "scope": _text(dep, "scope") or "compile",
            "system": _text(dep, "systemPath") is not None,
        }
    return list(deps.values())


def parse_pom(pom_path: str):
    """Return {artifactId: {'group', 'version', 'scope', 'system'}} for declared deps.

    Keyed by artifactId alone, so where two groups share one the last declaration wins. Kept for
    callers that only need a lookup; the drift check itself uses pom_dependencies().
    """
    return {d["artifact"]: {k: d[k] for k in ("group", "version", "scope", "system")}
            for d in pom_dependencies(pom_path)}


def jar_group(path: str, artifact: str):
    """The groupId a jar records for itself, or None.

    Maven-built jars embed META-INF/maven/<groupId>/<artifactId>/pom.properties. Only the entry
    for the jar's own artifactId counts (a shaded jar carries other artifacts' entries too), and
    only when exactly one group claims it.
    """
    try:
        with zipfile.ZipFile(path) as z:
            groups = {name.split("/")[2] for name in z.namelist()
                      if name.startswith("META-INF/maven/") and name.count("/") == 4
                      and name.endswith(f"/{artifact}/pom.properties")}
    except (zipfile.BadZipFile, OSError):
        return None
    return groups.pop() if len(groups) == 1 else None


def vendored_jars(lib_dir: str):
    """One dict per jar under lib/build: {'artifact', 'version', 'path', 'group'}.

    artifact and version come from the file name; group from the jar's own metadata (see
    jar_group()), or None. tools/vendor-sync.py uses the path to replace a jar in place.
    """
    out = []
    for path in sorted(glob.glob(os.path.join(lib_dir, "**", "*.jar"), recursive=True)):
        base = os.path.basename(path)[:-4]
        m = _JAR_RE.match(base)
        if m:
            out.append({"artifact": m.group(1), "version": m.group(2), "path": path,
                        "group": jar_group(path, m.group(1))})
    return out


def parse_vendored(lib_dir: str):
    """Return {artifactId: (version, filename)} for every jar under lib/build."""
    return {j["artifact"]: (j["version"], os.path.basename(j["path"]))
            for j in vendored_jars(lib_dir)}


def find_drift(deps, jars):
    """Pair each vendored jar with its pom declaration and classify the result.

    Returns a dict of lists:
      drift, ok      (dep, jar) pairs whose versions differ / agree
      vendored_only  jars with no declaration
      pom_only       declarations with no jar (all scopes; callers filter)
      ambiguous      (jar, reason) where the jar cannot be told apart from another

    Pairing is by artifactId, exactly as before, whenever that is unambiguous: one jar and at
    most one declaration. Only where an artifactId is shared -- by two declarations or two jars
    -- is the jar's own groupId used to tell them apart, and a colliding jar that records no
    group is AMBIGUOUS rather than guessed at. Keying everything by artifactId alone let the
    later of jackson-core 2.x/3.x shadow the other, so a bump to the shadowed line was never
    compared at all. A lone jar keeps pairing by artifactId even when its recorded group
    differs from the pom's, so a relocated groupId cannot turn a real drift into VENDORED-ONLY.

    ALLOWLIST is not applied here; callers decide what an allowlisted drift means for them.
    tools/vendor-sync.py calls this too, so the tool that re-vendors and the gate that checks
    it agree on what "drifted" means.
    """
    result = {"drift": [], "ok": [], "vendored_only": [], "pom_only": [], "ambiguous": []}
    deps_by_artifact, jars_by_artifact = {}, {}
    for d in deps:
        deps_by_artifact.setdefault(d["artifact"], []).append(d)
    for j in jars:
        jars_by_artifact.setdefault(j["artifact"], []).append(j)

    paired = set()  # id() of every declaration a jar was matched to
    for artifact in sorted(jars_by_artifact):
        js, ds = jars_by_artifact[artifact], deps_by_artifact.get(artifact, [])
        if len(js) == 1 and len(ds) <= 1:
            if ds:
                pairs = [(ds[0], js[0])]
            else:
                result["vendored_only"].append(js[0])
                continue
        else:
            pairs, claimed = [], {}
            for j in js:
                if j["group"] is None:
                    result["ambiguous"].append(
                        (j, f"{len(js)} jar(s) and {len(ds)} declaration(s) share artifactId "
                            f"{artifact}, and this jar records no groupId"))
                    continue
                match = [d for d in ds if d["group"] == j["group"]]
                if not match:
                    result["vendored_only"].append(j)
                    continue
                if id(match[0]) in claimed:
                    other = claimed[id(match[0])]
                    result["ambiguous"].append(
                        (j, f"{j['group']}:{artifact} is vendored twice (also as "
                            f"{os.path.basename(other['path'])})"))
                    continue
                claimed[id(match[0])] = j
                pairs.append((match[0], j))
        for d, j in pairs:
            paired.add(id(d))
            result["ok" if d["version"] == j["version"] else "drift"].append((d, j))
    result["pom_only"] = [d for d in deps if id(d) not in paired]
    return result


def _label(d, shared) -> str:
    """artifactId, qualified by groupId where two groups share it."""
    return f"{d['artifact']} ({d['group']})" if d["artifact"] in shared else d["artifact"]


def main() -> int:
    repo = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("-") else "."
    strict = "--strict" in sys.argv or os.environ.get("STRICT") == "1"

    deps = pom_dependencies(os.path.join(repo, "pom.xml"))
    jars = vendored_jars(os.path.join(repo, "lib", "build"))
    found = find_drift(deps, jars)
    drift, ok, vendored_only, ambiguous = (found["drift"], found["ok"], found["vendored_only"],
                                           found["ambiguous"])
    # pom deps that ought to ship (not test, not provided) but have no vendored jar
    pom_only = sorted((d for d in found["pom_only"]
                       if d["scope"] not in ("test", "provided") and not d["system"]),
                      key=lambda d: (d["artifact"], d["group"] or ""))
    # artifactIds carried by more than one declaration or jar get their groupId in the report
    dep_counts = Counter(d["artifact"] for d in deps)
    jar_counts = Counter(j["artifact"] for j in jars)
    shared = {a for a in dep_counts.keys() | jar_counts.keys()
              if dep_counts[a] > 1 or jar_counts[a] > 1}

    lines = []
    lines.append(f"Dependency drift report  (pom.xml vs vendored lib/build, {len(jars)} jars)")
    lines.append("=" * 72)
    lines.append("")
    lines.append(f"DRIFT -- pom ahead of / behind the shipped jar ({len(drift)}):")
    if drift:
        w = max(len(_label(d, shared)) for d, _ in drift)
        for d, j in drift:
            flag = "  [allowlisted]" if d["artifact"] in ALLOWLIST else ""
            lines.append(f"  {_label(d, shared).ljust(w)}  pom {d['version']:<16} "
                         f"WAR ships {j['version']}{flag}")
    else:
        lines.append("  (none)")
    lines.append("")
    lines.append(f"AMBIGUOUS -- jar cannot be matched to one declaration ({len(ambiguous)}):")
    if ambiguous:
        lines.extend(f"  {os.path.basename(j['path'])}: {why}" for j, why in ambiguous)
    else:
        lines.append("  (none)")
    lines.append("")
    lines.append(f"VENDORED-ONLY -- shipped but not a top-level pom dependency ({len(vendored_only)}):")
    if vendored_only:
        lines.extend(f"  {j['artifact']} {j['version']}" for j in vendored_only)
    else:
        lines.append("  (none)")
    lines.append("")
    lines.append(f"POM-ONLY -- declared shippable but no vendored jar ({len(pom_only)}):")
    if pom_only:
        lines.extend(f"  {_label(d, shared)} {d['version']}" for d in pom_only)
    else:
        lines.append("  (none)")
    lines.append("")
    blocking = [d for d, _ in drift if d["artifact"] not in ALLOWLIST]
    lines.append(f"Summary: {len(drift)} drifted, {len(blocking)} not allowlisted, "
                 f"{len(ambiguous)} ambiguous, {len(vendored_only)} vendored-only, "
                 f"{len(pom_only)} pom-only.")
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
                for d, j in drift:
                    fh.write(f"| `{_label(d, shared)}` | {d['version']} | **{j['version']}** |\n")

    if strict and (blocking or ambiguous):
        if blocking:
            print(f"\nFAIL (--strict): {len(blocking)} un-allowlisted drift(s). To re-vendor them "
                  "from Maven Central, run: python3 tools/vendor-sync.py", file=sys.stderr)
        if ambiguous:
            print(f"\nFAIL (--strict): {len(ambiguous)} vendored jar(s) could not be matched to a "
                  "single pom declaration; see AMBIGUOUS above.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
