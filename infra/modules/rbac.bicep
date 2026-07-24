// ---------------------------------------------------------------------------
// Role assignments for the application's system-assigned managed identity.
//
// Lives in its own module because the assignments need the app's principal
// id, which only exists after the app -- while the vault and registry are
// created before it. Two grants, both least-privilege data-plane reads:
//
//   Key Vault Secrets User -- resolve the DB_PASSWORD / CMS_SECRET_KEY /
//     CMS_ADMIN_PASSWORD references (decision #6; the vault is RBAC-mode).
//   AcrPull -- pull the application image; the registry admin account is
//     disabled, so this is the only pull path.
// ---------------------------------------------------------------------------

@description('Principal id of the App Service system-assigned managed identity.')
param principalId string

@description('Name of the Key Vault holding the application secrets.')
param keyVaultName string

@description('Name of the container registry the app pulls from.')
param acrName string

// Built-in role definition ids (stable, documented platform GUIDs).
var keyVaultSecretsUserRoleId = subscriptionResourceId(
  'Microsoft.Authorization/roleDefinitions', '4633458b-17de-408a-b874-0445c86b69e6')
var acrPullRoleId = subscriptionResourceId(
  'Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: keyVaultName
}

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = {
  name: acrName
}

resource keyVaultSecretsUser 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, principalId, keyVaultSecretsUserRoleId)
  scope: keyVault
  properties: {
    principalId: principalId
    roleDefinitionId: keyVaultSecretsUserRoleId
    principalType: 'ServicePrincipal'
  }
}

resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, principalId, acrPullRoleId)
  scope: registry
  properties: {
    principalId: principalId
    roleDefinitionId: acrPullRoleId
    principalType: 'ServicePrincipal'
  }
}
