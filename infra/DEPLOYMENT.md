# Azure Deployment Runbook — Phase 2

**Audience:** ISSM / DevOps for initial Azure deployment (fresh install)  
**Duration:** ~2 hours (infrastructure provisioning ~30 min + validation ~30 min + manual approval steps ~1 hour)  
**Scope:** Deploy simis-cms to Azure with hardened images, managed identity, private networking, WAF

---

## Pre-Deployment Checklist

- [ ] Azure subscription access (contributor role on resource group)
- [ ] `az` CLI installed and logged in (`az login`)
- [ ] Bicep CLI available (`az bicep build` works)
- [ ] Signed container images published to the registry (PR #246 complete, or manual push)
- [ ] PostgreSQL administrator password generated (random, 20+ chars, stored securely)
- [ ] Three Key Vault secrets ready to create (see §4)

---

## 1. Provision Infrastructure

### 1.1 Prepare parameters

Create a `deploy-params.json` file (keep secret, do not commit):

```json
{
  "location": "eastus",
  "environmentName": "pilot",
  "postgresAdministratorPassword": "«random-20-char-password»",
  "logRetentionInDays": 90,
  "fileShareQuotaGb": 100,
  "appServicePlanSku": "P1v3",
  "containerImage": "simis-cms:latest",
  "dbUser": "simiscmsadmin",
  "wafMode": "Prevention",
  "customDomainName": "",
  "customUrl": ""
}
```

**Key fields:**
- `postgresAdministratorPassword`: **NEVER commit**. Supply at deploy only, from secure pipeline variable or prompted
- `containerImage`: Must exist in the registry (push via CI or manual `docker push` to ACR)
- `customDomainName`, `customUrl`: Empty for now; set at DNS cutover (§5)
- `wafMode`: Start in `Prevention`; drop to `Detection` only if tuning false positives

### 1.2 Validate Bicep

```bash
az bicep build --file infra/main.bicep --stdout > /dev/null
```

Silence = success. If errors, fix before proceeding.

### 1.3 Run `what-if` preview

```bash
az deployment group what-if \
  --resource-group my-simis-rg \
  --template-file infra/main.bicep \
  --parameters @deploy-params.json
```

Review the planned resources. Confirm:
- ✅ One App Service plan (P1v3)
- ✅ One Linux App Service (container)
- ✅ One PostgreSQL Flexible Server
- ✅ One storage account (Azure Files share)
- ✅ One Key Vault
- ✅ One Container Registry
- ✅ One Log Analytics workspace
- ✅ One Front Door (Premium) with WAF

### 1.4 Deploy

```bash
az deployment group create \
  --resource-group my-simis-rg \
  --template-file infra/main.bicep \
  --parameters @deploy-params.json
```

**Typical duration:** 8–12 minutes for the full stack (most time: Postgres Flexible Server provisioning).

Capture the outputs (show at end of deployment):
- `appServiceName` — for next steps
- `keyVaultName` — for secrets
- `frontDoorEndpointHostName` — for WAF testing
- `acrLoginServer` — for push validation

---

## 2. Create Key Vault Secrets

Before the app boots, three secrets must exist in Key Vault (app startup reads them):

```bash
# Set these to real values
DB_PASSWORD="«same as postgresAdministratorPassword»"
CMS_SECRET_KEY="«base64-encoded 256-bit key for at-rest encryption»"
CMS_ADMIN_PASSWORD="«bootstrap admin password»"

# Create in Key Vault
az keyvault secret set \
  --vault-name «keyVaultName» \
  --name db-password \
  --value "$DB_PASSWORD"

az keyvault secret set \
  --vault-name «keyVaultName» \
  --name cms-secret-key \
  --value "$CMS_SECRET_KEY"

az keyvault secret set \
  --vault-name «keyVaultName» \
  --name cms-admin-password \
  --value "$CMS_ADMIN_PASSWORD"
```

**Generate CMS_SECRET_KEY** (256-bit base64):
```bash
openssl rand 32 | base64 -w0
```

Copy the output; use it as the secret value.

---

## 3. Approve Private Endpoint Connection

Front Door connects to App Service via a *private endpoint* that starts in **pending** state. Approve it manually:

```bash
# Find the pending connection
az network private-endpoint-connection list \
  --name «appServiceName» \
  --resource-group my-simis-rg

# Approve (use connection ID from above)
az network private-endpoint-connection approve \
  --id «connection-id»
```

**Until approved,** Front Door returns 502 errors (no app ingress). After approval, the edge can reach the app.

---

## 4. Verify App Startup

Check app container logs (CloudShell or portal):

```bash
az webapp log tail \
  --name «appServiceName» \
  --resource-group my-simis-rg
```

Look for:
- ✅ `Server startup in X milliseconds` (Tomcat started)
- ✅ `Starting up the web database connection pool`
- ✅ `Flyway version X.Y` and `Successfully validated X migrations` OR `Acquired migration lock` / `Released migration lock` (multi-instance)
- ✅ No `ERROR` or `FATAL` lines

### 4.0 Database Migrations (Multi-Instance)

The app uses Flyway for database schema management. In a multi-instance deployment (rolling update or scale-out):

**Migration Lock Behavior:**
- Primary instances (`CMS_NODE_TYPE` not set or `= primary`) acquire a distributed lock before running migrations
- Lock is held for up to 5 minutes; prevents concurrent Flyway execution on multiple instances
- Web-only instances (`CMS_NODE_TYPE=web`) never run migrations themselves (not even unprotected) -- they skip the lock and instead poll until the primary's migrations are confirmed complete, bounded by the same ~6 minute timeout used for lock acquisition
- Lock is released after migrations complete or on failure (no lock leak)

**Log markers:**
- `Acquired migration lock: «uuid»` → This instance is running migrations
- `Released migration lock: «uuid»` → Migrations complete, lock released
- `Web-only node detected (CMS_NODE_TYPE=web); skipping migration lock acquisition` → This instance will not run migrations
- `Waiting for the primary node to complete migrations...` → Web-only instance polling for the primary to finish
- `Primary node's migrations are complete.` → Web-only instance confirmed the schema is ready and continued startup
- `Could not acquire migration lock; another node is migrating` → Primary instance waiting for another primary to finish
- `Primary node did not complete migrations within «timeout»; refusing to start` → Web-only instance timed out waiting; check the primary instance's logs for a stuck/failed migration

If startup fails:
- Check logs for the specific error (database, secrets, file store)
- Common: missing Key Vault secret, DB password mismatch, connectivity, lock timeout, primary migration taking longer than the wait timeout
- Redeploy with corrected parameter and wait for container restart

### 4.1 Background Job Storage (Multi-Instance)

The app uses JobRunr for background jobs (snapshots, cleanup, order processing). With SQL-backed storage enabled (Production mode), JobRunr creates its schema tables automatically:
- `jobrunr_jobs` — job definitions and state
- `jobrunr_backgroundjobservers` — server registrations (for multi-instance)
- `jobrunr_recurring_jobs` — cron schedule persistence

**No manual action required.** JobRunr creates these tables on first startup with the SQL provider. The app's database user (from Bicep: `simiscmsadmin`) needs standard privileges (INSERT, UPDATE, DELETE, SELECT) which it already has.

**Verify in logs:**
```
JobRunr version X.Y.Z
Jobrunr storage provider: SqlStorageProvider using table prefix 'jobrunr_'
```

Multi-instance behavior:
- Jobs marked as "cluster jobs" (snapshots, cleanup) run once across all instances, coordinated by the database
- Per-instance jobs (page hits, system health) run on every instance independently
- Instances with `CMS_NODE_TYPE=web` skip cluster jobs entirely

---

## 5. Configure Proxy IP Forwarding

Set the `CMS_TRUSTED_PROXIES` environment variable to Azure Front Door's backend IP ranges. This allows the app to see real client IPs (for audit logs, firewall, rate limiting).

### 5.1 Get Front Door backend ranges

Azure publishes service-tag IP ranges. Retrieve them:

```bash
curl -s https://www.microsoft.com/en-us/download/details.aspx?id=56519 | \
  jq '.[] | select(.id=="AzureFrontDoor") | .prefixes' | \
  jq -r '.[] | select(startswith("BACKENDS"))'
```

Or check [Azure IP Ranges and Service Tags](https://www.microsoft.com/en-us/download/details.aspx?id=56519) and search for `AzureFrontDoor.Backend`.

### 5.2 Set the environment variable

```bash
# Format: comma-separated CIDR blocks
az webapp config appsettings set \
  --name «appServiceName» \
  --resource-group my-simis-rg \
  --settings CMS_TRUSTED_PROXIES="«IP_RANGE_1»,«IP_RANGE_2»,..."
```

Example (check current ranges before using):
```bash
az webapp config appsettings set \
  --name simiscms-pilot \
  --resource-group my-simis-rg \
  --settings "CMS_TRUSTED_PROXIES=147.243.0.0/16,2607:f758::/32"
```

**Revisit monthly:** Azure updates service tags ~weekly. Refresh this monthly so the regex stays current.

### 5.3 Session Affinity (Multi-Instance)

Azure Front Door uses session affinity to pin authenticated users to the same backend instance. Without affinity, a multi-instance deployment (scale-out or rolling update) would log out users mid-session because Tomcat stores sessions in JVM-local memory.

**Affinity behavior:**
- Session affinity is a property of the **origin group** (not the route) — a plain `Enabled`/`Disabled` toggle. There is no route-level session-affinity setting in the Azure Front Door schema.
- When enabled, AFD sets a routing cookie on the first request and routes subsequent requests from the same browser to the same backend.
- Azure manages the cookie's characteristics (name, attributes like `SameSite`) and its duration internally. Neither is exposed as a Bicep-configurable setting for Standard/Premium Front Door, so this document does not state specific values for them — check the current [Azure Front Door documentation](https://learn.microsoft.com/azure/frontdoor/) if the exact cookie behavior matters for a given investigation.

**Failover behavior (when a pinned backend becomes unhealthy):**
- AFD health checks detect the instance is DOWN (probes fail 3× in a row)
- Next request from that user is routed to a healthy instance
- Session is LOST (Tomcat on the new instance has no session data)
- User must re-authenticate
- This is acceptable for sticky-session deployments; users tolerate re-auth during rare outages

**Expected frequency:** Rare. Health probes every 30 seconds; instance must fail 3× to be marked unhealthy. Transient blips don't trigger failover.

**Configuration:**
- Enabled in `infra/modules/frontdoor.bicep` (`originGroup` resource, `sessionAffinityState: 'Enabled'`)
- Applies to all traffic routed through that origin group — affects the whole site
- Cannot be tuned in the Azure Portal outside of Bicep (Bicep is source of truth)

**Current impact:** The App Service plan behind this origin group is currently pinned to a single instance (`capacity: 1`, pilot posture — see `infra/modules/appservice.bicep`). With one instance there is nowhere else a session could be routed, so this setting has no observable effect until the plan is actually scaled to multiple instances. It's configured now so multi-instance routing behaves correctly once that happens.

---

## 6. Test Edge Connectivity

### 6.1 Health check

```bash
curl -s https://«frontDoorEndpointHostName»/healthz
```

Expected: `{"status":"UP"}` with HTTP 200.

If 502: Private endpoint not yet approved (§3).  
If 503: App not healthy (check logs, verify secrets, check DB connectivity).

### 6.2 Homepage

```bash
curl -s https://«frontDoorEndpointHostName»/ | head -20
```

Should see HTML homepage (not error page).

### 6.3 Admin access

Open browser: `https://«frontDoorEndpointHostName»/admin`

Expected: Login page.

---

## 7. WAF Tuning (if needed)

If content authoring hits WAF false positives (403 errors):

### 7.1 Enable Detection mode (temporary)

```bash
az deployment group create \
  --resource-group my-simis-rg \
  --template-file infra/main.bicep \
  --parameters @deploy-params.json wafMode=Detection
```

This logs blocked requests but doesn't block them (allows content authoring to proceed).

### 7.2 Review logs

```bash
az monitor metrics list \
  --resource /subscriptions/«sub»/resourceGroups/my-simis-rg/providers/Microsoft.Network/frontdoors/«frontdoor-name» \
  --metric WebApplicationFirewallMetric \
  --start-time 2026-07-26T00:00Z
```

Identify the rule IDs that are firing on legitimate traffic.

### 7.3 Add exclusions (Azure portal)

Front Door WAF Rules → Managed Rules → Add exclusion matching the legitimate request pattern.

### 7.4 Return to Prevention

Once false positives are excluded, re-deploy with `wafMode=Prevention`.

---

## 8. Custom Domain & DNS Cutover (Day-of or after validation)

### 8.1 Prepare DNS records

Create (or update) a CNAME in your DNS zone:

```
www.example.org  CNAME  «frontDoorEndpointHostName»
```

### 8.2 Deploy with custom domain

```bash
# Update deploy-params.json
{
  "customDomainName": "www.example.org",
  "customUrl": "https://www.example.org",
  ...
}

az deployment group create \
  --resource-group my-simis-rg \
  --template-file infra/main.bicep \
  --parameters @deploy-params.json
```

Front Door will provision a managed TLS certificate automatically.

### 8.3 Verify certificate

Wait ~10 minutes for the certificate to be issued. Then:

```bash
curl -s -I https://www.example.org/ | grep -i certificate
```

Should show a valid cert issued by Let's Encrypt (via Azure).

### 8.4 Update CMS_URL

Set the `CMS_URL` environment variable in App Service settings:

```bash
az webapp config appsettings set \
  --name «appServiceName» \
  --resource-group my-simis-rg \
  --settings CMS_URL=https://www.example.org
```

The app uses this for link generation, email, etc.

---

## 9. Monitoring & Ongoing Operations

### 9.1 Log Analytics queries

View app startup logs:

```kusto
ContainerAppConsoleLogs
| where ContainerAppName == "simis-cms-pilot"
| where TimeGenerated > ago(1h)
| sort by TimeGenerated desc
| limit 100
```

View access logs (every request):

```kusto
ContainerAppConsoleLogs
| where ContainerAppName == "simis-cms-pilot"
| where Log has "localhost_access_log"
| sort by TimeGenerated desc
| limit 50
```

### 9.2 Alerts

Create alerts for:
- App Service down (HTTP 5xx errors)
- Database unavailable (query latency spike)
- WAF blocks (if expected volume is zero)

### 9.3 Backup & restore test

PostgreSQL Flexible Server has automated backups (7-day retention by default). Test restore:

```bash
# Restore to a point-in-time (1 hour ago)
az postgres flexible-server server create \
  --resource-group my-simis-rg \
  --name «appServiceName»-restore-test \
  --restore-time 2026-07-26T17:00:00Z \
  --source-server «original-server-name»
```

Run the deploy smoke test against the restored DB to verify it boots. Clean up after.

---

## 10. Go-Live Checklist

Before switching user traffic:

- [ ] Health check returns 200 UP
- [ ] Homepage loads, login works
- [ ] Admin panel accessible
- [ ] Content editing, uploads working
- [ ] WAF in Prevention mode (no false positives)
- [ ] Proxy IP working (check audit logs for real IPs, not WAF IP)
- [ ] Custom domain resolves with valid TLS cert
- [ ] CMS_URL set correctly (links use right domain)
- [ ] Backup test passed
- [ ] Monitoring alerts configured
- [ ] Runback procedure documented (switch DNS back if needed)

---

## Rollback Procedure

If critical issues arise after cutover:

1. **DNS cutover rollback** (instant):
   ```bash
   # Point CNAME back to old environment
   # Propagation: ~5 min globally
   ```

2. **Full rollback** (if needed):
   ```bash
   # Delete the resource group (removes all Azure resources)
   az group delete --name my-simis-rg --yes --no-wait
   ```

The most common rollback is DNS-only (seconds). Azure resource deletion takes 5–10 minutes.

---

## Troubleshooting

**App returns 503 (DOWN health check)**
- Check logs for error messages
- Verify DB password matches what's in Key Vault
- Verify CMS_PATH (Azure Files share) is mounted and writable
- Check Key Vault secrets exist and app has access (RBAC)

**Front Door returns 502 (Bad Gateway)**
- Private endpoint connection not approved (§3)
- App Service public network access check (should be disabled by Bicep)

**Database connectivity fails**
- Verify DB password in Key Vault (`db-password` secret)
- Check private endpoint for DB is approved
- Verify network ACL allows App Service subnet

**WAF blocks legitimate traffic**
- Switch to Detection mode temporarily
- Review blocked requests in logs
- Add exclusion rule for the pattern
- Return to Prevention mode

**Slow startup**
- Flyway migrations run on first boot (can take several minutes for large databases)
- Check logs for migration progress
- This is normal; be patient on first startup

---

## Next Steps

- Phase 3: Implement Key Vault secret rotation (on a schedule)
- Phase 4: Wire Sentinel for audit log analysis + alerting
- Phase 5: Test and document disaster-recovery procedures (full Azure region failover)
