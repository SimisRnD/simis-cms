#!/bin/bash
# Test that ApplicationInfo.VERSION matches pom.xml version
# Run as part of CI to prevent version drift

set -e

echo "Testing version consistency..."

# Extract versions
APP_VERSION=$(grep 'VERSION = ' src/main/java/com/simisinc/platform/ApplicationInfo.java | sed 's/.*"\([^"]*\)".*/\1/')
POM_VERSION=$(grep '<version>' pom.xml | head -1 | sed 's/.*<version>\([^<]*\)<.*/\1/' | sed 's/-SNAPSHOT//')

echo "  ApplicationInfo.VERSION: $APP_VERSION"
echo "  pom.xml version: $POM_VERSION-SNAPSHOT"

if [[ "$APP_VERSION" != "$POM_VERSION" ]]; then
  echo ""
  echo "❌ FAIL: Version mismatch"
  echo ""
  echo "These two files must have matching version numbers:"
  echo "  - src/main/java/com/simisinc/platform/ApplicationInfo.java"
  echo "  - pom.xml"
  echo ""
  echo "Fix: Update pom.xml to:"
  echo "  <version>$APP_VERSION-SNAPSHOT</version>"
  exit 1
fi

echo "✅ PASS: Versions consistent"
exit 0
