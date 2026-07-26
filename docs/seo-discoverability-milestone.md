# SEO & Discoverability Milestone

**Status:** In Progress  
**Open Issues:** 9  
**Closed Issues:** 1  
**Last Updated:** July 26, 2026

---

## Executive Summary

Comprehensive SEO and discoverability improvements spanning three core areas:
1. **Metadata & Schema** — Standards compliance (robots.txt, canonical URLs, Open Graph, JSON-LD)
2. **Search Experience** — Full-text search optimization and faceted filtering
3. **AI/LLM Integration** — Structured data for modern AI consumption (llms.txt, FAQPage schema)

---

## Issues by Category

### 🤖 Metadata & Standards Compliance

#### #400: Serve robots.txt with configurable disallow rules
**Priority:** Foundation (blocks other crawling)  
**Scope:** 
- Serve dynamically generated robots.txt with site-wide rules
- Admin configurable disallow patterns
- Default: allow all public pages, disallow /admin, /api internals

**Effort:** 2–3 hours

---

#### #401: Add canonical URL link tag to all public page responses
**Priority:** High (prevents duplicate content penalties)  
**Scope:**
- Canonical URL in `<head>` of every public page
- Handle: www variants, protocol variants, pagination (rel="next"/"prev")
- Multi-language: hreflang link tags (issue #425)

**Dependencies:** None (independent)  
**Effort:** 3–4 hours

---

#### #402: Complete Open Graph meta tags on all public pages
**Priority:** High (social sharing)  
**Scope:**
- og:title, og:description, og:image, og:url, og:type
- Per-page image fallback to site logo
- Content-specific: articles, products, collections
- Twitter Card equivalents

**Effort:** 4–5 hours

---

#### #403: Add JSON-LD structured data for Organization, WebPage, and Product
**Priority:** High (rich snippets, featured results)  
**Scope:**
- JSON-LD blocks in `<head>`
- Organization schema (site info, contact, social)
- WebPage schema (breadcrumbs, author, publish date)
- Product schema (if product module active)
- Validate with Google's Structured Data Testing Tool

**Effort:** 5–6 hours

---

#### #425: hreflang link tags for multi-language page variants (Bonus)
**Priority:** Medium (if multilingual)  
**Status:** Appears to already have an issue open

---

### 🔍 Search & Discovery

#### #404: Move web page full-text search to PostgreSQL tsvector/GIN
**Priority:** High (performance, features)  
**Current State:** Likely SQL LIKE or basic substring matching  
**Target:** PostgreSQL native full-text search

**Scope:**
- tsvector column for title + content (with language-specific stemmers)
- GIN index for fast queries
- Ranking by relevance (ts_rank)
- Migration of existing data
- Replace existing search query logic

**Effort:** 6–8 hours (includes migration, testing)

**Performance Gain:** 10–100x faster on large datasets

---

#### #421: Faceted search: filter content by category, date, tag, and custom attributes
**Priority:** Medium (UX enhancement)  
**Depends On:** #404 (full-text search as base)

**Scope:**
- Search results page with filter sidebar
- Filters: category, date range, tags, custom attributes
- Apply multiple filters (AND logic)
- Facet count display (e.g., "3 results")
- URL-state preservation (facets in query params)

**Effort:** 8–10 hours (includes UI + backend)

---

#### #424: Search analytics: track zero-result queries and trending search terms
**Priority:** Medium (insights)  
**Depends On:** #404 (search is instrumented)

**Scope:**
- Log all search queries + result counts
- Track zero-result (no-match) queries separately
- Admin dashboard: trending searches, top zero-result terms
- Time-series data: search volume by week/month
- Opportunity identification: "no results for X" → content gap

**Effort:** 5–6 hours (logging + dashboard)

---

### 🧠 AI/LLM Integration

#### #417: llms.txt: structured site summary for LLM consumption
**Priority:** Emerging (future-proofing)  
**Status:** https://llms.txt/

**Scope:**
- Serve `/llms.txt` with structured content summary
- Format: site metadata, top pages, guidelines, contact
- Helps: Claude, ChatGPT, Perplexity crawl sites efficiently
- Similar to robots.txt, but for LLMs
- Example:
  ```
  Sitename: Example CMS
  Description: ...
  Creator: SimIS
  Updated: 2026-07-26
  
  Pages:
  - Title: Home
    URL: /
    Updated: 2026-07-15
  ```

**Effort:** 3–4 hours

---

#### #416: FAQPage schema widget for answer engine-friendly Q&A content
**Priority:** Medium (voice search, AI answers)  
**Depends On:** #403 (JSON-LD framework)

**Scope:**
- New widget type: FAQ section
- JSON-LD FAQPage schema (compatible with Google, Bing)
- Q&A pairs in admin UI
- Render in page (expandable accordions)
- Structured for: voice assistants, AI answer engines

**Effort:** 4–5 hours

---

## Dependency Graph

```
#400 (robots.txt) — Independent

#401 (canonical) — Independent

#402 (Open Graph) — Independent

#403 (JSON-LD org/web/product) — Independent
    ↓
#416 (FAQPage schema) — Depends on #403 framework

#404 (Full-text search) — Independent
    ↓
#421 (Faceted search) — Depends on #404
    ↓
#424 (Search analytics) — Depends on #404

#417 (llms.txt) — Independent

#425 (hreflang) — Bonus/optional
```

---

## Recommended Implementation Order

### Phase 1: Metadata Foundation (Weeks 1–2)
1. **#400** — robots.txt (enables crawling control)
2. **#401** — Canonical URLs (prevents duplicate content)
3. **#402** — Open Graph (social sharing)
4. **#403** — JSON-LD (structured data framework)

**Why:** Build metadata foundation first; enables #416, #421, #424

**Effort:** 14–18 hours

---

### Phase 2: Search Infrastructure (Weeks 3–4)
5. **#404** — PostgreSQL tsvector/GIN (search upgrade)
6. **#421** — Faceted search (UX on top of #404)
7. **#424** — Search analytics (insights)

**Why:** #404 is foundation for #421 and #424

**Effort:** 19–24 hours

---

### Phase 3: AI/LLM Integration (Week 5)
8. **#417** — llms.txt (future-proofing)
9. **#416** — FAQPage schema (AI-friendly Q&A)

**Why:** Emerging features, not blocking anything

**Effort:** 7–9 hours

---

### Bonus (If needed for multilingual)
10. **#425** — hreflang tags (depends on #401)

---

## Total Milestone Effort Estimate

| Phase | Issues | Hours | Timeline |
|-------|--------|-------|----------|
| Phase 1 | #400–#403 | 14–18 | 2 weeks |
| Phase 2 | #404, #421, #424 | 19–24 | 2.5 weeks |
| Phase 3 | #417, #416 | 7–9 | 1 week |
| **Total** | 9 issues | **40–51** | **5.5 weeks** |

---

## Business Impact

### Search Engine Optimization
- **Crawlability:** robots.txt → search engines understand what to index
- **Duplicate Content:** Canonical URLs → avoid penalties
- **Rich Results:** Structured data → featured snippets, knowledge panels
- **Social Sharing:** Open Graph → better link previews

### User Experience
- **Search Quality:** Full-text search → faster, more relevant results
- **Discoverability:** Faceted search → easy filtering
- **Insights:** Search analytics → identify content gaps

### AI/LLM Integration
- **LLM-Friendly:** llms.txt + structured schema → better AI comprehension
- **Voice Search:** FAQPage → voice assistant compatibility
- **Future-Proof:** Emerging AI search engines can consume content

---

## Success Metrics

| Metric | Target | How to Measure |
|--------|--------|----------------|
| Search engine indexing | 100% public pages | Google Search Console coverage |
| Search latency | < 200ms | Database query logs (tsvector) |
| Faceted filter adoption | > 20% of searches | Search analytics dashboard |
| Zero-result queries | < 5% of searches | Search analytics (after content gaps filled) |
| Rich snippet eligibility | 90%+ pages | Structured Data Testing Tool |
| LLM crawlability | llms.txt served | curl /llms.txt verification |

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|-----------|
| Search regression after tsvector migration | Medium | High | Thorough testing, parallel queries during migration |
| Duplicate content (before #401) | Low | Medium | Implement #401 early, monitor GSC errors |
| JSON-LD schema validation | Low | Low | Validate with Google's tool, test edge cases |
| Performance impact of faceted search | Medium | Medium | Index strategy, denormalization if needed |

---

## Files & Configuration

**New Files to Create:**
- `robots.txt` generator (servlet)
- `llms.txt` generator (servlet)
- FAQPage widget & template
- Search analytics schema + dashboard widget

**Modified Files:**
- Page rendering (add meta tags, JSON-LD)
- Search logic (replace with tsvector)
- Database schema (tsvector column, GIN index)
- Admin UI (search analytics dashboard)

---

## Next Steps

1. **Prioritization:** Confirm Phase 1 → Phase 2 → Phase 3 order with team
2. **Design Review:** JSON-LD structure, faceted search UI mockups
3. **Technical Spike:** Test PostgreSQL tsvector performance on current data volume
4. **Resource Allocation:** Assign owners to issues

---

**Milestone Owner:** Elizabeth (Product)  
**Next Review:** Aug 2, 2026  
**Status:** Ready for planning & prioritization
