// ---------------------------------------------------------------------------
// Application Insights (APM / distributed tracing).
//
// Workspace-based (not "classic") -- linked to the same Log Analytics
// workspace that already receives container stdout, so request tracing,
// dependency timing, and exception telemetry land alongside the platform
// logs the detection kit already queries. Classic (non-workspace-linked)
// Application Insights is deprecated and no longer creatable in most regions.
//
// The connection string is not treated as a Key-Vault-custody secret like
// DB_PASSWORD/CMS_SECRET_KEY (decision #6): it is a deploy-time Bicep output,
// not a value the ISSM creates out-of-band, and Microsoft's own guidance
// treats it as safe to embed directly in app configuration (client-side
// telemetry SDKs commonly ship it in browser-visible JS) -- worst case if it
// leaks is forged telemetry, not data exposure.
//
// The Java agent that reads this connection string is already baked into
// the container image (docker/app/Dockerfile) and self-disables cleanly
// with no connection string supplied, which is why every other environment
// (local, CI) boots normally without this resource existing.
// ---------------------------------------------------------------------------

@description('Azure region for the resource.')
param location string

@description('Prefix applied to resource names.')
param namePrefix string

@description('Tags applied to every resource.')
param tags object

@description('Log Analytics workspace resource id this workspace-based Application Insights instance reports into.')
param logAnalyticsWorkspaceId string

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: 'appi-${namePrefix}'
  location: location
  tags: tags
  kind: 'java'
  properties: {
    Application_Type: 'java'
    Flow_Type: 'Bluefield'
    Request_Source: 'rest'
    WorkspaceResourceId: logAnalyticsWorkspaceId
    IngestionMode: 'LogAnalytics'
    publicNetworkAccessForIngestion: 'Enabled'
    publicNetworkAccessForQuery: 'Enabled'
  }
}

output name string = appInsights.name
output connectionString string = appInsights.properties.ConnectionString
