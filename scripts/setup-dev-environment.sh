#!/bin/bash
# Set up development environment with safety checks
# Run this after cloning the repository

set -e

echo "🚀 Setting up SimIS CMS development environment..."
echo ""

# Check for git repository
if [[ ! -d .git ]]; then
  echo "❌ Not a git repository. Run this from the repo root."
  exit 1
fi

# Install pre-commit hook
echo "📋 Installing pre-commit hooks..."
if [[ -f scripts/pre-commit-hook ]]; then
  cp scripts/pre-commit-hook .git/hooks/pre-commit
  chmod +x .git/hooks/pre-commit
  echo "   ✅ Pre-commit hook installed"
else
  echo "   ⚠️  scripts/pre-commit-hook not found (this is optional)"
fi
echo ""

# Validate migration validator script exists
echo "🔍 Validating migration validator..."
if [[ ! -f scripts/validate-migration-versions.sh ]]; then
  echo "   ❌ scripts/validate-migration-versions.sh not found"
  exit 1
fi
chmod +x scripts/validate-migration-versions.sh
if ! ./scripts/validate-migration-versions.sh > /dev/null 2>&1; then
  echo "   ⚠️  Migration validation failed (this may indicate an existing issue)"
else
  echo "   ✅ Migration validator working"
fi
echo ""

# Check build prerequisites
echo "📦 Checking build prerequisites..."
if ! command -v ant &> /dev/null; then
  echo "   ❌ Ant not found. Install with: brew install ant"
  exit 1
fi
echo "   ✅ Ant available"

if ! command -v docker &> /dev/null; then
  echo "   ❌ Docker not found. Install Docker Desktop"
  exit 1
fi
echo "   ✅ Docker available"

if ! command -v java &> /dev/null; then
  echo "   ❌ Java not found. Install Java 21"
  exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -1)
echo "   ✅ Java available: $JAVA_VERSION"
echo ""

# Summary
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Development environment ready!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Next steps:"
echo "  1. Build with: ./scripts/build-safe.sh"
echo "  2. Pre-commit hooks are installed and will validate:"
echo "     - Migration version conflicts"
echo "     - [TESTING] markers in code"
echo ""
echo "⚠️  Important:"
echo "  - Always use ./scripts/build-safe.sh to build"
echo "  - Never run 'docker-compose restart' (use full down/up)"
echo "  - Review CLAUDE.md for mandatory build sequence"
echo ""
