-- Collects Content-Security-Policy violation reports so the remaining directives in #1430 can be
-- written from evidence instead of guesswork.
--
-- The row is an aggregate, not an event. The question this table answers is "which hosts would a
-- stricter policy block?", which needs one row per (directive, host) with a count -- not one row
-- per page view. That distinction matters more than storage: /csp-report is necessarily
-- unauthenticated, because browsers post violation reports without credentials, so anyone can post
-- to it. Aggregating means a flood inflates a counter rather than growing the table, and the
-- UNIQUE constraint below is what makes the upsert that does it possible.
--
-- Only the host of the blocked URL is kept, never the full URL. A blocked URL can carry a path and
-- query string, and a violation report is one of the few places a third party's URL parameters can
-- end up in our database. The host is both the privacy-preserving choice and precisely what a
-- source list needs.
--
-- CspViolationRepository caps the number of distinct rows (see MAX_DISTINCT_VIOLATIONS). Without
-- that, a poster inventing unlimited fake hosts would grow this table without bound; once the cap
-- is reached, existing rows still count up but new ones are refused.
CREATE TABLE IF NOT EXISTS csp_violation (
  violation_id BIGSERIAL PRIMARY KEY,
  effective_directive VARCHAR(64) NOT NULL,
  blocked_host VARCHAR(255) NOT NULL,
  occurrences BIGINT NOT NULL DEFAULT 1,
  sample_document_path VARCHAR(512),
  first_seen TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  last_seen TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT uq_csp_violation_directive_host UNIQUE (effective_directive, blocked_host)
);

CREATE INDEX idx_csp_violation_last_seen ON csp_violation(last_seen DESC);
