---
id: deployment-runbook
title: Deployment & Patch Runbook (v1)
description: How to build, deploy, patch, and roll back SimIS CMS (v1) in production.
---

> **Status: DRAFT.** Reconstructed from `docs/installation.md`, `docker/README`,
> `docs/pipeline.md`, and the build/Docker config after the original maintainer's
> departure. Sections marked **⚠️ GAP** are SimIS-specific facts that are *not*
> yet documented and must be confirmed against the live environment. Treat every
> ⚠️ GAP as a required follow-up before relying on this runbook for a real change.

## 1. What this system is

- **App:** a single Java `.war` (`simis-cms.war`) deployed as `ROOT.war` on **Apache Tomcat 9.0.x**.
- **Runtime:** **OpenJDK 17+** (image base `tomcat:9.0-jdk21` in `docker/app/Dockerfile`).
- **Database:** **PostgreSQL 14+** with **PostGIS 3.2**. The DB schema is created and **auto-migrated on application startup** (Flyway) — no manual SQL step for upgrades.
- **Assets:** uploaded/generated files live on a directory referenced by `CMS_PATH`.

## 2. Build toolchain (to reproduce a build)

- **JDK 17**, **Apache Ant**, **Docker + Docker Compose**.
- Node/npm are **not** required — frontend libraries are vendored into the repo.
- Source: `https://github.com/SimisRnD/simis-cms`.

## 3. Build the application (`.war`)

```bash
ant package        # produces target/simis-cms.war
```

Reference CI: `.github/workflows/ant.yml` runs `compile → checkstyle → test → package`.

## 4. Build the container images

```bash
docker-compose build
```

- **App image** — `docker/app/Dockerfile`: `FROM tomcat:9.0-jdk21`, copies `./target/simis-cms.war` → `/usr/local/tomcat/webapps/ROOT.war`.
- **DB image** — `docker/db/Dockerfile`: `postgres` + PostGIS, runs `init.sql`.

> Note: `docker-compose.yaml` **builds locally** (it does not pull a published image).

## 5. Configuration (environment variables)

Set via a `.env` file (see `docker-compose.yaml`). From `docs/installation.md`:

```dotenv
CMS_ADMIN_USERNAME=
CMS_ADMIN_PASSWORD=
CMS_FORCE_SSL=true|false
CMS_NODE_TYPE=<empty>|standalone|web
CMS_PATH=<empty>|/opt/simis        # asset/file library location
DB_SERVER_NAME=
DB_NAME=                            # default: simis-cms
DB_USER=
DB_PASSWORD=
```

> **⚠️ GAP:** The **actual production values** and **where these secrets are stored**
> (secrets manager? a `.env` on the host?) are undocumented. Locate and record the
> source of truth — do **not** commit real secrets to the repo.

## 6. Deploy / run

**Container path (per `docker/README`):**

```bash
docker-compose up -d
```

**Standalone Tomcat path (per `docs/installation.md`):** drop the built
`ROOT.war` into a `tomcat:9.0-jdk*` container or Tomcat instance and start it.

> **⚠️ GAP — the big one:** *Which* path production actually uses, and **where it runs**
> (cloud/VM/host), is unknown. Also unknown: whether the DB is a **managed service
> (e.g., RDS)** or a **container**, and how the app reaches it (`DB_SERVER_NAME`).
> Confirm from the live environment and record it here.

## 7. Verify a deployment

```bash
docker ps
docker logs --follow simis-cms-app-1        # watch Tomcat + Flyway startup
```

- App answers on its port; healthcheck hits `http://localhost:8080`.
- Log in at `/login`. If no admin env vars were set, the Tomcat log prints **one-time** admin credentials.

## 8. Backup (ALWAYS before any change)

- **Database:** `pg_dump` the `simis-cms` database.
- **Assets:** copy the `CMS_PATH` file library.

> **⚠️ GAP:** Documented backup **location, method, retention, and schedule** — none
> exist yet. Establishing this is a prerequisite to safely patching prod.

## 9. Patch & upgrade procedure (how to ship a security fix)

1. **Land the fix** in `SimisRnD/simis-cms` via PR (dependency bump, etc.).
2. **Back up** the database and assets (Section 8).
3. **Build** the new `.war` (`ant package`) / rebuild the image.
4. **Replace** `ROOT.war` with the new build (per `installation.md`, upgrading = swapping the war; Flyway auto-migrates the DB on startup).
5. **Verify** startup logs are clean and smoke-test key flows (login, edit a page, save).
6. Keep the previous `.war` + DB backup ready for rollback.

## 10. Rollback

- Restore the **previous `ROOT.war`**.
- Restore the **database backup** if the failed release changed the schema.
  (An app-only rollback *may* work if no schema change occurred — verify against release notes.)

## 11. ⚠️ GAPS TO CLOSE (undocumented since the maintainer left)

| # | Unknown | Why it matters |
|---|---------|----------------|
| 1 | Production host/infrastructure (where it runs) | Can't deploy or patch without access to it |
| 2 | Container vs. standalone-Tomcat in prod | Determines the deploy method |
| 3 | DB: managed (RDS) vs. container; connection details | Backup/restore + connectivity |
| 4 | Secrets store & actual env values | Config + credential rotation |
| 5 | `CMS_PATH` asset directory location | Data integrity + backups |
| 6 | Backup location/procedure/schedule | Safe rollback |
| 7 | DNS / TLS / reverse proxy in front | Exposure + cutover |
| 8 | Who holds prod host access today | Operational continuity |
| 9 | Monitoring/log aggregation/alerting | Detect exploitation & failures |

## 12. Access & ownership

- Repo write access: developers with `push` on `SimisRnD/simis-cms` (open a PR).
- Repo admin / archiving / settings: org admins (IT).
