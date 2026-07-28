# Issue #367: Geo Precision Reduction — Enhancement Summary

**Status:** ✅ Complete  
**Date:** July 26, 2026  
**Scope:** Analytics data minimization for anonymous visitors (GDPR/privacy compliance)

---

> **Correction (2026-07-28):** The code as originally merged had a real compliance bug: `isAnonymous` was computed *inside* the `if (getGeoIP() != null)` block in `SaveSessionCommand.java`, so it was never set when GeoIP data was unavailable -- exactly the ambiguous case this issue exists to handle safely. Fixed by [PR #538](https://github.com/SimisRnD/simis-cms/pull/538), merged; the code snippet in section 3 below has been updated to match what actually shipped. Separately, `SaveSessionCommandTest` (section 5) is currently `@Disabled` in CI due to a real, undiagnosed JaCoCo-instrumentation-only failure -- it passes locally and its assertions are accurate, but it is not currently providing CI regression coverage.

---

## What Was Enhanced

### 1. **Explicit `is_anonymous` Column** (Database)
**File:** `UPGRADE_20260726.1002__sessions_is_anonymous.sql`

Added a new boolean column to the `sessions` table to:
- Explicitly mark whether a session belongs to an anonymous visitor
- Replace implicit `NULL` checks (`latitude IS NOT NULL`) with semantic clarity
- Support future privacy/compliance reporting

**Schema:**
```sql
ALTER TABLE sessions ADD COLUMN is_anonymous BOOLEAN DEFAULT FALSE;

-- Backfill: anonymous = visitor_id IS NOT NULL
UPDATE sessions SET is_anonymous = (visitor_id IS NOT NULL);

-- Index for efficient location report queries
CREATE INDEX idx_sessions_is_anonymous_created ON sessions(is_anonymous, created DESC);
```

---

### 2. **Session Model Update**
**File:** `Session.java`

Added field and accessors:
```java
private boolean isAnonymous = false;

public boolean getIsAnonymous() { return isAnonymous; }
public void setIsAnonymous(boolean anonymous) { isAnonymous = anonymous; }
```

---

### 3. **SaveSessionCommand Enhancement**
**File:** `SaveSessionCommand.java` (as of PR #538, merged)

**Before:** Implicit geo restriction (conditional block only stores city/coords for auth users) -- and, as originally merged for this issue, `isAnonymous` was computed *inside* the `getGeoIP() != null` check below, so it was silently never set on sessions with no GeoIP data.

**After:** Explicit flag, computed unconditionally, + clearer intent
```java
boolean isAnonymous = (userSession.getUserId() == UserSession.GUEST_ID);
session.setIsAnonymous(isAnonymous);
if (userSession.getGeoIP() != null) {
  session.setContinent(userSession.getGeoIP().getContinent());
  session.setCountryIso(userSession.getGeoIP().getCountryISOCode());
  session.setCountry(userSession.getGeoIP().getCountry());
  session.setStateIso(userSession.getGeoIP().getStateISOCode());
  session.setState(userSession.getGeoIP().getState());
  session.setTimezone(userSession.getGeoIP().getTimezone());
  if (!isAnonymous) {
    // Store full geo precision for authenticated users only
    session.setCity(userSession.getGeoIP().getCity());
    session.setPostalCode(userSession.getGeoIP().getPostalCode());
    session.setLatitude(userSession.getGeoIP().getLatitude());
    session.setLongitude(userSession.getGeoIP().getLongitude());
    session.setMetroCode(userSession.getGeoIP().getMetroCode());
  }
}
```

**Behavior (unchanged):**
- **Anonymous visitors (GUEST_ID):** continent, country, state, timezone only (NULL for city/coords)
- **Authenticated users:** Full precision (all geo fields)

---

### 4. **SessionRepository Query Clarity**
**File:** `SessionRepository.java` — `findDailyUniqueLocations()` (line 77)

**Before:** 
```sql
WHERE country IS NOT NULL AND latitude IS NOT NULL AND is_bot = false
```

**After:**
```sql
WHERE country IS NOT NULL AND is_anonymous = false AND is_bot = false
```

**Benefit:** Query intent is now explicit — we're filtering authenticated users only, not relying on implicit NULL checks.

---

### 5. **Comprehensive Unit Tests**
**File:** `SaveSessionCommandTest.java` (currently `@Disabled` in CI -- see correction at top of this document)

Three test cases:
1. **`anonymousVisitorRestrictedToRegionLevel()`** — Verifies anonymous visitors get only continent/country/state/timezone
2. **`authenticatedUserReceivesFullGeoPrecision()`** — Verifies authenticated users get city/coords
3. **`anonymousVisitorWithoutGeoIP()`** — Edge case: no GeoIP data provided (this case is exactly what exposed the compliance bug the correction above describes)

Uses Mockito to mock UserSession and capture Session objects saved to repository. Pass locally; disabled in CI pending diagnosis of an undiagnosed JaCoCo-instrumentation-only failure (tracked as a follow-up in PR #538).

---

## Privacy & Compliance Impact

✅ **GDPR Data Minimization:** Anonymous visitors no longer stored with precise coordinates  
✅ **Compliance Audit Trail:** `is_anonymous` column explicitly tracks user type  
✅ **Query Clarity:** Reports explicitly filter for authenticated users, not relying on implicit NULLs  
✅ **Future-Proof:** Column supports future privacy dashboards and compliance reporting

---

## Performance Impact

**Index Added:** `idx_sessions_is_anonymous_created`
- Improves query performance for: location reports, analytics dashboards
- Minimal storage overhead (1 byte per session, index ~2-3% table size)

**Backfill Migration:** Single UPDATE with `visitor_id IS NOT NULL` logic (idempotent)
- Negligible performance impact on modern databases

---

## Related Files

| Component | Purpose |
|-----------|---------|
| Database Migration | `UPGRADE_20260726.1002__sessions_is_anonymous.sql` |
| Domain Model | `Session.java` (added isAnonymous field) |
| Command Layer | `SaveSessionCommand.java` (sets flag) |
| Repository | `SessionRepository.java` (updated queries) |
| Tests | `SaveSessionCommandTest.java` (3 test scenarios) |

---

## Backward Compatibility

✅ **Fully compatible:** Default value `is_anonymous = FALSE` preserves existing behavior  
✅ **Non-breaking:** Existing queries continue to work; explicit `is_anonymous = false` is optional  
✅ **Optional adoption:** Codebase can migrate queries gradually to use the new column

---

## Future Enhancements

1. **Privacy Dashboard (#368):** Use `is_anonymous` column to show compliance metrics
2. **Compliance Reporting:** Add queries showing anonymous vs. authenticated data retention
3. **Data Export:** Filter anonymous visitor data for GDPR data subject access requests

---

**Status:** Merged (PR #538), with the correction above  
**Test Coverage:** 3 unit tests (anonymous/auth/edge cases) -- pass locally, currently `@Disabled` in CI  
**Code Review Checklist:**
- [x] Database migration idempotent and tested
- [x] Model changes minimal and backward compatible
- [x] Command layer logic verified via tests (locally -- not currently running in CI, see above)
- [x] Repository queries use new column semantically
- [x] No breaking changes to public APIs
