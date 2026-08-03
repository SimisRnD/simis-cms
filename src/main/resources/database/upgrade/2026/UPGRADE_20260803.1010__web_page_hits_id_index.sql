-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Issue #497: web_page_hits.web_page_id has never had its own index -- findTopWebPages/
-- findTrafficBySolutionType/findEngagementBySolutionType already JOIN on it (unindexed), and the
-- new countViewsByWebPageId bulk query (WHERE web_page_id IN (...) GROUP BY web_page_id) makes
-- this a hot path for the /admin/web-pages traffic column. Mirrors NEW_10010__new_cms.sql, which
-- carries this index directly in the table definition for fresh installs.
CREATE INDEX IF NOT EXISTS web_pg_hits_wpid_idx ON web_page_hits(web_page_id);
