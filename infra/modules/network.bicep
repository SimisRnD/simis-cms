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

@description('''
Subnet reserved for an optional point-to-site VPN gateway (modules/vpngateway.bicep).
Always carved out, never charged for while empty. Reserving it up front means
turning the gateway on later is a additive deployment rather than a change to
this VNet's subnet list, which is the disruptive kind.
''')
param gatewaySubnetPrefix string = '10.20.255.0/27'

@description('''
Deploy a NAT Gateway on the app subnet so the App Service has a route to the public internet.
The app runs with vnetRouteAllEnabled, which sends every outbound packet into this VNet -- and a
VNet has no default internet path -- so without this the app cannot reach any external service.
reCAPTCHA's server-side siteverify call and any outbound SMTP relay both fail in ways that look
like bad credentials rather than a network fault.

Also gives egress a single static address, which is what an SMTP relay or a partner API needs on
its allow-list.
''')
param enableNatGateway bool = true

@description('''
Idle timeout for NAT Gateway SNAT flows. Raised above the 4-minute default because SMTP relays
hold connections open between sends and a shorter timeout drops them mid-conversation.
''')
@minValue(4)
@maxValue(120)
param natIdleTimeoutInMinutes int = 10

var appSubnetName = 'snet-app'
var privateEndpointSubnetName = 'snet-private-endpoints'
// Azure requires this exact name; the gateway will not deploy into anything else.
var gatewaySubnetName = 'GatewaySubnet'
var natGatewayName = 'nat-${namePrefix}'
var natPublicIpName = 'pip-nat-${namePrefix}'

// Egress path for the app subnet. Standard SKU and a static address are both required by NAT
// Gateway; this address is the one to hand to an SMTP relay. Deliberately regional rather than
// zonal -- a zonal NAT Gateway survives only its own zone, and pinning the single egress path to
// one zone would turn a zone outage into a total outbound outage.
resource natPublicIp 'Microsoft.Network/publicIPAddresses@2023-11-01' = if (enableNatGateway) {
  name: natPublicIpName
  location: location
  tags: tags
  sku: {
    name: 'Standard'
  }
  properties: {
    publicIPAllocationMethod: 'Static'
    publicIPAddressVersion: 'IPv4'
    idleTimeoutInMinutes: 4
  }
}

resource natGateway 'Microsoft.Network/natGateways@2023-11-01' = if (enableNatGateway) {
  name: natGatewayName
  location: location
  tags: tags
  sku: {
    name: 'Standard'
  }
  properties: {
    idleTimeoutInMinutes: natIdleTimeoutInMinutes
    publicIpAddresses: [
      {
        id: natPublicIp.id
      }
    ]
  }
}

resource vnet 'Microsoft.Network/virtualNetworks@2023-11-01' = {
  name: 'vnet-${namePrefix}'
  location: location
  tags: tags
  dependsOn: enableNatGateway ? [natGateway] : []
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
          // Attached inline for the same reason the subnets are declared inline: a separate subnet
          // child resource would race the VNet's own subnet list. Referenced by name rather than
          // symbolically because the resource is conditional; the dependsOn below supplies ordering.
          natGateway: enableNatGateway ? { id: resourceId('Microsoft.Network/natGateways', natGatewayName) } : null
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
      {
        // Declared inline with its siblings on purpose. Bicep lets you define
        // subnets either inline here or as separate child resources, but mixing
        // the two makes concurrent deployments overwrite each other's subnet
        // list -- so every subnet this VNet will ever hold belongs in this array.
        name: gatewaySubnetName
        properties: {
          addressPrefix: gatewaySubnetPrefix
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

// Azure Files private endpoint for the CMS_PATH share. Without this zone the app resolves the
// storage account's public FQDN and the SMB mount never reaches the private endpoint.
resource fileDnsZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'privatelink.file.${environment().suffixes.storage}'
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

resource fileDnsLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  parent: fileDnsZone
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
output gatewaySubnetId string = resourceId('Microsoft.Network/virtualNetworks/subnets', vnet.name, gatewaySubnetName)
output postgresDnsZoneId string = postgresDnsZone.id
output keyVaultDnsZoneId string = keyVaultDnsZone.id
output fileDnsZoneId string = fileDnsZone.id
