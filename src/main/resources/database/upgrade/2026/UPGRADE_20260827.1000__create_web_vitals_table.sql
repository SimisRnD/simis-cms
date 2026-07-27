-- Web Vitals RUM Collection (#429)
-- Stores per-URL Core Web Vitals aggregates collected from real user sessions
--
-- Metrics collected:
--   LCP (Largest Contentful Paint): main content is visible
--   CLS (Cumulative Layout Shift): visual stability
--   INP (Interaction to Next Paint): responsiveness to user input
--   FCP (First Contentful Paint): first visual change
--   TTFB (Time to First Byte): server response time
--
-- Data is aggregated server-side; p50/p75/p95 are computed from raw values

CREATE TABLE IF NOT EXISTS web_vitals (
  vitals_id BIGSERIAL PRIMARY KEY,
  web_page_id BIGINT REFERENCES web_pages(web_page_id) ON DELETE CASCADE,
  url VARCHAR(512) NOT NULL,
  metric_name VARCHAR(32) NOT NULL,
  metric_value INTEGER NOT NULL,
  user_agent_hash VARCHAR(64),
  viewport_width SMALLINT,
  connection_type VARCHAR(16),
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Aggregates table: pre-computed p50/p75/p95 for dashboard
CREATE TABLE IF NOT EXISTS web_vitals_aggregates (
  aggregate_id BIGSERIAL PRIMARY KEY,
  url VARCHAR(512) NOT NULL,
  metric_name VARCHAR(32) NOT NULL,
  p50_value INTEGER,
  p75_value INTEGER,
  p95_value INTEGER,
  sample_count INTEGER DEFAULT 0,
  aggregated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(url, metric_name, aggregated_at)
);

-- Indexes for query efficiency
CREATE INDEX IF NOT EXISTS idx_web_vitals_url ON web_vitals(url);
CREATE INDEX IF NOT EXISTS idx_web_vitals_metric ON web_vitals(metric_name);
CREATE INDEX IF NOT EXISTS idx_web_vitals_recorded_at ON web_vitals(recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_aggregates_url_metric ON web_vitals_aggregates(url, metric_name);
CREATE INDEX IF NOT EXISTS idx_aggregates_date ON web_vitals_aggregates(aggregated_at DESC);

-- Data retention policy: keep raw vitals for 30 days, aggregates for 1 year
-- (Implemented via scheduled job, similar to web_page_hits_cleanup_job)

COMMIT;
