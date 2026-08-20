// ---------------------------------------------------------------------------
// simis-cms — Azure infrastructure (Milestone #4 Phase 2)
//
// All three layers: the foundation (network, observability, storage, secret
// custody, database), the application tier (container registry, App Service,
// role grants), and the edge (Front Door Premium + WAF, Private Link origin).
//
// Design inputs, all resolved in Phase 0
// (governance/decision-milestone-4-phase0-decisions.md):
//   #1 Azure Commercial            #2 App Service for Containers
//   #3 Bicep                       #4 private endpoints for DB + Key Vault
//   #5 hardened official images    #6 Key Vault + managed identity
//   #7 platform FIPS modules       #8 scale UP only for the pilot
//
// Authored and type-checked with `az bicep build`. It has NOT been deployed or
// run through `what-if` -- that needs a subscription (Phase 0 §7 hard
// dependency). Treat it as reviewed-but-unapplied until then.
// ---------------------------------------------------------------------------

targetScope = 'resourceGroup'

@description('Azure region for all resources. Azure Commercial (decision #1).')
param location string = resourceGroup().location

@description('Environment name, used in resource naming and tags, e.g. pilot.')
param environmentName string = 'pilot'

@description('Workload name, used in resource naming.')
param workloadName string = 'simiscms'

@description('PostgreSQL administrator login.')
param postgresAdministratorLogin string = 'simiscmsadmin'

@description('PostgreSQL administrator password. Supply at deploy time from Key Vault or a secure pipeline variable; never commit a value.')
@secure()
param postgresAdministratorPassword string

@description('''
PostgreSQL major version. What is actually offered depends on the region, the
compute tier, and the subscription -- not on this value alone. A deployment that
fails with "The value of the 'Version' should be in: []" is reporting an EMPTY set
of available versions, which means the region/tier/SKU combination below is
unavailable, not that this version is wrong.
''')
@allowed(['14', '15', '16', '17'])
param postgresVersion string = '17'

@description('''
PostgreSQL compute SKU. Availability varies by region and by subscription quota,
and constrained regions can offer none of a family. The portal's "Create Azure
Database for PostgreSQL flexible server" blade lists what this subscription can
actually deploy here; take the value from there rather than guessing.
''')
param postgresSkuName string = 'Standard_D2ds_v5'

@description('PostgreSQL compute tier. Must match the SKU family above -- a Standard_B* name needs Burstable, Standard_D* needs GeneralPurpose, Standard_E* needs MemoryOptimized.')
@allowed(['Burstable', 'GeneralPurpose', 'MemoryOptimized'])
param postgresSkuTier string = 'GeneralPurpose'

@description('Log retention in days. Sentinel ingestion is usage-priced.')
param logRetentionInDays int = 90

@description('Quota for the CMS_PATH file share, in GiB.')
param fileShareQuotaGb int = 100

@description('App Service plan SKU. Scale up only for the pilot (decision #8).')
param appServicePlanSku string = 'P1v3'

@description('Image repository and tag the app runs, relative to the registry. The publish pipeline (issue #246) pushes it.')
param containerImage string = 'simis-cms:latest'

@description('Database login the application connects with. Pilot default is the administrator login; a lesser application role is a hardening follow-up.')
param dbUser string = 'simiscmsadmin'

@description('CMS_TRUSTED_PROXIES value. Set to the edge egress ranges when the edge tier (#245) fronts the app.')
param trustedProxies string = ''

@description('Public URL of the site (CMS_URL). Empty means the App Service default hostname; the custom domain replaces it at cutover.')
param customUrl string = ''

@description('WAF mode for the edge. Prevention blocks; Detection only logs while tuning.')
@allowed(['Prevention', 'Detection'])
param wafMode string = 'Prevention'

@description('Custom domain for the edge, e.g. www.example.org. Empty until the DNS cutover decision.')
param customDomainName string = ''

@description('''
Deploy the point-to-site VPN gateway that gives administrators a private path to
the database and Key Vault. Off by default: it bills hourly from creation
whether or not anyone connects, and routine operation does not need it -- Flyway
migrates unattended on first boot and the app reads its own secrets. Turn it on
when interactive access becomes routine rather than occasional. See infra/README.md
for the cheaper stopgap and the DNS step clients need either way.
''')
param enableVpnGateway bool = false

@description('Entra ID tenant id that authenticates VPN clients. Required only when enableVpnGateway is true.')
param vpnTenantId string = ''

var namePrefix = '${workloadName}-${environmentName}'

var tags = {
  workload: workloadName
  environment: environmentName
  managedBy: 'bicep'
  milestone: 'milestone-4'
}

module network 'modules/network.bicep' = {
  name: 'network'
  params: {
    location: location
    namePrefix: namePrefix
    tags: tags
  }
}

module logAnalytics 'modules/loganalytics.bicep' = {
  name: 'loganalytics'
  params: {
    location: location
    namePrefix: namePrefix
    tags: tags
    retentionInDays: logRetentionInDays
  }
}

module appInsights 'modules/appinsights.bicep' = {
  name: 'appinsights'
  params: {
    location: location
    namePrefix: namePrefix
    tags: tags
    logAnalyticsWorkspaceId: logAnalytics.outputs.workspaceId
  }
}

module storage 'modules/storage.bicep' = {
  name: 'storage'
  params: {
    location: location
    namePrefix: namePrefix
    tags: tags
    fileShareQuotaGb: fileShareQuotaGb
  }
}

module keyVault 'modules/keyvault.bicep' = {
  name: 'keyvault'
  params: {
    location: location
    namePrefix: namePrefix
    tags: tags
    privateEndpointSubnetId: network.outputs.privateEndpointSubnetId
    privateDnsZoneId: network.outputs.keyVaultDnsZoneId
  }
}

module postgres 'modules/postgres.bicep' = {
  name: 'postgres'
  params: {
    location: location
    namePrefix: namePrefix
    tags: tags
    privateEndpointSubnetId: network.outputs.privateEndpointSubnetId
    privateDnsZoneId: network.outputs.postgresDnsZoneId
    administratorLogin: postgresAdministratorLogin
    administratorLoginPassword: postgresAdministratorPassword
    postgresVersion: postgresVersion
    skuName: postgresSkuName
    skuTier: postgresSkuTier
  }
}

module acr 'modules/acr.bicep' = {
  name: 'acr'
  params: {
    location: location
    namePrefix: namePrefix
    tags: tags
  }
}

// CMS_URL seeds the site.url property, which drives the canonical link tag and og:url.
// It has to be the address visitors actually use: pointing it at the App Service origin
// tells search engines that origin is the authoritative host -- the very host the Front
// Door topology exists to keep unaddressed. Prefer an explicit customUrl; otherwise derive
// it from the Front Door custom domain when one is configured, so a domain cutover does not
// depend on remembering a second parameter (issue #1356).
//
// NOTE: site.url is written from CMS_URL by the V71130__set_properties Flyway migration,
// which runs ONCE. Changing CMS_URL on an already-initialised deployment does not update
// site.url -- that has to be corrected in Admin > Site Properties as well. See
// infra/DEPLOYMENT.md 8.4.
var publicUrl = !empty(customUrl)
  ? customUrl
  : (!empty(customDomainName) ? 'https://${customDomainName}' : '')

module appService 'modules/appservice.bicep' = {
  name: 'appservice'
  params: {
    location: location
    namePrefix: namePrefix
    tags: tags
    appSubnetId: network.outputs.appSubnetId
    keyVaultUri: keyVault.outputs.keyVaultUri
    storageAccountName: storage.outputs.storageAccountName
    cmsPathShareName: storage.outputs.fileShareName
    logAnalyticsWorkspaceId: logAnalytics.outputs.workspaceId
    acrLoginServer: acr.outputs.loginServer
    containerImage: containerImage
    planSkuName: appServicePlanSku
    postgresFqdn: postgres.outputs.serverFqdn
    postgresDatabaseName: postgres.outputs.databaseName
    dbUser: dbUser
    trustedProxies: trustedProxies
    customUrl: publicUrl
    appInsightsConnectionString: appInsights.outputs.connectionString
  }
}

// Grants come last: they need the app's principal id, which only exists once
// the app does.
module rbac 'modules/rbac.bicep' = {
  name: 'rbac'
  params: {
    principalId: appService.outputs.appServicePrincipalId
    keyVaultName: keyVault.outputs.keyVaultName
    acrName: acr.outputs.registryName
  }
}

module frontDoor 'modules/frontdoor.bicep' = {
  name: 'frontdoor'
  params: {
    namePrefix: namePrefix
    tags: tags
    appServiceId: appService.outputs.appServiceId
    appHostName: appService.outputs.defaultHostName
    privateLinkLocation: location
    logAnalyticsWorkspaceId: logAnalytics.outputs.workspaceId
    wafMode: wafMode
    customDomainName: customDomainName
  }
}

// Optional administrative access to the private data tier. Nothing else depends
// on it, so it can be turned on or off without disturbing the running stack.
module vpnGateway 'modules/vpngateway.bicep' = if (enableVpnGateway) {
  name: 'vpngateway'
  params: {
    location: location
    namePrefix: namePrefix
    tags: tags
    gatewaySubnetId: network.outputs.gatewaySubnetId
    tenantId: vpnTenantId
  }
}

// Deploy-time reference: hostnames for DNS and verification, ids for the
// approval and trusted-proxy steps documented in the README.
output vnetId string = network.outputs.vnetId
output appSubnetId string = network.outputs.appSubnetId
output logAnalyticsWorkspaceId string = logAnalytics.outputs.workspaceId
output appInsightsName string = appInsights.outputs.name
output keyVaultName string = keyVault.outputs.keyVaultName
output keyVaultUri string = keyVault.outputs.keyVaultUri
output storageAccountName string = storage.outputs.storageAccountName
output cmsPathShareName string = storage.outputs.fileShareName
output postgresFqdn string = postgres.outputs.serverFqdn
output postgresDatabaseName string = postgres.outputs.databaseName
output acrLoginServer string = acr.outputs.loginServer
output acrName string = acr.outputs.registryName
output appServiceName string = appService.outputs.appServiceName
output appServiceHostName string = appService.outputs.defaultHostName
output frontDoorEndpointHostName string = frontDoor.outputs.endpointHostName
output frontDoorId string = frontDoor.outputs.frontDoorId

// Empty unless enableVpnGateway is true. Safe-dereference rather than a ternary:
// a conditional module is null until it is actually deployed, and reading through
// it unguarded fails the whole deployment.
output vpnGatewayPublicIp string = vpnGateway.?outputs.gatewayPublicIp ?? ''
