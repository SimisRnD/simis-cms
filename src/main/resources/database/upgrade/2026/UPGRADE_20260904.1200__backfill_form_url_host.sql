-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Issue #1835: repoint stored submission URLs at the site's own address.
--
-- FormWidget.resolvePageUrl built the stored URL from the live request, and WidgetContext.getUrl()
-- composes that from request.getServerName() -- the Host header the app *receives*. Behind a CDN or
-- reverse proxy that is the origin's name, not the visitor's: Azure Front Door sends a fixed
-- originHostHeader, so submissions recorded the App Service's own *.azurewebsites.net hostname.
-- The companion code change reads site.url instead; this repairs the rows written before it.
--
-- Only the scheme and authority are replaced. The path and query -- the part that actually says
-- which page was submitted, and the only part that carries information -- are preserved byte for
-- byte. site.url's OWN path is deliberately not used: on a deployment mounted under a context path
-- the stored URL already contains that prefix in its path, and taking site.url's authority alone
-- keeps it from being doubled.
--
-- Idempotent: after running, each row's authority equals the site's, which the WHERE clause then
-- excludes. Re-running changes nothing, and rows written by the fixed code are never touched.
--
-- Deliberately conservative. It does nothing at all when site.url is unset or has no scheme (a
-- value like "www.example.com" cannot yield an authority to graft on, and guessing one would be
-- worse than leaving the rows alone), and it skips any row whose url is NULL or not absolute.
--
-- Known trade-off, stated plainly: an install that previously served this CMS on a different public
-- domain has those historical hostnames normalised to the current canonical one. That is the same
-- operation this migration exists to perform -- from SQL a superseded public domain and a leaked
-- origin hostname are indistinguishable, since both are simply "not site.url". The path is kept, so
-- what page a submission came from survives either way; only which hostname served it does not.
DO $$
DECLARE
  site_authority TEXT;
  updated_data INTEGER := 0;
  updated_failures INTEGER := 0;
BEGIN
  SELECT substring(trim(property_value) FROM '^https?://[^/]+')
    INTO site_authority
    FROM site_properties
   WHERE property_name = 'site.url';

  IF site_authority IS NULL OR site_authority = '' THEN
    RAISE NOTICE 'Issue #1835: site.url is unset or has no scheme; stored form URLs left unchanged';
    RETURN;
  END IF;

  UPDATE form_data
     SET url = site_authority || COALESCE(substring(url FROM '^https?://[^/]*(.*)$'), '')
   WHERE url ~ '^https?://'
     AND substring(url FROM '^https?://[^/]*') IS DISTINCT FROM site_authority;
  GET DIAGNOSTICS updated_data = ROW_COUNT;

  UPDATE form_submission_failures
     SET url = site_authority || COALESCE(substring(url FROM '^https?://[^/]*(.*)$'), '')
   WHERE url ~ '^https?://'
     AND substring(url FROM '^https?://[^/]*') IS DISTINCT FROM site_authority;
  GET DIAGNOSTICS updated_failures = ROW_COUNT;

  RAISE NOTICE 'Issue #1835: repointed % form_data and % form_submission_failures URLs at %',
    updated_data, updated_failures, site_authority;
END $$;
