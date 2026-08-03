-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Issue #454: site_properties has never tracked who changed a value or when, so there was no way
-- to answer "when was this secret last rotated, and by whom" short of parsing audit_log text. Adds
-- generic modified/modified_by tracking (useful for every property, not just secrets) plus an
-- optional expires_at for secrets that are known to expire (OAuth tokens, etc.) -- surfaced on the
-- new /admin/integrations hub. All nullable: existing rows are simply unstamped until next saved.
ALTER TABLE site_properties ADD COLUMN IF NOT EXISTS modified TIMESTAMP;
ALTER TABLE site_properties ADD COLUMN IF NOT EXISTS modified_by BIGINT;
ALTER TABLE site_properties ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
