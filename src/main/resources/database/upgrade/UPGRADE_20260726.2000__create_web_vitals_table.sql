-- Web Vitals RUM (Real User Monitoring) table
-- Stores Core Web Vitals metrics collected from real page loads
-- Used for performance monitoring and trend analysis

CREATE TABLE IF NOT EXISTS web_vitals (
  id BIGSERIAL PRIMARY KEY,
  url VARCHAR(2048) NOT NULL,
  metric_type VARCHAR(50) NOT NULL,  -- 'LCP', 'CLS', 'INP', 'FCP', 'TTFB'
  value NUMERIC(10, 2) NOT NULL,     -- metric value (milliseconds for timing, unitless for CLS)
  rating VARCHAR(20),                 -- 'good', 'needs-improvement', 'poor'
  session_id VARCHAR(64),             -- visitor session (optional, for correlation)
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT metric_type_check CHECK (metric_type IN ('LCP', 'CLS', 'INP', 'FCP', 'TTFB'))
);

-- Indexes for common queries
CREATE INDEX idx_web_vitals_url_metric_created ON web_vitals(url, metric_type, created_at DESC);
CREATE INDEX idx_web_vitals_created ON web_vitals(created_at DESC);
CREATE INDEX idx_web_vitals_metric ON web_vitals(metric_type);

-- Retention policy note:
-- Aggregate to hourly/daily buckets (p50/p75/p95) after 30 days
-- Archive raw data after 90 days (or delete if storage is constrained)
-- This table is write-heavy; consider partitioning by date in production
