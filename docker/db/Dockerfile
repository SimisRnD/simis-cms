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
FROM golang:1.26-bookworm@sha256:1ecb7edf62a0408027bd5729dfd6b1b8766e578e8df93995b225dfd0944eb651 AS gosu
ENV CGO_ENABLED=0
RUN go install github.com/tianon/gosu@1.17

# Pull official base image (digest-pinned -- see the header note)
FROM postgres:17-bookworm@sha256:4f736ae292687621d4dbe0d499ffd024a36bd2ee7d8ca6f2ccd4c800f047b394

# Replace the base image's gosu with the freshly built, patched-stdlib binary.
COPY --from=gosu /go/bin/gosu /usr/local/bin/gosu

# Patch base OS packages and install PostGIS, in a single layer with the apt
# cache removed to keep the image small.
RUN apt-get update \
    && apt-get -y upgrade \
    && apt-get install -y postgresql-17-postgis-3 postgis \
    && rm -rf /var/lib/apt/lists/*

# Run init.sql
ADD ./docker/db/init.sql /docker-entrypoint-initdb.d/
