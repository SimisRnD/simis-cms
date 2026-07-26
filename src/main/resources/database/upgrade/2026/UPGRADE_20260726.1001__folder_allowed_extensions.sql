-- Per-folder file extension allowlist (GH-370): restricts uploads to a comma-separated set of
-- extensions when configured. NULL/blank means unrestricted, so existing folders are unaffected.
ALTER TABLE folders ADD COLUMN IF NOT EXISTS allowed_extensions VARCHAR(500);
