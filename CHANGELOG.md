# Changelog

All notable changes to SimIS CMS are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow the project's `YYYYMMDD.NNNNN` release scheme; the git tag is the
version prefixed with `v` (for example `v20260719.10000`). Database migrations
apply automatically on startup — always take a database backup before upgrading.

## [20260804.10000] - 2026-08-04

Second tagged release since `v20260719.10000` (2026-07-20): compliance/audit-logging
build-out, an Azure cloud deployment path with container hardening, a new in-page
visual/composition-canvas page editor with a governed publish-review workflow
extended across content types, a broad authentication/access-control security
sweep, SEO/AI-visibility, mailing-list and webhook infrastructure, and faceted
content search (492 merged pull requests).

### Added
- Compliance & audit logging (Milestone #3): phased admin/data-change and
  authentication audit logging, an in-app audit review UI, REST/API auth-event
  coverage, and tamper-evidence with a configurable retention window (#167,
  #174, #183–#184, #187); later extended with on-box detection of
  oldest-prefix audit deletion, a queryable REST API for the audit log,
  pre-retention archival, and dedicated audit trails for
  page-layout/composition-canvas mutations, mailing-list entity changes, and
  the classic content-editor's draft saves (#354, #801, #808, #816, #820,
  #836, #889).
- Azure cloud deployment (Milestone #4): a `/healthz` readiness endpoint, the
  Application Insights Java agent for APM, and Bicep infrastructure for the
  application tier (ACR + App Service) and edge tier (Front Door Premium +
  WAF); container runtime hardening — non-root Tomcat, dropped Linux
  capabilities, a read-only root filesystem, the Tomcat SecurityListener, a
  DISA STIG profile, and digest-pinned base images (#178, #185–#186, #188,
  #219–#221, #226, #230–#231, #242, #264, #266, #446); Front Door cache purge
  wired into the publish/update/delete flow, and the Front Door
  session-affinity config and rolling-deploy slot-selection fixed (#755,
  #832, #906).
- Supply-chain & CI hardening: signed CycloneDX SBOM attestation per
  published image, VEX-gated image scanning with CVE triage evidence,
  SHA-pinned GitHub Actions and base images with job-level token permissions,
  a WAR-completeness check, a dependency-drift check against vendored jars
  backed by a committed SHA-256 manifest, a keyword-based security PR
  labeler, secret scanning on every pull request (not just push-to-main), and
  signed image publishing to ACR via a federated credential (#169, #171–#172,
  #175–#176, #180, #191–#192, #207–#208, #225, #229, #241, #243, #248–#249,
  #262–#263, #265, #267–#270, #274–#275, #277–#278, #280, #292, #317, #357,
  #527, #530, #537, #543, #551, #665, #971–#972).
- A new in-page visual/composition-canvas page editor (Visual Editor
  Program, phases P0–P5): format-aware Delta rendering behind a persisted
  content-format stamp; an edit-mode page overlay with inline
  (contenteditable) WYSIWYG editing, real-time preview, and undo/redo;
  structural mutation commands for adding, removing, and reordering
  sections/columns/widgets with canvas overlay controls, column-width and
  section-style pickers, and a widget-type/widget-schema picker; a Quill 2.x
  rich-text inline editor; a media library (schema, browse panel, real file
  upload, click-to-replace) and an inline-editable data table widget;
  authoring-time accessibility checks; and a dedicated
  collection-item-management admin UI (#279, #281–#291, #293, #331–#332,
  #335, #356, #389–#394, #439, #447–#448, #494, #505–#506, #513, #582–#584,
  #663, #696, #698, #734–#737, #745, #747, #757, #766, #781, #785–#786,
  #790–#792, #795, #797, #809, #813, #819, #825).
- Content governance extended platform-wide: the governed
  draft→submit→approve→publish workflow (separation of duties, a
  release-authority input) rolled out from content blocks to web pages and
  blog posts, each behind its own opt-in `*.review.required` site property
  with a submit/approve/reject UI; content-block and web-page version history
  with restore and a word-level diff; collection-level authorization on
  collection-item mutations; a warning before publishing shared content that
  affects other pages; and time-limited draft-preview bearer links so an
  editor can share an unpublished page or post at its real URL without
  publishing it (#374, #838–#839, #896, #898, #910, #950–#951, #959–#963,
  #965–#967).
- Admin dashboards: an actionable security/pending-work/traffic-split
  dashboard, a wiki editor with search and preview, an audit-log viewer, user
  account and bot-vs-real-traffic analytics, a System Health Dashboard, a Job
  Queue Dashboard, a database-maintenance dashboard, a cache-management
  dashboard, a Core Web Vitals trend chart, a zero-result-search-spike alert,
  geographic-anomaly and request-rate-per-IP spike alerts, content-engagement
  and community traffic-vs-engagement reports, and a status-count summary and
  search filter on the web-pages list (#555, #557, #559, #571–#572, #585,
  #591, #617, #630, #761, #765, #774, #793, #846, #851, #855, #858–#860,
  #884, #886, #890–#892, #897, #933, #956).
- SEO & AI-visibility: canonical URL tags, Open Graph/Twitter Card meta tags,
  JSON-LD structured data (Organization, WebPage, BreadcrumbList,
  Article/Person, Product), a configurable `robots.txt` with per-AI-crawler
  controls, a cached/paginated `sitemap.xml` covering blog posts and wikis,
  an SEO & AI Visibility admin overview page, PostgreSQL full-text (tsvector)
  search for web pages, an FAQ widget with `FAQPage` schema, and an
  `/llms.txt` endpoint (#458, #467, #475, #480–#482, #603, #605–#612,
  #614–#616, #623–#625, #627–#630, #649, #935).
- Mailing lists: real newsletter sending via MailChimp's Campaigns API,
  per-list signup checkboxes and a "Notify subscribers" blog-post option,
  deliverability-breakdown and status/search/import-audit member views,
  entity-level audit logging, an automated quarantine job, a "Block IP" row
  action, a blog-to-mailing-list association, a MailChimp connection-test
  button, and a non-functional Delete action fixed at its root cause — a
  POST-action dispatch bug that had been silently breaking 15+ widgets
  platform-wide, not just mailing lists (#578–#579, #650–#651, #655,
  #659–#662, #694, #706, #729, #764, #769, #798, #800, #807, #810, #854,
  #862).
- Admin-manageable IP allow/block lists with CIDR/subnet support, search, and
  per-IP audit history (#640, #645, #647–#648, #652, #656).
- Faceted content search: category and date facets for items, calendars,
  wikis, and web pages, a multi-select category facet, a first-class item Tag
  domain concept with facet-adoption tracking, and generic GROUP BY support
  powering the facet counts (#631, #847, #850, #861, #863–#865, #867–#869,
  #908, #919–#920, #964).
- Outbound webhooks: delivery/retry infrastructure with an admin panel
  (CRUD, event-type selection, test-send, delivery log), and real lifecycle
  events for mailing-list members and other core entities (#835, #840, #905,
  #922–#925).
- Folder/file management: a searchable, sortable folder-access-matrix admin
  UI, an audit trail for folder-file activity, file version history with
  restore, a document-expiration UI, zip download and bulk delete, a
  per-folder upload file-type allowlist, and resized image variants
  (thumbnail/medium/large) generated on upload (#442, #879, #895, #899–#902,
  #907, #917–#918, #927–#930, #932).
- Bulk admin actions: assign roles/reset password/suspend/unsuspend for
  users, and publish/unpublish/archive/delete for calendar events, web
  pages, blog posts, and collection items (#731, #911, #968–#970).
- Permission/capability model: a data-model walking skeleton, a grant/revoke
  UI with an audit trail for role capabilities, temporary/expiring direct
  capability grants, capability-based (not just role-based) page-access
  gates, maker-checker approval for unsuspending elevated-role accounts, and
  an account-status view surfacing MFA/lockout state on `/admin/users`
  (#235, #711, #716, #726–#728, #730, #732, #738–#739).
- CAPTCHA & bot protection: Cloudflare Turnstile as a second provider, and
  reCAPTCHA/rate limiting on the newsletter and contact-form signup paths
  (#490, #589, #888).
- Visitor-privacy controls: a third-party-script consent gate,
  region/country-only geo precision for anonymous visitors, and a daily
  PII-scrub job with an admin trigger/dashboard (#441, #443–#445, #457).
- A design-token layer with an opt-in light/dark color scheme (#206).
- Miscellaneous admin/editor additions: opt-in trusted-proxy client-IP
  resolution, an `itemsList` widget configure-placeholder, solution-page
  tagging with traffic/engagement reporting, search/filters/usage tracking
  on the content list, delete/search/usage detection and pagination on the
  images list, calendar event video/meeting links, a YouTube/Vimeo video
  widget with a consent gate, a widget-picker entry for
  Superset/Metabase/PowerBI (plus a PowerBI embed widget and docs), CSV
  export with an IP-address column for form data, raw IP-address display on
  mailing-list-members and form-data lists, lightweight `features.*` feature
  flags, and recovery of five feature branches that had been merged into
  intermediate branches but never reached `main` (#228, #489, #587, #604,
  #666, #706, #722, #741–#742, #748–#751, #823, #828, #831, #834, #841, #848,
  #885, #909, #912, #921).
- Governance & help documentation: `CONTRIBUTING.md`'s security/auditability
  section, GitHub issue templates with security routing, a README refresh
  with a product screenshot, a documented security-audit cadence, and
  admin-UI help text across Captcha Settings, the navigation-menu editor,
  item-search facets, e-learning settings, Mail Properties, Site Settings,
  the APIs page, and wikis (#158, #164, #168, #170, #173, #177, #334, #449,
  #545, #586, #594, #653, #657, #777, #811, #872–#873).

### Changed
- Runtime baseline moved to Tomcat 11 and the `jakarta.servlet` namespace
  (from Tomcat 9 / `javax.servlet`); the abandoned Granule asset bundler was
  removed as part of the migration, so JS/CSS now load unbundled rather than
  combined (#216, #276).
- Routine dependency maintenance: Flyway 12→13, OkHttp 4→5, the Stripe SDK
  22→33, JobRunr 6→8, JUnit 5→6, GeoIP2 4→5, and 16 further library/CI-action
  bumps (#193–#205, #210–#211, #272–#273, #842–#845, #913).
- CI/build hygiene: a path-based PR labeler, revived integration tests with a
  real database-migration test, pom-version drift guarding, a de-duplicated
  JDK-version fail-fast guard, container-level test-failure surfacing in CI
  logs, and a fixed migration-expand/contract gate that a shallow clone had
  been defeating (#179, #212, #214, #218, #306, #548, #573, #588, #743,
  #756, #767, #778, #780, #782, #803, #871).
- Deprecated API calls modernized (`InetAddressUtils`, `RandomStringUtils`),
  double-submit prevented on admin forms, and dead/unused analytics-settings
  fields cleaned up with added help text (#181, #380, #852).

### Removed
- The abandoned `jquery-formatcurrency` library; dead widgets
  (`CollectionTabsListWidget`, `ContentWidget`'s unreachable view redirect,
  `CustomerFormWidget`, an unfinished `SystemMessagesWidget` stub), the
  unused Web Conferencing (BigBlueButton) settings page, the dead USPS
  address-validation/tracking integration, unused Font Awesome 5
  compatibility CSS, dead Mapbox integration code, and five unused
  `processed`-variant image columns were dropped (#227, #533, #719, #721,
  #723–#724, #849, #927, #938, #943).

### Fixed
- Accessibility remediation: keyboard-operable authoring tools, a skip link
  and landmark structure, ARIA live regions on flash/status messages,
  accessible names on icon controls/logo images and social-media links,
  responsive reflow below 640px, accessible analytics-dashboard charts,
  corrected ARIA roles on nested nav dropdowns, and wired accessibility
  checks into the two real content-save paths (#190, #343–#349, #355, #451,
  #822, #829, #952, #955).
- A wave of fixes closing gaps in features that had shipped only partially
  working: the security-coverage gate, a broken fresh-install (missing
  `users.account_token_expires`), the Mail Test panel's SMTP test, a
  `web_vitals` migration conflict and dead-on-arrival duplicate feature,
  `sitemap.xml`/search silently excluding new pages, a fresh-install Flyway
  baseline drift, a `ClassCastException` on the Security Audit Log page, six
  widgets left broken by an earlier GET→POST action migration, dataset date
  and assignedTo field mappings that were silently dropped instead of
  applied, the PII-scrub job not actually scrubbing, and Chart.js v3
  scale/legend config (#233, #542, #546–#547, #550, #553–#554, #575, #593,
  #595).
- Admin surface reliability: NPEs (`XMLPageLoader`, `/admin/modify-user`),
  missing nav entries and dashboard tiles (Useful Links, siteStats
  locations-list, Apps edit link, EcommerceStats tiles, the
  analytics-retention page, 3 re-enabled siteStats tiles, the compact
  calendar view), a broken calendar-event-form redirect, an invalid table
  alias emptying the Top Modules tile, a Flyway migration-lock race, a
  mislabeled logger, a widget include recursing into a stack overflow when
  its JSP is missing, the admin sidebar losing its scroll position across
  navigations, `RobotsServlet` caching its own 500 response for 24
  hours, a doubled-hyphen XML comment that silently broke every
  `rest-services.xml` REST endpoint, JSON-B's inability to serialize
  `java.sql.Timestamp`, missing favicon/logo/icon fallbacks, the app Client
  ID not shown persistently/correctly labeled, and `systemPropertyMap`/etc.
  being invisible to a widget's own JSP during its first render (#234, #509,
  #667, #700, #705, #707, #712–#715, #718, #720, #768, #770, #779,
  #787–#788, #802, #805–#806, #821, #837, #853, #870, #887, #894, #915,
  #937, #939, #941, #945, #949, #954).

### Security
- Cross-site scripting remediation: file-serving (Download/Stream widgets),
  wiki markdown rendering, unescaped JSP EL reaching HTML/JS output, the
  item-lookup autocomplete, calendar and photo-gallery feeds, the
  icon/leftIcon widget preference, and JSON-LD structured-data output (#189,
  #209, #213, #309, #313, #319, #536).
- Server-side request forgery closed in the dataset remote-file fetch (source
  and paging URLs), `RemoteContentWidget`'s server-side fetch, the shared
  `HttpGetCommand`/`HttpDownloadFileCommand` path, and dataset pagination
  fetches, plus a follow-up closing a DNS-rebinding gap by pinning the
  connection to the address that was actually validated (#236, #307, #381,
  #784, #789).
- CSRF protection added to the login/OAuth state binding, the logout action,
  `PageServlet`'s action dispatch, and `MenuWidget`'s Log Out link (#239,
  #376, #544, #744).
- Access-control and authorization gaps closed: a deny-by-default gate for
  ungated admin-layout pages enforced platform-wide; missing delete-permission
  and IDOR checks on CMS folder deletion and collaboration-member removal;
  privilege escalation via the user-edit form and via a client-supplied id on
  New User creation prevented; `TableWidget` validation and edit-mode gated on
  the real permission check; inverted/missing privileged-role checks in web
  page content search; an unauthenticated `editMode` bypass in
  `ItemsListWidget`; deactivated items still reachable via direct URL/REST;
  stale edit-mode UI leaking across a re-login; Suspend/Restore/Delete/Unlock
  Account silently no-op'ing on `/admin/user-details`; duplicate
  widget-preference tags silently overwriting each other; unescaped
  attribute-context EL findings closed; and `MediaApiController`'s broken
  auth and unregistered/no-op update path (#240, #308, #310, #314, #351,
  #538, #576–#577, #654, #697, #699, #710, #746, #775–#776, #794, #830).
- Authentication & session hardening: cached credentials invalidated on
  password change, OAuth login matched to a local account only by a
  verified email, bearer tokens/Authorization headers no longer logged at
  DEBUG, session id rotated on remember-me auto-login, declining "Stay
  logged in" no longer force-logging the user out on their next request,
  TOTP replay prevented within the validity window, password-reset/account-
  validation tokens expired after 24 hours, the credential cache value
  replaced with an HMAC-SHA256 token, MFA recovery codes rehashed to
  argon2id, username enumeration closed in login/forgot-password, durable
  account lockout (threshold lock, auto-expiry, admin unlock, audit),
  step-up re-authentication for sensitive admin actions (and a follow-up
  closing a bypass in it), and org-level MFA enforcement for privileged
  roles (#237–#238, #311–#312, #318, #326–#330, #352–#353, #539, #581).
- Secrets handling: the generated bootstrap admin password no longer logged
  at INFO, and a leaked token value removed after being flagged by secret
  scanning (#316, #549).
- Transport & request hardening: Host-header redirect validation across
  `PageServlet` and both request filters, a per-request CSP nonce enforcing
  `script-src` across all JSPs (plus a follow-up closing a header-override
  and lost-request-attribute gap that had left it inert), a `Referrer-Policy`
  header on all page responses, configurable caps on paged dataset downloads
  and upload size, a comprehensive file-upload security/UI audit (plus a
  follow-up fixing JSP edits that audit had broken), and raw SMTP exception
  text no longer leaked to the admin UI (#375, #377–#379, #386–#388, #440,
  #540, #602, #946).
- Data-integrity/financial: checkout now re-derives sales tax instead of
  trusting the cart's stale client-supplied amount, and CSV exports are
  guarded against formula injection (#232, #590).
- Opt-in trusted-proxy client-IP resolution (X-Forwarded-For) added for
  deployments behind a load balancer or CDN (#182).
- Encryption at rest for secrets now fails closed instead of silently
  storing plaintext when `CMS_SECRET_KEY` is unset, and the audit-log
  tamper-detection an earlier PR claimed to add was fixed to actually detect
  oldest-prefix deletion (#315, #541).

### Upgrade notes
- Runtime baseline moved to Tomcat 11 and the `jakarta.servlet` namespace
  (from Tomcat 9 / `javax.servlet`); confirm any custom servlet-container
  configuration targets Jakarta EE 10+ (#216).
- `SecretCryptoCommand.encrypt()` now fails closed: if `CMS_SECRET_KEY` is
  unset, the application still boots (with a startup warning), but saving a
  secret value (an MFA/TOTP seed, or an integration/payment credential) now
  throws instead of silently storing it as plaintext, as the previous release
  did. Deployments that already set `CMS_SECRET_KEY` (all Azure deployments
  do) are unaffected; self-hosted deployments that have been running without
  it should set it before upgrading (#315).
- Image-variant generation (thumbnail/medium/large resizing on upload) shells
  out to ImageMagick's `convert`. The published Docker image installs it
  automatically; non-Docker deployments must install ImageMagick on the host
  themselves, or uploads will still succeed while variant generation silently
  fails and pages fall back to serving the original image (#928).

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

[20260804.10000]: https://github.com/SimisRnD/simis-cms/releases/tag/v20260804.10000
[20260719.10000]: https://github.com/SimisRnD/simis-cms/releases/tag/v20260719.10000
[20240106.10000]: https://github.com/SimisRnD/simis-cms/releases/tag/v20240106.10000
