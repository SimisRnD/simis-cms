# Phase 4: Observability & Sentinel — Monitoring Rules & Queries

**Milestone #4 Phase 4**  
**Status:** Ready for implementation (needs Azure Sentinel workspace + Log Analytics)  
**Gated on:** Azure subscription with Sentinel license

---

## Overview

Phase 4 wires the monitoring infrastructure:

1. **Log Analytics workspace** ← App logs via stdout (Path A)
2. **Sentinel parser** ← Parses app logs into structured fields
3. **KQL queries** ← Monitor health, errors, performance, security
4. **Alert rules** ← Trigger on anomalies
5. **Workbook** ← Ops dashboard with key metrics
6. **On-call routing** ← Connect alerts to PagerDuty / Teams / email

---

## Part 1: Log Ingestion & Parsing

### Log Flow

```
App stdout
  ↓ (container logs)
App Service console logs
  ↓ (diagnostic settings)
Log Analytics workspace
  ↓ (Sentinel parser)
Structured tables (AppLogs, SecurityEvents, etc.)
  ↓ (KQL queries & rules)
Alerts & Workbook
```

### Phase 2 Setup (Already Configured)

**infra/loganalytics.bicep** creates:
- Log Analytics workspace (name: `simiscms-pilot-law`)
- Diagnostic settings on App Service (send stdout to workspace)
- Retention: configurable (default 90 days)

### Sentinel Parser

The parser transforms raw log lines into structured fields. Create in Sentinel:

**Parser name:** `SimISAppLog`

```kusto
// Parser: SimISAppLog
// Parses simis-cms application logs from stdout
// 
// Log format examples:
//   [INFO] 12:34:56.789 com.simisinc.platform.presentation.controller.ContextListener - Starting up...
//   [ERROR] 12:34:57.890 com.simisinc.platform.application.HealthCommand - Database unreachable
//   26-Jul-2026 16:48:05.776 INFO [main] org.apache.catalina.startup.VersionLoggerListener.log Server version name: Tomcat/11

let LogData = ContainerAppConsoleLogs
  | where ContainerAppName == "simis-cms" or ContainerAppName matches regex "simiscms.*"
  | project TimeGenerated, Log;

LogData
| parse Log with 
    "[" LogLevel:string "]" @" " LogTime:string " " LoggerClass:string " - " Message:string
  | where isnotempty(Message)
| extend
    Severity = case(
      LogLevel == "ERROR" or LogLevel == "FATAL", "High",
      LogLevel == "WARN", "Medium",
      LogLevel == "INFO", "Informational",
      "Low"
    ),
    Component = case(
      LoggerClass has "ContextListener", "startup",
      LoggerClass has "HealthCommand", "health",
      LoggerClass has "DatabaseCommand", "database",
      LoggerClass has "SchedulerManager", "scheduler",
      LoggerClass has "SecurityListener", "security",
      LoggerClass has "FileSystemCommand", "filesystem",
      "application"
    ),
    IsError = LogLevel == "ERROR" or LogLevel == "FATAL",
    IsWarning = LogLevel == "WARN"
| project 
    TimeGenerated,
    LogLevel,
    Severity,
    Component,
    LoggerClass,
    Message,
    IsError,
    IsWarning
```

**To deploy in Sentinel:**
1. Go to Sentinel → Logs
2. Click "+" (create) → Parser
3. Paste the above query
4. Save as `SimISAppLog`

---

## Part 2: KQL Monitoring Queries

### Query 1: App Health Status

```kusto
SimISAppLog
| where Component == "health"
| summarize
    HealthChecks = count(),
    SuccessCount = countif(Message has "UP"),
    FailureCount = countif(Message has "DOWN"),
    LastCheck = max(TimeGenerated)
  by bin(TimeGenerated, 5m)
| project
    TimeGenerated,
    HealthChecks,
    SuccessCount,
    FailureCount,
    SuccessRate = toreal(SuccessCount) / HealthChecks * 100,
    LastCheck
| order by TimeGenerated desc
```

**Purpose:** Track health check success rate (should be 100%; any failure means app is not ready).

### Query 2: Startup Verification

```kusto
SimISAppLog
| where Component == "startup"
| parse Message with * "Server startup in" StartupTime:string "milliseconds" *
| extend StartupMs = toint(trim_start(@' ', StartupTime))
| summarize
    StartupCount = count(),
    AvgStartupMs = avg(StartupMs),
    MaxStartupMs = max(StartupMs),
    ErrorCount = countif(IsError),
    LastStartup = max(TimeGenerated)
  by todate(TimeGenerated)
| project
    Date = tostring(TimeGenerated),
    StartupCount,
    AvgStartupMs,
    MaxStartupMs,
    ErrorCount,
    LastStartup
| order by Date desc
```

**Purpose:** Track startup performance over time (should be <3 seconds; >5s indicates issues).

### Query 3: Database Connectivity Issues

```kusto
SimISAppLog
| where Component == "database" or Message has "database" or Message has "SQLException"
| where IsError or IsWarning
| summarize
    ErrorCount = count(),
    UniqueErrors = dcount(Message),
    LastError = max(TimeGenerated)
  by Component, LogLevel
| order by ErrorCount desc
```

**Purpose:** Alert on database connectivity issues (0 errors expected).

### Query 4: File Store Warnings

```kusto
SimISAppLog
| where Component == "filesystem" or Message has "CMS_PATH"
| where IsError or IsWarning
| extend
    IssueType = case(
      Message has "writable", "not_writable",
      Message has "not found", "not_found",
      Message has "mount", "mount_issue",
      "unknown"
    )
| summarize
    Count = count(),
    FirstSeen = min(TimeGenerated),
    LastSeen = max(TimeGenerated)
  by IssueType, LogLevel
| order by Count desc
```

**Purpose:** Catch file store mounting or write failures (0 errors expected).

### Query 5: Error Rate & Trend

```kusto
SimISAppLog
| where IsError
| summarize
    ErrorCount = count(),
    UniqueComponents = dcount(Component),
    TopError = arg_max(Message, TimeGenerated)
  by bin(TimeGenerated, 5m)
| extend
    Trend = case(
      ErrorCount > 10, "High",
      ErrorCount > 3, "Medium",
      ErrorCount > 0, "Low",
      "None"
    )
| order by TimeGenerated desc
| limit 50
```

**Purpose:** Track error rate and trend (early detection of cascading failures).

### Query 6: Audit Trail (Request Logs)

```kusto
ContainerAppConsoleLogs
| where ContainerAppName == "simis-cms"
| where Log has "localhost_access_log"
| parse Log with * " \"" Method:string " " Path:string " " * "\" " Status:int " " *
| extend
    StatusClass = case(
      Status >= 500, "ServerError",
      Status >= 400, "ClientError",
      Status >= 300, "Redirect",
      Status >= 200, "Success",
      "Other"
    ),
    IsError = Status >= 400
| summarize
    RequestCount = count(),
    ErrorCount = countif(IsError),
    AvgStatus = avg(Status),
    TopPaths = make_set(Path, 5)
  by bin(TimeGenerated, 5m), StatusClass
| order by TimeGenerated desc
| limit 100
```

**Purpose:** Audit all requests (for compliance, tracing, DDoS detection).

---

## Part 3: Alert Rules

### Alert 1: App Down (Health Check Failing)

**Trigger:** Health check returning 503 for >2 minutes

```kusto
SimISAppLog
| where Component == "health"
| where Message has "DOWN"
| summarize DownCount = count() by bin(TimeGenerated, 1m)
| where DownCount >= 5  // More than 5 DOWN logs in 1 min = critical
```

**Action:** Page on-call engineer immediately  
**Severity:** Critical  
**Recovery:** Restart App Service, check DB connectivity

### Alert 2: Database Unreachable

**Trigger:** Database connection errors for >1 minute

```kusto
SimISAppLog
| where Component == "database" and IsError
| summarize DBErrorCount = count() by bin(TimeGenerated, 1m)
| where DBErrorCount >= 3
```

**Action:** Alert ops (page if >5 min)  
**Severity:** Critical  
**Recovery:** Check Azure DB connectivity, check network ACLs

### Alert 3: File Store Unmounted

**Trigger:** CMS_PATH write failures

```kusto
SimISAppLog
| where Component == "filesystem" and IsError
| where Message has "writable" or Message has "mount"
| summarize FSErrorCount = count() by bin(TimeGenerated, 1m)
| where FSErrorCount >= 1
```

**Action:** Alert ops  
**Severity:** Critical  
**Recovery:** Remount Azure Files, check storage account connectivity

### Alert 4: High Error Rate

**Trigger:** >10 errors in 5 minutes

```kusto
SimISAppLog
| where IsError
| summarize ErrorCount = count() by bin(TimeGenerated, 5m)
| where ErrorCount > 10
```

**Action:** Alert on-call  
**Severity:** High  
**Recovery:** Check logs for error pattern, may require restart or code fix

### Alert 5: WAF False Positives

**Trigger:** Spike in 403 responses (possible false positive tuning needed)

```kusto
ContainerAppConsoleLogs
| where Log has "localhost_access_log" and Log has " 403 "
| summarize BlockedCount = count() by bin(TimeGenerated, 5m)
| where BlockedCount > 20  // Threshold for ops review
```

**Action:** Alert ops (not urgent; review next day)  
**Severity:** Low  
**Recovery:** Review WAF rules, add exclusions if legitimate

### Alert 6: Slow Startup (Cascading Issue Indicator)

**Trigger:** Startup time >5 seconds (indicates DB/network issues)

```kusto
SimISAppLog
| where Component == "startup"
| parse Message with * "Server startup in" StartupTime:string "milliseconds" *
| extend StartupMs = toint(trim_start(@' ', StartupTime))
| where StartupMs > 5000
```

**Action:** Alert on-call  
**Severity:** Medium  
**Recovery:** Check database performance, network latency, migrations

---

## Part 4: Ops Workbook

Create a workbook in Sentinel for the ops dashboard. This can be built in the Azure portal UI or via JSON:

### Dashboard Sections

**Section 1: System Health (Top)**
```
Tile: App Health Status (5m)
  Query: Health check success rate
  Visualize: Large number (green if 100%, red if <100%)

Tile: Database Connectivity (5m)
  Query: DB error count
  Visualize: Large number (green if 0, red if >0)

Tile: File Store Status (5m)
  Query: File system error count
  Visualize: Large number (green if 0, red if >0)
```

**Section 2: Errors & Logs (Historical)**
```
Table: Recent Errors (last 24h)
  Query: Error rate query
  Columns: TimeGenerated, Component, LogLevel, Message
  Order: Most recent first
  Limit: 50

Chart: Error Rate Trend (last 7d)
  Query: Error count per 5-min bin
  Visualize: Line chart
  Y-axis: Count, X-axis: Time
```

**Section 3: Request Audit (Performance)**
```
Table: Request Summary (last 24h)
  Query: Audit trail query
  Columns: TimeGenerated, StatusClass, RequestCount, ErrorCount
  
Chart: Request Rate (last 7d)
  Query: Request count per 5-min bin
  Visualize: Bar chart
```

**Section 4: Startup & Deployment**
```
Table: Recent Startups (last 7d)
  Query: Startup verification query
  Columns: Date, StartupCount, AvgStartupMs, MaxStartupMs, ErrorCount
  
Metric: Last Startup Success
  Query: Most recent startup
  Visualize: Green (success) or Red (failure)
```

---

## Part 5: On-Call Alert Routing

### Integration Options

**Option A: Email (Simple)**
```
Alert Rule → Email action → ops-oncall@company.com
Subject: [ALERT] App down
Body: See Sentinel workspace for details
```

**Option B: Teams (Recommended)**
```
Azure → Logic Apps → Create "When a Sentinel alert is triggered"
  → Post message to Teams channel: #simis-cms-ops-alerts
  → @mention @on-call-engineer
  → Include alert details (time, severity, app health status)
```

**Option C: PagerDuty (Full On-Call)**
```
Sentinel alert → Logic App → PagerDuty API
  → Create incident
  → Assign to on-call schedule
  → Page on-call engineer (SMS if critical)
```

### Recommended Setup

1. **Email alerts** for all rules (automatic notification)
2. **Teams channel** for visibility across ops team
3. **PagerDuty escalation** for Critical severity only (app down, DB down, file store down)

### Escalation Policy

```
Severity: Critical (app down, DB down, file store down)
  → Page on-call immediately (SMS after 5 min if unacked)
  → Escalate to manager after 15 min

Severity: High (error rate spike, slow startup)
  → Teams notification
  → Page on-call if unresolved after 30 min

Severity: Medium (WAF tuning, connection timeouts)
  → Email to ops-oncall
  → Review next business day

Severity: Low (informational trends)
  → Log only, no alert
```

---

## Part 6: Runbook Reference

When alerts fire, on-call should follow these runbooks:

### Runbook: App Health Check Failing

```
1. Check Sentinel workbook → App Health Status tile
   - If 0%, app is not ready
   - If intermittent, database or network issue

2. SSH to App Service container:
   az webapp ssh --name simis-cms-pilot --resource-group my-rg
   
3. Check app logs:
   docker logs $(docker ps -q) | grep -i error | head -20
   
4. Health check query:
   curl http://localhost:8080/healthz
   
5. If DOWN, check:
   - DB connectivity: curl http://db-server:5432 (should fail; DB runs outside app)
   - File store: ls -la /opt/simis (should be writable)
   - Logs for errors (see above)
   
6. Recovery:
   - If DB issue: Check Azure DB status, restart if hung
   - If file store: Remount Azure Files, check ACLs
   - If app error: Restart App Service
   
7. After recovery:
   - Wait 5 min for health checks to stabilize
   - Confirm in workbook: Health Status tile should show 100%
```

### Runbook: Database Unreachable

```
1. Check alert → Database query for specific error
2. Verify DB status in Azure portal:
   - Check if PostgreSQL Flexible Server is "Available"
   - Check if it's restarting (normal for first min)
3. Check network:
   - Private endpoint connection approved? (Portal → App Service → Networking → Private endpoints)
   - Network security rules allow App Service subnet?
4. Restart DB if hung (Azure portal → Restart)
5. If not resolving after 10 min, escalate to Azure support
```

### Runbook: File Store Unmounted

```
1. SSH to app container (see above)
2. Check mount:
   df -h | grep simis
   ls -la /opt/simis
3. Check logs for mount errors:
   dmesg | grep -i azure
4. Remount:
   umount /opt/simis
   mount /opt/simis  (or restart container)
5. Verify:
   touch /opt/simis/test && rm /opt/simis/test
   (If this works, file store is writable)
```

---

## Deployment Checklist (Phase 4)

- [ ] Log Analytics workspace created (Phase 2 Bicep)
- [ ] Diagnostic settings send App Service logs to workspace
- [ ] Sentinel parser `SimISAppLog` created in Sentinel
- [ ] KQL queries (1-6) saved in Sentinel → Saved queries
- [ ] Alert rules (1-6) configured with correct thresholds
- [ ] Workbook created with 4 sections above
- [ ] Email action configured (test alert delivered)
- [ ] Teams webhook configured (test message posted)
- [ ] PagerDuty integration configured (if using)
- [ ] On-call rotation set up in PagerDuty (or email list)
- [ ] Runbooks above shared with on-call team
- [ ] Test: Manually trigger one alert, verify routing works
- [ ] Test: Kill database connection, verify alert fires
- [ ] Test: Stop app, verify app-down alert fires

---

## Maintenance

**Weekly:**
- Review error trends in workbook
- Spot-check recent request audit logs
- Address any patterns (e.g., 400 errors may indicate client bug)

**Monthly:**
- Review alert thresholds (adjust if too noisy or too quiet)
- Test runbooks (do they work? Are they up-to-date?)
- Check on-call rotation is correct

**Quarterly:**
- Review alerting strategy (are we catching what matters?)
- Update runbooks based on lessons learned
- Consider new queries based on incidents

---

## Next Steps

1. **Phase 4 execution:** Set up in Azure (needs subscription)
   - Create Sentinel parser
   - Deploy KQL queries
   - Configure alert rules
   - Build workbook
   - Wire alert routing

2. **Phase 5:** Cutover runbook (fresh install + go-live procedures)

3. **Ongoing:** Incident response & runbook refinement
