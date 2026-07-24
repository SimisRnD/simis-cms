// ---------------------------------------------------------------------------
// Edge tier: Front Door Premium + WAF in front of App Service.
//
// Premium rather than Standard because the WAF managed rulesets (OWASP-derived
// DRS + bot rules) only run on Premium -- a WAF with custom rules alone would
// be an empty shell. Premium also enables the Private Link origin below, which
// is what makes "reachable only through the edge" literal: the app's public
// ingress is disabled outright (appservice.bicep), and Front Door reaches it
// over a private endpoint. There is no public path to the origin at all.
//
// Ingress path, end to end:
//   client -> Front Door (TLS, WAF) -> Private Link -> App Service
//          -> private endpoints (PostgreSQL, Key Vault)
//
// Two deploy-time steps this template cannot do by itself:
//   1. The Private Link connection from Front Door appears on the App Service
//      as a pending private endpoint connection and must be APPROVED once
//      (az network private-endpoint-connection approve). Until approved, the
//      origin is unreachable and the endpoint serves 502s.
//   2. CMS_TRUSTED_PROXIES (main.bicep trustedProxies) must be set to the
//      AzureFrontDoor.Backend service-tag ranges so the app resolves the real
//      client from X-Forwarded-For (the handling shipped in #166/#182; this
//      configuration is what activates it).
// ---------------------------------------------------------------------------

@description('Prefix applied to resource names.')
param namePrefix string

@description('Tags applied to every resource.')
param tags object

@description('Resource id of the App Service the edge fronts (Private Link target).')
param appServiceId string

@description('Default hostname of the App Service (origin host).')
param appHostName string

@description('Region of the App Service, used for the Private Link connection.')
param privateLinkLocation string

@description('Log Analytics workspace for access, WAF, and health-probe logs.')
param logAnalyticsWorkspaceId string

@description('WAF mode. Prevention blocks; Detection only logs. Ship in Prevention; drop to Detection temporarily if content-authoring hits false positives, tune, and return to Prevention before cutover.')
@allowed(['Prevention', 'Detection'])
param wafMode string = 'Prevention'

@description('Custom domain, e.g. www.example.org. Empty skips the domain + managed certificate until cutover (DNS is a cutover-day decision).')
param customDomainName string = ''

// WAF policy names allow letters and digits only.
var wafPolicyName = 'waf${replace(namePrefix, '-', '')}'
var profileName = 'afd-${namePrefix}'
var endpointName = 'fde-${namePrefix}'

resource wafPolicy 'Microsoft.Network/FrontDoorWebApplicationFirewallPolicies@2024-02-01' = {
  name: wafPolicyName
  location: 'Global'
  tags: tags
  sku: {
    name: 'Premium_AzureFrontDoor'
  }
  properties: {
    policySettings: {
      enabledState: 'Enabled'
      mode: wafMode
      requestBodyCheck: 'Enabled'
    }
    managedRules: {
      managedRuleSets: [
        {
          // Microsoft Default Rule Set (OWASP-derived), block on match.
          ruleSetType: 'Microsoft_DefaultRuleSet'
          ruleSetVersion: '2.1'
          ruleSetAction: 'Block'
        }
        {
          ruleSetType: 'Microsoft_BotManagerRuleSet'
          ruleSetVersion: '1.0'
        }
      ]
    }
  }
}

resource profile 'Microsoft.Cdn/profiles@2024-02-01' = {
  name: profileName
  location: 'global'
  tags: tags
  sku: {
    name: 'Premium_AzureFrontDoor'
  }
}

resource endpoint 'Microsoft.Cdn/profiles/afdEndpoints@2024-02-01' = {
  parent: profile
  name: endpointName
  location: 'global'
  tags: tags
  properties: {
    enabledState: 'Enabled'
  }
}

resource originGroup 'Microsoft.Cdn/profiles/originGroups@2024-02-01' = {
  parent: profile
  name: 'og-app'
  properties: {
    loadBalancingSettings: {
      sampleSize: 4
      successfulSamplesRequired: 3
      additionalLatencyInMilliseconds: 50
    }
    healthProbeSettings: {
      // The container exposes /healthz (same probe App Service itself uses).
      probePath: '/healthz'
      probeRequestType: 'GET'
      probeProtocol: 'Https'
      probeIntervalInSeconds: 30
    }
  }
}

resource origin 'Microsoft.Cdn/profiles/originGroups/origins@2024-02-01' = {
  parent: originGroup
  name: 'app-service'
  properties: {
    hostName: appHostName
    httpPort: 80
    httpsPort: 443
    // The Host header must match the App Service hostname or its front end
    // rejects the request.
    originHostHeader: appHostName
    priority: 1
    weight: 1000
    enabledState: 'Enabled'
    // Private Link to the app: with the app's public access disabled, this is
    // the only ingress path. The connection must be approved once at deploy
    // time (see header).
    sharedPrivateLinkResource: {
      privateLink: {
        id: appServiceId
      }
      groupId: 'sites'
      privateLinkLocation: privateLinkLocation
      requestMessage: 'Front Door edge for ${namePrefix}'
    }
    enforceCertificateNameCheck: true
  }
}

// Custom domain with a managed certificate; skipped until the domain exists.
resource customDomain 'Microsoft.Cdn/profiles/customDomains@2024-02-01' = if (!empty(customDomainName)) {
  parent: profile
  name: replace(customDomainName, '.', '-')
  properties: {
    hostName: customDomainName
    tlsSettings: {
      certificateType: 'ManagedCertificate'
      minimumTlsVersion: 'TLS12'
    }
  }
}

resource route 'Microsoft.Cdn/profiles/afdEndpoints/routes@2024-02-01' = {
  parent: endpoint
  name: 'route-app'
  properties: {
    originGroup: {
      id: originGroup.id
    }
    supportedProtocols: ['Http', 'Https']
    patternsToMatch: ['/*']
    forwardingProtocol: 'HttpsOnly'
    httpsRedirect: 'Enabled'
    linkToDefaultDomain: 'Enabled'
    customDomains: empty(customDomainName) ? [] : [
      {
        id: customDomain.id
      }
    ]
    // No caching: the CMS is dynamic and sets its own headers. Static-asset
    // caching is a deliberate later tune, not a default.
  }
  dependsOn: [
    origin
  ]
}

// Attach the WAF to everything the endpoint serves.
resource securityPolicy 'Microsoft.Cdn/profiles/securityPolicies@2024-02-01' = {
  parent: profile
  name: 'waf-attach'
  properties: {
    parameters: {
      type: 'WebApplicationFirewall'
      wafPolicy: {
        id: wafPolicy.id
      }
      associations: [
        {
          domains: concat(
            [{ id: endpoint.id }],
            empty(customDomainName) ? [] : [{ id: customDomain.id }]
          )
          patternsToMatch: ['/*']
        }
      ]
    }
  }
}

// Access, WAF, and health-probe logs to the workspace -- the WAF half of the
// Sentinel correlation the app's console logs already feed.
resource diagnostics 'Microsoft.Insights/diagnosticSettings@2021-05-01-preview' = {
  name: 'diag-${profileName}'
  scope: profile
  properties: {
    workspaceId: logAnalyticsWorkspaceId
    logs: [
      { category: 'FrontDoorAccessLog', enabled: true }
      { category: 'FrontDoorWebApplicationFirewallLog', enabled: true }
      { category: 'FrontDoorHealthProbeLog', enabled: true }
    ]
    metrics: [
      { category: 'AllMetrics', enabled: true }
    ]
  }
}

output endpointHostName string = endpoint.properties.hostName
// Every request Front Door forwards carries X-Azure-FDID with this id; an
// origin-side check against it is a later hardening option.
output frontDoorId string = profile.properties.frontDoorId
output wafPolicyId string = wafPolicy.id
