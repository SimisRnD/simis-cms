// ---------------------------------------------------------------------------
// Azure Container Registry for the signed application image.
//
// The publish pipeline builds, scans, signs, and attests the image; pushing it
// here (issue #246) replaces GHCR as the deployment source. The registry
// admin account stays disabled: App Service pulls with its managed identity
// via the AcrPull role (rbac.bicep), so no registry password exists at all.
// ---------------------------------------------------------------------------

@description('Azure region for the registry.')
param location string

@description('Prefix applied to resource names.')
param namePrefix string

@description('Tags applied to every resource.')
param tags object

@description('Registry SKU. Standard fits the pilot; Premium adds private endpoints and geo-replication if required later.')
@allowed(['Basic', 'Standard', 'Premium'])
param skuName string = 'Standard'

// Registry names are globally unique, alphanumeric only, 5-50 chars.
// 'cr' (2) + prefix with hyphens removed (12 for simiscms-pilot) + 13 from
// uniqueString() stays comfortably inside the limit.
var registryName = toLower('cr${replace(namePrefix, '-', '')}${uniqueString(resourceGroup().id)}')

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: registryName
  location: location
  tags: tags
  sku: {
    name: skuName
  }
  properties: {
    // Managed identity + RBAC only; a registry password would be a second,
    // unaudited credential path.
    adminUserEnabled: false
  }
}

output registryName string = registry.name
output registryId string = registry.id
output loginServer string = registry.properties.loginServer
