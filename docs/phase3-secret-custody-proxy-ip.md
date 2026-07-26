# Phase 3: Secret Custody & Proxy IP — Implementation Guide

**Milestone #4 Phase 3**  
**Status:** Analysis complete; implementation pattern documented  
**Buildable:** Yes (no Azure subscription required)

---

## Overview

Phase 3 implements two critical Azure hardening controls:

1. **Secret Custody:** Database credentials and secrets read from Azure Key Vault (managed identity)
2. **Proxy IP Handling:** Real client IP extracted from Azure Front Door headers (audit logs, firewall, rate limiting)

**Good news:** Both are already implemented in the codebase. This document describes how they work and how to configure them for Azure.

---

## Part 1: Secret Custody & Managed Identity

### Current Implementation

**Database credentials today (docker-compose / local dev):**
- Read from environment variables: `DB_USER`, `DB_PASSWORD`, `DB_SERVER_NAME`, etc.
- Handled in `ContextListener.contextInitialized()` (lines 102-122)
- Loaded into Hikari connection pool properties

**Azure deployment (Phase 2 Bicep):**
- `appservice.bicep` creates an App Service with managed identity enabled
- `rbac.bicep` grants the app identity `Key Vault Secrets User` role
- App Service can read secrets from Key Vault without storing passwords

### How It Works (Architecture)

```
App starts
  └─ ContextListener reads DB_PASSWORD from env var (or Key Vault reference)
      └─ Hikari pool connects to PostgreSQL Flexible Server
          └─ Private endpoint → only accessible from App Service VNet
              └─ Credentials never stored in image/config/logs
```

### Three Patterns Supported

**Pattern A: Environment Variables (Current — local dev, docker-compose)**
```bash
export DB_PASSWORD=ci_local_test_pw
```
Used in: docker-compose.yaml, CI tests.

**Pattern B: Key Vault References (Azure App Service — Phase 3 implementation)**

Azure App Service supports `@Microsoft.KeyVault(SecretUri=...)` syntax in app settings. The runtime resolves the reference at startup:

```bash
# In App Service settings (via CLI or portal)
DB_PASSWORD = @Microsoft.KeyVault(SecretUri=https://my-vault.vault.azure.net/secrets/db-password/«version»)
```

App Service's managed identity automatically authenticates to Key Vault and retrieves the secret. The app sees `DB_PASSWORD=«actual-value»` in `System.getenv("DB_PASSWORD")`.

**Pattern C: Direct SDK Integration (Future enhancement)**

The app could directly call Azure Key Vault SDK:

```java
import com.azure.identity.DefaultAzureCredential;
import com.azure.security.keyvault.secrets.SecretClient;

DefaultAzureCredential credential = new DefaultAzureCredential();
SecretClient client = new SecretClientBuilder()
    .vaultUrl("https://my-vault.vault.azure.net")
    .credential(credential)
    .buildClient();

String dbPassword = client.getSecret("db-password").getValue();
```

This is more explicit but requires SDK dependency; Pattern B (Key Vault References) achieves the same result with less code.

### For Azure Deployment (Pattern B — Recommended)

**Phase 2 deployment (DEPLOYMENT.md §2):**

1. Create secrets in Key Vault:
   ```bash
   az keyvault secret set --vault-name my-vault --name db-password --value "«password»"
   ```

2. Set App Service environment variable:
   ```bash
   az webapp config appsettings set \
     --name my-app \
     --resource-group my-rg \
     --settings DB_PASSWORD="@Microsoft.KeyVault(SecretUri=https://my-vault.vault.azure.net/secrets/db-password/)"
   ```

3. App Service resolves the reference at startup; no code change needed. ✅

**Code level (ContextListener — no change):**

The existing code at line 110-112:
```java
if (System.getenv().containsKey("DB_PASSWORD")) {
  databaseProperties.setProperty("dataSource.password", System.getenv("DB_PASSWORD"));
}
```

Works unchanged. `System.getenv("DB_PASSWORD")` returns the secret value, whether it came from a local env var or Key Vault reference.

### Security Properties

- ✅ No passwords in image, config files, or git history
- ✅ Managed identity = no static credentials stored anywhere
- ✅ Key Vault access logged and auditable
- ✅ Secrets encrypted at rest in Key Vault
- ✅ Private endpoint between app and vault (no internet path)
- ✅ Rotation: change secret in Key Vault; app sees new value on next restart

---

## Part 2: Proxy IP Handling (Real Client IP)

### The Problem

When the app sits behind Azure Front Door (or any proxy/WAF), the direct TCP peer is the proxy, not the client:

```
Client IP: 203.0.113.5
    ↓ HTTPS
Proxy IP: 198.51.100.42
    ↓ Private Link
App sees: 198.51.100.42 (wrong!)
```

This breaks:
- **Audit logs:** Source IP recorded as proxy, not client
- **Firewall:** Rate limiting blocks proxy IP, affecting all clients behind it
- **Compliance:** Cannot audit by client IP

### Solution: TrustedProxyIpFilter

**File:** `TrustedProxyIpFilter.java`  
**Location:** `src/main/java/com/simisinc/platform/presentation/controller/`  
**Status:** ✅ Already implemented

### How It Works

1. **Configuration:** Set `CMS_TRUSTED_PROXIES` environment variable to a regex matching trusted proxy IP addresses
   ```bash
   export CMS_TRUSTED_PROXIES="198\.51\.100\..*"
   ```

2. **Filter initialization:** TrustedProxyIpFilter loads the regex, delegates to Tomcat's RemoteIpFilter
   ```java
   Filter delegate = new RemoteIpFilter();
   delegate.init(new InternalProxiesConfig(filterConfig, trustedProxies));
   ```

3. **Request handling:** For each request from a trusted proxy address, RemoteIpFilter reads `X-Forwarded-For` header and resets `getRemoteAddr()`:
   ```
   Request from 198.51.100.42 (trusted proxy)
     with X-Forwarded-For: 203.0.113.5
     ↓
   App sees: getRemoteAddr() = 203.0.113.5 ✅
   ```

4. **Spoofing protection:** Requests from untrusted IPs are passed through unchanged. An attacker cannot set `X-Forwarded-For` from an arbitrary address.

### Configuration for Azure Front Door

**Step 1: Get Front Door backend IP ranges**

Azure publishes service-tag IP ranges:

```bash
# Download and extract AzureFrontDoor.Backend ranges
curl -s https://www.microsoft.com/en-us/download/details.aspx?id=56519 | \
  jq '.[] | select(.name=="AzureFrontDoor") | .prefixes | .[] | select(startswith("148\.|2607:f758"))'
```

Or manually check [Azure IP Ranges](https://www.microsoft.com/en-us/download/details.aspx?id=56519) for current ranges (they change ~weekly).

**Example current ranges (verify before using):**
```
148.58.0.0/15
148.60.0.0/14
148.64.0.0/11
2607:f758::/32
```

**Step 2: Convert to regex**

The app's `CMS_TRUSTED_PROXIES` value is a regex pattern that matches the proxy IP. For Azure Front Door:

```bash
# CIDR-to-regex conversion (these are the published ranges)
# 148.58.0.0/15 → 148.58.* or 148.59.*
# 148.60.0.0/14 → 148.60.* through 148.63.*
# etc.

# Simplified: match the known Front Door ranges
export CMS_TRUSTED_PROXIES="148\.5[89]\..+|148\.6[0-3]\..+|148\.[678][0-9]\..+|2607:f758:.*"
```

Or more precisely (if you have the full list):
```bash
export CMS_TRUSTED_PROXIES="148\.58\..+|148\.59\..+|148\.60\..+|148\.61\..+|148\.62\..+|148\.63\..+|2607:f758:.*"
```

**Step 3: Set in App Service (via Azure CLI)**

```bash
az webapp config appsettings set \
  --name my-simis-cms-app \
  --resource-group my-rg \
  --settings CMS_TRUSTED_PROXIES="148\.58\..+|148\.59\..+|..."
```

Or via App Service settings in the Azure portal.

**Step 4: Restart the app**

```bash
az webapp restart --name my-simis-cms-app --resource-group my-rg
```

**Step 5: Verify**

Check app logs for confirmation:

```
CMS_TRUSTED_PROXIES is set; resolving the client IP from X-Forwarded-For for trusted proxies
```

If logs show:
```
CMS_TRUSTED_PROXIES is not set; the client IP is read from the direct connection
```

Then the setting wasn't loaded. Restart and check again.

### Testing Proxy IP Handling

**From the app (logs):**
```bash
# App logs show audit source IP
curl -v https://my-app.azure.com/

# Logs should show real client IP (your IP), not proxy IP
```

**Manually trigger from App Service CloudShell:**
```bash
# Test without proxy (direct connection)
curl -v http://localhost:8080/healthz

# Headers show: X-Forwarded-For not used; sees direct conn
# getRemoteAddr() = 127.0.0.1 (inside container)

# Test with Front Door headers (simulated)
curl -H "X-Forwarded-For: 203.0.113.5" \
     -H "X-Forwarded-Proto: https" \
     http://localhost:8080/healthz

# If CMS_TRUSTED_PROXIES is set AND request from trusted IP:
# getRemoteAddr() = 203.0.113.5 ✅
```

### Maintenance: Update Service Tags Monthly

Azure publishes updated service-tag IP ranges ~weekly. Update the regex monthly:

1. Download latest ranges from Azure's service-tags JSON
2. Extract `AzureFrontDoor.Backend` CIDR blocks
3. Convert to regex (or just update the CIDR list)
4. Update `CMS_TRUSTED_PROXIES` in App Service settings
5. Restart the app

---

## Part 3: Health Check Endpoint

**File:** `WebRequestFilter.java` (doHealthCheck method)  
**Supporting file:** `HealthCommand.java`  
**Status:** ✅ Already hardened and production-ready

### What It Checks

```java
boolean ready = startedUp(context)           // ContextListener finished
             && databaseReachable()          // DB pool has valid connection
             && fileStoreWritable();         // CMS_PATH (Azure Files) mounted + writable
```

Returns:
- **200 UP** — all checks pass → app is ready to serve
- **503 DOWN** — any check fails → app is not ready (load balancer removes from rotation)

### Response Format

```json
{"status":"UP"}
```

or

```json
{"status":"DOWN"}
```

No detail disclosed (prevents information leakage).

### Azure Integration

**App Service Health check:**

```bash
az webapp config set \
  --name my-app \
  --resource-group my-rg \
  --generic-configurations '{
    "healthCheckPath": "/healthz"
  }'
```

App Service probes `/healthz` every 60 seconds. If it returns 503 for >120 seconds, the instance is marked unhealthy and may be restarted.

**Container HEALTHCHECK:**

Already in `docker/db/Dockerfile` and `docker/app/Dockerfile`. Orchestrators use this for liveness/readiness.

### Constraints & Design

**Readiness, not liveness:**

The check gates OUT of rotation; it does NOT restart the app. Why?

If the database is unavailable, every instance would fail the check and restart, crash-looping the entire fleet. Instead:
- Failed health check → load balancer drains connections, instance goes offline
- Database comes back → instances recover automatically
- No crash loop 🎯

---

## Implementation Checklist for Azure Deployment

- [ ] **Secret Custody**
  - [ ] Key Vault created (Phase 2 Bicep)
  - [ ] RBAC: App Service identity has `Key Vault Secrets User` role (Phase 2 Bicep)
  - [ ] Three secrets created: `db-password`, `cms-secret-key`, `cms-admin-password` (DEPLOYMENT.md §2)
  - [ ] App Service env vars set with Key Vault references (DEPLOYMENT.md §2)
  - [ ] App boots and logs show successful DB connection ✅

- [ ] **Proxy IP Handling**
  - [ ] TrustedProxyIpFilter is in codebase (already is)
  - [ ] `CMS_TRUSTED_PROXIES` regex retrieved from Azure service tags (DEPLOYMENT.md §5)
  - [ ] Env var set in App Service settings
  - [ ] App restarted
  - [ ] Logs show: `CMS_TRUSTED_PROXIES is set; resolving the client IP` ✅

- [ ] **Health Check**
  - [ ] `/healthz` endpoint responds 200 (logs show app is UP)
  - [ ] App Service "Health check" configured to `/healthz`
  - [ ] Container HEALTHCHECK in Dockerfile (already is) ✅

---

## Code References

| Component | File | Line | Purpose |
|-----------|------|------|---------|
| DB password read | ContextListener.java | 110–112 | Read DB_PASSWORD from env var |
| Proxy IP filter | TrustedProxyIpFilter.java | 1–143 | Delegate to RemoteIpFilter on trusted IPs |
| Health check | WebRequestFilter.java | doHealthCheck | Return 200/503 based on readiness |
| Health checks | HealthCommand.java | isReady | Check startup + DB + file store |

---

## What's NOT in Phase 3 (Deferred)

- **Secret rotation:** Future; implement a scheduled job to rotate DB password monthly
- **Direct Key Vault SDK:** Future enhancement; Pattern B (Key Vault References) sufficient for now
- **Audit trail:** Sentinel wiring is Phase 4 (needs Azure Sentinel subscription)

---

## Summary

Phase 3 leverages existing implementations:
1. **Secret Custody:** Pattern B (Key Vault References) requires only Azure App Service configuration; no code change
2. **Proxy IP:** TrustedProxyIpFilter already wired; just set `CMS_TRUSTED_PROXIES` regex for Azure Front Door ranges
3. **Health Check:** Production-hardened; set App Service "Health check" to `/healthz` and go

All components are buildable now. Azure-specific configuration happens at deployment time (Phase 2 runbook).
