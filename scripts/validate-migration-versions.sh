#!/bin/bash
# Pre-deployment migration version validator
# Catches Flyway version conflicts before Docker build
# Exit code 1 if conflicts found, 0 if clean

set -e

MIGRATION_DIR="src/main/resources/database/upgrade"
if [ ! -d "$MIGRATION_DIR" ]; then
  echo "❌ Migration directory not found: $MIGRATION_DIR"
  exit 1
fi

# Extract version numbers from migration filenames
VERSIONS=$(find "$MIGRATION_DIR" -name "UPGRADE_*.sql" -type f | \
  sed 's/.*UPGRADE_//' | sed 's/__.*$//' | sort)

# Check for duplicates
DUPLICATES=$(echo "$VERSIONS" | sort | uniq -d)

if [ -n "$DUPLICATES" ]; then
  echo "❌ FATAL: Duplicate migration versions found:"
  for dup in $DUPLICATES; do
    echo "   Version $dup:"
    find "$MIGRATION_DIR" -name "UPGRADE_${dup}__*.sql" | sed 's/^/     /'
  done
  echo ""
  echo "Fix: Rename duplicate versions to unique numbers (e.g., 1002 -> 1005)"
  exit 1
fi

echo "✅ Migration versions valid ($(echo "$VERSIONS" | wc -l) migrations)"
exit 0
