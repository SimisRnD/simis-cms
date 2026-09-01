-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Issue #1724: the list every default signup path names, actually created.
--
-- NEW_10070 creates mailing_lists with no rows, while NEW_50040 seeds a /subscribe page whose
-- emailSubscribe widget carries <mailingList>Newsletter</mailingList>, and the checkout newsletter
-- checkbox and the ajax footer/inline form fall back to the same literal name. Nothing created a
-- list by that name, so the first public signup on a fresh install was what brought it into
-- existence -- SaveEmailCommand.resolveMailingList() created whatever name it was handed.
--
-- That auto-create is being removed (a public form must not create mailing-list records as a side
-- effect of configuration drift: an admin renaming this list had the old name silently recreated by
-- the next visitor, splitting subscribers across two lists). Seeding the list here is what makes
-- that removal safe -- without it, first-run signup would fail on every new site.
--
-- name is the key the emailSubscribe widget's mailingList preference matches on; title is the
-- label an admin can rename freely. show_online stays false so findOnlineLists() is still empty on
-- a fresh install and the per-list checkboxes (issue #598) render exactly as they did before.
INSERT INTO mailing_lists (name, title, description, enabled, show_online)
VALUES ('Newsletter', 'Newsletter', 'General news and updates', true, false);
