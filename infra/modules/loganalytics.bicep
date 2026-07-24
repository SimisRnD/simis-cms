// ---------------------------------------------------------------------------
// Log Analytics workspace.
//
// The app writes to container stdout, which App Service ships here. That is
// exactly the Sentinel "Path A" ingestion the detection kit was written
// against, so no agent or custom collector is required.
// ---------------------------------------------------------------------------

@description('Azure region for the workspace.')
param location string

@description('Prefix applied to resource names.')
param namePrefix string

@description('Tags applied to every resource.')
param tags object

@description('Log retention in days. Ingestion is usage-priced, so this is a cost lever.')
@minValue(30)
@maxValue(730)
param retentionInDays int = 90

resource workspace 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'log-${namePrefix}'
  location: location
  tags: tags
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: retentionInDays
    features: {
      enableLogAccessUsingOnlyResourcePermissions: true
    }
    publicNetworkAccessForIngestion: 'Enabled'
    publicNetworkAccessForQuery: 'Enabled'
  }
}

output workspaceId string = workspace.id
output workspaceCustomerId string = workspace.properties.customerId
