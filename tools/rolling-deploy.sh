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
# KNOWN LIMITATION -- this script cannot currently succeed against the Azure
# pilot, and the reason is architectural rather than a misconfiguration.
#
# Step 3 polls https://<staging-slot>/healthz from the GitHub Actions runner,
# over the public internet. The pilot's App Service runs with
# `publicNetworkAccess: Disabled` (ingress is Front Door's Private Link origin
# only), and a slot inherits that setting. Verified 2026-08-14 against a real
# staging slot: the slot's hostname answers HTTP 403 "Web App - Unavailable"
# from outside Azure. The health check can therefore never pass; the script
# would poll for the full timeout, delete staging, and exit non-zero on every
# run. Production is never touched, so this fails safe -- it simply never
# succeeds.
#
# Two further gaps found in the same test:
#   - The CI service principal has AcrPush but no rights to create or swap
#     slots, so step 1 fails before any of the above is reached.
#   - `--configuration-source` clones app settings but NOT VNet integration
#     (the created slot had virtualNetworkSubnetId = null), so a staging slot
#     cannot reach PostgreSQL or Key Vault through their private endpoints and
#     would not start even if it were reachable.
#
# Making this work needs a health signal that originates inside Azure rather
# than from the runner -- App Service's own warm-up/health-check on swap is the
# most likely answer. Tracked as its own issue; do not "fix" this by exposing
# the staging slot publicly, which would put an unprotected copy of the app
# outside Front Door and the WAF.
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

NEXT_FQDN=$(az webapp show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --slot staging \
  --query "defaultHostName" \
  --output tsv)

log "  Deploying to: staging (FQDN: $NEXT_FQDN)"

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
# Step 3: Wait for health check to pass
# ============================================================================

log "Step 3: Polling /healthz for readiness (timeout: ${HEALTH_CHECK_TIMEOUT}s)..."

HEALTH_CHECK_PASSED=0
ELAPSED=0
INTERVAL=5

while [[ $ELAPSED -lt $HEALTH_CHECK_TIMEOUT ]]; do
  RESPONSE=$(curl -s -w "\n%{http_code}" "https://$NEXT_FQDN/healthz" 2>/dev/null || echo "")
  HTTP_CODE=$(echo "$RESPONSE" | tail -1)
  BODY=$(echo "$RESPONSE" | head -1)

  log_debug "  Attempt $((ELAPSED / INTERVAL + 1)): HTTP $HTTP_CODE"

  if [[ "$HTTP_CODE" == "200" ]] && echo "$BODY" | grep -q '"status":"UP"'; then
    log "  ✓ Health check PASSED (HTTP 200, status=UP)"
    HEALTH_CHECK_PASSED=1
    break
  fi

  sleep "$INTERVAL"
  ELAPSED=$((ELAPSED + INTERVAL))
done

if [[ $HEALTH_CHECK_PASSED -eq 0 ]]; then
  log "❌ Health check FAILED (timeout after ${HEALTH_CHECK_TIMEOUT}s)"
  log "Initiating rollback: deleting staging slot (production was never touched)..."

  az webapp deployment slot delete \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_SERVICE_NAME" \
    --slot staging \
    --yes || log "  Warning: failed to delete staging slot (see the Azure error above)"

  die "Health check failed; rolled back staging deployment"
fi

# ============================================================================
# Step 4: Swap slots (traffic redirect)
# ============================================================================

log "Step 4: Swapping slots (production ↔ staging)..."

az webapp deployment slot swap \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --slot staging \
  || die "Failed to swap slots -- see the Azure error above"

log "  ✓ Slot swap complete (traffic now on production; staging holds the previous build)"

# ============================================================================
# Step 5: Connection drain window
# ============================================================================

log "Step 5: Draining old instance (now sitting in staging) for ${DRAIN_TIMEOUT}s..."

sleep "$DRAIN_TIMEOUT"

log "  ✓ Drain window complete"

# ============================================================================
# Step 6: Clean up old slot
# ============================================================================

# After a successful swap, staging unconditionally holds the pre-swap
# (now old) build -- Azure swap semantics move content between slot
# objects, not hostnames, so this is never in doubt and never needs
# detection. Delete it so the next run starts from a clean, idempotent
# Step 1 (recreate staging, deploy, health-check, swap).
log "Step 6: Deleting staging slot (now holds the pre-swap build; cleanup)..."

az webapp deployment slot delete \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --slot staging \
  --yes \
  || log "  Warning: failed to delete staging slot (see the Azure error above; manual cleanup may be needed -- the next run reuses and overwrites it either way)"

log "  ✓ Old slot deleted"

# ============================================================================
# Success
# ============================================================================

log "✓ Rolling deploy complete ($(date))"
log "  Active instance: production"
log "  Image: $IMAGE_URI"

exit 0
