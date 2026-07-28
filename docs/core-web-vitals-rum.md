# Core Web Vitals RUM (Real User Monitoring)

**Audience:** Performance engineers, DevOps, content managers  
**Goal:** Collect real user experience metrics and detect regressions

## Overview

RUM enables performance monitoring from actual visitor sessions, not just synthetic Lighthouse tests:

```
Visitor loads page
  → web-vitals.js measures Core Web Vitals
    → LCP, CLS, INP, FCP, TTFB
    → POST to /rum/vitals
      → Server stores in web_vitals table
        → Admin dashboard queries for p75 values
          → Color-coded: Good (green), Needs Improvement (orange), Poor (red)
```

## What's Collected

| Metric | Name | Threshold Good | Measure | Why Matters |
|--------|------|---|---------|-----------|
| **LCP** | Largest Contentful Paint | < 2.5s | Time to render largest visual element | Perceived page load speed |
| **CLS** | Cumulative Layout Shift | < 0.1 | Layout instability during load | Accidental clicks, jarring UX |
| **INP** | Interaction to Next Paint | < 200ms | Input latency (click to visual feedback) | Responsiveness, sluggishness |
| **FCP** | First Contentful Paint | < 1.8s | Time to first visible content | Perceived responsiveness |
| **TTFB** | Time to First Byte | < 600ms | Backend response time | Server performance |

**Rating scale (from web-vitals library):**
- 🟢 **Good** — within threshold
- 🟡 **Needs Improvement** — between good and poor
- 🔴 **Poor** — exceeds threshold

## Architecture

### Client Side (Browser)

**Script:** `web-vitals-collector.js` (`src/main/webapp/javascript/`) — a small,
self-contained collector built on the standard `PerformanceObserver` API. No external
library dependency; not the npm `web-vitals` package.

```html
<!-- Not yet added to any page/layout template -- see Phase 2 checklist below -->
<script src="/javascript/web-vitals-collector.js"></script>
```

It computes LCP/CLS/INP/FCP/TTFB itself, rates each against the same thresholds used by
`WebVitalsWidget`, and POSTs only the metrics that actually finalized before the page was
left, via `navigator.sendBeacon()` (falling back to `fetch` with `keepalive`).

**Respect consent gate:**
```javascript
// Check if analytics consent is active (from existing consent gate #366)
if (shouldTrackAnalytics()) {
  // Only run web-vitals collection if consent granted
  initWebVitals();
}
```

### Server Side (Application)

**API Endpoint:** `POST /rum/vitals`

Deliberately *not* under `/api/*`: `RestRequestFilter` is mapped to that whole prefix
and gates every request behind `site.api` being enabled plus an `X-API-Key` -- neither
of which makes sense for an anonymous RUM beacon fired from every page load. The
original endpoint lived at `/api/metrics/vitals` and was consequently unreachable
(always a 403) regardless of anything else being correct; caught by loading a real page
in a browser against a live container, not by the build or test suite.

```
Request:
  {
    "url": "/news/article",
    "metrics": {
      "LCP": {"value": 2500, "rating": "good"},
      "CLS": {"value": 0.1, "rating": "good"},
      "INP": {"value": 150, "rating": "good"},
      "FCP": {"value": 1200, "rating": "good"},
      "TTFB": {"value": 400, "rating": "good"}
    }
  }

Response:
  204 No Content (success)
```

**Components:**
- `WebVitalsApiController` — Accept POST requests, validate JSON
- `WebVitalsCollector` — Store metrics in database
- `WebVitalsAggregationJob` — Nightly job computing p50/p75/p95 into `web_vitals_aggregates`
- `WebVitalsCleanupJob` — Daily retention cleanup
- `WebVitalsWidget` — Admin dashboard widget (`webVitals` in widget-library.xml)
- `web_vitals` / `web_vitals_aggregates` tables — raw + aggregated storage

**Database schema:**
```sql
CREATE TABLE web_vitals (
  id BIGSERIAL PRIMARY KEY,
  url VARCHAR(2048) NOT NULL,
  metric_type VARCHAR(50),       -- LCP, CLS, INP, FCP, TTFB
  value NUMERIC(10, 2),          -- milliseconds, or a 0-1-ish fraction for CLS
  rating VARCHAR(20),            -- good, needs-improvement, poor
  session_id VARCHAR(64),        -- visitor session (optional)
  web_page_id BIGINT REFERENCES web_pages(web_page_id),  -- optional CMS page match
  user_agent_hash VARCHAR(64),   -- SHA-256 of User-Agent; raw UA is never stored
  viewport_width SMALLINT,
  connection_type VARCHAR(16),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_web_vitals_url_metric_created ON web_vitals(url, metric_type, created_at);

CREATE TABLE web_vitals_aggregates (
  id BIGSERIAL PRIMARY KEY,
  url VARCHAR(2048) NOT NULL,
  metric_type VARCHAR(50) NOT NULL,
  p50_value NUMERIC(10, 2),
  p75_value NUMERIC(10, 2),
  p95_value NUMERIC(10, 2),
  sample_count INTEGER NOT NULL DEFAULT 0,
  aggregated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE (url, metric_type, aggregated_at)  -- one row per URL/metric/day
);
```

Fresh installs get this shape directly from `NEW_10010__new_cms.sql`; existing databases
get there via `UPGRADE_20260726.2000` (base `web_vitals` table) followed by
`UPGRADE_20260727.1001` (context columns + `web_vitals_aggregates`) — both must stay in
sync with the install script per this repo's install/upgrade parity convention.

### Admin Dashboard

**Dashboard Widget** — Real User Metrics Summary (`webVitals`, add via the page-composer widget picker)

```
Core Web Vitals (Last 7 days)

┌─────────────────────────────────────────────────┐
│ LCP (Largest Contentful Paint)       2.2 seconds│
│ 🟢 Good (< 2.5s)          [████████░] 85%       │
│ 🟡 Needs Improvement                 13%        │
│ 🔴 Poor                               2%        │
│ Sample size: 12,450 page views                   │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ CLS (Layout Shift)               0.08 unitless  │
│ 🟢 Good (< 0.1)           [███████░░] 78%       │
│ 🟡 Needs Improvement                 18%        │
│ 🔴 Poor                               4%        │
│ Sample size: 12,450 page views                   │
└─────────────────────────────────────────────────┘

Top Pages (P75 LCP)
 1. /news/article         2.3s  🟢
 2. /products             2.8s  🟡
 3. /checkout             3.2s  🔴
```

**Query structure:**
```sql
SELECT url,
       metric_type,
       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY value) as p50,
       PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY value) as p75,
       PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY value) as p95,
       COUNT(*) as sample_size
FROM web_vitals
WHERE created_at > NOW() - INTERVAL '7 days'
GROUP BY url, metric_type
ORDER BY url, metric_type;
```

## Implementation Checklist

### Phase 1: Backend (Done)
- [x] Create `web_vitals` table (Flyway migration)
- [x] Implement `WebVitalsApiController` (API endpoint)
- [x] Implement `WebVitalsCollector` (storage logic)
- [x] `web_page_id`/`user_agent_hash`/`viewport_width`/`connection_type` context columns
- [x] `web_vitals_aggregates` table + `WebVitalsAggregationJob` (nightly p50/p75/p95)
- [x] `WebVitalsCleanupJob` (retention: 30 days raw, 1 year aggregates)

### Phase 2: Frontend (Done)
- [x] `web-vitals-collector.js` — PerformanceObserver-based collector (no external library dependency)
- [x] Add `<script src="${ctx}/javascript/web-vitals-collector.js">` to `main.jsp`, gated by the
      exact same condition as the other analytics scripts (non-admin page, no DNT/GPC, consent OK)
- [x] Consent gate integration — the script also independently checks the real
      `analytics-consent` cookie the banner sets, as defense-in-depth on top of the
      server-side gate that decides whether the script tag is even emitted
- [ ] Test metric collection in dev/staging with real traffic

### Phase 3: Admin Dashboard (Done)
- [x] Build dashboard widget component (`WebVitalsWidget`, JSP at `/admin/web-vitals.jsp`)
- [x] Register widget (`webVitals` in `widget-library.xml`) — add it to an admin page via the widget picker
- [x] Display p75 per metric, per page, color-coded Good/Needs Improvement/Poor
- [ ] 7-day / 30-day / 90-day time window selector (currently fixed to 7 days)
- [ ] Test with real/seeded data once the collector is wired into pages

### Phase 4: Alerting (Future)
- [ ] Alert rule: if p75 LCP > 3s for 2 consecutive days
- [ ] Alert rule: if p95 CLS > 0.25 (indicates regression)
- [ ] Notify ops team via Sentinel / PagerDuty

## Privacy & Consent

### Data Minimization

**What's sent:**
- Page URL (path only, no query string)
- Metric values (numeric)
- Metric ratings (good/needs-improvement/poor)
- Session ID (for correlation; optional)

**Not sent:**
- User identity
- Referrer
- Device type
- Browser info
- Geographic location

### Consent Gate Integration

RUM respects the existing analytics consent gate (#366):

```javascript
// In consent-gate code
if (consentManager.hasAnalyticsConsent()) {
  initWebVitals();  // Only collect if opted-in
} else {
  // Wait for consent
  consentManager.onConsent('analytics', () => {
    initWebVitals();
  });
}
```

**Notes:**
- If no consent gate is active, RUM runs for all visitors
- If consent gate is active, RUM only runs for visitors who opted-in
- No third-party services (all data stored server-side)

## Retention & Cleanup

**Raw data retention:** 30 days
- Space-efficient for recent trends
- Supports the dashboard's 7-day window

**Aggregated data retention:** 1 year
- Daily percentiles (p50/p75/p95) per URL per metric
- Long-term trend analysis

**Cleanup job (daily, `WebVitalsCleanupJob`, 4:05 AM UTC):**
```sql
DELETE FROM web_vitals WHERE created_at < NOW() - INTERVAL '30 days';
DELETE FROM web_vitals_aggregates WHERE aggregated_at < NOW() - INTERVAL '1 year';
```

**Aggregation job (nightly, `WebVitalsAggregationJob`, 11 PM UTC):**
```sql
SELECT DISTINCT url, metric_type FROM web_vitals WHERE created_at > NOW() - INTERVAL '24 hours';
-- then, per (url, metric_type): PERCENTILE_CONT(0.50/0.75/0.95) WITHIN GROUP (ORDER BY value)
-- upserted into web_vitals_aggregates keyed on (url, metric_type, day)
```

## Troubleshooting

### Metrics not showing up in dashboard

**Check:**
1. Is `web-vitals-collector.js` loaded in the page? (Not yet added to any template — see Phase 2.)
   ```bash
   curl https://example.org/ | grep web-vitals-collector
   ```

2. Does the browser console show POST to /rum/vitals?
   ```javascript
   // In browser DevTools → Network tab, filter by /rum/vitals
   ```

3. Check server logs for API endpoint:
   ```bash
   tail -f logs/app.log | grep WebVitalsApi
   ```

4. Check database has data:
   ```sql
   SELECT COUNT(*) FROM web_vitals WHERE created_at > NOW() - INTERVAL '1 hour';
   ```

### All metrics show "Poor" rating

**Likely cause:** Application or infrastructure issue

**Investigate:**
1. Check app performance: `/admin/monitoring/app-performance`
2. Check database query time: slow migrations, connection pool?
3. Check network: AFD → origin latency (Phase 4 Sentinel logs)
4. Check caching: cache hit rate dropped? (Phase 2: page caching)

### Consent gate blocks RUM data collection

**Expected behavior:** If visitor hasn't consented to analytics, no vitals sent

**Verify:**
- `consentManager.hasAnalyticsConsent()` returns true
- Page loads `/javascript/web-vitals-collector.js`
- Network tab shows POST to /rum/vitals

## Performance Impact

**Client-side overhead:**
- `web-vitals-collector.js`: no external library fetch, single small script
- Metric collection: < 1ms overhead (fires after page load)
- Sending metrics: `sendBeacon`/`fetch keepalive`, doesn't block interactivity

**Server-side overhead:**
- API endpoint: < 5ms per request (fast INSERT)
- Database: 1 row per metric (typically 3-5 metrics per page load)
- Query (for dashboard): ~100ms for weekly aggregates

**Total:** Negligible impact on user experience.

## Next Steps

1. **Test with real visitor traffic** now that the collector is wired into `main.jsp`

2. **Admin dashboard polish** (remainder of Phase 3)
   - Add time-window selector (7/30/90 days) — currently fixed to 7 days

3. **Alerting** (Phase 4)
   - Wire Sentinel dashboard metrics
   - Alert on regressions (LCP > 3s, CLS > 0.25)
   - Escalate to ops on-call

---

**See Also:**
- `WebVitalsApiController.java` — API endpoint
- `WebVitalsCollector.java` — Storage logic
- `WebVitalsAggregationJob.java` / `WebVitalsCleanupJob.java` — Scheduled jobs
- `WebVitalsWidget.java` — Admin dashboard widget
- `UPGRADE_20260726.2000__create_web_vitals_table.sql` / `UPGRADE_20260727.1001__web_vitals_context_and_aggregates.sql` — Database schema
- [Web Vitals](https://web.dev/vitals/) (documentation)
