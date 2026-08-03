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
- Old instance drains connections for 60 seconds before termination
- New instance health-checked before swap (automatic rollback on failure)
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

### How to Set Repository Variables

1. Go to repository → **Settings** → **Variables and secrets** → **Actions**
2. Click **New repository variable**
3. Name: `ACR_APP_SERVICE_NAME`, Value: `simis-cms-pilot`
4. Repeat for other variables
5. Click **Add variable**

**Note:** Do not use secrets (which require approval); use public variables. The ACR registry hostname is non-sensitive.

## Process: Behind the Scenes

### Step 1: Ensure the staging slot exists

`production` is the App Service's implicit default slot: it always exists, is never returned by `az webapp deployment slot list`, and can't be created or deleted — only swapped into. `staging` is the only extra slot this workflow uses, and new code always lands there first; production is only ever reached through the health-checked swap in Step 4. There is no path in the routine deploy flow that writes to production directly.

The staging slot does not persist between deploys — Step 6 deletes it after every successful swap — so this step (re)creates it unconditionally, cloning settings from production:
```bash
az webapp deployment slot create \
  --slot staging \
  --configuration-source "$APP_SERVICE_NAME"
```

**Idempotency:** this is safe to run every time regardless of current state — whether staging is missing (first-ever run, or after a normal cleanup) or already present (a prior cleanup failed and left it behind, in which case it's reused as-is and its content is about to be overwritten in Step 2 anyway).

### Step 2: Deploy image

Deploy new image to the target slot:
```bash
az webapp config container set \
  --slot staging \
  --docker-custom-image-name "$IMAGE_URI"
```

The App Service pulls the image and starts the container.

### Step 3: Health check

Poll `/healthz` every 5 seconds until it returns `{"status":"UP"}` with HTTP 200:
- **Success:** Proceed to swap
- **Timeout (300s):** Automatic rollback; delete staging, report failure
- **Failed:** Automatic rollback (same as timeout)

```bash
# Typical sequence:
# Attempt 1: connection refused (container still starting)
# Attempt 2-20: HTTP 503 DOWN (migrations running, starting up)
# Attempt 21: HTTP 200 UP ✓ (ready to serve)
```

### Step 4: Swap slots

Instantly redirect traffic from old instance to new:
```bash
az webapp deployment slot swap --slot staging
```

All new requests go to the new instance. Old instance enters drain window.

### Step 5: Connection drain

Wait 60 seconds for old instance to finish in-flight requests:
- In-flight HTTP connections are given time to complete
- New requests are not sent to the old instance
- Sessions may be lost if using JVM-local memory (without session affinity)

### Step 6: Cleanup

Delete the old slot to save costs and reduce noise.

## Monitoring & Troubleshooting

### Check deployment status

View GitHub Actions run output:
```bash
# See the rolling-deploy-workflow step in the publish-images workflow
# Logs show each step and timing
```

Look for:
- ✓ `Acquired migration lock: «uuid»` — database migrations running
- ✓ `Health check PASSED` — new instance ready
- ✓ `Slot swap complete` — traffic redirected
- ✓ `Rolling deploy complete` — success

### Rollback scenarios

**Auto-rollback on health check failure:**
```
Health check FAILED (timeout after 300s)
Initiating rollback: deleting staging slot...
ERROR: Health check failed; rolled back staging deployment
```

Action: Investigate why `/healthz` isn't returning UP. Check logs:
```bash
az webapp log tail --name simis-cms-pilot --slot staging
```

Common causes:
- Database unreachable (connection pool timeout)
- Key Vault secret missing or wrong value
- File store (Azure Files) mount failed
- Startup migration hanging

**Manual rollback:**
If an automated rollback didn't complete (e.g., deploy script crashed), manually restore:

```bash
# Check which slot is current
az webapp show --resource-group my-rg --name simis-cms-pilot

# If new (bad) deployment is active, swap back
az webapp deployment slot swap --name simis-cms-pilot \
  --resource-group my-rg --slot staging
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

The workflow never tries to auto-detect "which slot is active" — that question doesn't need answering. `production` is always the stable slot bound to the public hostname; `staging` is always the deploy target, unconditionally (re)created in Step 1 if it isn't already there. This holds regardless of how the previous run ended:

**Scenario: previous run completed cleanly.**
Staging was deleted in its Step 6. This run's Step 1 recreates it from production's current config, deploys the new image, health-checks it, swaps, and deletes it again.

**Scenario: previous run's slot swap succeeded, but cleanup (Step 6) crashed or was killed.**
Staging is still sitting there holding the pre-swap (old) build. This run's Step 1 finds it already exists and reuses it as-is — its stale content is irrelevant, since Step 2 immediately overwrites it with the new image before anything reads from it.

**Scenario: previous run's health check failed and rollback deleted staging, then the run exited.**
Same as a clean completion — staging is absent, Step 1 recreates it.

In every case, staging is the only slot ever written to before a swap, staging is always health-checked before the swap happens, and production is only ever changed by the swap itself — never by a direct write. There is no fallback path where a routine deploy pushes new code straight onto production; a swap-then-health-check failure is handled by the rollback block deleting/recreating staging, not by mutating production in place.

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

**App logs (new instance during startup):**
```bash
az webapp log tail --name simis-cms-pilot --slot staging
```

**Deployment metrics (Azure Portal):**
- App Service → Deployment slots → Activity log
- Shows slot operations (create, swap, delete) with timestamps

**AFD metrics:**
- Front Door → Health probes (check why instance was marked unhealthy)
- Front Door → WAF logs (check for blocks during swap)

---

**See Also:**
- `tools/rolling-deploy.sh` — Implementation details
- `.github/workflows/publish-images.yml` — CI/CD trigger
- DEPLOYMENT.md §4.0 — Database migration locking (coordinates with deploy)
- DEPLOYMENT.md §5.3 — Session affinity (prevents logout on swap)
