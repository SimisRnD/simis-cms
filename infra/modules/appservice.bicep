// ---------------------------------------------------------------------------
// App Service plan + Linux container app (Phase 0 decision #2).
//
// Single instance, scale UP only for the pilot (decision #8) -- no autoscale
// and no additional instances. The container runs non-root and listens on
// 8080 (it cannot bind below 1024), exposing /healthz for the health check.
//
// Secret custody (decision #6): the app reads plain env vars; the three
// sensitive ones (DB_PASSWORD, CMS_SECRET_KEY, CMS_ADMIN_PASSWORD) are Key
// Vault references resolved at startup by the system-assigned managed
// identity, so no secret value ever appears in app configuration, template,
// or output. The secrets themselves are created by the ISSM at deploy time --
// never by IaC.
//
// CMS_PATH is an Azure Files mount: App Service containers are ephemeral, and
// an in-container file library silently loses every upload on restart.
// ---------------------------------------------------------------------------

@description('Azure region for the plan and app.')
param location string

@description('Prefix applied to resource names.')
param namePrefix string

@description('Tags applied to every resource.')
param tags object

@description('Delegated subnet for regional VNet integration (outbound).')
param appSubnetId string

@description('Key Vault URI, e.g. https://kv-name.vault.azure.net/. Used to build the Key Vault references.')
param keyVaultUri string

@description('Storage account holding the CMS_PATH file share.')
param storageAccountName string

@description('Name of the CMS_PATH file share.')
param cmsPathShareName string

@description('Log Analytics workspace for diagnostic settings (Sentinel Path A: container stdout).')
param logAnalyticsWorkspaceId string

@description('Login server of the container registry, e.g. crname.azurecr.io.')
param acrLoginServer string

@description('Image repository and tag to run, relative to the registry.')
param containerImage string = 'simis-cms:latest'

@description('App Service plan SKU. Scale up only for the pilot (decision #8).')
param planSkuName string = 'P1v3'

@description('PostgreSQL server FQDN (DB_SERVER_NAME).')
param postgresFqdn string

@description('Application database name (DB_NAME).')
param postgresDatabaseName string

@description('Database login the application connects with (DB_USER).')
param dbUser string

@description('CMS administrator username created at first boot (CMS_ADMIN_USERNAME).')
param cmsAdminUsername string = 'admin'

@description('CMS_TRUSTED_PROXIES value. A Java regular expression -- NOT CIDR -- matching the addresses of the immediate peer, i.e. the App Service front ends. Without it getRemoteAddr()/isSecure() see the proxy, degrading the Secure-cookie flag, IP firewall, rate limiting, and audit source IP.')
param trustedProxies string = ''

@description('CMS_CLIENT_IP_HEADER value. Behind Front Door set this to X-Azure-ClientIP: its origin-facing addresses are public and span ~147 IPv4 prefixes that Microsoft revises, so they cannot practically be listed in trustedProxies, and X-Forwarded-For resolution then stops at the Front Door node and reports it as the client (issue #1675). Empty keeps X-Forwarded-For.')
param clientIpHeader string = ''

@description('Public URL of the site (CMS_URL). Defaults to the App Service hostname until the custom domain lands at cutover.')
param customUrl string = ''

@description('Application Insights connection string (APPLICATIONINSIGHTS_CONNECTION_STRING). Activates the APM/tracing Java agent already baked into the container image; leave empty to run without APM.')
param appInsightsConnectionString string = ''

@description('Key Vault secret name for the database password.')
param dbPasswordSecretName string = 'db-password'

@description('Key Vault secret name for the CMS secret key.')
param cmsSecretKeySecretName string = 'cms-secret-key'

@description('Key Vault secret name for the CMS admin bootstrap password.')
param cmsAdminPasswordSecretName string = 'cms-admin-password'

@description('Public ingress to the app. Disabled: the only path in is the edge tier\'s Private Link origin (frontdoor.bicep). Enable temporarily only for pre-edge debugging.')
@allowed(['Enabled', 'Disabled'])
param publicNetworkAccess string = 'Disabled'

// The plan is only ever addressed inside this resource group, so its name needs
// no suffix. The app's does: it becomes app-<prefix>.azurewebsites.net, which is
// unique across all of Azure. Same suffix as the storage account, registry, and
// vault -- deterministic, so redeploying reproduces this name.
var planName = 'plan-${namePrefix}'
var appName = toLower(take('app-${namePrefix}-${uniqueString(resourceGroup().id)}', 60))
var cmsPathMount = '/cms-data'
var cmsUrl = empty(customUrl) ? 'https://${appName}.azurewebsites.net' : customUrl

// The share is mounted with the storage account key, fetched at deploy time --
// the key is never written into source or parameters.
resource storageAccount 'Microsoft.Storage/storageAccounts@2023-05-01' existing = {
  name: storageAccountName
}

resource plan 'Microsoft.Web/serverfarms@2023-12-01' = {
  name: planName
  location: location
  tags: tags
  kind: 'linux'
  sku: {
    name: planSkuName
    capacity: 1
  }
  properties: {
    reserved: true
  }
}

resource app 'Microsoft.Web/sites@2023-12-01' = {
  name: appName
  location: location
  tags: tags
  kind: 'app,linux,container'
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    virtualNetworkSubnetId: appSubnetId
    // Ingress only through the edge tier's Private Link origin.
    publicNetworkAccess: publicNetworkAccess
    // Key Vault references resolve with the system-assigned identity.
    keyVaultReferenceIdentity: 'SystemAssigned'
    siteConfig: {
      linuxFxVersion: 'DOCKER|${acrLoginServer}/${containerImage}'
      // Pull with the managed identity (AcrPull, rbac.bicep) -- the registry
      // admin account is disabled, so there is no password to leak.
      acrUseManagedIdentityCreds: true
      alwaysOn: true
      http20Enabled: true
      minTlsVersion: '1.2'
      ftpsState: 'Disabled'
      healthCheckPath: '/healthz'
      // Route all outbound through the VNet so the private endpoints for the
      // database and Key Vault (and their private DNS zones) are what resolve.
      vnetRouteAllEnabled: true
      azureStorageAccounts: {
        cmspath: {
          type: 'AzureFiles'
          accountName: storageAccountName
          shareName: cmsPathShareName
          mountPath: cmsPathMount
          accessKey: storageAccount.listKeys().keys[0].value
        }
      }
      appSettings: [
        // The container listens on 8080 (non-root cannot bind below 1024).
        { name: 'WEBSITES_PORT', value: '8080' }
        // --- Slot swap warm-up (issue #1178) ---
        // These make App Service itself probe a slot before completing a swap, and abort the swap
        // if the probe never succeeds. That matters because the app runs with
        // publicNetworkAccess Disabled and a slot inherits it: a staging slot answers HTTP 403 from
        // outside Azure, so a health check run from a GitHub Actions runner can never pass. The
        // probe has to originate inside Azure, and this is the supported way to arrange that.
        // WEBSITE_SWAP_WARMUP_PING_PATH intentionally matches healthCheckPath below.
        { name: 'WEBSITE_SWAP_WARMUP_PING_PATH', value: '/healthz' }
        { name: 'WEBSITE_SWAP_WARMUP_PING_STATUSES', value: '200' }
        // --- Application contract (issue #244): plain env vars ---
        { name: 'CMS_URL', value: cmsUrl }
        { name: 'CMS_FORCE_SSL', value: 'true' }
        // Single-node pilot runs everything; 'web' would skip the overhead
        // tasks that only make sense to skip in a multi-node cluster.
        { name: 'CMS_NODE_TYPE', value: 'standalone' }
        { name: 'CMS_PATH', value: cmsPathMount }
        { name: 'CMS_TRUSTED_PROXIES', value: trustedProxies }
        { name: 'CMS_CLIENT_IP_HEADER', value: clientIpHeader }
        { name: 'CMS_ADMIN_USERNAME', value: cmsAdminUsername }
        { name: 'CMS_ADMIN_PASSWORD', value: '@Microsoft.KeyVault(SecretUri=${keyVaultUri}secrets/${cmsAdminPasswordSecretName})' }
        { name: 'CMS_SECRET_KEY', value: '@Microsoft.KeyVault(SecretUri=${keyVaultUri}secrets/${cmsSecretKeySecretName})' }
        { name: 'DB_SERVER_NAME', value: postgresFqdn }
        { name: 'DB_NAME', value: postgresDatabaseName }
        { name: 'DB_USER', value: dbUser }
        { name: 'DB_PASSWORD', value: '@Microsoft.KeyVault(SecretUri=${keyVaultUri}secrets/${dbPasswordSecretName})' }
        { name: 'DB_SSL', value: 'true' }
        // Activates the APM/tracing Java agent already baked into the image (docker/app/Dockerfile);
        // the agent self-disables cleanly if this is ever empty.
        { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', value: appInsightsConnectionString }
      ]
    }
  }
}

// Container stdout to Log Analytics -- exactly the Sentinel "Path A" ingestion
// the detection kit was written against. HTTP/audit/platform logs ride along
// for the workbook and the WAF correlation later.
resource diagnostics 'Microsoft.Insights/diagnosticSettings@2021-05-01-preview' = {
  name: 'diag-${appName}'
  scope: app
  properties: {
    workspaceId: logAnalyticsWorkspaceId
    logs: [
      { category: 'AppServiceConsoleLogs', enabled: true }
      { category: 'AppServiceHTTPLogs', enabled: true }
      { category: 'AppServiceAuditLogs', enabled: true }
      { category: 'AppServicePlatformLogs', enabled: true }
    ]
    metrics: [
      { category: 'AllMetrics', enabled: true }
    ]
  }
}

output appServiceName string = app.name
output appServiceId string = app.id
output appServicePrincipalId string = app.identity.principalId
output defaultHostName string = app.properties.defaultHostName
output planId string = plan.id
