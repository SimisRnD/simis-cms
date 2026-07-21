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

Everything else — a handful of general-purpose OS utilities (ncurses, gzip, util-linux,
libldap, sysstat, libacl1) — is marked **`under_investigation`**, not `not_affected`. We make
no exploitability claim we cannot support; uncertainty is never rendered as "safe".

> **Scope.** These statements describe the image **as shipped and configured**. If an operator
> installs PL/Perl, creates `postgis_raster`, or uses PostgreSQL's `xml` type, the corresponding
> statements no longer hold and the VEX should be re-evaluated.

Regenerate after any rebuild changes the finding set (this keeps the document from going
stale, and it only ever emits statements for findings with **no** available fix):

```sh
python3 tools/generate-db-vex.py > docker/db/vex/simis-cms-db.openvex.json
```

### They clear over time on their own

`publish-images.yml` rebuilds and re-scans the image on a monthly schedule, so
as Debian releases fixes the image adopts them automatically and the alert count
falls with no code change.

### Re-check locally

```sh
docker build -f docker/db/Dockerfile -t simis-cms-db:check .
trivy image --scanners vuln --severity HIGH,CRITICAL simis-cms-db:check
```
