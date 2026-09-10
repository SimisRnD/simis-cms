# SimIS CMS database image

PostgreSQL 17 with PostGIS, used by `docker-compose` and published as
`ghcr.io/simisrnd/simis-cms-db`. Built from [`Dockerfile`](Dockerfile).

## Container CVE posture

The image is scanned with Trivy on every publish
(`.github/workflows/publish-images.yml`); results appear under the repository's
**Security → Code scanning** tab, category `image-simis-cms-db`.

The Dockerfile is hardened to clear every finding that actually has a fix:

- **Base pinned to Debian 12 "bookworm"** rather than the newer trixie default.
  Bookworm's packages are more fully triaged by Debian security, so far fewer
  CVEs are outstanding (~92 vs ~138 HIGH/CRITICAL at the time of writing).
- **`gosu` is rebuilt** from source with a current Go toolchain, so its static
  binary no longer carries the base image's outdated Go-stdlib CVEs.
- **`apt-get upgrade`** applies any OS-package fixes Debian has already published.

### The remaining findings are not fixable here

After the above, the residual HIGH/CRITICAL alerts are all Debian OS-package
CVEs that Trivy reports with **no fixed version available** — status
`affected`, `fix_deferred`, or `will_not_fix`. There is nothing to upgrade to;
Debian has not shipped a fix.

They are pulled in almost entirely by **PostGIS and its GDAL dependency tree**
(gdal, libheif, libcurl, perl, the MariaDB client libraries, and so on). In this
image that surface is not reachable the way the CVEs describe: it is a
database-only container, not internet-facing, and PostgreSQL serving SQL does
not exercise those libraries. Real-world exploitability is low.

### Machine-readable VEX

The reasoning above is also published as an [OpenVEX](https://openvex.dev) document so a
scanner can act on it without a human reading this file:

    docker/db/vex/simis-cms-db.openvex.json

Use it to suppress the findings we have justified, while still surfacing anything new:

```sh
trivy image --vex docker/db/vex/simis-cms-db.openvex.json \
  --scanners vuln --severity HIGH,CRITICAL ghcr.io/simisrnd/simis-cms-db
```

**What it claims, and what it deliberately does not.** Statements are `not_affected` only
where there is a concrete, checkable reason, and each carries an `impact_statement` saying
what was verified:

- **PostGIS/GDAL dependency chain** (gdal, libaom, libheif, libtiff, libhdf5, libsqlite3,
  libcurl, libssh2, libexpat) — `vulnerable_code_not_in_execute_path`. The database enables
  **vector PostGIS only** (`CREATE EXTENSION postgis`); `postgis_raster` is never created, so
  GDAL's raster decoders and remote-dataset drivers are never loaded.
- **Perl** (perl, perl-base, perl-modules, libperl) — `vulnerable_code_not_in_execute_path`.
  `postgresql-17-plperl` is **not installed**, so the engine cannot invoke Perl, and the
  entrypoint is the stock postgres shell entrypoint.
- **libxml2** — `vulnerable_code_not_in_execute_path`. Reached only via the `xml` type and
  `xpath()`/`xmltable()`; the shipped schema declares no xml columns.
- **zlib1g / CVE-2023-45853** — `vulnerable_code_not_present`. The flaw is in zlib's MiniZip
  contrib component, which Debian does not build into the shared libz shipped here (hence
  Debian's will-not-fix).
- **postgis and postgresql-\*-postgis-3(-scripts) / CVE-2026-73515** —
  `vulnerable_code_not_in_execute_path`. The flaw is in PostGIS's own
  `ST_FromFlatGeobuf()`/`ST_AsFlatGeobuf()` functions (memory disclosure and DoS via a
  malformed FlatGeobuf buffer) — this is PostGIS's native code, not the GDAL/OGR chain covered
  above, so it needed its own entry. The application never calls either function (a
  full-repository search finds zero references), so the vulnerable parser is never invoked.
- **General-purpose OS utilities** (ncurses, gzip, util-linux, libldap, sysstat, libacl1) —
  triaged one CVE at a time, because the reasoning is per-flaw rather than per-package: the
  ncurses overflow is in the `infocmp` binary nothing here invokes; `gzip` runs only over the
  image's own plain-text `init.sql`; sysstat's collectors need a cron daemon the image does not
  have; the libldap path needs LDAP authentication, which is not configured. Two carry their own
  justification rather than `vulnerable_code_not_in_execute_path` — **CVE-2026-14456** (libssl3,
  openssl) is `vulnerable_code_not_present`, since the flaw is in OpenSSL's QUIC listener,
  introduced in 3.5.0 and absent from bookworm's 3.0.x; **CVE-2026-53613** (util-linux) is
  `inline_mitigations_already_exist`, since the Dockerfile's `chmod u-s` on `mount`/`umount`
  removes the setuid transition the TOCTOU needs.

Because those reasons are about individual flaws, they are keyed by CVE in the generator and say
nothing about the next CVE in the same package: a newly published ncurses or util-linux issue
still arrives as **`under_investigation`**, not `not_affected`. That remains the default for
anything not yet analysed. We make no exploitability claim we cannot support; uncertainty is
never rendered as "safe".

> **Scope.** These statements describe the image **as shipped and configured**. If an operator
> installs PL/Perl, creates `postgis_raster`, uses PostgreSQL's `xml` type, configures LDAP
> authentication, or adds compressed `*.sql.gz` init scripts from an untrusted source, the
> corresponding statements no longer hold and the VEX should be re-evaluated.

Regenerate after any rebuild changes the finding set (this keeps the document from going
stale, and it only ever emits statements for findings with **no** available fix):

```sh
python3 tools/generate-db-vex.py
```

The script writes the document itself rather than being redirected into it. `>` truncates
the target before the script starts, so a refusal to write could not protect a file the
shell had already emptied. It refuses to write an empty document, and refuses to reduce the
statement count without `--allow-shrink` -- losing suppressions un-suppresses findings the
image scan gate currently clears.

Statements identify the image and its packages by **bare** PURL — `pkg:oci/simis-cms-db` and
`pkg:deb/debian/<pkg>`, with no version and no `distro=` qualifier. Trivy matches VEX
identifiers by PURL, and a qualifier in the statement must also match what it scanned; a
mismatch is skipped in silence, so a statement that does not match looks exactly like a VEX
that was never passed. Both qualifiers were removed by hand once the gate proved they did
not match — the subcomponent in `52718205`, the product in `4a2bde1e` — and the generator
now emits the bare form to match.

**Verify a regeneration actually suppresses something.** A VEX that matches nothing fails
silently, so scan with it before committing it; the run below must report *fewer* findings
than the same scan with no `--vex` at all:

```sh
docker build --pull -f docker/db/Dockerfile -t simis-cms-db:check .
trivy image --scanners vuln --severity CRITICAL,HIGH --exit-code 1 \
  --vex docker/db/vex/simis-cms-db.openvex.json \
  --ignorefile docker/db/.trivyignore simis-cms-db:check
```

**The policy tables and this document are kept in sync by a test.** They are two copies of
the same triage decisions, and for a while nothing held them together: statements were added
and enriched by hand while `CVE_POLICY`/`PACKAGE_POLICY` stood still, until a regeneration
would have downgraded eight triaged CVEs to `under_investigation` — which suppresses nothing
— and dropped the recorded evidence from six more, exiting 0 either way.
`test_policy_reproduces_every_committed_statement` now regenerates from this document's own
contents and requires the statements back byte-for-byte, so a hand-edit that skips the tables
fails in CI rather than in somebody's scan. **If you add a statement by hand, add its
reasoning to the tables in the same commit.**

That test fixes the *decisions*, not the statement count, and the two are not the same thing.
A scan of the current build reports 54 of the document's 60 CVEs, so a regeneration driven from
one produces 54 statements and `--allow-shrink` will refuse the write until you say the
reduction is intended. Usually it is not. Checked on 2026-08-26, only two of the six
(`CVE-2026-55199`, `CVE-2026-55200`, libssh2) had actually gone away; the other four —
`CVE-2026-8932` (libcurl), `CVE-2026-26197` (libhdf5), `CVE-2026-56131` and `CVE-2026-56407`
(libexpat) — are **still present and still unfixed**, and merely re-rated LOW or MEDIUM, which
puts them outside the `--severity CRITICAL,HIGH` window the gate scans in. Dropping those
statements would un-suppress real findings for anyone scanning at a lower threshold, and a
re-rating back up would un-suppress them here. Statements for CVEs outside the current window
cost nothing, so keep them: before using `--allow-shrink`, scan at every severity and confirm
each dropped CVE is genuinely gone rather than merely quieter.

### They clear over time on their own

`publish-images.yml` rebuilds and re-scans the image on a monthly schedule, so
as Debian releases fixes the image adopts them automatically and the alert count
falls with no code change.

### Re-check locally

```sh
docker build -f docker/db/Dockerfile -t simis-cms-db:check .
trivy image --scanners vuln --severity HIGH,CRITICAL simis-cms-db:check
```
