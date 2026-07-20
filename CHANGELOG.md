# Changelog

All notable changes to SimIS CMS are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow the project's `YYYYMMDD.NNNNN` release scheme; the git tag is the
version prefixed with `v` (for example `v20260719.10000`). Database migrations
apply automatically on startup — always take a database backup before upgrading.

## [20260719.10000] - 2026-07-20

First tagged release since `v20240106.10000` (2024-01-06): a broad security,
authentication, privacy, and platform-modernization uplift (76 merged pull
requests).

### Added
- Multi-factor authentication (TOTP): self-service enrollment, enforced login,
  recovery codes, and brute-force rate limiting (#89–#94, #101, #111, #112).
- Opt-in cookieless analytics — a daily rotating salted visitor hash with no
  persistent identifier (#116).
- Privacy-by-default analytics controls: opt-in IP-address anonymization
  (IPv4 /24, IPv6 /48), Do-Not-Track and Global Privacy Control honoring, and a
  configurable data-retention window (#135, #136, #137).
- On-site search-terms reporting in the analytics dashboard, with the searcher's
  IP anonymized at capture (#138).
- Self-hosted map-tile server option with a secured OpenStreetMap fallback, for
  air-gapped / in-boundary deployments (#118).
- Signed CycloneDX SBOM published with each release, generated from the built
  WAR so it describes exactly what ships (#85, #123).
- SimIS-owned container images published from CI (#88).
- CI safeguards: JaCoCo code-coverage reporting published as a build artifact, a
  coverage gate that fails the build if a hardened security class loses its unit
  tests, and a standalone JSP-precompile syntax gate (#141, #147, #148, #150).
- Governance docs: `SECURITY.md` (#78), `CONTRIBUTING.md` (#108), Code of
  Conduct (#99).

### Changed
- Runtime baseline moved to Java 21 (#87, #106) and PostgreSQL 17 (#103).
- Passwords are hashed with Argon2id; legacy Argon2i hashes still verify and are
  transparently re-hashed to Argon2id on the next successful login (#117, #145).
- Mermaid upgraded 10.6.1 → 10.9.6 (and wiki mermaid fences now render) (#119).
- Deprecated `URL(String)` usages modernized to `URI.create().toURL()` (#122).
- CodeQL scanning now excludes vendored third-party JavaScript to focus on
  first-party code (#139).
- Build hygiene: line-number debug info enabled for better stack traces and
  line-level coverage, and deprecated commons-io and Jackson APIs modernized
  (#142, #143, #146).
- Least-privilege `GITHUB_TOKEN` and serialized image publishing in CI (#95, #105).

### Removed
- Unused declared dependencies removed: `resilience4j-bulkhead` and
  `archunit-junit5` (#140).

### Fixed
- Content deletion now removes the content instead of silently reporting success
  without deleting (#152).

### Security
- Encryption at rest (AES-256-GCM, `CMS_SECRET_KEY`) for MFA/TOTP seeds (#114)
  and stored integration/payment secrets (#131); secret site properties masked
  in the admin editor (#120); previously-stored disabled payment secrets
  re-encrypted on upgrade (#144).
- Transport & browser hardening: HSTS (#84), a Content-Security-Policy baseline
  (#86), session-id rotation on login, and SameSite=Lax cookies (#113).
- Cross-site-scripting remediation across the JSP surface — reflected, stored,
  and attribute-context vectors — plus DOM-based, admin-input, widget,
  activity-message, remote-content, and site-copyright fixes
  (#98, #121, #124–#130, #151, #153, #154, #155).
- Injection & traversal: SQL-injection fix in the product SKU filter (#77),
  upload paths resolved within the file-server root (#96, #102), parameterized
  geo-search (#97), SSL redirect no longer trusts the client Host header (#83).
- Dependency CVEs cleared: vendored jars — thymeleaf, postgresql, jackson,
  commons (#115); unused Pushy/netty stack removed (#104); frontend npm
  vulnerabilities (#73). Embedded Mapbox token removed from source (#82, #91).

### Upgrade notes
- Drop-in WAR replacement; database migrations run automatically on startup.
  Take a database backup first.
- Java 21 and PostgreSQL 17 are the supported baseline.
- Set `CMS_SECRET_KEY` (a base64 256-bit key) to enable encryption at rest for
  MFA seeds and stored integration/payment secrets; if unset, those are stored
  as plaintext (backward compatible). Keep a secure backup of the key. See
  `docs/installation.md`.
- Analytics privacy controls (IP anonymization, DNT/GPC honoring, retention
  window) are opt-in site settings; existing installs keep prior behavior until
  enabled.

## [20240106.10000] - 2024-01-06
Earlier releases predate this changelog; see the GitHub releases page for history.

[20260719.10000]: https://github.com/SimisRnD/simis-cms/releases/tag/v20260719.10000
[20240106.10000]: https://github.com/SimisRnD/simis-cms/releases/tag/v20240106.10000
