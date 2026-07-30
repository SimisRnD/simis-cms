-- calendar_events.published has always defaulted to NULL, and nothing set it on save
-- before the draft/publish feature, so every pre-existing event has published = NULL.
-- The public calendar, sitewide search, and "upcoming events" widgets now filter on
-- published IS NOT NULL, which would otherwise make every event created before this
-- upgrade disappear from public view. Backfill using the event's created timestamp so
-- existing events keep behaving as published, matching their behavior prior to this change.
UPDATE calendar_events SET published = created WHERE published IS NULL;
