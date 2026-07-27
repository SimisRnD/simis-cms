-- Add is_anonymous column to sessions table for improved geo data filtering and privacy compliance
-- Tracks whether a session belongs to an anonymous visitor (no user_id) for clearer query intent
-- and to support data minimization requirements (issue #367)

ALTER TABLE sessions ADD COLUMN is_anonymous BOOLEAN DEFAULT FALSE;

-- Backfill existing sessions: anonymous = visitor_id IS NOT NULL
UPDATE sessions SET is_anonymous = (visitor_id IS NOT NULL);

-- Index for efficient filtering in reports
CREATE INDEX idx_sessions_is_anonymous_created ON sessions(is_anonymous, created DESC);

-- Add comment for documentation
COMMENT ON COLUMN sessions.is_anonymous IS 'True if session is from anonymous visitor (no user login)';
