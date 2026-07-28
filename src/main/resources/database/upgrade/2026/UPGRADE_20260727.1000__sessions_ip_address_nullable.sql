-- The daily PII-scrub job (SessionsPiiScrubJob, GH-365) is supposed to null out sessions.ip_address
-- for rows past the analytics retention window, but the column has carried a NOT NULL constraint
-- since the original 2022 schema, which was never relaxed when the scrub job was added -- every run
-- of the UPDATE throws a constraint violation on the first matching row, Postgres aborts the whole
-- statement, and the job silently scrubs zero rows forever. SessionRepository.scrubOldPii() and
-- countSessionsWithPii() already treat "ip_address IS NULL" as the expected post-scrub state (used
-- for idempotency and for the admin PII-visibility count), so the application code was always
-- written for a nullable column -- only the schema never caught up.
ALTER TABLE sessions ALTER COLUMN ip_address DROP NOT NULL;
