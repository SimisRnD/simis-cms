# Changelog

All notable changes to SimIS CMS are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow the project's `YYYYMMDD.NNNNN` release scheme; the git tag is the
version prefixed with `v` (for example `v20260719.10000`). Database migrations
apply automatically on startup — always take a database backup before upgrading.

## [20260807.10002] - 2026-08-07

Third tagged release since `v20260719.10000` (2026-07-20): the governed
draft/submit/approve/publish review workflow now extends to blog posts with
full version history, alongside a new admin-managed form builder and a
database-backed URL redirect manager. A parallel admin-UX overhaul pairs a
redesigned admin shell (Black Pearl palette, Inter typography) with in-page
guidance rolled out across nearly every admin dashboard, plus a new
editorial calendar and reorganized site/content analytics, Web Vitals, and
search-analytics reporting. Mailing lists gained double opt-in confirmation
and quarantine-reactivation hardening, and images moved to responsive
srcset delivery. A full-codebase security/quality audit sweep fixed
IDOR/mass-assignment, media-library ACL, secret-logging, and
broken-access-control findings, alongside several other access-control and
information-disclosure fixes found in parallel work this release (149
merged pull requests).

### Added
- REST API: read-only endpoints for retrieving web pages, blog posts, and
  folder files, each enforcing the same visibility/access rules as the live
  site, plus an authenticated endpoint for writing content blocks (#929,
  #978).
- Governed publish-review workflow: extended the existing
  draft-submit-approve-publish flow to blog posts; added bulk
  publish/unpublish/archive/delete actions to the admin web pages and blog
  posts lists, with archiving now actually taking a page or post offline
  instead of only hiding it from the admin view; added the same bulk actions
  for collection items; and added a "Get Preview Link" option in the visual
  editor that generates a time-limited, no-index link to an unpublished
  draft at its real page URL (#951, #961, #968-970).
- Content versioning: content blocks now keep a version history with a
  word-level diff between any two versions and the ability to restore an
  earlier one, matching the existing web page version history (#963).
- Editorial calendar: added an admin view aggregating scheduled, draft, and
  published web pages, blog posts, and calendar events by date, plus a
  "drafts with no dates" section surfacing content with no publish or
  expiration date set at all (#987, #999).
- Forms: added an admin-managed form builder at /admin/forms for
  configuring fields, CAPTCHA/spam settings, and notification emails from
  the UI instead of hand-editing widget XML; form submissions can now be
  auto-purged after a configurable retention period (default 90 days) once
  resolved; the Form Data page gained a spam-flagged filter; and a new
  admin documentation guide covers the builder's validation, captcha, spam
  heuristics, and rate limiting (#988, #1024-1025, #1027).
- Mailing lists: public self-service signups (footer form, its AJAX
  variant, and the checkout newsletter checkbox) now require confirming via
  an emailed link before activating, with an expiring token and a "Pending
  Confirmation" admin filter; admin-driven paths like CSV import continue
  to activate immediately since consent is already established there
  (#1026).
- Image handling: added lazy-loading, async decoding, and known
  width/height attributes across roughly 35 widget templates, and a
  focal-point picker so the server-generated square crop can center on an
  image's actual subject instead of dead center (#975, #991).
- Media library: added Date/Name/Size sorting and an Orphaned/Used filter
  to the Images admin page, plus a tagging system with a per-image
  assignment modal, a search filter, and an admin tag-management panel
  (#1055-1056).
- URL redirects: added a database-backed redirect manager with an admin UI
  for creating, editing, and disabling redirects without server file
  access, replacing the CSV-only mechanism (#981).
- Site analytics & stats: added a 30-day page view count column to the
  admin web pages list, a geographic-anomaly tile flagging countries that
  newly appear in top-5 traffic sources, and moved Facet Usage Breakdown to
  a new Search Analytics page alongside new search-volume,
  zero-result-rate, top-page, and near-miss reports broken down by content
  type (#930, #956, #1058).
- Cache management: added an admin dashboard for inspecting and clearing
  the application's in-memory caches, with hit/miss/eviction stats and an
  audit trail of clear actions (#933).
- SEO & AI visibility: added a /llms.txt endpoint publishing an
  admin-editable, access-filtered summary of the site's content for LLM and
  agentic-browsing clients, and grouped Sitemap, LLM/AI Visibility, and
  Robots/Crawlers settings into a new SEO & AI admin navigation section
  (#935, #1035).
- Theming: added automatic light/dark logo swapping, and a Footer Layout
  site property exposing the previously-unreachable 4-column footer
  template (#1004, #1065).
- Security & access control: added an admin "Reset MFA" action for
  clearing a locked-out user's second factor, extended the capability-grant
  permission system to the Users and Groups admin pages via a new
  users:manage capability, added a warning banner and on-demand trigger for
  the nightly audit-log tamper-evidence integrity check, and added a
  warning log when an App's private key falls back to unencrypted storage
  (#1036-1037, #1077, #1100).
- User & group management: added a Download CSV button to the admin users
  list, built from an explicit safe column list that excludes passwords,
  MFA secrets, and tokens (#1033).
- Audit & activity log: added a unified /admin/activity feed covering all
  six audit categories with a default 7-day window, category filters, and
  plain-language event descriptions, replacing an old page that only
  merged half the categories with no time bound (#1064).
- Database & job infrastructure: added a CI check that scans Flyway
  migration filenames for duplicate version numbers across install and
  upgrade sets, catching collisions between branches before they merge
  (#1090).
- In-app admin documentation: added in-page explanatory guidance to the
  System Health, Database Maintenance, Send Newsletter, User Groups,
  Users, Form Builder, Navigation Menu Editor/Edit Links, Web Pages, and
  Web Redirects admin pages (#1008, #1010, #1039, #1041-1042, #1045,
  #1051-1053).

### Changed
- Removed the USPS address-validation and package-tracking integration,
  since USPS shut down the underlying API and the feature had no reachable
  admin setting or active callers (#938).
- Removed the unused legacy Font Awesome 5 compatibility stylesheet, which
  duplicated icon fonts already loaded under Font Awesome 6 (#943).
- Redesigned the admin shell's visual style with a new
  Black Pearl/Anthracite/Frost color palette and Inter typography for the
  sidebar and topbar (previously falling back to a system font), added
  missing medium-weight font files, and grouped the admin dashboard's stat
  tiles into labeled sections (#1003).
- Reorganized the Site Analytics and Content Analytics admin pages into
  labeled sections (Traffic & Sessions, Users & Accounts, Mailing Lists,
  Forms, Content Engagement, etc.) with inline explanations of each tile's
  counting logic, and split the search-term tiles out to a new dedicated
  Search Analytics page since they're driven by a different data source
  (#1015, #1048).
- Removed the unreachable Apple Maps code path from MapWidget, since the
  map-tiles service resolver can never actually return a value other than
  openstreetmap or custom (#1084).
- Replaced three inconsistent, weak password-length checks (6-8 characters,
  no complexity) across registration, account activation, and checkout with
  a single configurable policy defaulting to a 15-character minimum with
  required character-class complexity (#1087).
- Reformatted the site properties editor's JSP so each per-property
  conditional sits on its own line instead of being packed onto a few very
  long lines, reducing the chance that unrelated branches editing different
  properties silently overwrite each other's changes on merge (#1091).
- Updated the mermaid npm dependency from 10.9.6 to 10.9.8 (#1092).

### Fixed
- Fixed several governed-publish-workflow gaps where a page or blog post's
  draft/review/approval status could be lost or bypassed: publishing a
  draft left its prior approval status in place instead of clearing it
  (#950), discarding a draft didn't clear its submission/approval status
  (#960), restoring an earlier version let the replacement inherit a stale
  approval (#966), and the legacy raw-XML and designer-canvas editors could
  write straight to the live page and skip review altogether (#959).
- Fixed the homepage's draft preview link being overridden by the
  pre-launch "setup mode" placeholder (#965), restored the Web Page Review
  widget's page and widget-library registrations after a merge had
  silently dropped them and made the review/approve step unreachable
  (#1096), and made sure content published outside the governed review
  process still gets a version-history snapshot before being overwritten
  (#967).
- Fixed WebP image uploads being rejected and WebP variant thumbnails
  failing to record dimensions (#932), replaced the third-party
  placehold.it hotlink used as the product-listing no-image fallback with a
  locally bundled placeholder (#977), and wired the previously-unused
  image-variant pipeline into page templates and rich-text content so
  images are served as appropriately sized, lazy-loaded srcset variants
  instead of always the full-resolution original (#986).
- Fixed robots.txt error responses being cached for 24 hours so a
  temporary failure no longer masked a working response for a full day
  (#937), restored Article/Author JSON-LD structured data that had
  silently disappeared from blog posts in an earlier merge, and fixed the
  SEO Sitemap status banner and default robots.txt so they stop
  advertising a sitemap that isn't actually being served (#1078).
- Fixed a malformed XML comment that silently broke startup parsing of the
  REST services configuration and took every /api endpoint offline with no
  error identifying the cause (#939), fixed REST endpoints returning HTTP
  500 instead of JSON whenever a response included a timestamp field
  (#941), and fixed the REST API request filter to reject non-GET/HEAD
  requests lacking a valid Bearer token instead of silently treating them
  as an unauthenticated guest (#984). Also fixed the Apps admin page
  silently failing to delete any App that had ever authenticated a
  request, closed a mass-assignment gap in the App form, added an audit
  trail for App changes, and split REST API rate limiting into its own
  bucket separate from web login/form throttling (#1076).
- Fixed the site favicon and several other logo/icon references (touch
  icon, header/footer logos, chat avatar) 404ing on installs without an
  uploaded custom asset, by falling back to a bundled default until a
  custom one is uploaded (#945, #949).
- Added an aria-label with the platform name to footer social media icon
  links so screen readers no longer announce an unlabeled icon-only link
  (#952), and corrected the ARIA role Foundation's menu JavaScript assigns
  to nested dropdown/drilldown submenus, clearing a critical accessibility
  violation on every Foundation dropdown menu site-wide (#955).
- Fixed system/site/theme/social property maps not being published to the
  request before widgets render, so a widget's JSP can now read values
  like the site's asset-path prefix correctly (#954); fixed saving a web
  page recording the form's modifiedBy value instead of overwriting it
  with the page's createdBy value (#962); fixed a site-wide jQuery console
  error on bare href="#" admin action icons (#990); fixed a crash on the
  login, registration, and forgot-password pages when a site hadn't yet
  been switched online (#1034); and corrected a repeated letter-ordering
  typo (uvwyxz instead of uvwxyz) in 23 character-allowlist constants
  (#1059).
- Fixed a bug where loading and saving an item — including archiving one
  from the admin page — silently stripped all of its tags, because the tag
  list was never populated when read from the database (#964).
- Pinned the trufflehog and attest-build-provenance GitHub Actions to
  specific commit SHAs instead of floating references, closing
  supply-chain risk in jobs that hold package-publish and signing
  permissions (#971, #972); fixed the failing publish-images.yml Trivy
  gate by adding a missing OpenVEX statement for CVE-2026-8458, unblocking
  :latest image publishing (#1067); and fixed the TruffleHog secret scan
  to check out full git history instead of a shallow clone so it can
  actually detect secrets that were committed and later removed (#1101).
- Fixed the public add-an-item form so its Browse Images and Upload Image
  File buttons are hidden for users who lack the required permissions
  (#980); fixed the orphaned/used image-usage scan to also check wiki page
  bodies, so wiki-referenced images no longer show as falsely orphaned
  (#1054); and closed a Media Library access-control gap where a user
  could register a media asset pointing at an arbitrary file path and read
  it back, by restricting uploads to the reserved media-library prefix and
  rejecting path traversal (#1094).
- Fixed calendar event JSON feeds and LocalDateCommand to use the site's
  configured timezone instead of the server's default, so events near
  local midnight no longer show on the wrong day (#982, #985); fixed the
  editorial calendar's keyboard-navigable date grid so clicking a day cell
  actually moves keyboard focus to it (#997); fixed the Editorial Calendar
  page mislabeling a page or post still awaiting review as "Scheduled,"
  dropping multi-day events that started before the visible range, and
  linking community-managers to pages they can't edit (#1063); fixed
  offline calendars whose events still appeared in the calendar grid,
  search results, and upcoming-events widget even though only the event's
  own details page respected the calendar's Online setting (#1060); and
  fixed a calendar-event data-loss bug where editing an event through the
  single-event form silently blanked its location, links, tags, and
  published status, plus two timezone bugs, an N+1 query, and missing
  permission checks (#1062).
- Fixed checkbox and checkbox-group fields in the public form widget,
  which had rendered as plain text inputs and kept only one selected value
  when several boxes were checked (#983); fixed the form builder's
  client-side validation for required dropdown and checkbox-group fields
  (#1000) and its "date" field type, which had silently fallen back to a
  plain text input (#1001); fixed the "Rejected Submissions by Reason"
  tile undercounting rejections by capturing three previously-silent
  early-return cases and database save failures (#1020); fixed the Form
  Data admin page's GeoIP Location column echoing the raw IP instead of
  the standard "--" placeholder on lookup failure (#1022); fixed CSV
  downloads from the Form Data admin page always exporting the whole table
  instead of respecting the active filters (#1023); fixed the Enabled and
  Check for Spam checkboxes on a form's settings always saving as on
  regardless of what was checked (#1044); and fixed two fields on the same
  form silently colliding on the same internal name, which discarded one
  field's submitted answers (#1046).
- Fixed editing an existing calendar event, web page, or blog post
  overwriting the original author's createdBy value with the editing
  user's id in the in-memory save result (the underlying database column
  was never actually affected) (#989).
- Fixed the login, register, and forgot-password pages so they show the
  site's header branding even while the site is still offline, without
  exposing the real navigation menu or footer links to an unauthenticated
  visitor before launch (#1007).
- Fixed the background job queue to actually use shared database-backed
  storage instead of an unshared in-memory store that silently duplicated
  recurring jobs across replicas and lost job history on every restart,
  disabled the unauthenticated built-in JobRunr dashboard, and added
  in-page guidance to the admin job queue dashboard (#1009).
- Fixed six admin/content caches that had no time-based expiry, which let
  a stale value persist indefinitely on a non-writing instance in
  multi-instance (Azure) deployments; they now expire within roughly one
  to five minutes, matching the existing redirect-cache pattern (#1011).
- Fixed the Community Analytics page crashing to a blank response whenever
  a report's value was pre-formatted text instead of a raw number (#1012);
  fixed the "Expiring Soon" dashboard tile counting any future expiration
  date instead of only the next 30 days (#1013); fixed DAU/MAU counts
  undercounting returning users by recording a login only once per session
  instead of once per calendar day, across the password, remember-me, and
  OAuth sign-in paths (#1017); fixed the Web Vitals summary showing up to
  six days of stale data as current and added a sample-count display
  (#1019); fixed the INP Core Web Vital never recording a value because
  the collector read a browser property that doesn't exist, and
  reimplemented it against interaction events (#1021); fixed a CSV
  bulk-import bug in the Bot User Agent list where whitespace in quoted
  cells could silently fail to match a record, most seriously causing
  removal rows to no-op, and fixed a misleading import summary (#1071);
  and fixed video embeds being permanently hidden behind a consent cookie
  that could never be set on a default install, aligned client-side Do Not
  Track/GPC handling with the server-side setting, and fixed the Social
  Media Settings page to reject duplicate platforms, support editing
  entries, and expose the link-ordering field (#1081).
- Fixed a quarantined email address (spamtrap, invalid, or abuse) being
  silently reinstated as active the moment it resubscribed through any
  signup, checkout, or CSV-import path; reactivation now requires
  deliberate admin review, recorded as an audit event (#1018).
- Fixed the CSV user-import summary silently counting rows as successful
  even when the database save failed, and added a matching audit failure
  record (#1029); fixed users created through CSV import not receiving the
  invite/welcome email that manually-added users already got (#1030);
  reduced the admin users list from one database round trip per user to
  two batched queries, fixing a performance bottleneck that scaled with
  page size (#1031); and fixed editing a user's email to one already in
  use returning a generic system error instead of a clear duplicate-email
  message (#1040).
- Fixed the Home navigation tab's delete protection, which had been dead
  code that couldn't actually stop a direct request from deleting the Home
  tab (#1049), and fixed the Navigation Menu editors attempting a failing,
  error-logging database update on save for every tab whose name/icon
  fields aren't rendered on the page (#1050).
- Fixed inefficient N+1 and O(posts×blogs) lookups on the Blogs and Blog
  Posts admin list pages, added pagination to the Blogs admin list, and
  added in-page guidance on how Blog categories and their two-step setup
  work (#1057).
- Fixed the Content admin page's shared/template detection incorrectly
  flagging single-page fragments as site-wide, a delete icon shown to a
  role that can't access the page, and a missing duplicate-unique-ID check
  (#1061).
- Fixed multiple Files & Folders admin bugs, including failed uploads
  reported as successful, file expiration dates that didn't actually block
  access, delete-permission gaps on several paths, and a non-functional
  bulk-delete button (#1066).
- Fixed a wiki-rename bug that silently orphaned an entire wiki's public
  pages, a New Page title-collision bug that could overwrite an existing
  page, and a search-results widget ignoring its empty-state setting, and
  added per-page delete (#1068).
- Fixed the BI Settings "Enable bi?" label incorrectly implying a global
  toggle when it only controls Superset, and corrected a stale comment
  claiming the site-property cache never expires (#1082); fixed several
  Map Settings inconsistencies including a page-title mismatch, a URL
  field's capitalization, a misspelled class name, and leftover dead
  Google Maps scaffolding (#1083); fixed a page-title/widget-title
  mismatch on the Email Settings page (#1085); and fixed the "Enable
  e-learning?" master switch having no actual effect on the
  Moodle/LRS/PERLS integrations, and removed a dead LRS auth-header
  property that was never read anywhere (#1086).
- Fixed several gaps left by the Black Pearl admin redesign so a fresh
  install actually shows the intended look: white dashboard tiles instead
  of yellow, the Inter font loading by default, a black utility bar
  instead of blue, and dark anthracite buttons instead of old Foundation
  blue (#1088).
- Restored three aria-describedby links on security settings in the site
  properties editor that were silently dropped when a prior merge
  collided on a long single-line JSP condition, leaving the related help
  text no longer associated with its input for screen readers (#1089).
- Fixed the Capability Grants link on the User Details page being visible
  to users not authorized to open it (#1028); fixed edit-user role
  checkboxes and the bulk role-assignment dropdown not matching the
  server's actual role-level enforcement (#1032); and fixed the admin
  users list, editorial-calendar author dropdown, and user autocomplete
  needlessly decrypting each user's stored MFA secret just to build a row
  that never displays it (#1038).
- Fixed bot/crawler detection never actually working in any Docker or
  Azure deployment because its CSV data file was never included in the
  container image; detection now reads from a database-backed table with
  an admin management page, seeded with 27 real crawler signatures
  (#1016).

### Security
- Fixed the Content-Security-Policy `script-src` restriction being
  silently overwritten and its script nonces never reaching the page,
  meaning the policy had provided no real protection since it was
  introduced (#946).
- Fixed the reserved-path denylist used when saving redirects so case
  variants of protected paths (like `/ADMIN` or `/Login`) are correctly
  rejected instead of slipping through a lowercase-only comparison (#998),
  and fixed a critical IP-block bypass where a blocked visitor could still
  load full page content whose URL slug merely started with a
  static-asset prefix (#1074).
- Closed a gap where the edit-user form, CSV user-import, and OAuth
  group-claim mapping could each grant a real, logged-in user membership
  in the "All Guests" group — an exclusion the New User form already
  enforced — while preserving any such membership a user already held
  (#1043, #1047).
- Fixed a stored XSS vulnerability in the table-of-contents link renderer
  shared by Useful Links and Sticky Page Buttons (#1069), and closed a
  CSS-injection hole in the Collection and Category theme editors' color
  fields (#1070).
- Fixed the admin:manage self-lockout guard, which had counted roles
  rather than actual enabled/validated users holding the capability and
  never checked direct-grant revocations, so a zero-member role or a lone
  direct grant could lock out every admin (#1072).
- Made the site-offline setting actually redirect visitors away from every
  non-home page instead of only swapping the homepage's content, and made
  the login-disabled setting actually block the login form server-side
  instead of only hiding the nav link to it (#1079).
- Fixed `/sitemap.xml` publicly listing privacy-restricted collection
  items and role/group-restricted web pages that an anonymous visitor
  could never actually view, and fixed archived web pages continuing to
  be served through the REST API and listed in `llms.txt` even though the
  live site already blocks access to them once archived (#1080, #1099).
- Fixed an IDOR vulnerability where a user could submit another item's or
  file's hidden-field ID during a form save and have the update applied to
  that record instead, letting them hijack a collection item or file they
  had no permission on (#1093).
- Restored the admin-only role restriction on the `/admin/apps` and
  `/admin/app` pages after a later merge had silently reverted it back to
  also allowing the data-manager role, closing a privilege gap on pages
  that expose OAuth2 client credentials (#1095).
- Stopped DEBUG-level logging from writing full OAuth access tokens and
  API keys embedded in request URLs (including Moodle's `wstoken` and
  other query-string credentials) into application logs (#1097).
- Added missing internal authorization checks to the mailing list member
  removal action and the item member add form, which previously relied
  only on the surrounding page/widget configuration and could be invoked
  directly by any logged-in user (#1098).
- Fixed the release-triggered container-image secret scan silently falling
  back to an unbounded full-history scan instead of an incremental one
  (the pinned TruffleHog action has no event-detection branch for
  `release`, only for push/workflow_dispatch/schedule/pull_request), and
  excluded two confirmed false-positive matches it surfaced -- a checksum
  manifest entry and a pytest function name neither of which is a real
  credential (#1106, #1107). A follow-up fix corrected the previous-tag
  lookup glob so it also matches same-day decimal-increment release tags
  like `v20260807.10001`, which had been falling through and re-triggering
  the same unbounded-scan fallback (#1109). This is a same-day
  decimal-increment release (10002) solely to get a clean image publish
  through the fully-fixed workflow; no application code changed from
  20260807.10000.

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
