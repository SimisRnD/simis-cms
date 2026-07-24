// ---------------------------------------------------------------------------
// simis-cms — Azure infrastructure (Milestone #4 Phase 2)
//
// Foundation layer: network, observability, storage, secret custody, database.
// The application tier (ACR + App Service) and the edge (Front Door + WAF) are
// authored separately and consume the outputs below.
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

// Consumed by the application tier when it is authored.
output vnetId string = network.outputs.vnetId
output appSubnetId string = network.outputs.appSubnetId
output logAnalyticsWorkspaceId string = logAnalytics.outputs.workspaceId
output keyVaultName string = keyVault.outputs.keyVaultName
output keyVaultUri string = keyVault.outputs.keyVaultUri
output storageAccountName string = storage.outputs.storageAccountName
output cmsPathShareName string = storage.outputs.fileShareName
output postgresFqdn string = postgres.outputs.serverFqdn
output postgresDatabaseName string = postgres.outputs.databaseName
