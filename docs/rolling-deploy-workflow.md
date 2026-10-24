# Rolling Deploy Workflow

**Audience:** DevOps engineers managing Azure App Service deployments  
**Goal:** Zero-downtime container updates using deployment slots

## Overview

The rolling deploy workflow uses Azure App Service deployment slots to swap new and old instances without dropping in-flight requests:

```
Time  Production (Active)    Staging (Inactive)      Status
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
0:00  v1.0 (healthy)         (empty)                 Production serving
0:05  v1.0 (healthy)         v1.1 (deploying)        New version deploying
0:10  v1.0 (healthy)         v1.1 (healthy)          New version ready
0:15  v1.1 (active)          v1.0 (draining)         Traffic redirected
0:16  v1.1 (serving)         v1.0 (stopped)          Cleanup complete
```

**Zero-downtime properties:**
- Traffic swaps instantly at slot level (no in-flight request loss)
- Old instance drains connections for 60 seconds, then its slot is stopped (kept for rollback)
- New instance must answer `/healthz` with 200 before the swap completes (App Service swap warm-up); otherwise production is untouched
- Idempotent: re-running a partial deploy doesn't create orphans

## Trigger

The rolling deploy is **automatically triggered** on every push to `main`:

1. PR merged → push to main
2. GitHub Actions builds image, runs tests, scans for vulnerabilities
3. Image pushed to Azure Container Registry (ACR)
4. **Rolling deploy starts automatically** (no manual step)
5. Zero-downtime swap happens

Deployments skip if:
- `ACR_REGISTRY` not configured (fork, local branch)
- `ACR_APP_SERVICE_NAME` not set (no target deployment)

## Setup Requirements

To enable automatic rolling deploys, set these **repository variables** in GitHub:

| Variable | Value | Example |
|----------|-------|---------|
| `ACR_REGISTRY` | Azure Container Registry hostname | `simiscms.azurecr.io` |
| `ACR_APP_SERVICE_NAME` | App Service instance name | `simis-cms-pilot` |
| `ACR_APP_RESOURCE_GROUP` | Azure resource group name | `simis-cms-rg` |
| `AZURE_CLIENT_ID` | Service principal client ID | (from workload identity federation) |
| `AZURE_TENANT_ID` | Azure tenant ID | (from workload identity federation) |
| `AZURE_SUBSCRIPTION_ID` | Azure subscription ID | (from workload identity federation) |

The `AZURE_*` variables are already configured for ACR login. Reuse them for the rolling deploy.

**Azure roles.** The CI principal needs Website Contributor on the App Service (or its resource group) to create, configure, start, stop, and swap the slot, plus AcrPush on the registry. Separately, the staging slot's own managed identity needs the same grants as production's — AcrPull on the registry and Key Vault Secrets User on the vault. `infra/main.bicep` makes those; for an environment where it has not been redeployed since the slot was added, see Step 1c below.

### How to Set Repository Variables

1. Go to repository → **Settings** → **Variables and secrets** → **Actions**
2. Click **New repository variable**
3. Name: `ACR_APP_SERVICE_NAME`, Value: `simis-cms-pilot`
4. Repeat for other variables
5. Click **Add variable**

**Note:** Do not use secrets (which require approval); use public variables. The ACR registry hostname is non-sensitive.

## Process: Behind the Scenes

### Step 1: Ensure the staging slot exists

`production` is the App Service's implicit default slot: it always exists, is never returned by `az webapp deployment slot list`, and can't be created or deleted — only swapped into. `staging` is the only extra slot this workflow uses, and new code always lands there first; production is only ever reached through the warm-up-gated swap in Step 4. There is no path in the routine deploy flow that writes to production directly.

The staging slot persists between deploys (Step 6 keeps it), so normally this step only confirms it exists. It is created — cloning settings from production — only on the first run or if someone has deleted it:
```bash
az webapp deployment slot create \
  --slot staging \
  --configuration-source "$APP_SERVICE_NAME"
```

A failed slot listing stops the deploy. It is never read as "no slots", which would send the script down the creation path for a slot that already exists.

**Step 1b** attaches the slot to production's subnet: `--configuration-source` does not copy VNet integration, and without it the slot cannot reach PostgreSQL or Key Vault through their private endpoints.

### Step 1c: The slot's own identity, with production's access

Production pulls its image and resolves its Key Vault references (`DB_PASSWORD`, `CMS_SECRET_KEY`, `CMS_ADMIN_PASSWORD`) with its system-assigned managed identity. The slot carries the same settings, but **a slot does not share the app's identity, and `--configuration-source` does not give it one.** Without one it cannot pull an image or resolve a secret on its own.

That went unnoticed for seven weeks on the pilot, because a swap pulls the incoming build and resolves its references as production. It surfaced on 2026-10-21: after the previous swap, the slot holding the old build started from the worker's image cache, presented the unresolved `@Microsoft.KeyVault(...)` text as its database password, and failed to log in every seven seconds — keeping three Azure Monitor alerts in the Fired state, where they would not have notified anyone about a real outage.

So this step:
1. gives the slot a system-assigned identity if it has none (`az webapp identity assign --slot staging`), and
2. compares the role grants held by the slot's identity with those held by production's, and **stops the deploy — before deploying anything — if any are missing**, printing the exact `az role assignment create` commands.

The CI principal cannot make those grants itself; it is a Website Contributor, which cannot create role assignments, by design. An Owner or User Access Administrator makes them once. `infra/main.bicep` declares the slot with its identity and makes the same grants (`modules/rbac.bicep`), so an environment deployed from the template passes this check on its first run. If you grant by hand, use the names the template would use (`guid(scope, principalId, roleDefinitionId)`), or the next deployment of the template fails with `RoleAssignmentExists`.

### Step 2: Deploy image and start the slot

```bash
az webapp config container set --slot staging --docker-custom-image-name "$IMAGE_URI"
az webapp start --slot staging
```

The slot is kept stopped between deploys (Step 6), and a stopped slot does not pull anything when its image changes, so it is started only once it holds the new image. With its own identity it pulls the image and resolves its secrets itself.

### Step 3: Settle window

The script waits `--health-check-timeout` seconds for the container to pull and start. This is not the health gate: the slot's hostname is not reachable from a GitHub Actions runner (`publicNetworkAccess` is Disabled and a slot inherits it), so the gate runs inside Azure, in Step 4.

### Step 4: Swap slots

```bash
az webapp deployment slot swap --slot staging
```

`WEBSITE_SWAP_WARMUP_PING_PATH=/healthz` and `WEBSITE_SWAP_WARMUP_PING_STATUSES=200` (set in `infra/modules/appservice.bicep`) make App Service request `/healthz` on the slot and abort the swap unless it answers 200. If the swap fails, production is untouched and the script stops the staging slot, which now holds the build that failed.

### Step 5: Connection drain

Wait 60 seconds for the old instance to finish in-flight requests:
- In-flight HTTP connections are given time to complete
- New requests are not sent to the old instance
- Sessions may be lost if using JVM-local memory (without session affinity)

### Step 6: Stop the old slot

After the swap, the staging slot holds the previous build. It is **kept** as a rollback artifact (deleting it would mean recreating it, the most fragile step in the script) and **stopped**: left running, it would be a second full copy of the app against the production database, running the scheduled jobs (`CMS_NODE_TYPE=standalone`) in their previous version. A failure to stop it is reported as a warning annotation rather than failing a deploy that has already succeeded.

## Monitoring & Troubleshooting

### Check deployment status

View GitHub Actions run output:
```bash
# See the rolling-deploy-workflow step in the publish-images workflow
# Logs show each step and timing
```

Look for:
- ✓ `staging slot identity holds all N of production's role grants` — the slot can run on its own
- ✓ `Slot swap complete` — warm-up passed and traffic is on the new build
- ✓ `Staging slot stopped and retained for rollback`
- ✓ `Rolling deploy complete` — success

### Rollback scenarios

**The staging slot's identity is missing grants:**
```
❌ The staging slot's identity lacks role grants that production's identity holds.
  ...
  az role assignment create --assignee-object-id <slot principal> --assignee-principal-type ServicePrincipal --role <role id> --scope <scope>
❌ ERROR: Staging slot identity is missing role grants; nothing was deployed and production is untouched
```

Action: have an Owner or User Access Administrator run the printed commands (or redeploy `infra/main.bicep`), then re-run the workflow.

**Swap failed warm-up:**
```
❌ Swap FAILED -- the new build did not pass App Service warm-up
Production was not modified and is still serving the previous build.
```

Action: find out why the new build does not answer `/healthz` with 200. Start the slot and read its container log through ARM — the slot's own log endpoint (SCM) is not publicly reachable, so `az webapp log tail` will not work from outside:
```bash
az webapp start -g my-rg -n simis-cms-pilot --slot staging
az rest --method post --url "$(az webapp show -g my-rg -n simis-cms-pilot --slot staging --query id -o tsv)/containerlogs?api-version=2023-12-01"
az webapp stop -g my-rg -n simis-cms-pilot --slot staging
```

Common causes:
- Database unreachable (connection pool timeout)
- Key Vault secret missing or wrong value, or the slot's identity lacks access to it
- File store (Azure Files) mount failed
- Startup migration hanging

**Manual rollback** (the new build is live and bad): the staging slot holds the previous build, stopped. Start it, give it time to start, and swap it back in — the same warm-up gate applies:
```bash
az webapp start --resource-group my-rg --name simis-cms-pilot --slot staging
az webapp deployment slot swap --resource-group my-rg --name simis-cms-pilot --slot staging
az webapp stop --resource-group my-rg --name simis-cms-pilot --slot staging   # now holds the bad build
```

### Monitoring in production

Set up alerts in Azure Monitor:
```bash
# HTTP 5xx rate spike (app crashing)
# Requests per second drop (traffic not reaching app)
# Response time spike (app slow or deadlocked)
```

See Phase 4 Sentinel documentation for monitoring setup.

## When Slot Swap is NOT Enough

The workflow above handles **application changes** (code, config). It does NOT handle:
- **Database schema changes** (handled by Flyway + distributed lock in DatabaseCommand)
- **Infrastructure changes** (App Service config, networking) — redeploy via Bicep

For combined app + infrastructure deploys, update Bicep first, then push code.

## Idempotency & Safety

### Re-running a partial deploy is safe

The workflow never tries to auto-detect "which slot is active" — that question doesn't need answering. `production` is always the stable slot bound to the public hostname; `staging` is always the deploy target. This holds regardless of how the previous run ended:

**Scenario: previous run completed cleanly.**
Staging holds the previous build, stopped. This run overwrites its image, starts it, and swaps.

**Scenario: previous run's swap succeeded, but Step 6 did not stop the slot.**
Staging is running the previous build. Step 2 overwrites the image (restarting it on the new build); starting an already-running slot is a no-op.

**Scenario: previous run's swap failed warm-up.**
Staging holds the failed build, stopped. This run overwrites it with the new image.

**Scenario: the staging slot was deleted.**
Step 1 recreates it and Step 1c gives it an identity; the deploy then stops until an administrator grants that new identity production's access.

In every case, staging is the only slot ever written to before a swap, and production is only ever changed by the warm-up-gated swap itself — never by a direct write.

### Connection drain is sufficient

During the 60-second drain window:
- Load balancer (AFD) stops sending new requests to old instance
- Old instance finishes existing HTTP connections gracefully
- No in-flight requests are dropped

Tomcat timeout defaults handle anything longer than 60 seconds.

### Session loss is acceptable

With session affinity enabled (AFD level), users are pinned to their instance. On failover, they re-auth. This is rare and acceptable.

Without session affinity, users are re-authenticated automatically on any swap. Session state is in JVM memory, not persistent storage.

## Metrics & Logging

**Workflow logs:**
- GitHub Actions run logs (`.github/workflows/publish-images.yml`, "Deploy to Azure App Service" step)

**App logs (new instance during startup):** the staging slot has no diagnostic setting, so its console output does not reach Log Analytics, and its log endpoint is not publicly reachable. Read its container log through ARM:
```bash
az rest --method post --url "$(az webapp show -g my-rg -n simis-cms-pilot --slot staging --query id -o tsv)/containerlogs?api-version=2023-12-01"
```

**Deployment metrics (Azure Portal):**
- App Service → Deployment slots → Activity log
- Shows slot operations (create, swap, start, stop) with timestamps

**AFD metrics:**
- Front Door → Health probes (check why instance was marked unhealthy)
- Front Door → WAF logs (check for blocks during swap)

---

**See Also:**
- `tools/rolling-deploy.sh` — Implementation details
- `.github/workflows/publish-images.yml` — CI/CD trigger
- DEPLOYMENT.md §4.0 — Database migration locking (coordinates with deploy)
- DEPLOYMENT.md §5.3 — Session affinity (prevents logout on swap)
