-- Issue #1155: an optional per-form privacy/data-handling notice, shown near the submit button when
-- the admin turns it on. Mirrors NEW_10010__new_cms.sql exactly (install/ and upgrade/ must stay in
-- sync -- see issue #431's precedent for what happens when they drift).

ALTER TABLE form_definitions ADD COLUMN IF NOT EXISTS show_privacy_notice BOOLEAN DEFAULT FALSE;
