# SimIS CMS database image: PostgreSQL 17 + PostGIS.
#
# Base is pinned to Debian 12 "bookworm" rather than the newer "trixie" default.
# Bookworm's packages are more fully triaged by Debian security, so the image
# ships with materially fewer open CVEs. See docker/db/README.md for the image's
# CVE posture and why the residual findings are not fixable here.
#
# Both FROM lines are pinned by DIGEST as well as tag. A tag is mutable: the
# content behind `postgres:17-bookworm` changes whenever upstream rebuilds, so a
# tag-only pin means the image can change underneath a scan result or an accepted
# risk without anything in this repo changing. Pinning the digest makes the build
# reproducible and makes an upstream change an explicit, reviewable commit --
# which is the CM-2 baseline evidence, not just hygiene.
#
# The tag is kept alongside the digest deliberately: it is what tells a human
# which upstream line this is, and Dependabot uses it to raise the update PR.
#
# Both digests are multi-arch OCI indexes (linux/amd64 + linux/arm64/v8 among
# others), so the same pin builds on CI runners and on Apple Silicon.
#
# To refresh (do this as its own reviewed commit, not silently):
#   docker buildx imagetools inspect postgres:17-bookworm --format '{{.Manifest.Digest}}'
#   docker buildx imagetools inspect golang:1.26-bookworm --format '{{.Manifest.Digest}}'

# Rebuild gosu with a current Go toolchain. The base image's gosu is built
# against an older Go and carries that Go's stdlib CVEs; a fresh static build
# clears them without changing gosu's behavior.
FROM golang:1.27-bookworm@sha256:69a7b9788769bec032d238959b61854e9ae87f57be9029ec04e9885fabf99195 AS gosu
ENV CGO_ENABLED=0
RUN go install github.com/tianon/gosu@1.17

# Pull official base image (digest-pinned -- see the header note)
# REVERTED from Alpine: while Alpine offers 67% smaller base (~50MB vs ~150MB),
# PostGIS extension availability/compatibility on Alpine is not straightforward.
# Debian bookworm is proven, maintainable, and has well-packaged PostGIS.
# Alpine can be revisited when either (a) we have more time to resolve PostGIS
# packaging, or (b) a dedicated hardened Alpine postgres+postgis image is available.
FROM postgres:17-bookworm@sha256:639ab7ceb90e13123085b741fb31ef493fba25463002f6da665352e7b534b652

# Replace the base image's gosu with the freshly built, patched-stdlib binary.
COPY --from=gosu /go/bin/gosu /usr/local/bin/gosu

# Patch base OS packages and install PostGIS, in a single layer with the apt
# cache removed to keep the image small.
#
# Only the version-specific postgresql-17-postgis-3 is installed (it depends on
# its -scripts package, which CREATE EXTENSION reads). The unversioned `postgis`
# package is deliberately left out: it holds only client-side loaders
# (shp2pgsql, pgsql2shp, raster2pgsql, pgtopo_*, postgis_restore) that nothing
# here runs, and it Recommends `postgresql-postgis`, a PGDG metapackage that
# tracks PGDG's default major rather than ours. Once that became 18, installing
# `postgis` pulled in a complete, never-started PostgreSQL 18 server
# (postgresql-18, postgresql-18-jit with LLVM, postgresql-18-postgis-3) plus
# systemd, dbus and sysstat -- 29 packages of size and CVE surface.
RUN apt-get update \
    && apt-get -y upgrade \
    && apt-get install -y postgresql-17-postgis-3 \
    && rm -rf /var/lib/apt/lists/* \
    # Fail the build if any PostgreSQL major besides 17 is installed, so a
    # dependency change upstream cannot bring a second server back silently.
    && { [ "$(ls /usr/lib/postgresql)" = "17" ] \
         || { echo "expected only PostgreSQL 17 in /usr/lib/postgresql, found: $(ls /usr/lib/postgresql)" >&2; exit 1; }; } \
    # Drop the setuid bit from mount/umount. PostgreSQL never mounts a
    # filesystem, so these do not need to escalate -- and while they are setuid
    # root, any vulnerability in them (e.g. CVE-2026-53613, a TOCTOU in mount
    # with no Debian fix available) is a postgres-to-root escalation path inside
    # the container. Removing the bit removes that path; the binaries still work
    # for a caller that is already root. See docker/db/vex/simis-cms-db.openvex.json.
    && chmod u-s /usr/bin/mount /usr/bin/umount /bin/mount /bin/umount

# Run init.sql
ADD ./docker/db/init.sql /docker-entrypoint-initdb.d/

# HEALTHCHECK: verify the database is accepting connections. pg_isready returns
# 0 if the server is accepting connections, 3 if it is not (e.g., still starting).
# Used by orchestrators for readiness probes and container restarts. No specific
# user required -- just checks if the port is listening.
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=3 \
  CMD pg_isready -h localhost || exit 1
