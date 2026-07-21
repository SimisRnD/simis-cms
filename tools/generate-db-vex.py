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
    python3 tools/generate-db-vex.py > docker/db/vex/simis-cms-db.openvex.json
"""

import datetime
import json
import re
import subprocess
import sys
from collections import OrderedDict

REPO = "SimisRnD/simis-cms"
PRODUCT_PURL = "pkg:oci/simis-cms-db?repository_url=ghcr.io/simisrnd"
VEX_ID = "https://github.com/SimisRnD/simis-cms/docker/db/vex/simis-cms-db"
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


def main():
    alerts = fetch_alerts()
    # vulnerability -> {package: version}
    grouped = OrderedDict()
    for a in alerts:
        msg = a.get("most_recent_instance", {}).get("message", {}).get("text", "")
        pkg, ver, fixed = field(msg, "Package"), field(msg, "Installed Version"), field(msg, "Fixed Version")
        if fixed:
            continue  # a fix exists -> fix it, never VEX it
        cve = a["rule"]["id"]
        grouped.setdefault(cve, {})[pkg] = ver

    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()
    statements = []
    for cve, pkgs in sorted(grouped.items()):
        # A statement's justification must hold for every affected package in it.
        decisions = set()
        reasons = []
        for pkg in pkgs:
            if cve in CVE_POLICY:
                st, why = CVE_POLICY[cve]
            elif pkg in PACKAGE_POLICY:
                st, why = PACKAGE_POLICY[pkg]
            else:
                st, why = None, UNDER_INVESTIGATION_NOTE
            decisions.add(st)
            if why not in reasons:
                reasons.append(why)

        subcomponents = [
            {"@id": "pkg:deb/debian/%s@%s?distro=debian-12" % (p, v)} for p, v in sorted(pkgs.items())
        ]
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
    print(json.dumps(doc, indent=2))

    n_na = sum(1 for s in statements if s["status"] == "not_affected")
    print("generated %d statements: %d not_affected, %d under_investigation"
          % (len(statements), n_na, len(statements) - n_na), file=sys.stderr)


if __name__ == "__main__":
    main()
