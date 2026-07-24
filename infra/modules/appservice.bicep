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

@description('CMS_TRUSTED_PROXIES value. Must be set to the edge egress ranges when the edge tier (#245) fronts the app; otherwise getRemoteAddr()/isSecure() see the proxy, degrading the Secure-cookie flag, IP firewall, rate limiting, and audit source IP.')
param trustedProxies string = ''

@description('Public URL of the site (CMS_URL). Defaults to the App Service hostname until the custom domain lands at cutover.')
param customUrl string = ''

@description('Key Vault secret name for the database password.')
param dbPasswordSecretName string = 'db-password'

@description('Key Vault secret name for the CMS secret key.')
param cmsSecretKeySecretName string = 'cms-secret-key'

@description('Key Vault secret name for the CMS admin bootstrap password.')
param cmsAdminPasswordSecretName string = 'cms-admin-password'

var planName = 'plan-${namePrefix}'
var appName = 'app-${namePrefix}'
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
        // --- Application contract (issue #244): plain env vars ---
        { name: 'CMS_URL', value: cmsUrl }
        { name: 'CMS_FORCE_SSL', value: 'true' }
        // Single-node pilot runs everything; 'web' would skip the overhead
        // tasks that only make sense to skip in a multi-node cluster.
        { name: 'CMS_NODE_TYPE', value: 'standalone' }
        { name: 'CMS_PATH', value: cmsPathMount }
        { name: 'CMS_TRUSTED_PROXIES', value: trustedProxies }
        { name: 'CMS_ADMIN_USERNAME', value: cmsAdminUsername }
        { name: 'CMS_ADMIN_PASSWORD', value: '@Microsoft.KeyVault(SecretUri=${keyVaultUri}secrets/${cmsAdminPasswordSecretName})' }
        { name: 'CMS_SECRET_KEY', value: '@Microsoft.KeyVault(SecretUri=${keyVaultUri}secrets/${cmsSecretKeySecretName})' }
        { name: 'DB_SERVER_NAME', value: postgresFqdn }
        { name: 'DB_NAME', value: postgresDatabaseName }
        { name: 'DB_USER', value: dbUser }
        { name: 'DB_PASSWORD', value: '@Microsoft.KeyVault(SecretUri=${keyVaultUri}secrets/${dbPasswordSecretName})' }
        { name: 'DB_SSL', value: 'true' }
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
output appServicePrincipalId string = app.identity.principalId
output defaultHostName string = app.properties.defaultHostName
output planId string = plan.id
