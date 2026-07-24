# Azure infrastructure (Bicep)

Infrastructure-as-code for deploying simis-cms to Azure — Milestone #4 Phase 2.

Every resource here is reproducible and reviewable, which is the point: the templates
are themselves a CM-2 configuration-management evidence artifact, not just a
convenience.

## Status

**Authored and type-checked. Not deployed.** Everything compiles clean with
`az bicep build`, but nothing here has been applied or run through `what-if` — that
needs an Azure subscription, which is the hard dependency called out in the Phase 0
design. Treat it as reviewed-but-unapplied.

## What is here

| File | Purpose |
|---|---|
| `main.bicep` | Orchestrator; wires the modules and exposes outputs for the edge tier |
| `modules/network.bicep` | VNet, App Service integration subnet, private-endpoint subnet, private DNS zones |
| `modules/loganalytics.bicep` | Log Analytics workspace (container stdout → Sentinel "Path A") |
| `modules/storage.bicep` | Storage account + file share backing `CMS_PATH` |
| `modules/keyvault.bicep` | Key Vault (RBAC, private endpoint, purge protection) |
| `modules/postgres.bicep` | PostgreSQL Flexible Server + database + PostGIS allow-list + private endpoint |
| `modules/acr.bicep` | Container registry for the signed app image; admin account disabled |
| `modules/appservice.bicep` | Plan + Linux container app: managed identity, Key Vault references, `CMS_PATH` mount, VNet integration, diagnostics |
| `modules/rbac.bicep` | The app identity's grants: Key Vault Secrets User + AcrPull |

## What is not here yet

The **edge** (Front Door + WAF, issue #245), which consumes `main.bicep`'s outputs —
and until it lands, `CMS_TRUSTED_PROXIES` stays empty. The pipeline that pushes the
image to the registry is issue #246.

## Decisions this implements

Resolved in Phase 0 — see `decision-milestone-4-phase0-decisions.md` in the runbooks:

- **#1** Azure Commercial · **#2** App Service for Containers · **#3** Bicep
- **#4** private endpoints for the database and Key Vault, with VNet integration
- **#5** hardened official base images · **#6** Key Vault + managed identity
- **#7** platform FIPS modules · **#8** scale up only for the pilot

## Two things that are load-bearing

- **`CMS_PATH` must be external storage.** App Service containers are ephemeral, so the
  uploaded file library lives on the Azure Files share and is mounted in. If it stays
  inside the container, every restart silently loses uploads.
- **PostGIS must be allow-listed before it can be created.** On Flexible Server an
  extension has to appear in the `azure.extensions` server parameter before
  `CREATE EXTENSION` succeeds. `postgres.bicep` sets it, which is what lets the Flyway
  install run unattended on first boot.

## Validate locally

No subscription or login required:

```
az bicep build --file infra/main.bicep --stdout > /dev/null
```

Silence means success. To check every file:

```
for f in infra/modules/*.bicep infra/main.bicep; do az bicep build --file "$f" --stdout > /dev/null || echo "FAILED: $f"; done
```

## Deploy-time inputs

`postgresAdministratorPassword` is a `@secure()` parameter with no default and **must
not** be committed. Supply it at deploy time from Key Vault or a secure pipeline
variable.

## Before first boot (application tier)

The app resolves three Key Vault references at startup, and IaC deliberately does
**not** create the secret values — the ISSM does, once, before the first start:

- `db-password` — the database login's password
- `cms-secret-key` — the CMS encryption key
- `cms-admin-password` — the admin bootstrap password

The image must also exist in the registry (issue #246's pipeline, or a one-time
manual push) — App Service pulls it with its managed identity via AcrPull; there is
no registry password. Two settings that matter at cutover: `customUrl` (CMS_URL)
switches to the custom domain, and `trustedProxies` (CMS_TRUSTED_PROXIES) must be
set to the edge egress ranges when the edge tier fronts the app — otherwise
`getRemoteAddr()`/`isSecure()` see the proxy, degrading the Secure-cookie flag, the
IP firewall, rate limiting, and the audit source IP.
