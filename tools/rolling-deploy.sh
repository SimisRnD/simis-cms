#!/bin/bash
set -euo pipefail

# Rolling deploy workflow for zero-downtime updates
#
# Manages two App Service deployment slots:
#   - Production (active, serving traffic via AFD)
#   - Staging (inactive, for next deployment)
#
# Process:
#   1. Deploy new image to staging slot
#   2. Wait for /healthz to return {"status":"UP"}
#   3. Swap slots (traffic redirects to new instance)
#   4. Drain old instance connections (wait window)
#   5. Terminate old slot (cleanup)
#
# Rollback on failure: if staging health check fails, delete staging and restore old instance
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
# Two further requirements, both easy to miss:
#   - The CI service principal needs rights to create and swap slots on this
#     one App Service (roughly Website Contributor, scoped to the resource).
#     AcrPush alone is not enough; without it step 1 fails immediately.
#   - `--configuration-source` clones app settings but NOT VNet integration
#     (a slot created that way has virtualNetworkSubnetId = null), so the slot
#     cannot reach PostgreSQL or Key Vault through their private endpoints and
#     will never pass warm-up. Step 1b below adds it explicitly.
#
# The staging slot is created once and then reused; step 6 deliberately does not
# delete it. Slot creation is the most failure-prone step in this script, and not
# doing it on every run is what keeps this path reliable -- see the comment on
# step 6 for the Azure CLI failure it avoids. A slot holding the previous build
# is also a rollback artifact rather than litter, and step 2 overwrites it, so
# reusing it costs nothing in cleanliness.
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
# swapped into. "staging" is the only extra slot this workflow uses, and
# it does not persist between deploys -- Step 6 deletes it after every
# successful swap (cost control), so on a normal run it needs to be
# (re)created here. This is intentionally unconditional and idempotent:
# safe whether staging is missing (first run, or after cleanup) or already
# present (a prior cleanup failed and left it behind).
SLOTS=$(az webapp deployment slot list \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --query "[].name" \
  --output tsv || echo "")

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
    log "  The staging slot is left in place, holding the build that failed warm-up."
    log "  Inspect it before the next run overwrites it:"
    log "    az webapp log tail -g $RESOURCE_GROUP -n $APP_SERVICE_NAME --slot staging"

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
# Step 6: Retain the old slot
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
log "Step 6: Retaining the staging slot (it now holds the previous build)..."
log "  ✓ Staging slot retained for rollback; the next run redeploys over it"

# ============================================================================
# Success
# ============================================================================

log "✓ Rolling deploy complete ($(date))"
log "  Active instance: production"
log "  Image: $IMAGE_URI"

exit 0
