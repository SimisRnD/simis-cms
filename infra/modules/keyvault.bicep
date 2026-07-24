// ---------------------------------------------------------------------------
// Key Vault + private endpoint.
//
// Phase 0 decision #6: Key Vault + managed identity is the secret custody
// mechanism for the database credential and the inventoried integration
// secrets. Nothing sensitive is baked into the image or app configuration.
//
// The app itself has no Azure-AD awareness -- it reads plain env vars
// (DB_PASSWORD, CMS_SECRET_KEY, CMS_ADMIN_PASSWORD). App Service resolves
// those from Key Vault using its managed identity, so the secret never
// appears in template output or app configuration.
// ---------------------------------------------------------------------------

@description('Azure region for the vault.')
param location string

@description('Prefix applied to resource names.')
param namePrefix string

@description('Tags applied to every resource.')
param tags object

@description('Subnet that will hold the private endpoint.')
param privateEndpointSubnetId string

@description('Private DNS zone for privatelink.vaultcore.azure.net.')
param privateDnsZoneId string

// Vault names are globally unique, 3-24 chars, alphanumeric and hyphens.
var keyVaultName = take('kv-${namePrefix}-${uniqueString(resourceGroup().id)}', 24)

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: keyVaultName
  location: location
  tags: tags
  properties: {
    sku: {
      family: 'A'
      name: 'standard'
    }
    tenantId: subscription().tenantId
    // RBAC rather than access policies: role assignments are auditable and
    // manageable as IaC, which access policies are not.
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 90
    enablePurgeProtection: true
    // Reachable only through the private endpoint below.
    publicNetworkAccess: 'Disabled'
    networkAcls: {
      bypass: 'AzureServices'
      defaultAction: 'Deny'
    }
  }
}

resource privateEndpoint 'Microsoft.Network/privateEndpoints@2023-11-01' = {
  name: 'pep-${keyVaultName}'
  location: location
  tags: tags
  properties: {
    subnet: {
      id: privateEndpointSubnetId
    }
    privateLinkServiceConnections: [
      {
        name: 'plsc-keyvault'
        properties: {
          privateLinkServiceId: keyVault.id
          groupIds: ['vault']
        }
      }
    ]
  }
}

resource privateDnsZoneGroup 'Microsoft.Network/privateEndpoints/privateDnsZoneGroups@2023-11-01' = {
  parent: privateEndpoint
  name: 'default'
  properties: {
    privateDnsZoneConfigs: [
      {
        name: 'vaultcore'
        properties: {
          privateDnsZoneId: privateDnsZoneId
        }
      }
    ]
  }
}

output keyVaultName string = keyVault.name
output keyVaultId string = keyVault.id
output keyVaultUri string = keyVault.properties.vaultUri
