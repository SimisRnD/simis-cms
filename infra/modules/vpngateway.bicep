// ---------------------------------------------------------------------------
// Point-to-site VPN gateway — optional administrative access to the private
// data tier.
//
// Why this exists: every data-tier resource in this design is private-endpoint
// only (Phase 0 decision #4). PostgreSQL and Key Vault reject public traffic,
// and the App Service has publicNetworkAccess disabled with FTPS off, so its
// SCM/Kudu console is not publicly reachable either. That is deliberate, but it
// leaves no path from an administrator's laptop to the database for the
// occasional task the application cannot do for itself — verifying the first
// Flyway run, inspecting state during an incident, an emergency correction.
//
// This module closes that gap without introducing a virtual machine. A jumpbox
// would also work, but it is an operating system somebody has to patch, harden,
// scan, and carry in the SSP as an in-scope system. A gateway has no OS.
//
// DISABLED BY DEFAULT. It bills hourly whether or not anyone connects (see the
// README), so main.bicep only deploys it when enableVpnGateway is true.
//
// Authentication is Entra ID, not certificates: it reuses the identity controls
// the tenant already enforces (MFA, conditional access, group membership,
// revocation on offboarding) instead of standing up a parallel certificate
// lifecycle nobody owns.
// ---------------------------------------------------------------------------

@description('Azure region for the gateway. Must match the virtual network.')
param location string

@description('Prefix applied to resource names.')
param namePrefix string

@description('Tags applied to every resource.')
param tags object

@description('Resource id of the GatewaySubnet. The subnet must be named exactly "GatewaySubnet" -- Azure requires it.')
param gatewaySubnetId string

@description('Entra ID tenant id that authenticates VPN clients.')
param tenantId string

@description('''
Address pool handed to connected VPN clients. Must not overlap the VNet address
space or anything else the client can already route to.
''')
param vpnClientAddressPool string = '172.16.201.0/24'

@description('''
Gateway SKU. The AZ variants are zone-redundant and are the combination that
pairs cleanly with the Standard public IP below -- Basic public IPs are retired,
and non-AZ gateway SKUs historically required them.
''')
@allowed(['VpnGw1AZ', 'VpnGw2AZ', 'VpnGw3AZ'])
param skuName string = 'VpnGw1AZ'

@description('''
Application id of the Azure VPN Client that tokens are issued for. The default
is the Microsoft-registered client for Azure Public. It differs in sovereign
clouds and Microsoft has changed it before -- confirm against current docs
rather than trusting this default blindly.
''')
param aadAudience string = 'c632b3df-fb67-4d84-bdcf-b95ad541b5c8'

@description('''
Token issuer host. Azure Public issues these tokens from sts.windows.net; the
environment() function exposes no equivalent property, so unlike the login
endpoint this one cannot be derived and must be overridden by hand in a
sovereign cloud.
''')
param aadIssuerHost string = 'https://sts.windows.net'

var gatewayName = 'vgw-${namePrefix}'

// Standard SKU, static allocation. Basic public IPs reached end of life, and a
// gateway's address must not move underneath connected clients.
resource publicIp 'Microsoft.Network/publicIPAddresses@2023-11-01' = {
  name: 'pip-${gatewayName}'
  location: location
  tags: tags
  sku: {
    name: 'Standard'
  }
  properties: {
    publicIPAllocationMethod: 'Static'
  }
}

resource vpnGateway 'Microsoft.Network/virtualNetworkGateways@2023-11-01' = {
  name: gatewayName
  location: location
  tags: tags
  properties: {
    gatewayType: 'Vpn'
    // Point-to-site requires a route-based gateway.
    vpnType: 'RouteBased'
    enableBgp: false
    activeActive: false
    sku: {
      name: skuName
      tier: skuName
    }
    ipConfigurations: [
      {
        name: 'vnetGatewayConfig'
        properties: {
          privateIPAllocationMethod: 'Dynamic'
          subnet: {
            id: gatewaySubnetId
          }
          publicIPAddress: {
            id: publicIp.id
          }
        }
      }
    ]
    vpnClientConfiguration: {
      vpnClientAddressPool: {
        addressPrefixes: [
          vpnClientAddressPool
        ]
      }
      // Entra ID authentication is only offered over OpenVPN.
      vpnClientProtocols: [
        'OpenVPN'
      ]
      vpnAuthenticationTypes: [
        'AAD'
      ]
      // environment() rather than a literal host, so this stays correct if the
      // deployment ever targets a sovereign cloud. loginEndpoint already carries
      // its trailing slash.
      aadTenant: '${environment().authentication.loginEndpoint}${tenantId}/'
      aadAudience: aadAudience
      aadIssuer: '${aadIssuerHost}/${tenantId}/'
    }
  }
}

output gatewayName string = vpnGateway.name
output gatewayPublicIp string = publicIp.properties.ipAddress
output vpnClientAddressPool string = vpnClientAddressPool
