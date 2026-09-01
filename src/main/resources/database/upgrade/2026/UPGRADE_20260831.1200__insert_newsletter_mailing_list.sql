-- Issue #1724: install parity for NEW_10071 -- see that file for why the list has to exist.
--
-- Gated on the table being empty, which is the one case where a site's behaviour would otherwise
-- change: it has never created a mailing list, so its /subscribe page (UPGRADE_20260816.1400) is
-- relying on the auto-create this migration's companion change removes. Seeding for it keeps
-- signup working exactly as before.
--
-- Deliberately NOT seeded when any list already exists, even if none is named "Newsletter". A site
-- in that state has an admin who renamed or replaced the list, and inserting a "Newsletter" here
-- would recreate the very duplicate this issue is about. Those sites fail closed instead: the
-- emailSubscribe widget stops rendering and logs which name it could not resolve, until the
-- mailingList preference on the page points at a list that exists.
INSERT INTO mailing_lists (name, title, description, enabled, show_online)
SELECT 'Newsletter', 'Newsletter', 'General news and updates', true, false
WHERE NOT EXISTS (SELECT 1 FROM mailing_lists);
