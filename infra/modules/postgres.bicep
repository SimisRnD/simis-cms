// ---------------------------------------------------------------------------
// Azure Database for PostgreSQL Flexible Server + database + private endpoint.
//
// PostGIS is required: the install migrations and the application's geo
// features depend on it. On Flexible Server an extension must first be
// allow-listed via the azure.extensions server parameter before CREATE
// EXTENSION will succeed -- allow-listing it here is what lets the Flyway
// install run unattended on first boot.
//
// Phase 0 §7 flags verifying PostGIS availability for the chosen region/tier
// early; that check belongs at deploy time, not here.
// ---------------------------------------------------------------------------

@description('Azure region for the database.')
param location string

@description('Prefix applied to resource names.')
param namePrefix string

@description('Tags applied to every resource.')
param tags object

@description('Subnet that will hold the private endpoint.')
param privateEndpointSubnetId string

@description('Private DNS zone for privatelink.postgres.database.azure.com.')
param privateDnsZoneId string

@description('Administrator login for the server.')
param administratorLogin string

@description('Administrator password. Supply from Key Vault at deploy time; never commit a value.')
@secure()
param administratorLoginPassword string

@description('PostgreSQL major version.')
@allowed(['14', '15', '16', '17'])
param postgresVersion string = '16'

@description('Compute SKU. Pilot is scale-up-only (Phase 0 decision #8), so a single server is sufficient.')
param skuName string = 'Standard_D2ds_v5'

@description('SKU tier.')
@allowed(['Burstable', 'GeneralPurpose', 'MemoryOptimized'])
param skuTier string = 'GeneralPurpose'

@description('Allocated storage in GiB.')
param storageSizeGb int = 128

@description('Name of the application database.')
param databaseName string = 'simiscms'

@description('Backup retention in days.')
@minValue(7)
@maxValue(35)
param backupRetentionDays int = 14

var serverName = 'psql-${namePrefix}'

resource postgresServer 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: serverName
  location: location
  tags: tags
  sku: {
    name: skuName
    tier: skuTier
  }
  properties: {
    version: postgresVersion
    administratorLogin: administratorLogin
    administratorLoginPassword: administratorLoginPassword
    storage: {
      storageSizeGB: storageSizeGb
      autoGrow: 'Enabled'
    }
    backup: {
      backupRetentionDays: backupRetentionDays
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      // Scale-up-only pilot; revisit with the scale-out topology decision.
      mode: 'Disabled'
    }
    network: {
      // Reachable only through the private endpoint below.
      publicNetworkAccess: 'Disabled'
    }
  }
}

// Allow-list PostGIS so the install migrations can CREATE EXTENSION.
resource extensionsAllowList 'Microsoft.DBforPostgreSQL/flexibleServers/configurations@2024-08-01' = {
  parent: postgresServer
  name: 'azure.extensions'
  properties: {
    value: 'POSTGIS'
    source: 'user-override'
  }
}

resource database 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgresServer
  name: databaseName
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
  dependsOn: [
    extensionsAllowList
  ]
}

resource privateEndpoint 'Microsoft.Network/privateEndpoints@2023-11-01' = {
  name: 'pep-${serverName}'
  location: location
  tags: tags
  properties: {
    subnet: {
      id: privateEndpointSubnetId
    }
    privateLinkServiceConnections: [
      {
        name: 'plsc-postgres'
        properties: {
          privateLinkServiceId: postgresServer.id
          groupIds: ['postgresqlServer']
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
        name: 'postgres'
        properties: {
          privateDnsZoneId: privateDnsZoneId
        }
      }
    ]
  }
}

output serverName string = postgresServer.name
output serverFqdn string = postgresServer.properties.fullyQualifiedDomainName
output databaseName string = database.name
