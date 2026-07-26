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
# Step 1: Identify current and next slots
# ============================================================================

log "Step 1: Identifying current slots..."

# Get all slots
SLOTS=$(az webapp deployment slot list \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --query "[].name" \
  --output tsv 2>/dev/null || echo "")

# Slots are: production (implicit), staging (explicit)
# If staging exists and is active, production must be old
STAGING_EXISTS=0
if echo "$SLOTS" | grep -q "staging"; then
  STAGING_EXISTS=1
fi

if [[ $STAGING_EXISTS -eq 1 ]]; then
  ACTIVE_SLOT="staging"
  NEXT_SLOT="production"
  log "  Current active: staging (swap target: production)"
else
  ACTIVE_SLOT="production"
  NEXT_SLOT="staging"
  log "  Current active: production (swap target: staging)"
fi

# Get the FQDN for health checks
if [[ "$NEXT_SLOT" == "staging" ]]; then
  NEXT_FQDN=$(az webapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_SERVICE_NAME" \
    --slot staging \
    --query "defaultHostName" \
    --output tsv)
  OLD_SLOT="production"
else
  NEXT_FQDN=$(az webapp show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_SERVICE_NAME" \
    --query "defaultHostName" \
    --output tsv)
  OLD_SLOT="staging"
fi

log "  Deploying to: $NEXT_SLOT (FQDN: $NEXT_FQDN)"

# ============================================================================
# Step 2: Deploy new image to staging/next slot
# ============================================================================

log "Step 2: Deploying new image to $NEXT_SLOT..."

if [[ "$NEXT_SLOT" == "staging" ]]; then
  az webapp config container set \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_SERVICE_NAME" \
    --slot staging \
    --docker-custom-image-name "$IMAGE_URI" \
    --docker-registry-server-url "https://$(echo "$IMAGE_URI" | cut -d'/' -f1)" \
    2>/dev/null || die "Failed to deploy image to staging slot"
else
  az webapp config container set \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_SERVICE_NAME" \
    --docker-custom-image-name "$IMAGE_URI" \
    --docker-registry-server-url "https://$(echo "$IMAGE_URI" | cut -d'/' -f1)" \
    2>/dev/null || die "Failed to deploy image to production slot"
fi

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
  log "Initiating rollback: deleting $NEXT_SLOT slot..."

  if [[ "$NEXT_SLOT" == "staging" ]]; then
    az webapp deployment slot delete \
      --resource-group "$RESOURCE_GROUP" \
      --name "$APP_SERVICE_NAME" \
      --slot staging \
      --yes 2>/dev/null || log "  Warning: failed to delete staging slot"
  fi

  die "Health check failed; rolled back $NEXT_SLOT deployment"
fi

# ============================================================================
# Step 4: Swap slots (traffic redirect)
# ============================================================================

log "Step 4: Swapping slots ($OLD_SLOT ↔ $NEXT_SLOT)..."

az webapp deployment slot swap \
  --resource-group "$RESOURCE_GROUP" \
  --name "$APP_SERVICE_NAME" \
  --slot "$NEXT_SLOT" \
  2>/dev/null || die "Failed to swap slots"

log "  ✓ Slot swap complete (traffic now on $NEXT_SLOT)"

# ============================================================================
# Step 5: Connection drain window
# ============================================================================

log "Step 5: Draining old instance ($OLD_SLOT) for ${DRAIN_TIMEOUT}s..."

sleep "$DRAIN_TIMEOUT"

log "  ✓ Drain window complete"

# ============================================================================
# Step 6: Clean up old slot
# ============================================================================

if [[ "$OLD_SLOT" == "staging" ]]; then
  log "Step 6: Deleting old $OLD_SLOT slot (cleanup)..."

  az webapp deployment slot delete \
    --resource-group "$RESOURCE_GROUP" \
    --name "$APP_SERVICE_NAME" \
    --slot staging \
    --yes \
    2>/dev/null || log "  Warning: failed to delete $OLD_SLOT slot (manual cleanup may be needed)"

  log "  ✓ Old slot deleted"
else
  log "Step 6: Old slot is production (cannot delete; will be staging for next deploy)"
fi

# ============================================================================
# Success
# ============================================================================

log "✓ Rolling deploy complete ($(date))"
log "  Active instance: $NEXT_SLOT"
log "  Image: $IMAGE_URI"

exit 0
