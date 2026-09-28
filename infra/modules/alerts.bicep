// ---------------------------------------------------------------------------
// Alert rules.
//
// Everything that pages someone about this estate: log-query rules against
// the workspace, platform metric alerts on Front Door / App Service /
// Postgres, Activity Log alerts on control-plane changes, and the processing
// rule that routes Azure Backup's built-in alerts. Each rule's description
// carries the reason for its threshold, measured against real traffic when
// the rule was built; read it before changing a number.
//
// Log-query rules: most have no time filter on purpose, because the rule's
// windowSize supplies it. Where a query ends in "summarize ... | where x == 0"
// it is a silence detector: a summarize with no "by" always returns exactly
// one row, so the trailing where yields a row ONLY when the count is
// genuinely zero -- which is what makes the rule fire on silence rather than
// on absence of data.
// ---------------------------------------------------------------------------

@description('Azure region for the log-query rules. Must match the workspace region.')
param location string

@description('Tags applied to every resource.')
param tags object

@description('Log Analytics workspace resource id the log-query rules query.')
param logAnalyticsWorkspaceId string

@description('Action group resource id notified when a rule fires.')
param actionGroupId string

@description('Front Door profile resource id (edge metrics).')
param frontDoorProfileId string

@description('App Service resource id (application metrics).')
param appServiceId string

@description('PostgreSQL flexible server resource id (database metrics).')
param postgresServerId string

@description('True when the database runs on a Burstable SKU. CPU credits exist only on that tier, so the credit alert is skipped otherwise.')
param postgresIsBurstable bool

@description('Recovery Services vault resource id whose built-in backup alerts should notify the action group. Empty skips the routing rule.')
param backupVaultId string = ''

// ---------------------------------------------------------------------------
// Log-query rules
// ---------------------------------------------------------------------------

type dimension = {
  name: string
  operator: 'Include' | 'Exclude'
  values: string[]
}

type logRule = {
  name: string
  displayName: string?
  severity: int
  frequency: string
  window: string
  threshold: int
  dimensions: dimension[]?
  @description('Numeric column to aggregate. Omit to count result rows.')
  measureColumn: string?
  @description('Query time range when the query must look back further than the window.')
  queryTimeRange: string?
  query: string
  description: string
}

var logRules logRule[] = [
  {
    name: 'alert-audit-logging-stopped'
    severity: 1
    frequency: 'PT10M'
    window: 'PT30M'
    threshold: 0
    query: '''
AppServiceConsoleLogs
| summarize lines = count()
| where lines == 0
'''
    description: 'The console log stream that carries the simis.audit.v1 trail has delivered nothing for 30 minutes: the audit pipeline (app stdout -> App Service diagnostic setting -> this workspace) has failed. NIST SP 800-171 3.3.4. Baseline 2026-09-08..09-28: longest natural gap in AppServiceConsoleLogs was 6.5 min, so 30 min silent is a failure, not a quiet period. Audit-event silence is covered at lower severity by alert-audit-events-quiet-24h.'
  }
  {
    name: 'alert-audit-events-quiet-24h'
    severity: 3
    frequency: 'PT1H'
    window: 'P1D'
    threshold: 0
    query: '''
AppServiceConsoleLogs
| where ResultDescription has "simis.audit"
| summarize auditEvents = count()
| where auditEvents == 0
'''
    description: 'No simis.audit.v1 events for 24 hours while console logging is otherwise healthy (pipeline failure is alert-audit-logging-stopped, Sev1). Audit events are activity-driven, so this can mean the audit emitter broke OR nobody used the site; check for logins/admin activity before escalating. Baseline 2026-09-08..09-28: longest natural gap 8.7h (overnight), no 24h window was ever empty.'
  }
  {
    name: 'alert-app-startup-failure'
    severity: 1
    frequency: 'PT5M'
    window: 'PT15M'
    threshold: 0
    query: 'AppExceptions | where OuterMessage has \'Startup failed\''
    description: 'The application failed to start. Two occurrences in the week before this alert existed; neither notified anyone.'
  }
  {
    name: 'alert-http-503'
    severity: 1
    frequency: 'PT5M'
    window: 'PT15M'
    threshold: 0
    query: 'AppRequests | where ResultCode == \'503\''
    description: 'Visitors are receiving HTTP 503. 33 were served on 2026-09-06 with no notification.'
  }
  {
    name: 'alert-recaptcha-verification-failure'
    severity: 1
    frequency: 'PT5M'
    window: 'PT15M'
    threshold: 0
    query: '''
AppServiceConsoleLogs | where ResultDescription has "CaptchaCommand" and ResultDescription has "ERROR" | where ResultDescription contains "could not be sent" or ResultDescription contains "returned HTTP" or ResultDescription contains "rejected the request" or ResultDescription contains "Could not build the assessment request" or ResultDescription contains "reached without usable settings" or ResultDescription contains "json error"
'''
    description: 'reCAPTCHA Enterprise verification is failing at the infrastructure level (API unreachable, HTTP error, unparseable response, or missing settings). CaptchaCommand fails CLOSED, so while this fires the contact and subscribe forms REJECT all submissions. Most likely cause: the backing Google Cloud project lost billing or the API key was revoked. Excludes normal bot rejections (low score, missing token).'
  }
  {
    name: 'alert-postgres-fatal'
    severity: 2
    frequency: 'PT15M'
    window: 'PT30M'
    threshold: 0
    query: 'AzureDiagnostics | where Category == \'PostgreSQLLogs\' and errorLevel_s == \'FATAL\' | where Message !has \'terminating connection due to administrator command\''
    description: 'Postgres FATAL, excluding planned admin disconnects. Caught a password authentication failure on 2026-09-03 that took the app down.'
  }
  {
    name: 'alert-login-failure-spike'
    severity: 2
    frequency: 'PT15M'
    window: 'PT1H'
    threshold: 5
    query: 'AppServiceConsoleLogs | where ResultDescription has \'simis.audit\' and ResultDescription has \'authentication.login.failure\''
    description: 'Burst of failed logins. Baseline is 7 in 30 days, so 5 in an hour is well outside normal and suggests credential guessing.'
  }
  {
    // Keyed on WHO is blocked, not on the path. The previous version matched
    // admin-looking paths, so it could only ever see scanners (every window
    // over threshold in 30 days was a bot probing /admin or admin.php), while
    // the real lockout on 2026-09-03 was editors blocked on /content-editor.
    // It also counted result rows instead of the count column, so it never
    // fired at all.
    name: 'simiscms-waf-blocking-admins'
    severity: 2
    frequency: 'PT15M'
    window: 'PT1H'
    // Azure's ceiling for a log alert's query range is 2 days.
    queryTimeRange: 'P2D'
    measureColumn: 'blocks'
    threshold: 1
    dimensions: [
      { name: 'ClientIp', operator: 'Include', values: ['*'] }
    ]
    query: '''
let signedInIps = AppServiceConsoleLogs
    | where ResultDescription has "simis.audit.v1"
    | extend audit = parse_json(substring(ResultDescription, indexof(ResultDescription, "{")))
    | extend actor = tostring(audit.actorUserId), ip = tostring(audit.sourceIp)
    | where isnotempty(ip) and isnotempty(actor) and actor !in ("0", "-1")
    | distinct ip;
AzureDiagnostics
| where TimeGenerated > ago(1h)
| where Category == "FrontDoorWebApplicationFirewallLog" and action_s == "Block"
| where clientIP_s in (signedInIps)
| summarize blocks = count() by ClientIp = clientIP_s
'''
    description: 'The WAF blocked more than one request in an hour from an IP address that a signed-in user has used in the last 2 days (Azure\'s maximum query range for a log alert). A WAF rule that is slightly wrong locks administrators out, and the only visible symptom is a 403 page with a tracking reference. Scanners never sign in, so they cannot trip this. Backtested over 30 days to 2026-09-28 with that 2-day lookback (identical result to a 14-day one): fires on the real lockout of 2026-09-03 (office IP, 5 blocks on /content-editor in 8 minutes, and 2 more that evening) and otherwise only on the ISSM\'s own WAF verification tests of 09-02..09-06; zero fires since. A single block (e.g. Outlook probing /autodiscover/autodiscover.xml from a user\'s home IP) does not fire. Grouped by ClientIp so the alert names the address; look it up in simis.audit.v1 events to see who.'
  }
  {
    name: 'alert-waf-block-surge'
    severity: 3
    frequency: 'PT30M'
    window: 'PT1H'
    threshold: 1500
    query: 'AzureDiagnostics | where Category == \'FrontDoorWebApplicationFirewallLog\' and action_s == \'Block\''
    description: 'WAF blocking far above baseline (~600/hour peak this week). Indicates an active campaign rather than background scanning.'
  }
  {
    name: 'alert-404-loop'
    displayName: '404 loop from a single client'
    severity: 3
    frequency: 'PT30M'
    window: 'PT1H'
    threshold: 60
    dimensions: [
      { name: 'ClientIp', operator: 'Include', values: ['*'] }
      { name: 'Path', operator: 'Include', values: ['*'] }
    ]
    query: '''
AzureDiagnostics
| where Category == "FrontDoorAccessLog"
| extend StatusCode = coalesce(toint(httpStatusCode_d), toint(httpStatusCode_s))
| where StatusCode == 404
| extend Path = tostring(parse_url(requestUri_s).Path)
| extend ClientIp = tostring(clientIp_s)
| where isnotempty(Path) and isnotempty(ClientIp)
| project TimeGenerated, ClientIp, Path
'''
    description: 'One client IP hammering one path with 404s -- the signature of a stuck browser poll or a crawler in a loop, not of ordinary not-found traffic.\n\nBuilt after 2026-09-07, when a single admin browser issued 1,378 requests to /admin/content/analytics over thirteen hours and nothing alerted. The underlying defect (stacked setInterval handles in site-stats-table.jsp, issues #1920 and #1922) is fixed and deployed, but the detection gap it exposed is not. The same shape recurred at least three times: 09-01 (84/hr), and 09-07 on two separate pages from two separate IPs.\n\nThreshold rationale, measured over the 7 days to 2026-09-08: storm hours ran 98-117 requests per hour for a single (ClientIp, Path) pair, while the busiest legitimate or scanner traffic peaked at 38. 60 sits between them. Backtested: fires on 14 windows, every one a genuine loop, including the ramp-up hour (65) and the tail (69); zero non-loop windows fire.\n\nGrouped by ClientIp and Path so the alert names the culprit. Note the status column in FrontDoorAccessLog is httpStatusCode_d (a double) -- httpStatusCode_s is populated on only ~3.5% of rows and filtering on it silently under-counts.'
  }
]

resource logAlerts 'Microsoft.Insights/scheduledQueryRules@2023-03-15-preview' = [for rule in logRules: {
  name: rule.name
  location: location
  tags: tags
  properties: {
    displayName: rule.?displayName ?? rule.name
    description: rule.description
    severity: rule.severity
    enabled: true
    scopes: [
      logAnalyticsWorkspaceId
    ]
    evaluationFrequency: rule.frequency
    windowSize: rule.window
    overrideQueryTimeRange: rule.?queryTimeRange
    autoMitigate: true
    criteria: {
      allOf: [
        {
          query: trim(rule.query)
          timeAggregation: rule.?measureColumn == null ? 'Count' : 'Total'
          metricMeasureColumn: rule.?measureColumn
          dimensions: rule.?dimensions ?? []
          operator: 'GreaterThan'
          threshold: rule.threshold
          failingPeriods: {
            numberOfEvaluationPeriods: 1
            minFailingPeriodsToAlert: 1
          }
        }
      ]
    }
    actions: {
      actionGroups: [
        actionGroupId
      ]
    }
  }
}]

// ---------------------------------------------------------------------------
// Platform metric alerts
// ---------------------------------------------------------------------------

var metricTargets = {
  edge: frontDoorProfileId
  app: appServiceId
  db: postgresServerId
}

type metricRule = {
  name: string
  target: 'edge' | 'app' | 'db'
  @description('Only deployed when the database is on a Burstable SKU.')
  burstableOnly: bool?
  severity: int
  frequency: string
  window: string
  criterionName: string?
  metricNamespace: string?
  metric: string
  aggregation: 'Average' | 'Minimum' | 'Maximum' | 'Total' | 'Count'
  operator: 'GreaterThan' | 'LessThan'
  threshold: int
  dimensions: dimension[]?
  description: string
}

var metricRules metricRule[] = [
  {
    name: 'simiscms-origin-unhealthy'
    target: 'edge'
    severity: 1
    frequency: 'PT1M'
    window: 'PT5M'
    metric: 'OriginHealthPercentage'
    aggregation: 'Average'
    operator: 'LessThan'
    threshold: 100
    description: 'Front Door reports the origin below full health. This is the site being unreachable or flapping; it is what a deploy restart looks like, and what a genuine outage looks like.'
  }
  {
    name: 'simiscms-5xx-surge'
    target: 'edge'
    severity: 1
    frequency: 'PT1M'
    window: 'PT5M'
    criterionName: 'FiveXxCount'
    metricNamespace: 'Microsoft.Cdn/profiles'
    metric: 'RequestCount'
    aggregation: 'Total'
    operator: 'GreaterThan'
    threshold: 10
    dimensions: [
      { name: 'HttpStatusGroup', operator: 'Include', values: ['5xx'] }
    ]
    description: 'Front Door returned more than 10 5xx responses in 5 minutes. Counts errors rather than measuring a percentage, deliberately: this site\'s MEDIAN 5-minute window carries only 21 requests, so the previous Percentage5XX>2 threshold made a single transient 502 read as 2.6% and page as Sev1 -- it fired 7 times in the week to 2026-09-09, every one of them noise. Measured over those 7 days, only 18 of ~2000 windows contained any 5xx at all and the worst held 4, so a threshold of 10 leaves 2.5x headroom over observed noise while a genuine origin outage at median traffic produces ~21. Matches simiscms-app-5xx\'s Http5xx>10 on purpose: this rule sees what that one cannot, namely the 502/503s Front Door generates when it cannot reach the origin at all, which never reach the application. Very-low-traffic outages fall to simiscms-origin-unhealthy and simiscms-traffic-stopped.'
  }
  {
    name: 'simiscms-traffic-stopped'
    target: 'edge'
    severity: 2
    frequency: 'PT5M'
    window: 'PT30M'
    metric: 'RequestCount'
    aggregation: 'Total'
    operator: 'LessThan'
    threshold: 5
    description: 'Fewer than 5 requests reached the edge in 30 minutes. Normal is roughly 200. A drop to nothing means DNS, certificate or Front Door itself, none of which the origin-health alert can see.'
  }
  {
    name: 'simiscms-latency'
    target: 'edge'
    severity: 3
    frequency: 'PT5M'
    window: 'PT15M'
    metric: 'TotalLatency'
    aggregation: 'Average'
    operator: 'GreaterThan'
    threshold: 3000
    description: 'Total latency above 3 seconds averaged over 15 minutes. Deliberately slow and wide: this is for sustained degradation, not for one slow page.'
  }
  {
    name: 'simiscms-app-5xx'
    target: 'app'
    severity: 2
    frequency: 'PT1M'
    window: 'PT5M'
    metric: 'Http5xx'
    aggregation: 'Total'
    operator: 'GreaterThan'
    threshold: 10
    description: 'The application itself returned 10 or more 5xx responses in 5 minutes. Distinct from the edge alert: Front Door can serve stale or absorb a fault the app is still logging.'
  }
  {
    name: 'simiscms-memory-pressure'
    target: 'app'
    severity: 2
    frequency: 'PT5M'
    window: 'PT15M'
    metric: 'MemoryWorkingSet'
    aggregation: 'Average'
    operator: 'GreaterThan'
    // 3.5 GiB. Sized for the plan's memory; revisit if appServicePlanSku changes.
    threshold: 3758096384
    description: 'Working set above 3.5 GB. The JVM runs with -XX:+ExitOnOutOfMemoryError, so running out of memory does not degrade the site, it terminates the container -- this is meant to fire before that, not after.'
  }
  {
    name: 'simiscms-db-cpu'
    target: 'db'
    severity: 2
    frequency: 'PT5M'
    window: 'PT15M'
    metric: 'cpu_percent'
    aggregation: 'Average'
    operator: 'GreaterThan'
    threshold: 80
    description: 'Database CPU above 80% for 15 minutes. Observed peak is 7.5%, so this is a runaway query or a load change, not normal variation.'
  }
  {
    name: 'simiscms-db-cpu-credits'
    target: 'db'
    burstableOnly: true
    severity: 2
    frequency: 'PT5M'
    window: 'PT15M'
    metric: 'cpu_credits_remaining'
    aggregation: 'Minimum'
    operator: 'LessThan'
    threshold: 30
    description: 'Burstable CPU credits running low. Below ~0 the server throttles hard and the site degrades. Introduced with the D2ds_v5 -> B2ms right-size; none of the other alerts cover this tier\'s failure mode.'
  }
  {
    name: 'simiscms-db-connections'
    target: 'db'
    severity: 2
    frequency: 'PT1M'
    window: 'PT5M'
    metric: 'active_connections'
    aggregation: 'Average'
    operator: 'GreaterThan'
    threshold: 200
    description: 'Active connections above 200 of a 859 ceiling. Normal peak is 19, so 200 means a leak or a pool misconfiguration rather than traffic -- and connection exhaustion takes the site down with errors that look nothing like a database problem.'
  }
  {
    name: 'simiscms-db-failed-connections'
    target: 'db'
    severity: 2
    frequency: 'PT1M'
    window: 'PT5M'
    metric: 'connections_failed'
    aggregation: 'Total'
    operator: 'GreaterThan'
    threshold: 10
    description: 'More than 10 failed connection attempts in 5 minutes. Catches credential problems, firewall changes and exhaustion early -- and would surface a brute-force attempt against the database, which nothing else here watches.'
  }
  {
    name: 'simiscms-db-storage'
    target: 'db'
    severity: 2
    frequency: 'PT5M'
    window: 'PT15M'
    metric: 'storage_percent'
    aggregation: 'Average'
    operator: 'GreaterThan'
    threshold: 80
    description: 'Database storage above 80%. Auto-grow is on, so this should not become an outage, but sustained growth is worth seeing before it doubles the bill or hits the ceiling. Peak over the last day was 7.2%.'
  }
]

resource metricAlerts 'Microsoft.Insights/metricAlerts@2018-03-01' = [for rule in filter(metricRules, r => postgresIsBurstable || !(r.?burstableOnly ?? false)): {
  name: rule.name
  location: 'global'
  tags: tags
  properties: {
    description: rule.description
    severity: rule.severity
    enabled: true
    scopes: [
      metricTargets[rule.target]
    ]
    evaluationFrequency: rule.frequency
    windowSize: rule.window
    criteria: {
      'odata.type': 'Microsoft.Azure.Monitor.SingleResourceMultipleMetricCriteria'
      allOf: [
        {
          criterionType: 'StaticThresholdCriterion'
          name: rule.?criterionName ?? 'cond0'
          metricNamespace: rule.?metricNamespace
          metricName: rule.metric
          dimensions: rule.?dimensions ?? []
          operator: rule.operator
          threshold: rule.threshold
          timeAggregation: rule.aggregation
        }
      ]
    }
    actions: [
      {
        actionGroupId: actionGroupId
      }
    ]
  }
}]

// ---------------------------------------------------------------------------
// Activity Log (control-plane) alerts, subscription-wide.
//
// STATUS FILTER: two different vocabularies exist for this field. The alert
// engine matches the RAW Activity Log (Started / Succeeded / Failed); the
// AzureActivity table in Log Analytics renders the same events as Start /
// Success. Both spellings are listed so the rule is correct against either.
// Pre-completion values are excluded so one change yields one notification.
// Verified by live fire test 2026-09-08T00:31Z: write-Succeeded, write-Failed
// and delete-Succeeded all alerted; the Started events correctly did not.
// ---------------------------------------------------------------------------

var completedStatuses = ['Success', 'Succeeded', 'Failed', 'Failure', 'Unknown']

var statusFilterNote = ' STATUS FILTER: two different vocabularies exist for this field. The alert engine matches the RAW Activity Log (Started / Succeeded / Failed); the AzureActivity table in Log Analytics renders the same events as Start / Success. Both spellings are listed so the rule is correct against either. Pre-completion values are excluded so one change yields one notification. Verified by live fire test 2026-09-08T00:31Z: write-Succeeded, write-Failed and delete-Succeeded all alerted; the Started events correctly did not.'

var activityRules = [
  {
    name: 'alert-activity-audit-tampering'
    operations: [
      'Microsoft.Insights/diagnosticSettings/delete'
      'Microsoft.Insights/diagnosticSettings/write'
      'Microsoft.OperationalInsights/workspaces/delete'
      'Microsoft.Insights/components/delete'
    ]
    description: 'Diagnostic setting or log destination written or deleted anywhere in the subscription. Deleting a diagnostic setting is the cheapest way to stop being logged. NIST 800-171 Rev 2 practices 3.3.8 and 3.3.9.'
  }
  {
    name: 'alert-activity-privilege-change'
    operations: [
      'Microsoft.Authorization/roleAssignments/write'
      'Microsoft.Authorization/roleAssignments/delete'
      'Microsoft.Authorization/roleDefinitions/write'
      'Microsoft.Authorization/roleDefinitions/delete'
    ]
    description: 'Azure RBAC role assignment or custom role definition created, changed or removed. NIST 800-171 Rev 2 practices 3.1.5 and 3.3.9.'
  }
  {
    name: 'alert-activity-security-control-change'
    operations: [
      'Microsoft.KeyVault/vaults/write'
      'Microsoft.KeyVault/vaults/delete'
      'Microsoft.KeyVault/vaults/accessPolicies/write'
      'Microsoft.Network/frontdoorWebApplicationFirewallPolicies/write'
      'Microsoft.Network/frontdoorWebApplicationFirewallPolicies/delete'
      'Microsoft.Cdn/profiles/delete'
    ]
    description: 'Key Vault or Front Door WAF configuration changed or deleted, including vault network rules and access policies. NIST 800-171 Rev 2 practices 3.4.1 and 3.1.1.'
  }
]

resource activityAlerts 'Microsoft.Insights/activityLogAlerts@2020-10-01' = [for rule in activityRules: {
  name: rule.name
  location: 'Global'
  tags: tags
  properties: {
    description: '${rule.description}${statusFilterNote}'
    enabled: true
    scopes: [
      subscription().id
    ]
    condition: {
      allOf: [
        {
          field: 'category'
          equals: 'Administrative'
        }
        {
          anyOf: [for op in rule.operations: {
            field: 'operationName'
            equals: op
          }]
        }
        {
          field: 'status'
          containsAny: completedStatuses
        }
      ]
    }
    actions: {
      actionGroups: [
        {
          actionGroupId: actionGroupId
        }
      ]
    }
  }
}]

// ---------------------------------------------------------------------------
// Azure Backup routing
// ---------------------------------------------------------------------------

resource backupAlertRouting 'Microsoft.AlertsManagement/actionRules@2021-08-08' = if (!empty(backupVaultId)) {
  name: 'apr-backup-failures'
  location: 'Global'
  tags: tags
  properties: {
    description: 'Routes Azure Backup\'s built-in alerts (job failures above all) to the action group. Without this the vault raises alerts that sit in the portal and notify nobody, which is the failure mode where backups quietly stop and everyone assumes they are covered.'
    enabled: true
    scopes: [
      backupVaultId
    ]
    actions: [
      {
        actionType: 'AddActionGroups'
        actionGroupIds: [
          actionGroupId
        ]
      }
    ]
  }
}
