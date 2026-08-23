// ---------------------------------------------------------------------------
// Storage account + file share backing CMS_PATH.
//
// This is load-bearing: App Service containers are ephemeral, so the uploaded
// file library MUST live on an external share mounted into the app at
// CMS_PATH. If it stays inside the container, every restart silently loses
// uploads. See digitalocean-recovery-and-cutover.md §3 (runbooks).
// ---------------------------------------------------------------------------

@description('Azure region for the storage account.')
param location string

@description('Prefix applied to resource names.')
param namePrefix string

@description('Tags applied to every resource.')
param tags object

@description('Quota for the CMS_PATH file share, in GiB.')
@minValue(1)
@maxValue(102400)
param fileShareQuotaGb int = 100

@description('Resource id of the subnet holding private endpoints.')
param privateEndpointSubnetId string

@description('Resource id of the privatelink.file DNS zone the endpoint registers in.')
param fileDnsZoneId string

@description('''
Whether the storage account answers on its public endpoint. Defaults to Disabled: the file share is
reached over the private endpoint below, so nothing needs the public one.

This is the one setting to flip back if a deployment cannot mount CMS_PATH -- set it to Enabled,
redeploy, and the mount falls back to the public endpoint while the private path is investigated.
''')
@allowed([
  'Disabled'
  'Enabled'
])
param publicNetworkAccess string = 'Disabled'

// Storage account names are globally unique, lowercase, alphanumeric, 3-24 chars.
// uniqueString() always returns 13 characters, so this is 15-24 by construction:
// 'st' (2) + up to 9 of the prefix + 13. Truncating the prefix rather than the
// whole string keeps the unique suffix intact.
var storageAccountName = toLower('st${take(replace(namePrefix, '-', ''), 9)}${uniqueString(resourceGroup().id)}')
var fileShareName = 'cms-path'

resource storageAccount 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: storageAccountName
  location: location
  tags: tags
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    // TLS 1.2 minimum and no anonymous blob access; the share is reached with
    // the account key held in Key Vault, never public.
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
    allowBlobPublicAccess: false
    // The App Service mounts this share with the account key (appservice.bicep's
    // azureStorageAccounts block), so shared-key access has to stay on -- disabling it breaks
    // the CMS_PATH mount, not just the tooling.
    allowSharedKeyAccess: true
    publicNetworkAccess: publicNetworkAccess
  }
}

resource fileServices 'Microsoft.Storage/storageAccounts/fileServices@2023-05-01' = {
  parent: storageAccount
  name: 'default'
}

resource cmsPathShare 'Microsoft.Storage/storageAccounts/fileServices/shares@2023-05-01' = {
  parent: fileServices
  name: fileShareName
  properties: {
    shareQuota: fileShareQuotaGb
    enabledProtocols: 'SMB'
  }
}

// Private endpoint for the file sub-resource. Storage needs one endpoint per sub-resource; only
// 'file' is deployed because the file share is the sole thing this account serves. Mirrors the
// pep-/plsc- pattern in modules/postgres.bicep.
resource privateEndpoint 'Microsoft.Network/privateEndpoints@2023-11-01' = {
  name: 'pep-${storageAccountName}'
  location: location
  tags: tags
  properties: {
    subnet: {
      id: privateEndpointSubnetId
    }
    privateLinkServiceConnections: [
      {
        name: 'plsc-file'
        properties: {
          privateLinkServiceId: storageAccount.id
          groupIds: ['file']
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
        name: 'file'
        properties: {
          privateDnsZoneId: fileDnsZoneId
        }
      }
    ]
  }
}

output storageAccountName string = storageAccount.name
output storageAccountId string = storageAccount.id
output fileShareName string = cmsPathShare.name
