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

### Step 1: Identify slots

The script checks which slot is currently active:
- If staging exists and is healthy → production is next (deploy to production, then swap)
- If staging is absent or old → staging is next (deploy to staging, then swap)

**Idempotency:** Re-running after a partial failure picks up the correct target slot based on current state.

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

**Scenario:** Slot swap succeeded, but cleanup script crashed.

**Result:** Old slot still exists. Re-running the workflow:
1. Detects old slot is now "staging"
2. Treats it as the current active slot
3. Deploys to "production" (which is now staging's contents)
4. Health check passes
5. Swaps back to "staging" (old → new)
6. Deletes the new slot (which is really the old one)

**Net effect:** Correct slot is active; orphaned slots cleaned up.

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
