-- Issue #1154: an optional confirmation email sent back to whoever submitted a form (previously
-- email only ever went to the site owner). Mirrors NEW_10010__new_cms.sql exactly (install/ and
-- upgrade/ must stay in sync -- see issue #431's precedent for what happens when they drift).

ALTER TABLE form_definitions ADD COLUMN IF NOT EXISTS send_confirmation_to_submitter BOOLEAN DEFAULT FALSE;
ALTER TABLE form_definitions ADD COLUMN IF NOT EXISTS confirmation_subject VARCHAR(255);
ALTER TABLE form_definitions ADD COLUMN IF NOT EXISTS confirmation_message TEXT;
