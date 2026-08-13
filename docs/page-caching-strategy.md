# Page-Level HTML Caching Strategy

**Audience:** Performance engineers, DevOps, content managers  
**Goal:** Maximize cache hit rate while keeping content fresh

## Overview

The application serves pages through Azure Front Door with intelligent caching:

```
User Request
  → AFD (edge cache)
     → Cache HIT? Return cached response (< 1 ms)
     → Cache MISS? Forward to origin
        → App checks: cacheable page?
           → Yes: Set Cache-Control: public, max-age=300
           → No: Set Cache-Control: no-cache, no-store
        → AFD caches if "public" header present
```

## Cacheable vs Non-Cacheable Pages

### ✅ Cacheable (Public Pages)

Pages that return `Cache-Control: public, max-age=300, stale-while-revalidate=3600`:

- No authenticated user session
- No personalization (user-specific content)
- No query string (search, filters, forms)
- Not marked cache-exempt by admin

**Examples:**
- Homepage
- Static product pages
- News articles (published content)
- Category listings
- About / Contact pages

**Cache duration:** 5 minutes (300s)  
**Stale-while-revalidate:** 1 hour (during cache revalidation, serve stale content)

### ❌ Non-Cacheable (Session/Personalized)

Pages that return `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`:

- Authenticated user (logged-in visitors)
- Admin/editor accounts
- Any page with a session cookie
- Pages with personalization (user preferences, cart)
- Search results (query strings disqualify from caching)
- Any custom URL parameters

**Examples:**
- User profile pages
- Admin dashboard
- Shopping cart
- Checkout pages
- Saved preferences

## How Caching Works

### 1. Application Sets Cache Header

When the app renders a page, it calls `CacheStrategy.setCacheHeaders()`:

```java
CacheStrategy.setCacheHeaders(request, response, pageId);
```

The strategy checks:
- Is there a session? → no-cache
- Does the URL have a query string? → no-cache
- Is there an X-Bypass-Cache header? → no-cache
- Otherwise → public, max-age=300, stale-while-revalidate=3600

### 2. AFD Honors Headers

Front Door is configured with a `cacheConfiguration` object on the route. Providing that
object is what enables edge caching at all (omitting it disables caching outright), and an
enabled cache honors the origin's `Cache-Control`/`Expires` headers unless a Rules Engine
rule overrides the duration — there is no such rule here. This means:
- If app says "public" → cache it for 5 minutes
- If app says "no-cache" → don't cache it
- If app says "no-store" → never cache it

### 3. Cache Hit/Miss

**Cache HIT** (page was cached and not expired):
- AFD serves from cache (edge location, near user)
- No request reaches the application
- Response time: < 10ms typically
- Header shows: `X-Cache: HIT` (in AFD diagnostic logs)

**Cache MISS** (page not cached or expired):
- AFD forwards to origin (App Service)
- App renders the page
- AFD caches response (if Cache-Control says public)
- Response time: 50-500ms (depends on app processing)
- Header shows: `X-Cache: MISS` (in AFD diagnostic logs)

## Publishing & Cache Invalidation

When content is published or updated:

**Automatic purge (future feature):**
1. Admin publishes a page via the CMS
2. `PublishEventCachePurgeHandler.onPagePublished()` fires
3. Handler calls AFD purge API with the URL
4. AFD removes the page from edge caches immediately
5. Next request fetches fresh content from origin

**Manual purge (interim):**
1. Go to Azure Portal → Front Door → Purge content
2. Enter the page path (e.g., `/news/my-article`)
3. Purge completes within 30 seconds
4. Next request gets fresh content

**Natural expiry (fallback):**
- If automatic purge fails, cached content expires after 5 minutes (max-age=300)
- Users see slightly stale content for up to 5 minutes
- Stale-while-revalidate allows AFD to serve 1-hour-old content while revalidating

## Admin Controls

### Bypass Cache During Editing

When authoring/editing content:

```
Request: GET /my-page
Header: X-Bypass-Cache: true

→ App returns: Cache-Control: no-cache, no-store
→ Page always fresh (no caching)
```

Your browser can set this header via a browser extension or admin toolbar (not yet implemented; future feature).

### Mark Page as Cache-Exempt

Some pages should never be cached (dynamic content, personalization):

```
Admin → Page Settings → Cache Behavior: Exempt from Cache

→ App returns: Cache-Control: no-store (always miss)
→ Every request hits origin
→ Page always latest
```

(Future feature; database schema change required)

## Metrics & Monitoring

### AFD Metrics (Azure Portal)

View cache performance:
```
Front Door → Overview → Metrics
  - Requests (total)
  - Cache hit rate (%)
  - Request rate (req/s)
  - Response time (P50/P95/P99)
```

**Good cache hit rate:** > 70% for public content  
**Expected:** Varies by content mix (news sites: 80%+; e-commerce: 30-50%)

### Access Logs

Check individual requests:
```
az network front-door frontdoor log list \
  --resource-group my-rg \
  --name afd-simis-cms \
  --query "[?contains(requestUri, '/my-page')]"
```

Look for `cacheStatus` field:
- `HIT` — served from cache
- `MISS` — forwarded to origin
- `EXPIRED` — cache expired, revalidated with origin

## Troubleshooting

### Cache not working (low hit rate)

**Symptom:** AFD shows < 50% cache hit rate

**Common causes:**
- Too many query strings (search params disable caching)
- Session cookies set on public pages
- Cache-Control header not being set

**Fix:**
1. Check app logs for `CacheStrategy` messages
2. Verify Cache-Control header on responses:
   ```bash
   curl -I https://example.org/page | grep Cache-Control
   ```
3. Check if pages have session context:
   - Clear cookies in browser
   - Retry; cache hit should be higher
4. Check AFD diagnostic logs for cache status

### Stale content (not purging on publish)

**Symptom:** Edit a page, but see old content in browser

**Expected behavior:**
- Automatic purge (< 30 seconds) — future feature
- Manual purge via Azure Portal
- Natural expiry (5 minutes max-age)

**Workaround:**
1. Hard-refresh browser: Ctrl+Shift+R (or Cmd+Shift+R on Mac)
2. Or manually purge via Azure Portal
3. Or wait 5 minutes for cache to expire

### Session pages being cached

**Symptom:** Logged-in user sees someone else's personal content

**Root cause:** Session header not detected

**Fix:**
1. Verify SessionConstants.USER is in session
2. Check CacheStrategy.setCacheHeaders() logic
3. Verify no query string (which could bypass session check)

## Performance Impact

### With Caching

```
Request: GET /news/article
Response from cache (HIT)
  Time: 5-10ms (AFD edge)
  Bytes: ~150KB
  Requests to origin per second: 0 (if cache sustained)
```

**Scenario:** 1000 req/s, 80% cache hit rate
- Origin handles: 200 req/s (20% misses)
- Edge serves: 800 req/s (80% hits)

### Without Caching

```
Request: GET /news/article
Response from origin
  Time: 100-300ms (app processing)
  Bytes: ~150KB
  Requests to origin per second: 1000
```

**Same 1000 req/s scenario:**
- Origin handles: 1000 req/s
- App CPU/memory scales with traffic

**Result:** Caching reduces origin load 5× (200 vs 1000 req/s).

## Best Practices

1. **Keep max-age short** (300s = 5 min)
   - Trade-off: freshness vs. load reduction
   - Longer = less origin load, staler content
   - 5 min is sweet spot for most content

2. **Purge on publish** (when available)
   - Don't wait for natural expiry
   - Users see updates within seconds

3. **Bypass during editing**
   - Don't cache admin/editor sessions
   - Preview must show live content

4. **Monitor cache hit rate**
   - Track in dashboards
   - Alert if drop below 50% (possible cache configuration issue)

5. **Use stale-while-revalidate**
   - Serve stale content while fetching fresh
   - Improves perceived performance
   - Hidden revalidation doesn't block user

---

**See Also:**
- `CacheStrategy.java` — Caching logic implementation
- `PublishEventCachePurgeHandler.java` — Cache invalidation on publish
- `infra/modules/frontdoor.bicep` — AFD caching configuration (route `cacheConfiguration`: query-string behavior + compression)
