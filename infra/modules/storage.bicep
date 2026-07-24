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
    allowSharedKeyAccess: true
    publicNetworkAccess: 'Enabled'
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

output storageAccountName string = storageAccount.name
output storageAccountId string = storageAccount.id
output fileShareName string = cmsPathShare.name
