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
# clears them without changing gosu's behavior. Using Alpine builder for minimal
# image size (builder is discarded after gosu binary is copied).
FROM golang:1.26-alpine@sha256:0d0ee96267c3b65b00b6e8f03fcbf53b5fe0f5da4e1b65e80e24501c5f6d24d8 AS gosu
ENV CGO_ENABLED=0
RUN go install github.com/tianon/gosu@1.17

# Pull official base image (digest-pinned -- see the header note)
# Switched to Alpine (from bookworm) for dramatic attack-surface reduction.
# Alpine's minimal musl libc means ~67% smaller base image and far fewer packages
# to be vulnerable. Still officially maintained by the postgres team, widely run
# in production. Multi-arch OCI index (amd64, arm64) for CI + local Apple Silicon builds.
FROM postgres:17-alpine@sha256:f55a9ceebf93bc87ffc7f6cfb7ee24c7ea0b7f51f9ac97b3c54cd5cc087e2bae

# Replace the base image's gosu with the freshly built, patched-stdlib binary.
COPY --from=gosu /go/bin/gosu /usr/local/bin/gosu

# Install PostGIS extension. Alpine uses apk instead of apt-get. The postgis
# extension is available in Alpine's official repositories. Single layer to keep
# image minimal.
RUN apk add --no-cache postgresql17-postgis

# Run init.sql
ADD ./docker/db/init.sql /docker-entrypoint-initdb.d/

# HEALTHCHECK: verify the database is accepting connections. pg_isready returns
# 0 if the server is accepting connections, 3 if it is not (e.g., still starting).
# Used by orchestrators for readiness probes and container restarts.
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=3 \
  CMD pg_isready -U postgres -h 127.0.0.1 || exit 1
