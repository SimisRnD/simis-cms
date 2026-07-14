---
id: azure-migration-runbook
title: v1 → v2 Azure Migration Runbook
description: Retire the unmaintained v1 CMS on DigitalOcean and stand up the maintained v2 (cms-platform) on Azure.
---

> **Status: DRAFT / planning.** Items marked **⚠️** are decisions or facts to fill in
> during execution. This runbook is the go-forward plan; the current-state details of
> v1 live in `deployment-runbook.md`.

## 0. Goal & strategy

SimIS's live CMS is **v1** (`simis-cms`) on **DigitalOcean** — unmaintained, carrying
known vulnerabilities, and with the **server itself no longer accessible** (root was
removed; no SSH). SimIS holds the **DigitalOcean account** but not server access.

Rather than patch a system we can't reach, we **start clean on Azure with v2**
(`cms-platform`) — the maintained successor, already secured, already Azure-ready. This
one move retires **both** problems at once: the vulnerable software **and** the lost
infrastructure.

**Shape:** recover the data from DO via the *account* (no server login) → build v2 on
Azure → restore data → verify → cut DNS → destroy the DO Droplet.

## 1. Pre-work (no DO/Azure access required — do this first)

- **GHCR pull token.** Azure must pull the **private** image `ghcr.io/simisrnd/cms-platform`.
  Create a **read-only** GitHub token with **`read:packages`** (fine-grained PAT or a
  service account) scoped to the SimIS packages. This is the credential App Service uses.
- Confirm the images exist (they do): `ghcr.io/simisrnd/cms-platform` + `-db`.
- **⚠️ Decide:** Azure subscription, resource group, region, naming convention.

## 2. Recover the data from DigitalOcean  *(SECURITY-SENSITIVE — needs the DO account)*

**Treat the DO box as untrusted/possibly compromised. Do NOT log into the original server.**

1. **Snapshot the Droplet** from the DO console — an **account-level** action; needs no
   SSH/root. A **live snapshot** does not reset or reboot the running site. This is also
   your **forensic image** — keep it.
2. **Check for a DO Managed Database** in the account's **Databases** section.
   - **If present:** the data is there — reset its credentials from the panel and connect
     directly (no snapshot needed for the DB).
   - **If not:** the database lives on the Droplet disk → recover it via the clone below.
3. **Create a NEW Droplet *from* the snapshot**, setting **your own SSH key**. This
   sidesteps the removed root entirely (you control access on the clone) and never touches
   production.
4. From the clone, **extract**: a **PostgreSQL dump** (`pg_dump`) and the **asset/file
   library** (the `CMS_PATH` directory).
5. **Rotate / treat as compromised** every secret found on the box (DB passwords, API keys).
- **⚠️ Fill in:** managed-DB vs on-disk; the `CMS_PATH` location; DB name/credentials.

## 3. Provision Azure

- **Resource group** + region (§1 decision).
- **Azure Database for PostgreSQL — Flexible Server**: create server + database + user;
  **enable the PostGIS extension**. (v2 requires PostgreSQL + PostGIS.)
- **Azure Files** share for uploaded assets. **⚠️ Critical:** containers are ephemeral —
  assets **must** live on external persistent storage, mounted into the app at `CMS_PATH`,
  or uploads are lost on every restart.
- **Key Vault** for secrets (DB password, admin credentials).
- **App Service for Containers (Linux):**
  - Image: `ghcr.io/simisrnd/cms-platform:latest` (or a pinned tag — prefer pinned for prod).
  - Registry auth: the **`read:packages`** token from §1.
  - Mount the Azure Files share; point `CMS_PATH` at the mount.
  - **App settings (env vars):** `CMS_ADMIN_USERNAME`, `CMS_ADMIN_PASSWORD`,
    `CMS_FORCE_SSL=true`, `CMS_NODE_TYPE`, `CMS_PATH`, `DB_SERVER_NAME`, `DB_NAME`,
    `DB_USER`, `DB_PASSWORD` — **or** the passwordless block v2 supports:
    `DB_AUTH_METHOD=azure-sql-spn`, `DB_TENANT_ID`, `DB_CLIENT_ID`, `DB_SECRET`.
    Reference secret values from Key Vault, not inline.

## 4. Restore the data

- Restore the `pg_dump` into Azure Database for PostgreSQL.
- Copy the asset files into the Azure Files share.
- **⚠️ Highest-risk step — validate in staging first.** v2 runs **Flyway** on startup, so
  booting v2 against a restored **v1** database will apply the v1→v2 schema migrations.
  Restore into a **staging** Azure DB, boot v2 against it, and watch the Flyway output
  before doing this against anything real.

## 5. Verify (staging)

- Boot v2 on Azure pointed at the restored DB.
- Watch startup logs: Flyway migrations apply cleanly, no errors.
- Smoke test: `/login`, edit a page + save, confirm assets load, confirm the editor
  (TinyMCE) and sliders (Swiper) render.

## 6. Cut over

- Point **DNS** at the Azure App Service (custom domain + managed TLS cert).
- Lower DNS TTL beforehand for a faster/reversible switch. Monitor after.

## 7. Decommission DigitalOcean

- After cutover is verified and a safety window has passed:
  - **Archive the forensic snapshot** (retain per policy).
  - **Destroy the Droplet** (and any DO Managed DB) from the account — this finally ends
    the exposed v1.
  - Rotate any remaining shared secrets.

## 8. Security / ISSM notes

- Forensic snapshot retained; record **chain of custody** (who accessed what, when).
- Old box treated as **compromised**; all discovered secrets rotated.
- v2 carries the security posture we set: Dependabot alerts + security/version updates,
  secret scanning + push protection, base-image monitoring.
- **Residual risk:** v1 stays exposed on DO until §7 — document it in the risk register
  with this migration as the remediation milestone.

## 9. ⚠️ Decisions / gaps to close

| # | Item |
|---|------|
| 1 | Azure subscription, resource group, region, naming |
| 2 | DO data location: Managed DB vs. on-Droplet-disk (§2.2) |
| 3 | `CMS_PATH` asset directory location + size |
| 4 | Auth method: `DB_*` password vs. `azure-sql-spn` (managed identity) |
| 5 | App Service plan tier / sizing |
| 6 | DNS TTL + cutover window |
| 7 | v1→v2 data-migration validation result (§4 staging test) |
