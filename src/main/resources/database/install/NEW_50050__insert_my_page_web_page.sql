-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Two separate problems, one page.
--
-- The header's "My Account" link (NEW_20000's seeded containers, and header-layout.xml) has always
-- pointed at /my-page, and nothing ever created it -- a dead link on every fresh install, exactly
-- like /about-us before NEW_50020 and /subscribe before NEW_50040.
--
-- Separately, mfa.enrollment.url defaulted to /my-profile (NEW_10000), and MFA enforcement
-- redirects every non-exempt request to that URL while exempting only that URL -- but nothing ever
-- created that page either. On a fresh install the shipped default therefore pointed at a page
-- that did not exist: turning on mfa.required.roles sent every affected admin to a "this is a new
-- page" stub, whose only action ("Set a Page Layout") links to /admin/web-page-designer, which
-- enforcement redirects straight back to the stub. That left no route to enroll and no route to the
-- settings screen to undo it -- recoverable only by a direct database update.
--
-- One page serves both: /my-page is the name the platform already uses everywhere a human meets it
-- (the nav link, the seeded containers, and the "My Page: Portal" template's own title), whereas
-- /my-profile was only ever a default string in a site property pointing at nothing. So the page is
-- seeded at /my-page and the property now points there too. The myMfaSettings widget is the one the
-- enforcement redirect depends on; myInfo/mySiteInfo match the "My Page - Portal" template's
-- profile column so the page is a sensible profile page in its own right, not an MFA-only stub.
--
-- role="users" keeps it to signed-in users. translation_group is NOT NULL (NEW_50030) and the new
-- row's own id is not yet known, so use the random placeholder that WebPageRepository.add() and the
-- /subscribe seed (NEW_50040) both use. searchable=false: a per-user profile page has nothing to index.
INSERT INTO web_pages (link, page_title, page_description, searchable, page_xml, translation_group) VALUES
('/my-page', 'My Page', 'Your profile and account security settings.', false,
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
'wp-' || gen_random_uuid());
