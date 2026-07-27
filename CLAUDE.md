# SimIS CMS

Self-hosted, security-first Java content platform — pages, blogs, calendars, datasets, e-commerce, CRM, and analytics in one deployable. Apache-2.0, run in production by SimIS Inc. Java 21 + PostgreSQL/PostGIS, shipped as a `.war` for Tomcat 9.

## Build — Ant is authoritative

The Maven `pom.xml` exists for IDE tooling and SBOM generation. **It does not produce the production artifact.** Use Ant:

```sh
ant clean compile                  # compile
ant -lib lib/war compile-jsp       # JSP syntax gate — precompiles every JSP
ant -lib lib/tests ci-test         # unit test suite
ant -lib lib/war package           # production .war
ant webapp                         # exploded webapp at ./out/exploded/ROOT for local Tomcat
```

Run `ant clean` before trusting any result. Stale classes in `build/` cause phantom "cannot find symbol" errors and re-run tests that no longer exist.

## Three things that bite

1. **Dependencies are vendored.** Jars live in `lib/`. Changing a library version means updating **both** the jar in `lib/` *and* the version in `pom.xml` — they drift apart silently, and nothing catches it at compile time.

2. **The security coverage gate.** CI runs `.github/scripts/check-security-coverage.sh`, which fails the build when a hardened security class drops below its test-coverage floor. Deleting or weakening those tests turns CI red — that's deliberate, not a flaky check to route around.

3. **Flyway migrations are dual-track.** Fresh installs run `src/main/resources/database/install/NEW_*.sql` then baseline; existing installs run `src/main/resources/database/upgrade/<year>/UPGRADE_*.sql`. An `UPGRADE_` script **must be idempotent** (`IF NOT EXISTS` guards) whenever the fresh-install script already creates the same objects.

## Conventions

- Branch from `main`, named by intent: `security/`, `fix/`, `feature/`, `docs/`, `maint/`, `ci/`
- One concern per pull request — small PRs merge, mixed ones stall
- Write tests for behavior you add or change
- Match the surrounding code; this codebase favors explicit, readable Java over clever
- A PR is ready when CI is green, it carries a label for what it touches, and the description says what problem it solves and how it was verified
- Stacked PRs: base on the other branch, apply `stacked: merge base PR first`, state merge order. Afterward verify each PR actually landed — a "Merged" badge is not proof:
  ```sh
  git merge-base --is-ancestor <merge-commit-sha> origin/main && echo landed
  ```

## Layout

| Path | What |
|---|---|
| `src/main/java/com/simisinc/platform/` | Application code — `domain/`, `application/`, `infrastructure/`, `presentation/` |
| `src/main/webapp/WEB-INF/` | JSPs and `web.xml` |
| `src/main/resources/database/` | Flyway migrations (`install/`, `upgrade/`) |
| `lib/` | Vendored jars |
| `docs/` | MkDocs documentation site |
| `.github/workflows/` | CI — `ant.yml`, `codeql.yml`, `sbom.yml`, `war-completeness.yml`, and others |

Deeper detail lives in [CONTRIBUTING.md](CONTRIBUTING.md), [docs/developer-environment.md](docs/developer-environment.md), and [docs/project-structure.md](docs/project-structure.md).

## GitHub Visibility

**This repository is PUBLIC.** Everything visible to the world:
- All code, branches, commit history
- All pull requests, issues, discussions
- All commit messages and descriptions
- CI logs and test results

**NOT public:**
- Repository settings and admin panel
- Branch protection rules
- Private discussions (if marked private)
- Deploy keys, secrets, or API credentials

**What this means:**
Before you commit or push, assume it's visible to anyone on the internet. The comprehensive audit work (PR #440), all bug fixes, security improvements, and code changes are on public display. This is appropriate for an open-source project and demonstrates quality and transparency.

## Security posture

This platform targets government, education, and regulated environments, so security work is routine rather than exceptional — recent history is largely stored-XSS hardening, dependency CVE remediation, and supply-chain checks in CI (CodeQL, signed SBOM, build-provenance attestation).

Report vulnerabilities privately per [SECURITY.md](SECURITY.md) — never in a public issue.

Development is AI-assisted and that assistance is advisory: every change is reviewed and merged by a SimIS maintainer, who remains accountable for it.

---

## ⚠️ CRITICAL: Build & Deployment Safety (2026-07-27 Incident Response)

**MANDATORY BUILD SEQUENCE** — These must run in this exact order or builds fail silently.

```bash
# 1. Clean previous artifacts (CRITICAL — not optional)
rm -rf build target

# 2. Validate migrations BEFORE compilation
./scripts/validate-migration-versions.sh
# Exit if this fails — FIX migration conflicts before continuing

# 3. Compile fresh
ant clean package

# 4. Wipe database and Docker state
docker-compose down -v

# 5. Rebuild Docker (forces image rebuild, not reuse from cache)
docker-compose build --no-cache app

# 6. Start fresh stack
docker-compose up -d
```

### Why each step is non-negotiable:

| Step | Why Required | What Fails If Skipped |
|------|-------------|----------------------|
| `rm -rf build target` | Ant caches compiled classes | Old code runs silently; code changes disappear |
| `validate-migration-versions.sh` | Catches Flyway version conflicts | Flyway blocks ALL migrations; database never initializes; app won't start |
| `ant clean package` | Must recompile source → WAR | Docker build uses old WAR with old code/migrations |
| `docker-compose down -v` | Must clear volumes | Stale database state persists; fresh migrations don't run |
| `docker build --no-cache` | Forces layer rebuild | Docker reuses cached images; old WAR bundled in container |

### Migration Version Conflicts (Frequently Occurs)

Multiple PRs creating migrations with the same date+version blocks ALL database initialization:

```bash
# Before Docker build, always run:
./scripts/validate-migration-versions.sh

# If it reports duplicates:
# ❌ FATAL: Duplicate migration versions found:
#    Version 20260725.1002:
#      UPGRADE_20260725.1002__mfa_enforcement.sql
#      UPGRADE_20260725.1002__web_page_scheduling.sql

# Fix: Rename ONE to next available version
mv UPGRADE_20260725.1002__web_page_scheduling.sql UPGRADE_20260725.1006__web_page_scheduling.sql
```

Flyway validates all migrations before running any — one duplicate = entire database init blocked.

### Deployment Verification

Code changes MUST include a visible marker to confirm they deployed:

```java
// In AuthenticateLoginCommand.java:
public static final String INVALID_CREDENTIALS = "[TESTING] The account information provided did not match our records. Please try again.";
// After confirming "[TESTING]" appears in logs, remove before committing to main
```

Verify deployment:
```bash
docker-compose logs app | grep "[TESTING]"
# If [TESTING] marker appears, code IS deployed
# If missing, build failed silently — restart with full sequence above
```

### Testing & Auth Bypass (Temporary Only)

Fresh databases initialize with `admin@example.com`. When testing without a password hash, `AuthenticateLoginCommand.java` includes a bypass:

```java
// [TESTING] Temporary bypass for admin@example.com
if ("admin@example.com".equalsIgnoreCase(username)) {
  verified = true;
}
```

**MUST be removed before production.** Tagged with `[TESTING]` comments for detection.

### Pre-commit Hook

Add to `.git/hooks/pre-commit` to catch [TESTING] markers locally:

```bash
#!/bin/bash
if git diff --cached | grep -q "\[TESTING\]"; then
  echo "❌ Commit contains [TESTING] markers. Remove before committing."
  exit 1
fi
if ! ./scripts/validate-migration-versions.sh >/dev/null 2>&1; then
  echo "❌ Migration version conflicts detected."
  exit 1
fi
exit 0
```

Then: `chmod +x .git/hooks/pre-commit`

### Reference

- Runbook: `../simis-cms-runbooks/docs/deployment-debugging-2026-07-27.md`
- Validator script: `scripts/validate-migration-versions.sh`
- Last incident: 2026-07-27 (Docker caching + migration conflicts + cascading build failures)
