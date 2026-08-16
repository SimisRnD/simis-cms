-- Seeds the same default footer links as NEW_80000, for existing installs. Runs after
-- UPGRADE_20260816.1700's toc_unique_id renames (footer-useful-links-1/2 -> footer-about-us /
-- footer-featured-solutions), so ON CONFLICT DO NOTHING correctly leaves an admin's own
-- already-configured Company/Support links alone rather than overwriting them with these
-- defaults -- this only fills in the columns that are still genuinely empty.
INSERT INTO table_of_contents (toc_unique_id, name, entries, created_by, modified_by)
SELECT 'footer-about-us', 'Footer - About Us Links',
  '[{"id":1,"name":"About Us","link":"/about-us"}]',
  user_id, user_id
FROM users WHERE unique_id = 'system-administrator'
ON CONFLICT (toc_unique_id) DO NOTHING;

INSERT INTO table_of_contents (toc_unique_id, name, entries, created_by, modified_by)
SELECT 'footer-site-links', 'Footer - Site Links',
  '[{"id":1,"name":"Contact Us","link":"/contact-us"},{"id":2,"name":"Terms of Use","link":"/legal/terms"},{"id":3,"name":"Privacy Policy","link":"/legal/privacy"}]',
  user_id, user_id
FROM users WHERE unique_id = 'system-administrator'
ON CONFLICT (toc_unique_id) DO NOTHING;
