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
4. The policy tables below and the committed document are two copies of the same triage
   decisions. ADDING A STATEMENT BY HAND WITHOUT ADDING ITS REASONING HERE IS THE ONE
   EDIT THAT BREAKS THIS TOOL: the statement keeps working, so nothing complains, and the
   next regeneration quietly downgrades it to under_investigation -- which suppresses
   nothing. That is what happened by 2026-08-26, to eight CVEs at once. Both directions
   are now held together by test_policy_reproduces_every_committed_statement, which
   regenerates from the document's own contents and demands the statements back exactly.

Regenerate (keeps the document from rotting as the alert set changes):
    python3 tools/generate-db-vex.py

The script writes the document itself rather than being redirected into it. That is
deliberate. `> the-file` truncates the target before this process even starts, so a
refusal to write cannot protect a file the shell has already emptied -- and the one
failure this tool must never have is quietly replacing 50-odd suppressions with none.
Owning the write is what makes the guards in write_document() worth anything.

Exit codes: 0 = a document was written, 1 = a write was refused by the guards in
write_document(), 2 = the alert source could not be read. A generator that never
reached its input has not decided anything, and must not be mistaken for one that
looked at the alerts and refused.
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
# Used where the flaw is real and reachable in principle, but the image removes the
# precondition it needs -- a mitigation baked into the build, not an absence.
INLINE_MITIGATIONS = "inline_mitigations_already_exist"

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
#
# The split between this table and PACKAGE_POLICY is the honesty rule in structural form.
# A PACKAGE_POLICY entry generalises: it claims the reason holds for every CVE in that
# package, including ones not yet published, so it is only honest where the package as a
# whole is unreachable (the GDAL stack is never loaded, Perl never executes, the libxml2
# parser is never entered). The entries below are each about one specific function, binary
# or API inside an otherwise-live package -- infocmp, libblkid's DOS prober, mount's setuid
# transition, OpenSSL's QUIC listener, sar/sadc, pathname-based ACL calls, gzip decompressing
# init scripts, libldap's client path. Those reasons say nothing about the next CVE in the
# same package, so they are keyed by CVE and a new CVE in ncurses or util-linux still falls
# through to under_investigation, which is the correct default.
#
# Each impact_statement below is the triage that was recorded for that CVE by hand in
# docker/db/vex/simis-cms-db.openvex.json. They are transcribed, not re-derived: this table
# exists so a regeneration reproduces those decisions instead of silently downgrading them
# to under_investigation. test_policy_reproduces_every_committed_statement() holds the two
# in sync.
CVE_POLICY = {
    "CVE-2023-2953": (
        NOT_IN_PATH,
        "The server binary links libldap, but the vulnerable code runs only during LDAP client "
        "operations, and LDAP authentication is not configured in this image (SCRAM password "
        "authentication; no ldap method in the active auth configuration). Configuring LDAP "
        "authentication would void this claim.",
    ),
    "CVE-2023-33204": (
        NOT_IN_PATH,
        "sysstat's collectors (sar/sadc) are launched only by cron, and the image contains no "
        "cron daemon, so they never execute; the read-only root filesystem additionally prevents "
        "staging the crafted data files the flaw requires.",
    ),
    "CVE-2023-45853": (
        NOT_PRESENT,
        "This vulnerability is in zlib's MiniZip contrib component "
        "(zipOpenNewFileInZip4_64), which Debian does not build into the shared libz "
        "shipped in this image - which is why Debian classifies it will-not-fix. The "
        "vulnerable code is not present in the delivered library.",
    ),
    "CVE-2025-69720": (
        NOT_IN_PATH,
        "The overflow is in the infocmp diagnostic binary (progs/infocmp.c), which no service in "
        "the image ever invokes; database operation processes no terminfo input. Debian rates the "
        "issue minor (no-DSA).",
    ),
    "CVE-2026-14456": (
        NOT_PRESENT,
        "This vulnerability is in OpenSSL's built-in QUIC server listener/channel API, which "
        "allocates a new channel object for every QUIC Initial packet bearing an unrecognized "
        "connection ID with no cap on the pending queue. That API was introduced in OpenSSL 3.5.0 "
        "and affects only the 3.5.x/3.6.x/4.0.x lines per OpenSSL's own advisory; Debian "
        "bookworm's libssl3 package (3.0.20-1~deb12u2) is built from the OpenSSL 3.0.x line, "
        "which predates the QUIC server code entirely. Independently, even if the affected code "
        "were present, nothing in this image would invoke it: the only network-facing process is "
        "the postgres server binary, whose wire protocol is TCP-only and whose libssl usage is "
        "the classic TLS-over-TCP SSL_accept() path, not OpenSSL's QUIC listener API; no other "
        "component (gosu, postgis/GDAL, the stock entrypoint) opens a UDP listener or calls into "
        "OpenSSL's QUIC surface.",
    ),
    "CVE-2026-41992": (
        NOT_IN_PATH,
        "gzip executes only to decompress *.sql.gz initialization scripts on first boot; this "
        "image ships a single plain-text init.sql, and initialization content is operator-baked "
        "image content, not adversary-supplied input. Adding compressed init scripts from "
        "untrusted sources would void this claim.",
    ),
    "CVE-2026-53613": (
        INLINE_MITIGATIONS,
        "TOCTOU in util-linux's mount program. Debian bookworm has no fixed version, so the "
        "package cannot be upgraded out of the image; all nine entries are binary packages built "
        "from the one util-linux source, so this is a single defect counted nine times. "
        "Exploitation requires an unprivileged local user to invoke mount across its setuid-root "
        "privilege transition. docker/db/Dockerfile removes that transition: the build runs "
        "`chmod u-s` on /usr/bin/mount, /usr/bin/umount, /bin/mount and /bin/umount, so no "
        "privilege boundary is crossed when they execute and the race has no privilege to win. "
        "Verified on the built image -- mount and umount are mode 0755 root:root, and neither "
        "appears in the image's remaining setuid set. The binaries stay functional for a caller "
        "that is already root, which is the only caller this image has: PostgreSQL never mounts a "
        "filesystem, and the base entrypoint never invokes mount (it appears only in comments and "
        "one diagnostic message). Confirmed the mitigation is behaviour-preserving by building "
        "the image and starting it -- pg_isready accepted connections and CREATE EXTENSION "
        "postgis reported 3.6 USE_GEOS=1 USE_PROJ=1 USE_STATS=1.",
    ),
    "CVE-2026-53615": (
        NOT_IN_PATH,
        "The flaw is in libblkid's DOS partition-table prober; nothing in the container probes or "
        "mounts block devices, and with all Linux capabilities dropped (CapEff 0000000000000000, "
        "PR #230) the container cannot perform mount or device-probe operations at all.",
    ),
    "CVE-2026-54369": (
        NOT_IN_PATH,
        "Exploitation requires a privileged process performing pathname-based ACL operations on "
        "attacker-influenced paths; no process in this single-user container manipulates POSIX "
        "ACLs, and the root filesystem is read-only.",
    ),
    "CVE-2026-57433": (
        NOT_IN_PATH,
        "Perl is present only as package-management dependency; no runtime component invokes it, "
        "the image contains no plperl library (so PL/Perl cannot be enabled), and nothing "
        "deserializes Storable blobs. The flaw is a deserialization panic (denial of service) "
        "even where reachable. Consistent with the six existing perl statements.",
    ),
    "CVE-2026-73515": (
        NOT_IN_PATH,
        "This vulnerability is in PostGIS's native ST_FromFlatGeobuf()/ST_AsFlatGeobuf() "
        "functions (memory disclosure and DoS via a malformed FlatGeobuf buffer passed to them), "
        "not the GDAL/OGR FlatGeobuf driver -- it affects the postgis and "
        "postgresql-*-postgis-3(-scripts) packages directly, not the GDAL dependency chain "
        "covered above. The application never calls either function: a full-repository search for "
        "FlatGeobuf/ST_FromFlatGeobuf/ST_AsFlatGeobuf finds zero references in src/. The schema "
        "does use PostGIS geometry/geography columns (world_cities, item locations), but nothing "
        "in the codebase serializes or parses FlatGeobuf, so the vulnerable code path is never "
        "reached by anything this application does.",
    ),
}

# CVE-specific evidence layered ON TOP of a package rule, rather than replacing it.
#
# These are the statements where the recorded triage is the package rationale plus a
# sentence or two naming what the individual flaw needs and why that is still unreachable.
# They are kept here rather than in CVE_POLICY on purpose: a CVE_POLICY entry answers for
# every package attached to the CVE, which would bypass the per-package check below, so a
# newly affected package outside GDAL_CHAIN would silently inherit a GDAL rationale. Via
# this table the package rule is still consulted for each package, and an uncovered one
# still forces the whole statement to under_investigation.
CVE_ADDENDUM = {
    "CVE-2026-11822": (
        "Reaching this flaw requires SQLite itself to parse attacker-supplied database content -- "
        "crafted FTS5 full-text index data for CVE-2026-11822, and the heap-overflow path on the "
        "same parsing surface for CVE-2026-11824. Nothing in this image opens a SQLite database: "
        "libsqlite3 arrives only under GDAL's SQLite/GeoPackage driver, reachable solely through "
        "postgis_raster. PostgreSQL's own full-text search (tsvector/tsquery) is unrelated code "
        "and does not use SQLite, so enabling it does not change this analysis. Consistent with "
        "the existing CVE-2025-7458 statement for this same package."
    ),
    "CVE-2026-11824": (
        "Reaching this flaw requires SQLite itself to parse attacker-supplied database content -- "
        "crafted FTS5 full-text index data for CVE-2026-11822, and the heap-overflow path on the "
        "same parsing surface for CVE-2026-11824. Nothing in this image opens a SQLite database: "
        "libsqlite3 arrives only under GDAL's SQLite/GeoPackage driver, reachable solely through "
        "postgis_raster. PostgreSQL's own full-text search (tsvector/tsquery) is unrelated code "
        "and does not use SQLite, so enabling it does not change this analysis. Consistent with "
        "the existing CVE-2025-7458 statement for this same package."
    ),
    "CVE-2026-26197": (
        "The CVE's out-of-bounds read requires opening a corrupted HDF5 file through libhdf5's "
        "own reader, which is only reachable via GDAL's HDF5 driver -- never loaded here, "
        "consistent with the existing CVE-2018-11205 statement for these same two packages."
    ),
    "CVE-2026-52490": (
        "The CVE is attributed to process_command_opts(), an option-handling routine belonging to "
        "libtiff's command-line utilities; this image installs the libtiff6 shared library as a "
        "GDAL dependency and ships no TIFF tooling to invoke it. GDAL's own TIFF reader is "
        "likewise reachable only through postgis_raster. Consistent with the existing "
        "CVE-2023-52355, CVE-2026-12912 and CVE-2026-36849 statements for this same package."
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


def fail(message):
    """Exit 2: the alert source could not be read.

    Distinct from exit 1 (a deliberate refusal to write) on purpose -- an unreachable
    input is a broken run, not a triage decision about the document.
    """
    print("error: " + message, file=sys.stderr)
    sys.exit(2)


def fetch_alerts():
    """Open Trivy alerts for the db image, from GitHub code scanning."""
    out = subprocess.run(
        ["gh", "api", "repos/%s/code-scanning/alerts?state=open&per_page=100" % REPO, "--paginate"],
        capture_output=True, text=True,
    )
    if out.returncode != 0:
        fail("gh api failed: %s" % out.stderr.strip())
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
                if cve in CVE_ADDENDUM:
                    why = "%s %s" % (why, CVE_ADDENDUM[cve])
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
