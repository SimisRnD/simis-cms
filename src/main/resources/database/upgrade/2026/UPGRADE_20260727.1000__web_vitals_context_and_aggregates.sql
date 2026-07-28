-- Extend web_vitals (created in UPGRADE_20260726.2000) with request context, and add
-- web_vitals_aggregates for pre-computed percentiles powering the admin dashboard (#429).
--
-- web_page_id lets a metric be tied to a specific CMS page instead of just its URL string.
-- user_agent_hash/viewport_width/connection_type give enough device/network context to tell
-- a slow page apart from a slow client, without storing the raw user agent string.

ALTER TABLE web_vitals ADD COLUMN web_page_id BIGINT REFERENCES web_pages(web_page_id) ON DELETE CASCADE;
ALTER TABLE web_vitals ADD COLUMN user_agent_hash VARCHAR(64);
ALTER TABLE web_vitals ADD COLUMN viewport_width SMALLINT;
ALTER TABLE web_vitals ADD COLUMN connection_type VARCHAR(16);

CREATE INDEX IF NOT EXISTS idx_web_vitals_web_page_id ON web_vitals(web_page_id);

-- Pre-computed p50/p75/p95 per URL per metric, refreshed nightly from raw web_vitals rows
CREATE TABLE IF NOT EXISTS web_vitals_aggregates (
  id BIGSERIAL PRIMARY KEY,
  url VARCHAR(2048) NOT NULL,
  metric_type VARCHAR(50) NOT NULL,
  p50_value NUMERIC(10, 2),
  p75_value NUMERIC(10, 2),
  p95_value NUMERIC(10, 2),
  sample_count INTEGER NOT NULL DEFAULT 0,
  aggregated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT web_vitals_aggregates_metric_type_check CHECK (metric_type IN ('LCP', 'CLS', 'INP', 'FCP', 'TTFB')),
  CONSTRAINT web_vitals_aggregates_url_metric_day_unique UNIQUE (url, metric_type, aggregated_at)
);

CREATE INDEX IF NOT EXISTS idx_web_vitals_aggregates_url_metric ON web_vitals_aggregates(url, metric_type);
CREATE INDEX IF NOT EXISTS idx_web_vitals_aggregates_aggregated_at ON web_vitals_aggregates(aggregated_at DESC);
