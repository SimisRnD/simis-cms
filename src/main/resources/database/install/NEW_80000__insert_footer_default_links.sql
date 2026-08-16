-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Seeds a couple of default links into the footer's new About Us and Site Links columns
-- (NEW_20000's footer.default) so they aren't empty "Add Links" placeholders on a fresh install.
-- table_of_contents.created_by/modified_by are NOT NULL, and no user exists yet at the point
-- NEW_20000 itself runs -- this migration is deliberately numbered above 71120
-- (V71120__create_admin.java, the Flyway migration that creates the system administrator account)
-- so a valid user_id is available here.
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
