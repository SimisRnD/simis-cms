#!/usr/bin/env python3
"""Generate an OpenVEX document for the simis-cms-db container image.

WHY THIS EXISTS
---------------
Every HIGH/CRITICAL Trivy finding on `simis-cms-db` is a Debian bookworm OS package
with NO fixed version available -- there is nothing to upgrade to (see docker/db/README.md).
A customer scanning the published image therefore sees a wall of red with no context.
VEX (Vulnerability Exploitability eXchange) is the machine-readable way to say
"present, but not exploitable here, and here is why" so their scanner can suppress it
honestly rather than us asking them to read a README.

HONESTY RULES (the whole point -- a VEX that overclaims is worse than no VEX)
----------------------------------------------------------------------------
1. `not_affected` is asserted ONLY where there is a concrete, checkable reason, and every
   claim carries an impact_statement saying what was verified.
2. Anything not covered by the policy below falls through to `under_investigation`.
   Silence/uncertainty is never rendered as "not affected".
3. Claims are scoped to the image AS SHIPPED AND CONFIGURED. If an operator enables
   PL/Perl, creates postgis_raster, or uses PostgreSQL's xml type, the corresponding
   statements no longer hold -- this is stated in the document itself.

Regenerate (keeps the document from rotting as the alert set changes):
    python3 tools/generate-db-vex.py

The script writes the document itself rather than being redirected into it. That is
deliberate. `> the-file` truncates the target before this process even starts, so a
refusal to write cannot protect a file the shell has already emptied -- and the one
failure this tool must never have is quietly replacing 50-odd suppressions with none.
Owning the write is what makes the guards in write_document() worth anything.
"""

import argparse
import datetime
import json
import os
import re
import subprocess
import sys
import tempfile
from collections import OrderedDict

REPO = "SimisRnD/simis-cms"
# Bare, deliberately. Trivy matches VEX identifiers by PURL, and any qualifier present in
# the statement must also match what it scanned -- a mismatch is skipped in silence, with
# no warning, and the CVE simply stays unsuppressed. See package_purl() below for the
# whole story; the product identifier carried `?repository_url=ghcr.io/simisrnd` and was
# stripped by hand in 4a2bde1e, the commit that first made the gate enforce.
PRODUCT_PURL = "pkg:oci/simis-cms-db"
VEX_ID = "https://github.com/SimisRnD/simis-cms/docker/db/vex/simis-cms-db"
DEFAULT_OUTPUT = "docker/db/vex/simis-cms-db.openvex.json"
AUTHOR = "SimIS Inc. (SimIS CMS maintainers)"

NOT_IN_PATH = "vulnerable_code_not_in_execute_path"
NOT_PRESENT = "vulnerable_code_not_present"

# --- Justification policy -----------------------------------------------------------
# Each entry: reason string shown as the statement's impact_statement.
# Verified against upstream/main: the schema issues only `CREATE EXTENSION postgis`
# (vector); postgis_raster is never created; postgresql-plperl is not installed by
# docker/db/Dockerfile; the schema declares no xml columns and the app calls no
# XML/xpath functions.

GDAL_CHAIN = {
    "libgdal32", "gdal-plugins", "gdal-data", "libaom3", "libheif1", "libde265-0",
    "libtiff6", "libhdf5-103-1", "libhdf5-hl-100", "libsqlite3-0", "libcurl4",
    "libcurl3-gnutls", "libssh2-1", "libexpat1",
}
GDAL_REASON = (
    "Present only as a transitive dependency of the PostGIS package's GDAL stack. The "
    "database enables vector PostGIS only (`CREATE EXTENSION postgis`); postgis_raster is "
    "never created, so GDAL's raster/image decoders, its remote-dataset (curl/ssh2) drivers "
    "and its embedded SQLite/HDF5/XML readers are never loaded or reachable from SQL."
)

PERL_PKGS = {"perl", "perl-base", "perl-modules-5.36", "libperl5.36"}
PERL_REASON = (
    "Perl is present as a Debian base/tooling dependency but is never executed at runtime: "
    "the image does not install postgresql-17-plperl, so the database engine cannot invoke "
    "Perl, and the container entrypoint is the stock postgres shell entrypoint, not a Perl "
    "script. No process in the running container executes these modules."
)

LIBXML2_REASON = (
    "PostgreSQL only enters libxml2 through the `xml` data type and the xpath()/xmltable() "
    "family. The shipped schema declares no xml columns and the application issues no XML "
    "functions, so the parser is never invoked. (GDAL's XML drivers are likewise unreachable "
    "- see the PostGIS/GDAL rationale.)"
)

# CVE-specific overrides take precedence over package rules.
CVE_POLICY = {
    "CVE-2023-45853": (
        NOT_PRESENT,
        "This vulnerability is in zlib's MiniZip contrib component "
        "(zipOpenNewFileInZip4_64), which Debian does not build into the shared libz "
        "shipped in this image - which is why Debian classifies it will-not-fix. The "
        "vulnerable code is not present in the delivered library.",
    ),
    "CVE-2026-73515": (
        NOT_IN_PATH,
        "This vulnerability is in PostGIS's native ST_FromFlatGeobuf()/ST_AsFlatGeobuf() "
        "functions (memory disclosure and DoS via a malformed FlatGeobuf buffer passed to "
        "them), not the GDAL/OGR FlatGeobuf driver - it affects the postgis and "
        "postgresql-*-postgis-3(-scripts) packages directly. The application never calls "
        "either function: a full-repository search for FlatGeobuf/ST_FromFlatGeobuf/"
        "ST_AsFlatGeobuf finds zero references in src/. The schema does use PostGIS "
        "geometry/geography columns (world_cities, item locations), but nothing in the "
        "codebase serializes or parses FlatGeobuf, so the vulnerable code path is never "
        "reached by anything this application does.",
    ),
}

PACKAGE_POLICY = {}
for _p in GDAL_CHAIN:
    PACKAGE_POLICY[_p] = (NOT_IN_PATH, GDAL_REASON)
for _p in PERL_PKGS:
    PACKAGE_POLICY[_p] = (NOT_IN_PATH, PERL_REASON)
PACKAGE_POLICY["libxml2"] = (NOT_IN_PATH, LIBXML2_REASON)

UNDER_INVESTIGATION_NOTE = (
    "General-purpose OS package. Not yet individually analysed for reachability in this "
    "image; no exploitability claim is made. Tracked for the next review."
)


def fetch_alerts():
    """Open Trivy alerts for the db image, from GitHub code scanning."""
    out = subprocess.run(
        ["gh", "api", "repos/%s/code-scanning/alerts?state=open&per_page=100" % REPO, "--paginate"],
        capture_output=True, text=True,
    )
    if out.returncode != 0:
        sys.exit("gh api failed: %s" % out.stderr.strip())
    alerts = []
    for chunk in re.findall(r"\[.*?\](?=\s*\[|\s*$)", out.stdout, re.S) or [out.stdout]:
        try:
            alerts.extend(json.loads(chunk))
        except json.JSONDecodeError:
            pass
    return [a for a in alerts if a.get("tool", {}).get("name") == "Trivy"]


def field(msg, key):
    m = re.search(key + r":[ \t]*([^\n]*)", msg)
    return m.group(1).strip() if m else ""


def package_purl(name):
    """The identifier Trivy will actually match against a scanned Debian package.

    Bare on purpose: no version, no `distro=` qualifier. Trivy matches VEX subcomponents by
    PURL, and any qualifier present in the statement must also match the scanned package.
    A mismatch is not an error and not a warning -- the statement is skipped in silence and
    the CVE stays unsuppressed, so the failure looks exactly like a VEX that was never
    passed at all.

    This generator used to emit `pkg:deb/debian/<pkg>@<version>?distro=debian-12`. The image
    is Debian 12.15, so `distro=debian-12` never matched, and pinning the version meant the
    statement also expired the moment the package was rebuilt. Every statement in the
    working document uses the bare form, because both qualifiers were already removed by
    hand once the gate proved they did not match -- the subcomponent in 52718205 ("so Trivy
    actually applies it") and the product identifier in 4a2bde1e. The generator was never
    brought in line, so regenerating would have re-introduced both at once and produced a
    document that suppresses nothing while looking entirely correct.
    """
    return "pkg:deb/debian/%s" % name


def existing_statement_count(path):
    """How many statements the document at `path` already has; None if unreadable."""
    try:
        with open(path, encoding="utf-8") as fh:
            return len(json.load(fh).get("statements", []))
    except (OSError, ValueError):
        return None


def write_document(doc, path, allow_shrink=False):
    """Write `doc` to `path`, refusing the two writes that destroy the suppression set.

    This tool's failure mode is uniquely bad: its input is a remote alert list, and an
    empty input produces a structurally valid document that asserts nothing. Committed,
    that silently drops every not_affected statement the image scan gate depends on, and
    the run reports success while doing it. Both guards exist because that is not
    hypothetical -- it is what this tool did on 2026-08-26, once every code-scanning alert
    had been dismissed and fetch_alerts() began returning an empty list (issue #1463).

    Returns the number of statements written. Raises SystemExit on refusal.
    """
    new_count = len(doc.get("statements", []))
    old_count = existing_statement_count(path)

    if new_count == 0:
        raise SystemExit(
            "refusing to write an empty VEX document to %s.\n"
            "No statements were generated, which almost always means the alert source is\n"
            "empty rather than that nothing is vulnerable -- check that open Trivy alerts\n"
            "exist in code scanning before trusting this result." % path
        )

    if old_count is not None and new_count < old_count and not allow_shrink:
        raise SystemExit(
            "refusing to shrink %s from %d statements to %d.\n"
            "Dropping suppressions un-suppresses findings the image scan gate currently\n"
            "clears, so this is a gate failure waiting to happen. If the reduction is\n"
            "intended, re-run with --allow-shrink and say why in the commit message."
            % (path, old_count, new_count)
        )

    # Temp file in the same directory, then rename: an interrupted or failed write leaves
    # the previous document intact rather than a half-written one.
    directory = os.path.dirname(os.path.abspath(path)) or "."
    os.makedirs(directory, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=directory, suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            json.dump(doc, fh, indent=2)
            fh.write("\n")
        os.replace(tmp, path)
    except BaseException:
        if os.path.exists(tmp):
            os.unlink(tmp)
        raise
    return new_count


def parse_args(argv=None):
    ap = argparse.ArgumentParser(description="Generate the simis-cms-db OpenVEX document.")
    ap.add_argument("--output", default=DEFAULT_OUTPUT,
                    help="where to write the document (default: %(default)s)")
    ap.add_argument("--allow-shrink", action="store_true",
                    help="permit writing fewer statements than the existing document has. "
                         "Losing suppressions un-suppresses findings the scan gate clears, "
                         "so this needs a reason in the commit message.")
    return ap.parse_args(argv)


def main():
    args = parse_args()
    alerts = fetch_alerts()
    # vulnerability -> {affected package names}. Versions are deliberately not carried:
    # statements identify packages by bare PURL, so a version here would be collected and
    # then dropped. See package_purl().
    grouped = OrderedDict()
    for a in alerts:
        msg = a.get("most_recent_instance", {}).get("message", {}).get("text", "")
        pkg, fixed = field(msg, "Package"), field(msg, "Fixed Version")
        if fixed:
            continue  # a fix exists -> fix it, never VEX it
        cve = a["rule"]["id"]
        grouped.setdefault(cve, set()).add(pkg)

    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()
    statements = []
    for cve, pkgs in sorted(grouped.items()):
        # A statement's justification must hold for every affected package in it.
        decisions = set()
        reasons = []
        # sorted(), not raw iteration order: `reasons` is joined into the impact_statement,
        # and set iteration order varies between processes, which would make every
        # regeneration produce a spurious diff.
        for pkg in sorted(pkgs):
            if cve in CVE_POLICY:
                st, why = CVE_POLICY[cve]
            elif pkg in PACKAGE_POLICY:
                st, why = PACKAGE_POLICY[pkg]
            else:
                st, why = None, UNDER_INVESTIGATION_NOTE
            decisions.add(st)
            if why not in reasons:
                reasons.append(why)

        subcomponents = [{"@id": package_purl(p)} for p in sorted(pkgs)]
        stmt = {
            "vulnerability": {"name": cve},
            "products": [{"@id": PRODUCT_PURL, "subcomponents": subcomponents}],
        }
        # Only claim not_affected when EVERY affected package in this CVE is justified.
        if None in decisions or len(decisions) != 1:
            stmt["status"] = "under_investigation"
            stmt["impact_statement"] = UNDER_INVESTIGATION_NOTE
        else:
            stmt["status"] = "not_affected"
            stmt["justification"] = decisions.pop()
            stmt["impact_statement"] = " ".join(reasons)
        statements.append(stmt)

    doc = OrderedDict([
        ("@context", "https://openvex.dev/ns/v0.2.0"),
        ("@id", VEX_ID),
        ("author", AUTHOR),
        ("timestamp", now),
        ("version", 1),
        ("tooling", "tools/generate-db-vex.py"),
        ("statements", statements),
    ])
    written = write_document(doc, args.output, allow_shrink=args.allow_shrink)

    n_na = sum(1 for s in statements if s["status"] == "not_affected")
    print("wrote %d statements to %s: %d not_affected, %d under_investigation"
          % (written, args.output, n_na, written - n_na), file=sys.stderr)


if __name__ == "__main__":
    main()
