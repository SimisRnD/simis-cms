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
| `main.bicep` | Orchestrator; wires the modules and exposes outputs for the app tier |
| `modules/network.bicep` | VNet, App Service integration subnet, private-endpoint subnet, private DNS zones |
| `modules/loganalytics.bicep` | Log Analytics workspace (container stdout → Sentinel "Path A") |
| `modules/storage.bicep` | Storage account + file share backing `CMS_PATH` |
| `modules/keyvault.bicep` | Key Vault (RBAC, private endpoint, purge protection) |
| `modules/postgres.bicep` | PostgreSQL Flexible Server + database + PostGIS allow-list + private endpoint |

## What is not here yet

The **application tier** (Container Registry + App Service with managed identity,
Key Vault references, the `CMS_PATH` mount, and diagnostic settings) and the **edge**
(Front Door + WAF). Both consume `main.bicep`'s outputs.

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
