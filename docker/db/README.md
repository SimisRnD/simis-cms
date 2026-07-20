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

### They clear over time on their own

`publish-images.yml` rebuilds and re-scans the image on a monthly schedule, so
as Debian releases fixes the image adopts them automatically and the alert count
falls with no code change.

### Re-check locally

```sh
docker build -f docker/db/Dockerfile -t simis-cms-db:check .
trivy image --scanners vuln --severity HIGH,CRITICAL simis-cms-db:check
```
