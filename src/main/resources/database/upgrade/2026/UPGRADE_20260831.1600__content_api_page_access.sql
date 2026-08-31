-- Issue #1701: GET /api/content/{uniqueId} returned any content record to anyone holding an app
-- key. The key is not a credential -- RestRequestFilter admits a GET on a key alone while the site
-- is online, and the Apps screen calls the key safe to share and safe to embed in client-side
-- scripts -- so content placed only on a role-, group- or internal-restricted page was readable by
-- anyone who knew its uniqueId. ValidateApiAccessToContentCommand now resolves the pages that
-- render a record and requires that the caller may open at least one of them.
--
-- 'true' is the seeded value AND the behaviour when this row is absent. That is deliberate and it
-- is the opposite of security.internalPages.group (issue #1688), which ships blank: that one adds
-- a NEW restriction, so opting in is the operator's decision. This one CLOSES A HOLE, so the
-- default has to be the safe side -- including in the window between deploying the code and
-- running this migration, which is why the command treats a missing row as enforcing rather than
-- reading it as boolean false.
--
-- Set to 'false' only for a deployment whose existing integrations read content anonymously and
-- that has accepted the exposure knowingly.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
-- Order 26, the free slot after security.internalPages.group (25) and before
-- security.iframe.allowedHosts (30), so the two API-facing controls sit together.
VALUES (26, 'Require page access for the content API', 'security.contentApi.enforcePageAccess', 'true', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
