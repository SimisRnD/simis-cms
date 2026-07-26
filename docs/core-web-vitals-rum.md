# Core Web Vitals RUM (Real User Monitoring)

**Audience:** Performance engineers, DevOps, content managers  
**Goal:** Collect real user experience metrics and detect regressions

## Overview

RUM enables performance monitoring from actual visitor sessions, not just synthetic Lighthouse tests:

```
Visitor loads page
  → web-vitals.js measures Core Web Vitals
    → LCP, CLS, INP, FCP, TTFB
    → POST to /api/metrics/vitals
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

**Library:** `web-vitals` (small, ~1.5KB minified; no external deps)

```javascript
// In JSP page footer or layout template
<script src="/js/web-vitals.js"></script>
<script>
  // Import from web-vitals library (bundled or from CDN)
  import {getCLS, getFID, getFCP, getLCP, getTTFB} from 'web-vitals';

  // Collect each metric as it becomes available
  getLCP(metric => sendMetric('LCP', metric));
  getCLS(metric => sendMetric('CLS', metric));
  getINP(metric => sendMetric('INP', metric));  // Replaces FID
  getFCP(metric => sendMetric('FCP', metric));
  getTTFB(metric => sendMetric('TTFB', metric));

  // Send collected metrics to server
  function sendMetric(name, metric) {
    const url = window.location.pathname;  // e.g., /news/article
    fetch('/api/metrics/vitals', {
      method: 'POST',
      body: JSON.stringify({
        url: url,
        metrics: {
          [name]: {value: metric.value, rating: metric.rating}
        }
      })
    }).catch(err => console.warn('vitals send failed:', err));
  }
</script>
```

**Respect consent gate:**
```javascript
// Check if analytics consent is active (from existing consent gate #366)
if (shouldTrackAnalytics()) {
  // Only run web-vitals collection if consent granted
  initWebVitals();
}
```

### Server Side (Application)

**API Endpoint:** `POST /api/metrics/vitals`

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
- `web_vitals` table — Raw metrics storage
- Flyway migration — Create schema on first deployment

**Database schema:**
```sql
CREATE TABLE web_vitals (
  id BIGSERIAL PRIMARY KEY,
  url VARCHAR(2048) NOT NULL,
  metric_type VARCHAR(50),  -- LCP, CLS, INP, FCP, TTFB
  value NUMERIC(10, 2),     -- milliseconds or unitless
  rating VARCHAR(20),       -- good, needs-improvement, poor
  session_id VARCHAR(64),   -- visitor session (optional)
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_web_vitals_url_metric_created ON web_vitals(url, metric_type, created_at);
```

### Admin Dashboard

(Future feature; skeleton only)

**Dashboard Widget** — Real User Metrics Summary

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

### Phase 1: Backend (Already Done)
- [x] Create `web_vitals` table (Flyway migration)
- [x] Implement `WebVitalsApiController` (API endpoint)
- [x] Implement `WebVitalsCollector` (storage logic)

### Phase 2: Frontend (TODO)
- [ ] Bundle or link `web-vitals` library
- [ ] Create `/js/web-vitals-init.js` with metric collection
- [ ] Add snippet to page footer/layout template
- [ ] Test metric collection in dev/staging
- [ ] Verify consent gate integration (only collect if opted-in)

### Phase 3: Admin Dashboard (TODO)
- [ ] Create `WebVitalsRepository` (query metrics)
- [ ] Build dashboard widget component
- [ ] Add route `/admin/metrics/core-web-vitals`
- [ ] Display p75 per metric, per page
- [ ] Color-code Good/Needs Improvement/Poor
- [ ] 7-day / 30-day / 90-day time windows
- [ ] Test with sample data

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
- Supports 7-day/30-day dashboard windows

**Aggregated data retention:** 90 days
- Hourly percentiles (p50/p75/p95)
- Long-term trend analysis

**Cleanup job (daily):**
```sql
-- Archive data older than 30 days
DELETE FROM web_vitals WHERE created_at < NOW() - INTERVAL '30 days';

-- Or aggregate to hourly buckets first
INSERT INTO web_vitals_hourly (...)
  SELECT date_trunc('hour', created_at), url, metric_type, ...
  FROM web_vitals
  WHERE created_at BETWEEN NOW() - INTERVAL '2 days' AND NOW() - INTERVAL '1 day'
  GROUP BY ...;
```

## Troubleshooting

### Metrics not showing up in dashboard

**Check:**
1. Is web-vitals.js loaded in the page?
   ```bash
   curl https://example.org/ | grep web-vitals
   ```

2. Does the browser console show POST to /api/metrics/vitals?
   ```javascript
   // In browser DevTools → Network tab, filter by /api/metrics/vitals
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
- Page loads /js/web-vitals-init.js
- Network tab shows POST to /api/metrics/vitals

## Performance Impact

**Client-side overhead:**
- web-vitals library: ~1.5KB (minified)
- Metric collection: < 1ms overhead (fires after page load)
- Sending metrics: async POST, doesn't block interactivity

**Server-side overhead:**
- API endpoint: < 5ms per request (fast INSERT)
- Database: 1 row per metric (typically 3-5 metrics per page load)
- Query (for dashboard): ~100ms for weekly aggregates

**Total:** Negligible impact on user experience.

## Next Steps

1. **Frontend integration** (Phase 2)
   - Bundle web-vitals library or CDN link
   - Add collection script to page template
   - Test with real visitors

2. **Admin dashboard** (Phase 3)
   - Query recent metrics
   - Display p75 per URL per metric
   - Add time-window selector (7/30/90 days)

3. **Alerting** (Phase 4)
   - Wire Sentinel dashboard metrics
   - Alert on regressions (LCP > 3s, CLS > 0.25)
   - Escalate to ops on-call

---

**See Also:**
- `WebVitalsApiController.java` — API endpoint
- `WebVitalsCollector.java` — Storage logic
- `UPGRADE_20260726.2000__create_web_vitals_table.sql` — Database schema
- [web-vitals library](https://github.com/GoogleChromeLabs/web-vitals) (source)
- [Web Vitals](https://web.dev/vitals/) (documentation)
