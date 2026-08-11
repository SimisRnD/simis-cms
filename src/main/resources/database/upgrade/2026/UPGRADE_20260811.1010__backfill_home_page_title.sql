-- The home page (link = '/') is never seeded with a page_title by any install migration -- an
-- admin who built it from a page template was only ever prompted for a title on every OTHER page
-- (web-page-templates.jsp skips the title field specifically when webPage.link eq '/'), so it's
-- easy for a live home page to end up with no title at all. With no title, the browser tab, any
-- page-title-based report, and social/search previews all fall back to just the bare site name,
-- with nothing distinguishing the home page from any other untitled page.
--
-- Only backfills a genuinely blank title -- never touches a home page that already has one set.
UPDATE web_pages
SET page_title = 'Home'
WHERE link = '/' AND (page_title IS NULL OR page_title = '');
