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
    customUrl: customUrl
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

// Deploy-time reference: hostnames for DNS and verification, ids for the
// approval and trusted-proxy steps documented in the README.
output vnetId string = network.outputs.vnetId
output appSubnetId string = network.outputs.appSubnetId
output logAnalyticsWorkspaceId string = logAnalytics.outputs.workspaceId
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
