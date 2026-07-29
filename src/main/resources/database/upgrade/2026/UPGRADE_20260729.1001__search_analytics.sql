-- Search analytics: zero-result queries and trending search terms (issue #424)
-- This is deliberately separate from the pre-existing web_searches table, which stores
-- session_id and ip_address (even if anonymized) and has no result-count column at all, so it
-- cannot answer either of the two questions this issue exists to answer. No PII of any kind is
-- stored here: no session id, no IP address, no user reference.

-- One row per (query, search_type) per search event. search_type identifies which of the several
-- independent search widgets on the Search Results page produced this result (pages/content/blog/
-- wiki/items/calendar), since each searches a different content type and computes its own count --
-- there is no single combined "the search" result count to log instead. Both dashboard reports
-- (zero-result terms, trending terms) query this table directly over a rolling window, the same
-- way web_searches' own "top search terms" tile does -- there is no daily/monthly rollup table,
-- since nothing in this issue needs a long-range trend chart that would outlive retention pruning.
CREATE TABLE search_analytics (
  search_analytics_id BIGSERIAL PRIMARY KEY,
  query VARCHAR(255) NOT NULL,
  search_type VARCHAR(50) NOT NULL,
  result_count INTEGER NOT NULL DEFAULT 0,
  page_path VARCHAR(255),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX search_analytics_created_idx ON search_analytics(created);
CREATE INDEX search_analytics_query_idx ON search_analytics(query);
