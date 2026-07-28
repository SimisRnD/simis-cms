# Privacy & Analytics Completion Milestone

**Status:** In Progress  
**Last Updated:** July 26, 2026, 14:25 UTC  
**Owner:** Elizabeth (Product), Engineering Team

---

> **Correction (2026-07-28):** This document's "✅ Complete" status for #365 was inaccurate when written -- `SessionsPiiScrubJob` (added by #444) has been silently scrubbing **zero rows** since it shipped. `SessionRepository.scrubOldPii()`'s `UPDATE` always hit a `NOT NULL` constraint on `sessions.ip_address` that was never relaxed when the job was added; the constraint violation aborted the update, the code's own try/catch swallowed the `SQLException` into a log line, and the job "succeeded" while doing nothing. Found during a security-PR audit; fixed by [PR #542](https://github.com/SimisRnD/simis-cms/pull/542), **open, not yet merged** as of this correction. #367's underlying code also had a real (smaller) bug -- the `is_anonymous` flag wasn't being set when GeoIP data was unavailable, a compliance gap for exactly the anonymous-visitor case this issue exists to protect -- fixed and merged via [PR #538](https://github.com/SimisRnD/simis-cms/pull/538). See the corrected notes in each section below; the original claims are left in place with strikethrough/annotations rather than silently rewritten.

## Executive Summary

Four privacy and analytics compliance initiatives to ensure visitor data is handled responsibly and transparently. All issues are architecturally sound and implementation-ready.

| Issue | Title | Status | Details |
|-------|-------|--------|---------|
| **#365** | Scheduled Retention/Purge Job | ⚠️ Was non-functional; fix open (PR #542) | Job ran daily but scrubbed 0 rows due to a schema/code mismatch; see correction above |
| **#366** | Analytics Consent Gate | ✅ Complete | Cookie-based consent banner; 365-day persistence; page reload on accept |
| **#367** | Geo Precision Reduction | ✅ Complete (bug fixed by PR #538, merged) | Anonymous visitors: region/country only; authenticated: full precision; is_anonymous column + tests (currently `@Disabled` in CI -- see correction below) |
| **#368** | Privacy Dashboard | ⏳ Ready | Admin panel showing row counts, retention period, purge estimates, "Purge now" button |

---

## Issue #365: Scheduled Retention/Purge Job

**Implementation Status (as originally written, INACCURATE):** ~~✅ Complete (commit `4af648ef`)~~

**Actual status as of 2026-07-28:** ⚠️ The job existed and ran on schedule, but did nothing. `sessions.ip_address` has carried a `NOT NULL` constraint since the original 2022 schema, which was never relaxed when this job was added -- every run threw a constraint violation on the first matching row, Postgres aborted the whole `UPDATE`, and the job's own try/catch swallowed the `SQLException` into a log line and returned `0`. No exception escaped, nothing alerted, and the job "succeeded" while scrubbing nothing, indefinitely. Fixed by [PR #542](https://github.com/SimisRnD/simis-cms/pull/542) (makes `sessions.ip_address` nullable, verified against a real Testcontainers Postgres) -- **open, not yet merged**. This document's "tested in staging" claim below was never true; there is no record of this job's actual output ever having been checked before now.

### What It Does (once PR #542 merges)
- Runs daily at 4:45 AM via JobRunr distributed scheduler
- Nullifies PII older than `analytics.retentionDays` (default 365 days)
- Soft-delete pattern: keeps non-sensitive geo (continent, country, state) for analytics
- Idempotent: skips already-scrubbed rows via `WHERE ip_address IS NOT NULL`

### Architecture
- **Scheduler:** JobRunr (SQL-based, distributed lock coordination)
- **Configuration:** Site property `analytics.retentionDays` (1–3650 days, admin-editable)
- **Performance:** Single UPDATE statement; 10–30 seconds for 10M rows
- **Compliance:** GDPR data minimization; audit-friendly soft-delete

### Key Files
- `SessionsPiiScrubJob.java` — Scheduler job
- `SessionRepository.scrubOldPii()` — Database query
- Database: `sessions` table with `ip_address IS NOT NULL` filter (nullable only once PR #542 merges)

---

## Issue #366: Analytics Consent Gate

**Implementation Status:** ✅ Complete (commit `461f1a93`)

### What It Does
- Shows consent banner when `analytics.consentRequired=true`
- User can Accept (loads scripts, page reload) or Decline (silent, no reload)
- Cookie-based: `analytics-consent` with 365-day expiration
- Works orthogonally with DNT/GPC suppression (PR #344)

### Architecture
- **Storage:** Cookie-based (`analytics-consent` = 'accepted' | 'declined')
- **Banner:** Fixed-bottom UI, shown only when no prior decision
- **Accept flow:** Set cookie → page reload → scripts load
- **Decline flow:** Set cookie → banner hide (silent)
- **Gating:** Server-side JSP conditionals prevent script loading

### Key Files
- `main.jsp` (lines 786–805) — Banner UI + conditional script loading
- Site property: `analytics.consentRequired` (default false, admin-editable)
- Integrates with: GTM, GA4, Simplifi, Brand CDN tracking

---

## Issue #367: Geo Precision Reduction for Anonymous Visitors

**Implementation Status:** ✅ Enhanced, with a correction: the code as originally merged had a real compliance bug -- `SaveSessionCommand` computed the `is_anonymous` flag *inside* the `if (getGeoIP() != null)` block, so it was never set at all when GeoIP data was unavailable (exactly the ambiguous case this issue exists to handle safely). Fixed by [PR #538](https://github.com/SimisRnD/simis-cms/pull/538), **merged**. Also: `SaveSessionCommandTest` (the "3 test cases" below) is currently `@Disabled` in CI -- a real, undiagnosed JaCoCo-instrumentation-only failure with no reproducible stack trace, tracked as a follow-up in #538. The tests pass locally and their assertions are accurate, but they are not currently providing CI regression coverage for this code.

### What It Does
- Restricts geo precision at write-time for anonymous visitors
- Anonymous visitors (GUEST_ID): continent, country, state, timezone only
- Authenticated users: full precision including city, postal_code, lat/long
- Explicit `is_anonymous` boolean column for clarity and future compliance reporting

### Architecture
- **Write-time gate:** SaveSessionCommand checks `userId == GUEST_ID`
- **Columns:**
  - Stored for all: continent, country, state, timezone
  - Authenticated only: city, postal_code, latitude, longitude, metro_code
- **Database:** New `is_anonymous` column (Boolean, indexed) for semantic queries
- **Queries:** `findDailyUniqueLocations()` now explicitly filters `is_anonymous = false`

### Enhancements Made
1. **New column:** `is_anonymous` boolean (indexed, backfilled)
2. **Explicit flag:** SaveSessionCommand sets flag based on `userId == GUEST_ID`
3. **Query clarity:** Reports use `is_anonymous = false` instead of `latitude IS NOT NULL`
4. **Unit tests:** 3 test cases verifying anonymous/auth geo handling
5. **Documentation:** `issue-367-geo-precision-enhancement.md`

### Key Files
- `Session.java` — Added `isAnonymous` field + accessors
- `SaveSessionCommand.java` — Sets flag explicitly
- `SessionRepository.java` — Updated `findDailyUniqueLocations()` query
- `UPGRADE_20260726.1002__sessions_is_anonymous.sql` — Migration
- `SaveSessionCommandTest.java` — 3 unit tests (NEW)

### Impact
- ✅ GDPR compliance: Anonymous visitors no longer stored with precise coordinates
- ✅ Query transparency: Intent is explicit, not implicit NULL checks
- ✅ Future-proof: Column supports privacy dashboards and compliance reporting

---

## Issue #368: Privacy Dashboard

**Implementation Status:** ⏳ Design Ready, Implementation Pending

### What It Should Do
- Admin panel showing privacy/retention metrics
- Display: session count, oldest record, retention period, next purge estimate
- "Purge now" button for manual trigger
- Privacy section in Admin area

### Architecture
- **Depends on:** Issue #365 (retention job) — READY ✅
- **Data source:** `sessions` table aggregations + `site_properties` analytics.retentionDays
- **Widget:** New AdminWidget or section in existing admin interface
- **Queries needed:**
  - `SELECT COUNT(*) FROM sessions` — Total rows
  - `SELECT MIN(created) FROM sessions` — Oldest record
  - `SELECT MIN(created) FROM sessions WHERE created < NOW() - INTERVAL '${days}' days` — Purge estimate
  - `SELECT value FROM site_properties WHERE property_name = 'analytics.retentionDays'` — Config

### Design Questions (for implementation)
1. **Manual purge button:** Should it trigger `SessionsPiiScrubJob.execute()` immediately, or queue a background job?
2. **Metrics refresh:** Real-time queries or cached/async?
3. **UI placement:** Separate "Privacy" section or nested in Admin > Analytics?
4. **Retention slider:** Admin UI to edit retention days (1–3650), or read-only display?

### Estimated Effort
- 4–6 hours (widget, queries, UI, testing)

---

## Dependencies & Sequence

```
#365 (Retention Job) ⚠️ WAS NON-FUNCTIONAL -- fix open, PR #542
      ↓
#367 (Geo Precision) ✅ COMPLETE (compliance bug fixed by merged PR #538)
      ↓
#368 (Privacy Dashboard) ⏳ READY TO START
      (also depends on #366 for consent visibility)

#366 (Consent Gate) ✅ COMPLETE (orthogonal)
```

---

## Compliance Checklist

| Requirement | Status | Issue |
|------------|--------|-------|
| GDPR data minimization | ✅ #367 | Anonymous visitors: region-only geo |
| Data retention policy | ⚠️ #365 | Job ran but scrubbed nothing; fix open (PR #542) -- see correction above |
| Visitor consent | ✅ #366 | Banner + cookie-based gate |
| DNT/GPC honor | ✅ #344 | Separate visitor tracking suppression |
| Audit trail | ✅ Partial | Manual purge logs needed for #368 |
| Data export/deletion | ⏳ Future | GDPR subject access requests |

---

## Timeline

**Week of Jul 29:**
- [ ] Code review #367 enhancements
- [ ] Merge #367
- [ ] Begin #368 implementation (privacy dashboard widget)

**Week of Aug 5:**
- [ ] Complete #368 testing
- [ ] Launch Privacy & Analytics Completion milestone
- [ ] Monitor metrics (retention job effectiveness, consent rate)

---

## Rollout Checklist

Before launch:
- [ ] #365: Retention job tested in staging -- correction: this was never actually done; the job silently scrubbed 0 rows until PR #542 (open) fixes the underlying schema mismatch
- [x] #366: Consent gate tested cross-browser
- [x] #367: Geo precision verified for anonymous/auth (verification gap found and closed by PR #538; `SaveSessionCommandTest` currently `@Disabled` in CI, see above)
- [ ] #368: Privacy dashboard tested
- [ ] Admin documentation updated
- [ ] Analytics/privacy team briefed

---

**Milestone Owner:** Elizabeth (Product)  
**Engineering Lead:** Team  
**Next Review:** Aug 2, 2026
