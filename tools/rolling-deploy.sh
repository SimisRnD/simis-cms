#!/bin/bash
set -euo pipefail

# Rolling deploy workflow for zero-downtime updates
#
# Manages two App Service deployment slots:
#   - Production (active, serving traffic via AFD)
#   - Staging (inactive, for next deployment)
#
# Process:
#   1. Ensure the staging slot exists, is on the VNet, and has its own identity
#      with production's access
#   2. Deploy the new image to the staging slot and start it
#   3. Give the container time to pull and start
#   4. Swap slots; App Service's warm-up ping to /healthz gates the swap
#   5. Drain old instance connections (wait window)
#   6. Stop the staging slot, which now holds the previous build (kept for rollback)
#
# On a failed swap production is untouched; the staging slot is stopped, holding
# the build that failed.
#
# ---------------------------------------------------------------------------
# HEALTH SIGNAL DESIGN -- read this before changing step 3.
#
# The health check that gates a swap runs INSIDE Azure, not from the CI runner.
# This is not a stylistic preference. The App Service runs with
# `publicNetworkAccess: Disabled` (ingress is Front Door's Private Link origin
# only) and a deployment slot inherits that setting. Verified 2026-08-14
# against a real staging slot: the slot's hostname answers HTTP 403
# "Web App - Unavailable" from outside Azure. An earlier version of this script
# polled https://<staging-slot>/healthz with curl from the GitHub Actions
# runner, which could never pass -- it would poll for the full timeout, delete
# staging, and exit non-zero on every run.
#
# The fix is App Service's own swap warm-up. `WEBSITE_SWAP_WARMUP_PING_PATH`
# and `WEBSITE_SWAP_WARMUP_PING_STATUSES` (set in
# infra/modules/appservice.bicep) tell Azure to request that path on the slot
# from inside the platform and to abort the swap unless it answers 200. So the
# swap in step 4 IS the health gate: if the new build cannot serve /healthz,
# `az webapp deployment slot swap` fails and production is never touched.
#
# Do NOT "fix" a swap failure by exposing the staging slot publicly. That would
# put an unprotected copy of the app outside Front Door and the WAF.
#
# Three further requirements, all easy to miss:
#   - The CI service principal needs rights to create and swap slots on this
#     one App Service (roughly Website Contributor, scoped to the resource).
#     AcrPush alone is not enough; without it step 1 fails immediately.
#   - `--configuration-source` clones app settings but NOT VNet integration
#     (a slot created that way has virtualNetworkSubnetId = null), so the slot
#     cannot reach PostgreSQL or Key Vault through their private endpoints and
#     will never pass warm-up. Step 1b below adds it explicitly.
#   - Nor does it give the slot a managed identity, and a slot does not share
#     the app's. Production pulls its image and resolves its Key Vault
#     references with its own system-assigned identity; a slot without one can
#     do neither. Step 1c gives the slot an identity and refuses to deploy until
#     that identity holds the same role grants as production's.
#
# The staging slot is created once and then reused; step 6 deliberately does not
# delete it. Slot creation is the most failure-prone step in this script, and not
# doing it on every run is what keeps this path reliable -- see the comment on
# step 6 for the Azure CLI failure it avoids. A slot holding the previous build
# is also a rollback artifact rather than litter, and step 2 overwrites it, so
# reusing it costs nothing in cleanliness. It is kept STOPPED between deploys,
# though: step 2 starts it and step 6 stops it again.
# ---------------------------------------------------------------------------
#
# Usage:
#   rolling-deploy.sh \
#     --resource-group my-rg \
#     --app-service-name simis-cms-pilot \
#     --image-uri my-acr.azurecr.io/simis-cms:v1.2.3 \
#     --health-check-timeout 300 \
#     --drain-timeout 60
#
# Environment:
#   Required: AZURE_SUBSCRIPTION_ID (for slot management)
#   Optional: DEBUG=1 (verbose output)

set +u
DEBUG="${DEBUG:-0}"
set -u

log() {
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*" >&2
}

log_debug() {
  if [[ "$DEBUG" == "1" ]]; then
    echo "[DEBUG] $*" >&2
  fi
}

die() {
  echo "❌ ERROR: $*" >&2
  exit 1
}

# A GitHub Actions warning annotation (stdout is where the runner reads them),
# for problems that should be seen but must not fail a deploy that succeeded.
warn() {
  echo "::warning::$*"
  log "⚠️  $*"
}

# Stops the staging slot. Returns the CLI's status rather than exiting: whether
# a failure here is fatal depends on what the caller has already done.
stop_staging() {
  az webapp stop \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_SERVICE_NAME" \
    --slot staging
}

# Parse arguments
RESOURCE_GROUP=""
APP_SERVICE_NAME=""
IMAGE_URI=""
HEALTH_CHECK_TIMEOUT=300
DRAIN_TIMEOUT=60

while [[ $# -gt 0 ]]; do
  case "$1" in
    --resource-group) RESOURCE_GROUP="$2"; shift 2 ;;
    --app-service-name) APP_SERVICE_NAME="$2"; shift 2 ;;
    --image-uri) IMAGE_URI="$2"; shift 2 ;;
    --health-check-timeout) HEALTH_CHECK_TIMEOUT="$2"; shift 2 ;;
    --drain-timeout) DRAIN_TIMEOUT="$2"; shift 2 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

[[ -z "$RESOURCE_GROUP" ]] && die "Missing --resource-group"
[[ -z "$APP_SERVICE_NAME" ]] && die "Missing --app-service-name"
[[ -z "$IMAGE_URI" ]] && die "Missing --image-uri"

log "Rolling deploy: $APP_SERVICE_NAME"
log "  Resource group: $RESOURCE_GROUP"
log "  New image: $IMAGE_URI"
log "  Health check timeout: ${HEALTH_CHECK_TIMEOUT}s"
log "  Drain timeout: ${DRAIN_TIMEOUT}s"

# ============================================================================
# Step 1: Ensure the staging slot exists
# ============================================================================

log "Step 1: Ensuring staging slot exists..."

# "production" is the App Service's implicit default slot: it is never
# listed by `deployment slot list` and can't be created or deleted, only
# swapped into. "staging" is the only extra slot this workflow uses. It
# persists between deploys (step 6 keeps it), so on a normal run it already
# exists and this step only confirms that; it is created here only on the
# first run, or if someone has deleted it.
#
# A failed listing is fatal rather than read as "no slots". Treating it as an
# empty list (as this step used to, with `|| echo ""`) sends the script down
# the creation path for a slot that already exists, which is both the wrong
# action and the most fragile step in this file (see step 6).
SLOTS=$(az webapp deployment slot list \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --query "[].name" \
  --output tsv) \
  || die "Could not list the App Service's deployment slots -- see the Azure error above"

if echo "$SLOTS" | grep -qx "staging"; then
  log "  staging slot already exists"
else
  log "  staging slot not found; creating it (cloning settings from production)..."
  az webapp deployment slot create \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_SERVICE_NAME" \
    --slot staging \
    --configuration-source "$APP_SERVICE_NAME" \
    || die "Failed to create staging slot -- see the Azure error above"
  log "  ✓ staging slot created"
fi

# ============================================================================
# Step 1b: Attach the slot to the VNet
# ============================================================================
#
# --configuration-source clones app settings but NOT VNet integration, so a
# freshly created slot has virtualNetworkSubnetId = null and cannot reach
# PostgreSQL or Key Vault through their private endpoints. It would start,
# fail every dependency, and never answer /healthz -- which the swap warm-up
# would then correctly refuse to accept. Attach it explicitly.
#
# Idempotent: re-adding an already-integrated subnet is a no-op.

SLOT_SUBNET_ID=$(az webapp show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --query "virtualNetworkSubnetId" \
  --output tsv)

if [[ -z "$SLOT_SUBNET_ID" || "$SLOT_SUBNET_ID" == "None" ]]; then
  # Production itself is not VNet-integrated, so the slot does not need to be
  # either -- but say so rather than silently skipping a step this script
  # claims to perform.
  log "  production has no VNet integration; skipping slot VNet attach"
else
  log "  Attaching staging slot to the production subnet..."
  az webapp vnet-integration add \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_SERVICE_NAME" \
    --slot staging \
    --vnet "$(echo "$SLOT_SUBNET_ID" | cut -d'/' -f9)" \
    --subnet "$(echo "$SLOT_SUBNET_ID" | cut -d'/' -f11)" \
    || die "Failed to attach the staging slot to the VNet -- see the Azure error above"
  log "  ✓ staging slot attached to the VNet"
fi

# ============================================================================
# Step 1c: Give the slot its own identity, with production's access
# ============================================================================
#
# Production pulls its image (acrUseManagedIdentityCreds) and resolves its Key
# Vault references (keyVaultReferenceIdentity: SystemAssigned) with its own
# system-assigned identity, and the slot carries the same two settings. But a
# slot does not share the app's identity, and `slot create
# --configuration-source` does not give it one, so the staging slot in use since
# 2026-09-07 ran for seven weeks with none.
#
# Deploys still succeeded, which is what hid it: during a swap App Service
# pulls the image and resolves the references for the incoming build as
# production. The registry's login log shows it -- the 2026-10-19 swap pulled
# with production's identity. What breaks is everything the slot does on its
# own. After each swap it holds the previous build and restarts it, so it
# requested the image anonymously (401, 204 times in 32 hours) until on
# 2026-10-21 it started from the worker's image cache, presented the
# unresolved "@Microsoft.KeyVault(...)" text as its database password, and
# failed to log in every seven seconds for days -- holding three Azure Monitor
# alerts in the Fired state, so a real outage would not have notified anyone.
# For the same reason a rollback (start staging, swap back) could not have
# worked.
#
# So: ensure the slot has a system-assigned identity, then require that it
# holds every role grant production's identity holds. This script cannot fix a
# missing grant -- the CI principal is a Website Contributor and cannot create
# role assignments, by design -- so it stops here, before deploying anything,
# and prints the exact grants for an administrator to make. infra/main.bicep
# makes the same grants, so an environment deployed from it passes on its
# first run.
#
# Grants are read with a raw ARM call, not `az role assignment list
# --assignee`: that resolves the principal through Microsoft Graph, which the
# CI principal cannot read, and it returns an empty list rather than an error
# when the lookup fails -- the silent result this step exists to prevent.

log "Step 1c: Checking the staging slot's managed identity..."

APP_ID=$(az webapp show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --query "id" \
  --output tsv) \
  || die "Could not read the App Service's resource id -- see the Azure error above"
# /subscriptions/<sub>/resourceGroups/<rg> -- the scope the grants are listed at.
RESOURCE_GROUP_ID="${APP_ID%%/providers/*}"

PRODUCTION_PRINCIPAL=$(az webapp identity show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --query "principalId" \
  --output tsv) \
  || die "Could not read production's managed identity -- see the Azure error above"
if [[ -z "$PRODUCTION_PRINCIPAL" || "$PRODUCTION_PRINCIPAL" == "None" ]]; then
  die "Production has no system-assigned managed identity, so there is no access for the staging slot to match. infra/modules/appservice.bicep gives it one; this App Service does not match that template."
fi

STAGING_PRINCIPAL=$(az webapp identity show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --slot staging \
  --query "principalId" \
  --output tsv) \
  || die "Could not read the staging slot's managed identity -- see the Azure error above"
if [[ -z "$STAGING_PRINCIPAL" || "$STAGING_PRINCIPAL" == "None" ]]; then
  log "  staging slot has no managed identity; assigning a system-assigned one..."
  STAGING_PRINCIPAL=$(az webapp identity assign \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_SERVICE_NAME" \
    --slot staging \
    --query "principalId" \
    --output tsv) \
    || die "Failed to give the staging slot a managed identity -- see the Azure error above"
  [[ -z "$STAGING_PRINCIPAL" || "$STAGING_PRINCIPAL" == "None" ]] \
    && die "Azure accepted the identity assignment but returned no principal id for the staging slot"
  log "  ✓ staging slot identity created (principal $STAGING_PRINCIPAL)"
else
  log "  staging slot identity: principal $STAGING_PRINCIPAL"
fi

# Prints "<roleDefinitionId> <scope>" for every role assignment held by the
# given principal at, above, or below this resource group.
role_grants() {
  az rest --method get \
    --url "${RESOURCE_GROUP_ID}/providers/Microsoft.Authorization/roleAssignments?api-version=2022-04-01&\$filter=assignedTo('$1')" \
    --query "value[].join(' ', [properties.roleDefinitionId, properties.scope])" \
    --output tsv
}

# Reduces a grant to "<role guid> <scope>" in lower case. ARM ids are
# case-insensitive and Azure does not always return them in the same case.
grant_key() {
  echo "${1##*/} $2" | tr '[:upper:]' '[:lower:]'
}

PRODUCTION_GRANTS=$(role_grants "$PRODUCTION_PRINCIPAL") \
  || die "Could not list production's role assignments -- see the Azure error above"
STAGING_GRANTS=$(role_grants "$STAGING_PRINCIPAL") \
  || die "Could not list the staging slot's role assignments -- see the Azure error above"

STAGING_KEYS=""
while read -r role_id scope; do
  [[ -z "$role_id" ]] && continue
  STAGING_KEYS+="$(grant_key "$role_id" "$scope")"$'\n'
done <<< "$STAGING_GRANTS"

MATCHED=0
MISSING=""
while read -r role_id scope; do
  [[ -z "$role_id" ]] && continue
  if grep -qxF "$(grant_key "$role_id" "$scope")" <<< "$STAGING_KEYS"; then
    MATCHED=$((MATCHED + 1))
  else
    MISSING+="  az role assignment create --assignee-object-id $STAGING_PRINCIPAL --assignee-principal-type ServicePrincipal --role ${role_id##*/} --scope $scope"$'\n'
  fi
done <<< "$PRODUCTION_GRANTS"

if [[ -n "$MISSING" ]]; then
  log "❌ The staging slot's identity lacks role grants that production's identity holds."
  log "  Without them the slot cannot pull its image or resolve its Key Vault references"
  log "  on its own, so the build it holds after a swap cannot run and a rollback cannot work."
  log "  The CI principal cannot create role assignments. An Owner or User Access"
  log "  Administrator must run, once (redeploying infra/main.bicep makes the same grants):"
  printf '%s' "$MISSING" >&2
  die "Staging slot identity is missing role grants; nothing was deployed and production is untouched"
fi

if [[ $MATCHED -eq 0 ]]; then
  log "  production's identity holds no role grants in this resource group; nothing to match"
else
  log "  ✓ staging slot identity holds all $MATCHED of production's role grants"
fi

NEXT_FQDN=$(az webapp show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --slot staging \
  --query "defaultHostName" \
  --output tsv)

# Logged as a diagnostic only -- this hostname answers 403 from outside Azure by design
# (publicNetworkAccess Disabled, inherited by the slot). Do not try to reach it from CI.
log "  Deploying to: staging (internal FQDN, not publicly reachable: $NEXT_FQDN)"

# ============================================================================
# Step 2: Deploy new image to the staging slot
# ============================================================================
#
# New code always lands on staging first, and only ever reaches production
# via the health-checked swap in Step 4. There is no direct-to-production
# path in the routine deploy flow: that would skip the health check and the
# rollback safety net entirely, which is exactly the stop-replace-restart
# failure mode this script exists to eliminate.

log "Step 2: Deploying new image to staging..."

az webapp config container set \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --slot staging \
  --docker-custom-image-name "$IMAGE_URI" \
  --docker-registry-server-url "https://$(echo "$IMAGE_URI" | cut -d'/' -f1)" \
  || die "Failed to deploy image to staging slot -- see the Azure error above"

# Step 6 leaves the slot stopped, and a stopped slot does not pull or start
# anything when its image changes. Starting it after the image is set means the
# first container it runs is the new build. A no-op if it is already running.
az webapp start \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --slot staging \
  || die "Failed to start the staging slot -- see the Azure error above"

log "  Deployment queued. Container pulling..."

# ============================================================================
# Step 3: Give the container time to pull and start
# ============================================================================
#
# This is a settle window, NOT a health gate. The health gate is the swap
# itself (step 4): App Service pings WEBSITE_SWAP_WARMUP_PING_PATH on the slot
# from inside Azure and refuses to complete the swap unless it answers 200.
#
# An earlier version of this script polled the slot with curl from the runner.
# That could never succeed -- see the HEALTH SIGNAL DESIGN note at the top of
# this file. Do not reintroduce it.
#
# Waiting here at all is a courtesy to the swap: a container that has not begun
# starting will burn warm-up retries. HEALTH_CHECK_TIMEOUT is reused as the
# name for this window so the workflow's existing arguments keep working.

log "Step 3: Allowing ${HEALTH_CHECK_TIMEOUT}s for the container to pull and start..."
log "  (readiness is enforced by App Service swap warm-up in step 4, not from this runner)"

sleep "$HEALTH_CHECK_TIMEOUT"

log "  ✓ Settle window complete"

# ============================================================================
# Step 4: Swap slots (traffic redirect)
# ============================================================================

log "Step 4: Swapping slots (production ↔ staging)..."
log "  App Service will ping the slot's warm-up path from inside Azure and abort if it fails."

if ! az webapp deployment slot swap \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --slot staging; then
  # A failed swap means warm-up never got a 200 from the new build. Azure leaves
  # production untouched, so this fails safe -- the old build is still serving.
  log "❌ Swap FAILED -- the new build did not pass App Service warm-up"
  log "Production was not modified and is still serving the previous build."
  # Stopped, not left running: a build that cannot pass warm-up would otherwise
  # keep running against the production database until the next deploy.
  if stop_staging; then
    log "  The staging slot is stopped, holding the build that failed warm-up."
  else
    warn "Could not stop the staging slot after the failed swap; it is still running the build that failed warm-up"
  fi
  log "  Inspect it before the next run overwrites it: start the slot, then read its"
  log "  container log through ARM (the slot's own log endpoint is not publicly reachable):"
  log "    az webapp start -g $RESOURCE_GROUP -n $APP_SERVICE_NAME --slot staging"
  log "    az rest --method post --url \"\$(az webapp show -g $RESOURCE_GROUP -n $APP_SERVICE_NAME --slot staging --query id -o tsv)/containerlogs?api-version=2023-12-01\""

  die "Swap failed warm-up; production untouched"
fi

log "  ✓ Slot swap complete (traffic now on production; staging holds the previous build)"

# ============================================================================
# Step 5: Connection drain window
# ============================================================================

log "Step 5: Draining old instance (now sitting in staging) for ${DRAIN_TIMEOUT}s..."

sleep "$DRAIN_TIMEOUT"

log "  ✓ Drain window complete"

# ============================================================================
# Step 6: Retain the old slot, stopped
# ============================================================================

# After a successful swap, staging unconditionally holds the pre-swap
# (now old) build -- Azure swap semantics move content between slot
# objects, not hostnames, so this is never in doubt and never needs
# detection.
#
# The slot is kept rather than deleted. Deleting it would mean recreating it on
# the next run, and creation is by far the most fragile step here: with
# --configuration-source, the Azure CLI notices production's Azure Files mount,
# tries to copy its access key, and resolves the storage account's resource
# group by listing storage accounts across the whole subscription
# (_resolve_storage_account_resource_group in the CLI's appservice/custom.py).
# The CI service principal holds no storage role, so that list comes back empty,
# the function returns None implicitly, and list_keys(None, ...) dies with a bare
# "ValueError: No value for given attribute" -- see issue #1178.
#
# Reusing the slot avoids that path entirely. Step 2 deploys the new image over
# whatever the slot holds, so "clean" is already guaranteed without deleting it,
# and what it holds in the meantime is the previous build -- which is a useful
# thing to have when a deploy goes wrong, not garbage to be swept up.
#
# The delete this replaces had never once succeeded: it passed --yes, which
# `az webapp deployment slot delete` does not accept (it does not prompt, so
# there is no confirmation flag), and every run logged the CLI's
# "unrecognized arguments: --yes" and then printed a success line anyway.
# The slot has therefore always survived, which is the only reason the creation
# path above has not been re-entered and the storage-key crash has stayed
# dormant.
#
# Retained, but stopped. Once the drain window has passed, the previous build
# serves no traffic, yet left running it is a second full copy of the app
# against the production database: CMS_NODE_TYPE=standalone, so it runs the
# scheduled jobs too, in their previous version. Stopping it keeps it as a
# rollback artifact without running it. To roll back: start the slot, wait
# for it to answer, then swap it in exactly as step 4 does.
#
# A failure to stop is a warning, not an error: the swap has already put the
# new build in production, and failing the job would misreport that.
log "Step 6: Stopping the staging slot (it now holds the previous build)..."
if stop_staging; then
  log "  ✓ Staging slot stopped and retained for rollback; the next run redeploys over it"
else
  warn "Could not stop the staging slot; it is still running the previous build against the production database. Stop it by hand: az webapp stop -g $RESOURCE_GROUP -n $APP_SERVICE_NAME --slot staging"
fi

# ============================================================================
# Success
# ============================================================================

log "✓ Rolling deploy complete ($(date))"
log "  Active instance: production"
log "  Image: $IMAGE_URI"

exit 0
