// ---------------------------------------------------------------------------
// Network foundation: VNet, delegated app-integration subnet, private-endpoint
// subnet, and the private DNS zones the private endpoints resolve through.
//
// Phase 0 decision #4: private endpoints for the database and Key Vault, with
// VNet integration for the app. Nothing in the data path is publicly reachable.
// ---------------------------------------------------------------------------

@description('Azure region for all networking resources.')
param location string

@description('Prefix applied to resource names, e.g. simiscms-pilot.')
param namePrefix string

@description('Tags applied to every resource.')
param tags object

@description('Address space for the virtual network.')
param vnetAddressPrefix string = '10.20.0.0/16'

@description('Subnet delegated to App Service for VNet integration (outbound).')
param appSubnetPrefix string = '10.20.1.0/24'

@description('Subnet holding the private endpoints for PostgreSQL and Key Vault.')
param privateEndpointSubnetPrefix string = '10.20.2.0/24'

var appSubnetName = 'snet-app'
var privateEndpointSubnetName = 'snet-private-endpoints'

resource vnet 'Microsoft.Network/virtualNetworks@2023-11-01' = {
  name: 'vnet-${namePrefix}'
  location: location
  tags: tags
  properties: {
    addressSpace: {
      addressPrefixes: [vnetAddressPrefix]
    }
    subnets: [
      {
        // App Service regional VNet integration requires a delegated, dedicated subnet.
        name: appSubnetName
        properties: {
          addressPrefix: appSubnetPrefix
          delegations: [
            {
              name: 'appservice-delegation'
              properties: {
                serviceName: 'Microsoft.Web/serverFarms'
              }
            }
          ]
        }
      }
      {
        name: privateEndpointSubnetName
        properties: {
          addressPrefix: privateEndpointSubnetPrefix
          privateEndpointNetworkPolicies: 'Disabled'
        }
      }
    ]
  }
}

// Private DNS zones. Without these the app resolves the public FQDN of each
// service instead of its private endpoint address, and the connection fails.
resource postgresDnsZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'privatelink.postgres.database.azure.com'
  location: 'global'
  tags: tags
}

resource keyVaultDnsZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'privatelink.vaultcore.azure.net'
  location: 'global'
  tags: tags
}

resource postgresDnsLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  parent: postgresDnsZone
  name: 'link-${namePrefix}'
  location: 'global'
  properties: {
    registrationEnabled: false
    virtualNetwork: {
      id: vnet.id
    }
  }
}

resource keyVaultDnsLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  parent: keyVaultDnsZone
  name: 'link-${namePrefix}'
  location: 'global'
  properties: {
    registrationEnabled: false
    virtualNetwork: {
      id: vnet.id
    }
  }
}

output vnetId string = vnet.id
output appSubnetId string = resourceId('Microsoft.Network/virtualNetworks/subnets', vnet.name, appSubnetName)
output privateEndpointSubnetId string = resourceId('Microsoft.Network/virtualNetworks/subnets', vnet.name, privateEndpointSubnetName)
output postgresDnsZoneId string = postgresDnsZone.id
output keyVaultDnsZoneId string = keyVaultDnsZone.id
