# Azure infrastructure (Bicep)

Infrastructure-as-code for deploying simis-cms to Azure — Milestone #4 Phase 2.

Every resource here is reproducible and reviewable, which is the point: the templates
are themselves a CM-2 configuration-management evidence artifact, not just a
convenience.

## Status

**Deployed.** First applied to a real subscription on 13–14 August 2026; the pilot
runs on these templates. Everything still compiles clean with `az bicep build`.

Two things the first deployment established, because neither is visible in the
templates:

- **Region availability is a subscription-level constraint, not a template one.**
  The first attempt failed because PostgreSQL Flexible Server was not permitted in
  the requested region for that subscription. Confirm regional availability for the
  *database* before choosing a region for everything else.
- **`environmentName` is permanent.** It feeds every resource name through
  `uniqueString(resourceGroup().id)`, so changing it later means redeploying the
  whole group rather than renaming anything.

Operating the deployed environment — releasing an image, reading container logs,
diagnosing a failed start — is documented internally alongside the other runbooks
rather than here. This file stays scoped to what the templates provision and the
deploy-time steps they cannot perform themselves.

## What is here

| File | Purpose |
|---|---|
| `main.bicep` | Orchestrator; wires the modules and exposes outputs for the edge tier |
| `modules/network.bicep` | VNet, App Service integration subnet, private-endpoint subnet, private DNS zones |
| `modules/loganalytics.bicep` | Log Analytics workspace (container stdout → Sentinel "Path A") |
| `modules/storage.bicep` | Storage account + file share backing `CMS_PATH` |
| `modules/keyvault.bicep` | Key Vault (RBAC, private endpoint, purge protection) |
| `modules/postgres.bicep` | PostgreSQL Flexible Server + database + PostGIS allow-list + private endpoint |
| `modules/acr.bicep` | Container registry for the signed app image; admin account disabled |
| `modules/appservice.bicep` | Plan + Linux container app: managed identity, Key Vault references, `CMS_PATH` mount, VNet integration, diagnostics; public ingress disabled |
| `modules/rbac.bicep` | The app identity's grants: Key Vault Secrets User + AcrPull |
| `modules/frontdoor.bicep` | Front Door Premium + WAF (managed rulesets), Private Link origin, optional custom domain with managed TLS, edge logs to the workspace |
| `modules/vpngateway.bicep` | **Optional, off by default.** Point-to-site VPN gateway giving administrators a private path to the database and Key Vault |

## What is not here yet

The pipeline that pushes the image to the registry is issue #246.

## The ingress path, end to end

```
client
  └─ HTTPS → Front Door (managed TLS; WAF in Prevention: DRS 2.1 + bot rules)
       └─ Private Link → App Service (publicNetworkAccess: Disabled — no public path)
            └─ VNet-integrated outbound → private endpoints → PostgreSQL, Key Vault
```

Nothing between the client and the database is publicly reachable except the edge
itself. The app's only ingress is Front Door's Private Link origin; the database
and vault accept only their private endpoints.

## Decisions this implements

Resolved in Phase 0 — see `decision-milestone-4-phase0-decisions.md` in the runbooks:

- **#1** Azure Commercial · **#2** App Service for Containers · **#3** Bicep
- **#4** private endpoints for the database and Key Vault, with VNet integration
- **#5** hardened official base images · **#6** Key Vault + managed identity
- **#7** platform FIPS modules · **#8** scale up only for the pilot

## Two things that are load-bearing

- **`CMS_PATH` must be external storage.** App Service containers are ephemeral, so the
  uploaded file library lives on the Azure Files share and is mounted in. If it stays
  inside the container, every restart silently loses uploads.
- **PostGIS must be allow-listed before it can be created.** On Flexible Server an
  extension has to appear in the `azure.extensions` server parameter before
  `CREATE EXTENSION` succeeds. `postgres.bicep` sets it, which is what lets the Flyway
  install run unattended on first boot.

## Validate locally

No subscription or login required:

```
az bicep build --file infra/main.bicep --stdout > /dev/null
```

Silence means success. To check every file:

```
for f in infra/modules/*.bicep infra/main.bicep; do az bicep build --file "$f" --stdout > /dev/null || echo "FAILED: $f"; done
```

## Deploy-time inputs

`postgresAdministratorPassword` is a `@secure()` parameter with no default and **must
not** be committed. Supply it at deploy time from Key Vault or a secure pipeline
variable.

## Before first boot (application tier)

The app resolves three Key Vault references at startup, and IaC deliberately does
**not** create the secret values — the ISSM does, once, before the first start:

- `db-password` — the database login's password
- `cms-secret-key` — the CMS encryption key
- `cms-admin-password` — the admin bootstrap password

The image must also exist in the registry (issue #246's pipeline, or a one-time
manual push) — App Service pulls it with its managed identity via AcrPull; there is
no registry password.

### Creating those secrets is harder than it looks

Two things block it, and both are consequences of the design working correctly.
Neither is obvious from an error message, and both cost time on the first
deployment.

**Subscription Owner does not let you write secrets.** The vault is created with
`enableRbacAuthorization: true`, so Owner grants management-plane rights only —
it can see the vault, change its configuration, and delete it, but cannot read or
write a single secret. Assign yourself **Key Vault Secrets Officer** on the vault
itself first (vault → Access control (IAM) → Add role assignment). Allow a couple
of minutes for the assignment to propagate.

**The vault is not reachable from your laptop.** `publicNetworkAccess` is
`Disabled` and the network default action is `Deny`, so even holding the right
role, the portal's secret blade fails with a network error. This is the same gap
described under "Administrative access to the private data tier" above, and the
same stopgap applies: vault → Networking → allow your own address, create the
three secrets, then **set it back to disabled**.

Two details that matter when you do:

- The firewall accepts **IPv4 only**. A "what's my IP" site will hand you an IPv6
  address if your connection has one; use `curl -4 ifconfig.me` or an IPv4-only
  lookup instead.
- Unlike the PostgreSQL connectivity method, Key Vault's public access toggles
  freely and reversibly. Turning it on briefly is a temporary deviation, not a
  permanent change to the posture — but it is still a deviation, and the failure
  mode to avoid is forgetting the second half.

Alternatively, a client running inside the VNet (the VPN gateway above, or the
App Service's own Kudu console if public ingress were temporarily enabled)
reaches the vault through its private endpoint with no firewall change at all.
That is the cleaner path once either is routinely available.

## Administrative access to the private data tier

Everything in the data path is private-endpoint only, which is the point — and it
means there is **no path from an administrator's laptop to the database.** Key Vault
and PostgreSQL reject public traffic, and the App Service has `publicNetworkAccess`
disabled with FTPS off, so its SCM/Kudu console is not publicly reachable either.

Routine operation does not need one. Flyway migrates unattended on first boot, and
the app reads its own secrets through its managed identity. The gap only matters for
occasional work: confirming that first migration, inspecting state during an
incident, an emergency correction.

Two ways to close it. Pick deliberately — the difference is roughly $150/month.

### Occasional access: temporarily allow one address

PostgreSQL Flexible Server here is `publicNetworkAccess: Disabled` **plus** a private
endpoint, which is the toggleable arrangement rather than VNet injection. Public
access can be turned on, scoped to a single address, and turned back off:

```bash
# open
az postgres flexible-server update --resource-group <rg> --name <server> --public-access Enabled
az postgres flexible-server firewall-rule create --resource-group <rg> --name <server> \
  --rule-name temp-admin --start-ip-address <your.ip> --end-ip-address <your.ip>

# ... do the work ...

# close, and mean it
az postgres flexible-server firewall-rule delete --resource-group <rg> --name <server> --rule-name temp-admin --yes
az postgres flexible-server update --resource-group <rg> --name <server> --public-access Disabled
```

Costs nothing and takes seconds, but it is a deliberate, temporary deviation from
the private-only posture: for the duration, the database's TLS endpoint is reachable
from the internet and protected only by that firewall rule and its credentials. It
is the ISSM's call whether that is acceptable, and it should be logged when used.
The failure mode to avoid is forgetting the second half.

### Routine access: the VPN gateway

Set `enableVpnGateway: true` and supply `vpnTenantId`. Administrators install the
Azure VPN Client, authenticate with their normal Entra ID identity — inheriting MFA,
conditional access, and offboarding — and their laptop joins the VNet. The private
endpoints then resolve and connect as if from inside.

Chosen over a jumpbox VM deliberately: a gateway has no operating system to patch,
harden, scan, or carry in the SSP as an in-scope system.

It bills hourly from creation whether or not anyone connects. Confirm the current
rate before enabling; at the time of writing a `VpnGw1AZ` runs on the order of
$150/month, which is real money against a pilot budget for something that may be
used a few times a month.

**Clients need one more step, or this looks broken.** Joining the VNet gives a route,
not name resolution: the private DNS zones are resolvable from inside the VNet, but a
connected client still asks its own resolver and gets the *public* address for
`psql-<prefix>.postgres.database.azure.com`. The connection then fails in a way that
looks like the VPN is not working. Either add a hosts entry mapping that FQDN to the
private endpoint's address (keep the FQDN — Flexible Server requires TLS and the
certificate is issued for the name, so connecting by bare IP fails validation), or
stand up an Azure DNS Private Resolver, which solves it properly and costs about as
much again. For a handful of administrators the hosts entry is the proportionate
answer.

`GatewaySubnet` is carved out of the VNet whether or not the gateway is enabled. An
empty subnet is free, and reserving it means turning this on later is an additive
deployment rather than a change to the VNet's subnet list.

## Edge deploy-time steps (the template cannot do these)

1. **Approve the Private Link connection.** Front Door's origin appears on the App
   Service as a *pending* private endpoint connection; approve it once
   (`az network private-endpoint-connection approve`). Until then the edge serves
   502s — the app has no other ingress by design.
2. **Set `clientIpHeader` (CMS_CLIENT_IP_HEADER) to `X-Azure-ClientIP`, and
   `trustedProxies` (CMS_TRUSTED_PROXIES) to a regex matching the App Service front
   ends.** The proxy-aware handling shipped in #166/#182; these values activate it.
   Without them, `getRemoteAddr()`/`isSecure()` see the proxy rather than the client,
   degrading the Secure-cookie flag, the IP firewall, rate limiting, and the audit
   source IP. Note `trustedProxies` is a **Java regex, not CIDR** — a CIDR value
   compiles and then matches nothing, leaving the setting looking configured while
   the app still records the proxy. Do *not* try to list the `AzureFrontDoor.Backend`
   ranges there: they are public, number ~147 IPv4 prefixes, and change over time, so
   `X-Forwarded-For` resolution stops on the Front Door node (issue #1675). The header
   carries the client directly and needs no monthly refresh.
3. **At cutover:** set `customDomainName` (Front Door provisions the managed
   certificate; DNS CNAMEs to the endpoint hostname) and `customUrl` (CMS_URL)
   to the same domain. Until then the WAF fronts the default endpoint hostname.
4. **WAF mode:** ships in `Prevention`. If content authoring hits managed-rule
   false positives, drop to `Detection` *temporarily*, tune exclusions, and return
   to `Prevention` before cutover — Detection-forever is the failure mode to avoid.
