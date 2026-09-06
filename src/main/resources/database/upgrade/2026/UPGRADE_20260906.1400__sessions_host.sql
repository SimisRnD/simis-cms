-- The hostname a request actually arrived on (issue #1893).
--
-- The Referrals tile filters self-referrals by deriving six spellings of site.url -- six spellings,
-- one hostname. A site reachable on a second host reports its own internal navigation as external
-- referrals, and nothing on screen indicates it. Recording the host the request came in on lets the
-- read-time filter recognise a self-referral whatever the hostname is, including ones nobody
-- enumerated: a staging domain, a new custom domain, a CDN endpoint hostname.
--
-- Nullable on purpose. Rows written before this column existed keep NULL and continue to be
-- filtered by the site.url comparison alone, so historical data stays interpretable rather than
-- silently changing meaning.

ALTER TABLE sessions ADD COLUMN IF NOT EXISTS host VARCHAR(255);
