# Phase 5: Cutover & Operational Readiness — Go-Live Runbook

**Milestone #4 Phase 5**  
**Status:** Final phase (executes with live Azure subscription)  
**Purpose:** Fresh install, go-live procedures, disaster recovery

---

## Overview

Phase 5 executes the live deployment and establishes operational procedures:

1. **Fresh Install** — First boot, database initialization, admin bootstrap
2. **Backup & Restore Test** — Verify recovery procedures work
3. **DNS Cutover** — Switch user traffic from old to new environment
4. **Go-Live Verification** — Final sign-off checklist
5. **Disaster Recovery** — Region failover, data recovery procedures
6. **Rollback Procedure** — Quick recovery if critical issues arise

---

## Part 1: Fresh Install Procedure

### Prerequisites

- ✅ Phases 1-4 complete (images hardened, infrastructure deployed, monitoring live)
- ✅ Key Vault secrets created (db-password, cms-secret-key, cms-admin-password)
- ✅ Private endpoint approved (Front Door ↔ App Service)
- ✅ Health check passing (App Service shows "Healthy")
- ✅ Sentinel monitoring active (log ingestion, alert rules configured)

### Step 1: Bootstrap Admin Account

The app needs an initial admin user created at first boot. This happens via Flyway migration:

```sql
-- File: src/main/resources/db/migration/V001__initial_schema.sql
-- (This is part of the standard schema installation)

-- Create initial admin user (only runs once)
INSERT INTO users (email, password_hash, role, is_active, created_at)
VALUES (
  'admin@example.org',
  crypt('«CMS_ADMIN_PASSWORD from Key Vault»', gen_salt('bf')),
  'admin',
  true,
  now()
)
ON CONFLICT DO NOTHING;
```

**How it works:**
1. App starts → ContextListener initializes database
2. Flyway runs migrations (includes admin creation)
3. Admin password comes from `CMS_ADMIN_PASSWORD` Key Vault secret
4. After first boot, admin account exists and can log in

**Verify:** 
```bash
# After first boot, app logs should show:
# "Flyway version X.Y" 
# "Successfully validated 42 migrations"
# "Successfully applied 42 migrations"

# Then try login:
curl -s https://my-app.azure.com/admin/login
# Should return login form (no 500 error)
```

### Step 2: Content Re-creation

Since this is a fresh install, content must be manually created:

1. **Log in as admin:**
   ```
   URL: https://my-app.azure.com/admin
   Email: admin@example.org
   Password: (from CMS_ADMIN_PASSWORD)
   ```

2. **Create initial pages/structure:**
   - Homepage (via CMS UI)
   - About page
   - Contact page
   - Navigation menus

3. **Upload initial files:**
   - Logo / branding assets
   - Document templates

4. **Configure settings:**
   - Site name, URL
   - Email settings (SMTP relay)
   - Analytics codes
   - Custom branding

### Step 3: Verify All Systems

Health check query:
```bash
curl -s https://my-app.azure.com/healthz | jq .
# Expected: {"status":"UP"}
```

Access logs:
```kusto
ContainerAppConsoleLogs
| where Log has "localhost_access_log"
| order by TimeGenerated desc
| limit 10
```

App logs (no errors):
```kusto
SimISAppLog
| where IsError
| order by TimeGenerated desc
| limit 5
# Expected: 0 results (no errors)
```

---

## Part 2: Backup & Restore Test

**Objective:** Verify recovery procedures work before go-live.

### Backup Strategy (Automatic)

Azure DB for PostgreSQL Flexible Server includes:
- **Automated daily backups** (7-day retention by default)
- **Point-in-time restore (PITR)** — restore to any moment in last 7 days
- **Long-term backups** (optional, up to 10 years)

### Manual Backup (Optional)

```bash
# Export full database for archival
az postgres flexible-server backup create \
  --name simiscms-pilot \
  --resource-group my-rg \
  --backup-name manual-backup-2026-07-27

# List backups
az postgres flexible-server backup list \
  --name simiscms-pilot \
  --resource-group my-rg
```

### Restore Test Procedure

**Goal:** Restore to a test server, verify it boots successfully.

```bash
# 1. Restore from backup (to a new test instance)
az postgres flexible-server server create \
  --name simiscms-pilot-restore-test \
  --resource-group my-rg \
  --restore-time 2026-07-26T17:00:00Z \
  --source-server simiscms-pilot

# 2. Wait for restore (~5-10 min)
az postgres flexible-server server show \
  --name simiscms-pilot-restore-test \
  --resource-group my-rg \
  --query "state"
# Expected: "Ready"

# 3. Test connectivity
psql -h simiscms-pilot-restore-test.postgres.database.azure.com \
     -U simiscmsadmin \
     -d simis-cms \
     -c "SELECT version();"
# Should return PostgreSQL version

# 4. Run deploy smoke test against restored DB
./deploy-smoke-test.sh --database-host simiscms-pilot-restore-test.postgres.database.azure.com

# 5. If successful, delete test instance
az postgres flexible-server server delete \
  --name simiscms-pilot-restore-test \
  --resource-group my-rg \
  --yes
```

### Recovery Time Objective (RTO)

| Scenario | RTO | Procedure |
|----------|-----|-----------|
| App crash (container restart) | 1-2 min | Auto-restart via health check |
| DB connection drop | 2-5 min | Wait for DB to come back online |
| Full database loss (corruption) | 30 min | PITR restore (+ app boot ~2 min) |
| Full region loss | 60 min | Failover to secondary region (Phase 6) |

---

## Part 3: DNS Cutover (Go-Live)

### Pre-Cutover Validation

**1 hour before cutover, verify:**

```bash
# Health check
curl -s https://«frontDoorEndpointHostName»/healthz | jq .
# Expected: {"status":"UP"}

# Homepage
curl -s https://«frontDoorEndpointHostName»/ | grep -i "title\|h1" | head -3
# Expected: Real homepage content

# Admin access
curl -s -I https://«frontDoorEndpointHostName»/admin/login | grep "200"
# Expected: HTTP 200

# Monitoring
# Check Sentinel workbook → App Health Status tile
# Expected: All green (health=UP, DB=connected, file store=writable)
```

### DNS Switch Procedure

**Current state (before cutover):**
```
Old environment          New environment (Azure)
   ↓                              ↑
Users access            Not yet used
OLD_IP                   (Front Door endpoint)
```

**After cutover:**
```
Old environment          New environment (Azure)
   X                              ↑
DNS points to           Users access
New Front Door           New environment
```

**Steps:**

1. **Create DNS record** (if not already done in Phase 2):
   ```bash
   # In your DNS provider (Route53, Azure DNS, Cloudflare, etc.)
   CNAME: www.example.org → «frontDoorEndpointHostName»
   TTL: 300 (5 min, for quick rollback if needed)
   ```

2. **Verify CNAME resolves:**
   ```bash
   nslookup www.example.org
   # Expected: Points to «frontDoorEndpointHostName»
   ```

3. **Wait for DNS propagation** (typically 5-15 min, up to 48h):
   ```bash
   # Check global DNS propagation
   dig @8.8.8.8 www.example.org
   dig @1.1.1.1 www.example.org
   # Both should resolve to Front Door endpoint
   ```

4. **Test via custom domain:**
   ```bash
   curl -s https://www.example.org/healthz | jq .
   # Expected: {"status":"UP"}
   
   curl -s https://www.example.org/ | grep title
   # Expected: Real homepage
   ```

5. **Monitor traffic switch:**
   - Watch App Service logs for request volume increase
   - Check Front Door request metrics (should climb)
   - Old environment should see request drop to zero over 30 min

### Rollback (If Issues Arise)

If critical issues appear after DNS cutover:

```bash
# Point DNS back to old environment (instant)
# CNAME: www.example.org → old-ip-or-endpoint

# Rollback time: ~5 min (DNS propagation) + time to fix issue

# After rollback, investigate root cause before re-cutover
```

---

## Part 4: Go-Live Sign-Off Checklist

**Complete this checklist before declaring go-live successful:**

### Pre-Cutover (Execute 1 hour before)
- [ ] Health check endpoint returning 200 UP
- [ ] Homepage loads without errors (via Front Door)
- [ ] Admin panel accessible (login form loads)
- [ ] Monitoring dashboard populated (no errors visible)
- [ ] Alert rules configured and tested
- [ ] On-call rotation active (team aware of cutover)
- [ ] Backup created and tested (restore procedure works)

### During Cutover (Execute cutover procedure above)
- [ ] CNAME DNS record created
- [ ] DNS resolves globally (check multiple public DNS servers)
- [ ] TLS certificate valid (check cert details)
- [ ] Initial traffic flows to new environment (logs show requests)
- [ ] No error spike (error rate remains low)
- [ ] Old environment traffic declining (not abrupt drop)

### Post-Cutover (1 hour after cutover)
- [ ] 100% of traffic to new environment (old should be ~0 requests)
- [ ] No critical alerts fired (health/DB/file store all UP)
- [ ] User-initiated tests pass (create content, upload file, etc.)
- [ ] Request audit logs show expected traffic patterns
- [ ] Performance acceptable (response times < 500ms)
- [ ] No cascading errors in logs

### Final Sign-Off
- [ ] Product owner / business stakeholder tested and approves
- [ ] Ops team confirms monitoring is working
- [ ] Security review complete (no vulnerabilities spotted)
- [ ] Documentation updated (runbooks, alerts, team wiki)
- [ ] Post-mortem planned (review what went well/poorly after 1 week)

**Sign-off:** Signed off by _____________ (ops lead) at _____________ (time)

---

## Part 5: Disaster Recovery Procedures

### Scenario 1: Database Corruption (Rare)

**Symptom:** App returns 500 errors; logs show DB errors; health check fails.

**Recovery:**
1. Check DB status in Azure portal (might just be restarting)
2. If really down, restore from PITR backup:
   ```bash
   az postgres flexible-server server create \
     --name simiscms-pilot-restored \
     --restore-time 2026-07-26T16:00:00Z \
     --source-server simiscms-pilot
   
   # Update app connection string to new DB
   # (via App Service environment variables)
   
   # Restart app to pick up new DB host
   az webapp restart --name simis-cms-pilot --resource-group my-rg
   ```
3. Verify health check passes
4. Delete corrupted old DB once verified

### Scenario 2: File Store Mount Failure

**Symptom:** App returns 503; health check fails on file store writable; logs show mount errors.

**Recovery:**
1. SSH to app container
2. Check mount: `df -h | grep simis`
3. If unmounted, restart app container (often remounts automatically)
4. If still unmounted, check Azure Files status
5. Verify storage account connectivity
6. Remount manually: `mount /opt/simis`
7. Verify: `touch /opt/simis/test && rm /opt/simis/test`

### Scenario 3: App Memory Leak / Slow Response

**Symptom:** Response times climbing; health check times out; app CPU high.

**Recovery:**
1. Check logs for memory leak pattern (unbounded queue, cache growth, etc.)
2. If can be fixed: deploy new app version
3. If needs immediate fix: restart app (`az webapp restart`)
4. Monitor recovery via Sentinel workbook (response times should drop)

### Scenario 4: Full Region Failure (Azure Outage)

**Symptom:** App Service region goes down; DNS resolves but no connectivity; Azure status page shows outage.

**Recovery (Multi-Region Failover — Phase 6+):**

This requires a secondary region deployment. Procedure:
1. Detect: Sentinel alert fires "app unreachable from all regions"
2. Failover: Update DNS to point to secondary region Front Door
3. Recovery: Secondary region takes traffic; ops investigates primary
4. Restore: Once primary recovers, migrate traffic back

**Note:** Phase 5 is single-region only. Multi-region failover is Phase 6+ work.

---

## Part 6: Incident Response Procedures

### Alert: App Down (Health Check 503)

```
1. Alert fires: Sentinel → Teams → On-call page
2. On-call: Check Sentinel workbook
   - If app unavailable: likely DB or file store issue
   - If intermittent: likely network blip (wait 2 min, recheck)
3. Investigate:
   - SSH to container
   - Check logs: docker logs $(docker ps -q) | tail -50
   - Check health: curl http://localhost:8080/healthz
4. Recovery: Likely need restart or DB reconnection
   - Restart: az webapp restart --name simis-cms-pilot --resource-group my-rg
   - Wait 2 min for app to boot
   - Verify: curl https://www.example.org/healthz
5. Post-incident: Check logs for root cause (DB timeout? File store issue? Out of memory?)
```

### Alert: Database Unreachable

```
1. Alert fires: Sentinel → on-call
2. Check Azure DB status in portal:
   - Is it "Available"?
   - Is it restarting?
   - Any CPU/storage alerts?
3. If DB is fine but app can't reach:
   - Check private endpoint connection (App Service → Networking → Private endpoints)
   - Check network security rules
   - Restart private endpoint if needed
4. Recovery: Usually just wait for DB to be available; app reconnects automatically
5. If taking >5 min, escalate to Azure support
```

### Alert: Error Rate Spike

```
1. Alert fires: Error count > 10 in 5 min
2. Check Sentinel → Error logs
3. Identify error pattern:
   - Is it from one component (database, file store, security)?
   - Is it a repeated error or many different errors?
4. Recovery options:
   - If temporary (network blip): wait and recheck
   - If cascading: likely need restart
   - If code issue: likely need code fix + deploy
5. Escalate if unresolved after 15 min
```

---

## Part 7: Post-Go-Live Operations

### Day 1 (Cutover Day)
- Monitor health dashboard continuously
- Watch error rates (should be zero)
- Check request logs (should see steady traffic)
- Team stays on-call (quick response to any issues)

### Week 1
- Daily health checks (app, DB, file store all UP)
- Review error logs daily (should be empty)
- Check performance metrics (response times, request rates)
- Document any issues and fixes
- Hold debrief meeting with team

### Month 1
- Weekly health review (trending metrics)
- Test backup/restore procedure again
- Refine alert thresholds (too noisy? not catching issues?)
- Update runbooks based on any incidents
- Performance baseline established

### Quarterly
- Full disaster recovery test (restore from backup to test environment)
- Review monitoring strategy (are we catching what matters?)
- Update runbooks and escalation procedures
- Plan any needed infrastructure upgrades

---

## Runbook Summary

| Scenario | Time | Procedure |
|----------|------|-----------|
| App crash | 2 min | Restart via az webapp restart |
| DB connection drop | 5 min | Wait for DB to come back; app reconnects auto |
| Database corruption | 30 min | Restore from PITR backup |
| File store unmount | 5 min | Restart container (usually remounts) |
| Full region loss | 60 min | Failover to secondary region (Phase 6) |
| Critical bug found | 15 min | Fix + deploy new app version |

---

## Completion Checklist

**Phase 5 Complete When:**
- [ ] Fresh install procedure tested (admin account created, bootstrap works)
- [ ] Backup/restore procedure tested (restore test passed)
- [ ] DNS cutover procedure documented and approved
- [ ] Go-live sign-off checklist signed off by all stakeholders
- [ ] Disaster recovery procedures documented and reviewed
- [ ] Incident response runbooks reviewed with ops team
- [ ] On-call rotation confirmed (team trained, schedules set)
- [ ] Monitoring live (Sentinel parser active, all alert rules firing)
- [ ] Cutover executed (users now on new environment)
- [ ] Post-cutover verification complete (all systems green)

---

## Success Criteria

**Milestone #4 is complete when:**
- ✅ Phase 1: Container hardening deployed (images hardened, CI secure)
- ✅ Phase 2: Infrastructure as Code deployed (Azure resources provisioned)
- ✅ Phase 3: Secret custody & proxy IP configured (no passwords in config, real client IP in logs)
- ✅ Phase 4: Sentinel monitoring live (alerts firing, workbook populated)
- ✅ Phase 5: Cutover complete and operational (users on new environment, incident response ready)

**Result:** simis-cms running on Azure with hardened images, managed infrastructure, secure secrets, live monitoring, and tested disaster recovery. ✅

---

## Post-Deployment Support

**For 30 days after go-live:**
- Dedicated on-call engineer (24/7 support)
- Daily standups to discuss any issues
- Weekly performance review meetings
- Rapid deployment capability for critical fixes
- 24/7 escalation to Microsoft Azure support if needed

**After 30 days (steady state):**
- Normal on-call rotation (1 engineer on-call)
- Weekly health reviews
- Monthly disaster recovery drills
- Quarterly performance optimization

---

## Documentation & Knowledge Transfer

**Completed documents** (all phases):
- [Phase 1: Container hardening](../docs/)
- [Phase 2: Deployment runbook](../infra/DEPLOYMENT.md)
- [Phase 3: Secret custody & proxy IP](../docs/phase3-secret-custody-proxy-ip.md)
- [Phase 4: Sentinel observability](../docs/phase4-sentinel-observability.md)
- [Phase 5: Cutover runbook](../docs/phase5-cutover-runbook.md)

**Team training required:**
- DevOps/Ops: Full Phase 2 (IaC), Phase 4 (monitoring), Phase 5 (cutover & incident response)
- Security: Phase 1 (hardening), Phase 3 (secrets)
- Product/SRE: Phase 4 (monitoring), Phase 5 (procedures)

**Training checklist:**
- [ ] Ops team trained on Phase 2 IaC
- [ ] Ops team trained on Phase 4 monitoring & runbooks
- [ ] Ops team trained on Phase 5 cutover & incident response
- [ ] Security team reviewed Phase 1 hardening
- [ ] Security team reviewed Phase 3 secret custody
- [ ] All team members signed off on procedures
