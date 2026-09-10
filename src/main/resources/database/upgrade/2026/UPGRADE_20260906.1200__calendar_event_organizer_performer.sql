-- Optional schema.org Event credits for a calendar event: who runs it and who appears at it.
-- Same columns as NEW_10010__new_cms.sql, added here for databases that already exist.
--
-- These exist because Search Console reports "organizer", "performer" and "offers" as missing on
-- Event items, and there was no truthful way to populate the first two: the events on a typical
-- site are third-party ones the organization attends, so defaulting organizer to the site owner
-- would assert it runs conferences it merely exhibits at. They are nullable and unset, and
-- StructuredDataCommand omits each property entirely while its fields are blank -- an absent
-- recommended property is a suggestion from Google, a wrong one is misleading markup.
--
-- "offers" needs no column: it is built from the existing sign_up_url.

ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS organizer_name VARCHAR(255);
ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS organizer_url VARCHAR(255);
ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS performer_name VARCHAR(255);
ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS performer_url VARCHAR(255);
