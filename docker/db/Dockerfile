# SimIS CMS database image: PostgreSQL 17 + PostGIS.
#
# Base is pinned to Debian 12 "bookworm" rather than the newer "trixie" default.
# Bookworm's packages are more fully triaged by Debian security, so the image
# ships with materially fewer open CVEs. See docker/db/README.md for the image's
# CVE posture and why the residual findings are not fixable here.

# Rebuild gosu with a current Go toolchain. The base image's gosu is built
# against an older Go and carries that Go's stdlib CVEs; a fresh static build
# clears them without changing gosu's behavior.
FROM golang:1.26-bookworm AS gosu
ENV CGO_ENABLED=0
RUN go install github.com/tianon/gosu@1.17

# Pull official base image
FROM postgres:17-bookworm

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
