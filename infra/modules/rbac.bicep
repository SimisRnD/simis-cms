// ---------------------------------------------------------------------------
// Role assignments for the application's system-assigned managed identities:
// the app's own, and its staging slot's.
//
// Lives in its own module because the assignments need the principal ids,
// which only exist after the app -- while the vault and registry are created
// before it. Two grants per identity, both least-privilege data-plane reads:
//
//   Key Vault Secrets User -- resolve the DB_PASSWORD / CMS_SECRET_KEY /
//     CMS_ADMIN_PASSWORD references (decision #6; the vault is RBAC-mode).
//   AcrPull -- pull the application image; the registry admin account is
//     disabled, so this is the only pull path.
//
// The staging slot needs both for the same reasons production does: it runs
// the same container with the same configuration, and a slot does not share
// the app's identity (see stagingSlot in appservice.bicep). The rolling deploy
// checks the slot holds every grant production holds, and stops if not.
//
// Assignment names are guid(scope, principal, role). The app's principal comes
// first in principalIds and its names are unchanged from when this module took
// a single principal, so redeploying does not churn the existing assignments.
// Grants made by hand for an identity listed here must use the same names, or
// the next deployment of this template fails with RoleAssignmentExists.
// ---------------------------------------------------------------------------

@description('Principal ids of the system-assigned managed identities to grant: the App Service and its staging slot.')
param principalIds array

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

resource keyVaultSecretsUser 'Microsoft.Authorization/roleAssignments@2022-04-01' = [for principalId in principalIds: {
  name: guid(keyVault.id, principalId, keyVaultSecretsUserRoleId)
  scope: keyVault
  properties: {
    principalId: principalId
    roleDefinitionId: keyVaultSecretsUserRoleId
    principalType: 'ServicePrincipal'
  }
}]

resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = [for principalId in principalIds: {
  name: guid(registry.id, principalId, acrPullRoleId)
  scope: registry
  properties: {
    principalId: principalId
    roleDefinitionId: acrPullRoleId
    principalType: 'ServicePrincipal'
  }
}]
