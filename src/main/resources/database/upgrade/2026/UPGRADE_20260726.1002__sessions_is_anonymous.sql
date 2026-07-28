-- Add is_anonymous column to sessions table for improved geo data filtering and privacy compliance
-- Tracks whether a session belongs to an anonymous visitor (no logged-in user) for clearer query
-- intent and to support data minimization requirements (issue #367)

ALTER TABLE sessions ADD COLUMN is_anonymous BOOLEAN DEFAULT FALSE;

-- Historical sessions predate this column and have no reliable way to reconstruct whether the
-- visitor was actually logged in at the time -- sessions has no persisted user_id to backfill
-- from, only visitor_id (an unrelated general analytics/visitor-cookie reference; NOT the same
-- thing as being logged in, since visitor_id is set for anonymous and authenticated traffic
-- alike). Treat every pre-existing row as anonymous: the privacy-conservative default for a
-- column whose purpose is deciding whether precise geo data should be retained/exposed. New rows
-- going forward are set correctly by SaveSessionCommand from the actual session's login state.
UPDATE sessions SET is_anonymous = TRUE;

-- Index for efficient filtering in reports
CREATE INDEX idx_sessions_is_anonymous_created ON sessions(is_anonymous, created DESC);

ALTER TABLE sessions ALTER COLUMN is_anonymous SET NOT NULL;
COMMENT ON COLUMN sessions.is_anonymous IS 'True if session is from anonymous visitor (no user login)';
