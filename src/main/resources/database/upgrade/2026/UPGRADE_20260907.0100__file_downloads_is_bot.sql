-- Same is_bot column as NEW_10200__new_file_downloads.sql, for databases that already exist.
--
-- Downloads were only excluded from the Top Downloads report when their session was flagged as a
-- bot. A crawler that requests a file URL directly is never issued a session, so nothing was
-- excluded and its downloads counted as human -- which on a live site was most of the report.
--
-- Existing rows default to false rather than being backfilled: the user agent that made those
-- requests was never stored, so there is nothing to classify them from. Historic counts stay as
-- they are and the report becomes accurate going forward.

ALTER TABLE file_downloads ADD COLUMN IF NOT EXISTS is_bot BOOLEAN DEFAULT false;
CREATE INDEX IF NOT EXISTS file_downloads_is_bot_idx ON file_downloads(is_bot);
