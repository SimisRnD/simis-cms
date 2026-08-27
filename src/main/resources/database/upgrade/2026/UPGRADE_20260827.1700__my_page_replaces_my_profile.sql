-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Moves the profile page to /my-page and points the MFA enrollment default at it.
--
-- UPGRADE_20260827.1200 seeded this page at /my-profile, because that is what mfa.enrollment.url
-- had always defaulted to. That was the wrong name: /my-profile was only ever a default string in a
-- site property, while /my-page is what the platform uses everywhere a person actually meets the
-- page -- the header's "My Account" link (header-layout.xml and the containers seeded in
-- NEW_20000), and the "My Page: Portal" template's own title. So the seeded page was reachable only
-- by typing its URL, and "My Account" stayed the dead link it had always been.
--
-- That earlier migration is left exactly as it is rather than edited: it has already run on
-- deployed sites, and Flyway runs with validateOnMigrate(false), so an edit would not re-run --
-- it would silently do nothing while looking like a fix.
--
-- Every step is guarded so this is safe whatever a given site already has: a site that never got
-- the /my-profile page, one that got it untouched, and one where an admin has since built their own
-- /my-page all end up correct.

-- 1. Move the seeded page, but only into a name nothing already occupies. If a site already has its
--    own /my-page, that page wins and the /my-profile copy is left alone rather than clobbering it.
UPDATE web_pages
SET link = '/my-page',
    page_title = 'My Page'
WHERE link = '/my-profile'
  AND NOT EXISTS (SELECT 1 FROM web_pages WHERE link = '/my-page');

-- 2. Create it for a site that has neither -- one that upgraded past 1200 without it, or that
--    deleted the page since.
INSERT INTO web_pages (link, page_title, page_description, searchable, page_xml, translation_group)
SELECT '/my-page', 'My Page', 'Your profile and account security settings.', false,
'<page role="users" title="My Page">
  <section>
    <column class="small-12 medium-6 cell">
      <widget name="myInfo" />
      <widget name="mySiteInfo" />
    </column>
    <column class="small-12 medium-6 cell">
      <widget name="myMfaSettings">
        <title>Two-Factor Authentication</title>
      </widget>
    </column>
  </section>
</page>',
'wp-' || gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM web_pages WHERE link = '/my-page');

-- 3. Repoint the MFA enrollment URL, but only where it is still the old untouched default AND the
--    page it would now name actually carries the enrollment widget.
--
--    That second condition is not theoretical. A site may already have its own /my-page -- the
--    pilot's is a built-out employee portal with no myMfaSettings widget on it. Step 1 correctly
--    declines to touch such a page, but repointing enforcement at it anyway would aim the redirect
--    at a page nobody can enroll from, which is precisely the lockout this whole line of work
--    exists to prevent. MfaEnrollmentPageCommand refuses exactly this value when an admin types it
--    into the settings form; a migration must not do quietly what the form refuses.
UPDATE site_properties
SET property_value = '/my-page'
WHERE property_name = 'mfa.enrollment.url'
  AND property_value = '/my-profile'
  AND EXISTS (
    SELECT 1 FROM web_pages
    WHERE link = '/my-page'
      AND page_xml LIKE '%myMfaSettings%');

-- 4. Keep any existing /my-profile link working -- a bookmark, or the value someone copied out of
--    the settings field while it still said /my-profile. Skipped when a real page still lives at
--    /my-profile (step 1 declined to move it because the site has its own /my-page), since a
--    redirect must never shadow a live page, and on such a site /my-profile is still the page that
--    can enroll someone.
INSERT INTO web_redirects (from_path, to_url, status_code, enabled)
SELECT '/my-profile', '/my-page', 301, true
WHERE NOT EXISTS (SELECT 1 FROM web_pages WHERE link = '/my-profile')
  AND NOT EXISTS (SELECT 1 FROM web_redirects WHERE from_path = '/my-profile');
