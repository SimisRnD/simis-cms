-- Issue #1734: mailing_lists.title is NOT NULL, but NOT NULL permits '' -- and until the companion
-- change to SaveMailingListCommand nothing else checked it, so the admin form marked Title
-- required with an asterisk and then saved whatever was submitted. Title is what nearly every
-- surface displays, so a blank one renders as an empty link on /admin/mailing-lists, an empty
-- option in the newsletter-send and blog list dropdowns, and an unnamed checkbox on
-- /my-email-preferences.
--
-- name is the right default: it is the fallback confirm-subscription.jsp already applies when the
-- title is empty, and the signup path that used to auto-create lists set title to the name for the
-- same reason.
--
-- Only touches a genuinely blank title -- never a list that already has one set, and re-running it
-- changes nothing.
UPDATE mailing_lists
SET title = name
WHERE title IS NULL OR trim(title) = '';
