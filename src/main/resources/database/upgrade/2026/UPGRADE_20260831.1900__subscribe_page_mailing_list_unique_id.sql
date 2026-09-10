-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Point the seeded /subscribe page at the mailing list's stable id instead of its name, matching
-- what NEW_50040 now seeds on a fresh install. UPGRADE_20260831.1800 gave every existing list a
-- unique_id; this is what makes the page that ships with the product stop depending on a field an
-- admin is free to rename (issue #1724).
--
-- Written as a separate migration rather than an edit to UPGRADE_20260816.1400, which seeded this
-- page: that migration has already been applied on every site past 2026-08-16, so changing its text
-- would only change its Flyway checksum and fail validation on the next boot. Sites still behind
-- 20260816.1400 get the page from that migration first and are rewritten here a moment later, so
-- both paths converge on the same XML.
--
-- Three conditions, all deliberate:
--   * the page still carries the seeded <mailingList>Newsletter</mailingList> verbatim -- an admin
--     who has already repointed this widget at their own list keeps their edit untouched;
--   * a list named "Newsletter" actually exists -- on a site whose admin renamed it, the page is
--     already failing closed (PR #1727) and there is no way to know from here which list they meant,
--     so guessing one would be worse than leaving the visible misconfiguration in place;
--   * the substituted value is that list's real unique_id, not the literal 'newsletter' -- a site
--     that once had two lists slugifying the same way may hold 'newsletter-2'.
UPDATE web_pages
SET page_xml = REPLACE(page_xml, '<mailingList>Newsletter</mailingList>',
      '<mailingListUniqueId>'
        || (SELECT unique_id FROM mailing_lists WHERE LOWER(name) = 'newsletter' ORDER BY list_id LIMIT 1)
        || '</mailingListUniqueId>')
WHERE link = '/subscribe'
  AND page_xml LIKE '%<mailingList>Newsletter</mailingList>%'
  AND EXISTS (SELECT 1 FROM mailing_lists WHERE LOWER(name) = 'newsletter');
