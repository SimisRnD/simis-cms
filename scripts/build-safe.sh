#!/bin/bash
# Safe build script with all validation checks
# This is the ONLY recommended way to build SimIS CMS
# Usage: ./scripts/build-safe.sh

set -e

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔍 SimIS CMS Safe Build"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Step 1: Validate migrations
echo "1️⃣  Validating migration versions..."
if ! ./scripts/validate-migration-versions.sh; then
  echo ""
  echo "❌ FAILED: Migration version conflicts detected"
  echo "   Fix duplicate migration versions and try again"
  exit 1
fi
echo "   ✅ Migration versions valid"
echo ""

# Step 2: Validate version consistency
echo "2️⃣  Checking version consistency..."
APP_VERSION=$(grep 'public static final String VERSION = ' src/main/java/com/simisinc/platform/ApplicationInfo.java | sed 's/.*"\([^"]*\)".*/\1/')
POM_VERSION=$(grep '<version>' pom.xml | head -1 | sed 's/.*<version>\([^<]*\)<.*/\1/' | sed 's/-SNAPSHOT//')

if [[ "$APP_VERSION" != "$POM_VERSION" ]]; then
  echo ""
  echo "❌ FAILED: Version mismatch"
  echo "   ApplicationInfo.VERSION = $APP_VERSION"
  echo "   pom.xml version = $POM_VERSION"
  echo ""
  echo "   Fix: Update pom.xml to $APP_VERSION-SNAPSHOT"
  exit 1
fi
echo "   ✅ Versions consistent ($APP_VERSION)"
echo ""

# Step 3: Clean artifacts
echo "3️⃣  Cleaning previous build artifacts..."
rm -rf build target
echo "   ✅ Cleaned"
echo ""

# Step 4: Check for testing code
echo "4️⃣  Checking for testing markers..."
if grep -r '\[TESTING\]' src/main/java --include="*.java" > /dev/null 2>&1; then
  echo ""
  echo "⚠️  WARNING: [TESTING] markers found in Java code"
  echo "   These must be removed before production deployment"
  grep -r '\[TESTING\]' src/main/java --include="*.java" | sed 's/^/   /'
  echo ""
  echo "   This is OK for local testing, but will fail CI gates"
fi
echo "   ✅ Checked"
echo ""

# Step 5: Compile
echo "5️⃣  Compiling..."
ant clean package -q
echo "   ✅ Compiled (WAR created)"
echo ""

# Step 6: Prepare Docker
echo "6️⃣  Preparing Docker..."
echo "   Clearing volumes..."
docker-compose down -v > /dev/null 2>&1 || true
echo "   Building image (no cache)..."
docker-compose build --no-cache app > /dev/null
echo "   ✅ Docker ready"
echo ""

# Step 7: Start stack
echo "7️⃣  Starting stack..."
docker-compose up -d
echo "   Waiting for app to be healthy..."
for i in {1..30}; do
  if curl -s http://localhost > /dev/null 2>&1; then
    echo "   ✅ Stack running"
    break
  fi
  if [[ $i -eq 30 ]]; then
    echo ""
    echo "❌ FAILED: App did not start within 60 seconds"
    docker-compose logs app | tail -20
    exit 1
  fi
  sleep 2
done
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ BUILD SUCCESSFUL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Application running at: http://localhost"
echo ""
echo "Next steps:"
echo "  - Visit http://localhost/login to test the application"
echo "  - Review changes with: git status"
echo "  - Commit when ready: git add -A && git commit -m '...'"
echo ""
