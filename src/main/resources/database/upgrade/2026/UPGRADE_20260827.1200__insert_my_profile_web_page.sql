-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Upgrade-path twin of install/NEW_50050. Existing sites need this page just as much as fresh
-- installs do -- arguably more, since their mfa.enrollment.url has been sitting at the /my-profile
-- default since NEW_10000 with nothing behind it, and the failure only shows up at the moment an
-- admin turns MFA enforcement on (at which point they are locked out and cannot reach the setting
-- to turn it back off). See the install file for the full description of the failure mode.
--
-- Guarded so it is safe wherever a site already has the page: an admin visiting an unknown link
-- creates a stub row with no page_xml, so "row exists" is not the same as "page is usable". Fill in
-- a stub's layout, but never overwrite a page an admin has actually designed.
UPDATE web_pages
SET page_title = 'My Profile',
    page_xml =
'<page role="users" title="My Profile">
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
</page>'
WHERE link = '/my-profile'
  AND (page_xml IS NULL OR page_xml = '');

INSERT INTO web_pages (link, page_title, page_description, searchable, page_xml, translation_group)
SELECT '/my-profile', 'My Profile', 'Your profile and account security settings.', false,
'<page role="users" title="My Profile">
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
WHERE NOT EXISTS (SELECT 1 FROM web_pages WHERE link = '/my-profile');
