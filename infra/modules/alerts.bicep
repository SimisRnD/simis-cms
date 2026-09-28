// ---------------------------------------------------------------------------
// Audit-logging failure alerts (NIST SP 800-171 3.3.4: alert in the event of
// an audit logging process failure).
//
// The application's tamper-evident audit trail (simis.audit.v1, with
// recordHash/previousHash) is written to container stdout and reaches the
// workspace as AppServiceConsoleLogs. Two things can go wrong, and they need
// different signals:
//
//   1. The pipeline breaks (app stops writing, the App Service diagnostic
//      setting is removed or stops delivering). Console logs are continuous --
//      the longest natural gap measured 2026-09-08..09-28 was 6.5 minutes --
//      so 30 silent minutes is a failure. Sev1.
//
//   2. The app keeps logging but the audit emitter stops. Audit events are
//      ACTIVITY-driven: they only appear when someone logs in, views a file,
//      publishes, and so on. Natural gaps reach 8.7 hours overnight, which is
//      why the original "6 hours with no audit events" Sev1 fired on quiet
//      nights and weekends. 24 hours of silence has never occurred, but an
//      unused day would also produce it, so this one is Sev3.
//
// Both queries deliberately have no time filter: the rule's windowSize
// supplies it. A summarize with no "by" always returns exactly one row, so
// the trailing where yields a row ONLY when the count is genuinely zero --
// which is what makes the rule fire on silence rather than on absence of data.
// ---------------------------------------------------------------------------

@description('Azure region for the rules. Must match the workspace region.')
param location string

@description('Tags applied to every resource.')
param tags object

@description('Log Analytics workspace resource id the rules query.')
param logAnalyticsWorkspaceId string

@description('Action group resource id notified when a rule fires.')
param actionGroupId string

resource auditPipelineStopped 'Microsoft.Insights/scheduledQueryRules@2023-03-15-preview' = {
  name: 'alert-audit-logging-stopped'
  location: location
  tags: tags
  properties: {
    displayName: 'alert-audit-logging-stopped'
    description: 'The console log stream that carries the simis.audit.v1 trail has delivered nothing for 30 minutes: the audit pipeline (app stdout -> App Service diagnostic setting -> this workspace) has failed. NIST SP 800-171 3.3.4. Baseline 2026-09-08..09-28: longest natural gap in AppServiceConsoleLogs was 6.5 min, so 30 min silent is a failure, not a quiet period. Audit-event silence is covered at lower severity by alert-audit-events-quiet-24h.'
    severity: 1
    enabled: true
    scopes: [
      logAnalyticsWorkspaceId
    ]
    evaluationFrequency: 'PT10M'
    windowSize: 'PT30M'
    autoMitigate: true
    criteria: {
      allOf: [
        {
          query: '''
AppServiceConsoleLogs
| summarize lines = count()
| where lines == 0
'''
          timeAggregation: 'Count'
          operator: 'GreaterThan'
          threshold: 0
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
}

resource auditEventsQuiet 'Microsoft.Insights/scheduledQueryRules@2023-03-15-preview' = {
  name: 'alert-audit-events-quiet-24h'
  location: location
  tags: tags
  properties: {
    displayName: 'alert-audit-events-quiet-24h'
    description: 'No simis.audit.v1 events for 24 hours while console logging is otherwise healthy (pipeline failure is alert-audit-logging-stopped, Sev1). Audit events are activity-driven, so this can mean the audit emitter broke OR nobody used the site; check for logins/admin activity before escalating. Baseline 2026-09-08..09-28: longest natural gap 8.7h (overnight), no 24h window was ever empty.'
    severity: 3
    enabled: true
    scopes: [
      logAnalyticsWorkspaceId
    ]
    evaluationFrequency: 'PT1H'
    windowSize: 'P1D'
    autoMitigate: true
    criteria: {
      allOf: [
        {
          query: '''
AppServiceConsoleLogs
| where ResultDescription has "simis.audit"
| summarize auditEvents = count()
| where auditEvents == 0
'''
          timeAggregation: 'Count'
          operator: 'GreaterThan'
          threshold: 0
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
}
