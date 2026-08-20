-- Raise the shared upload cap to 50 MB, and make sure the property exists at all.
--
-- UPGRADE_20260815.1000 already intended this, but it was a bare UPDATE guarded on the row
-- already holding the exact 10 MB default:
--
--   UPDATE site_properties SET property_value = '52428800'
--   WHERE property_name = 'system.upload.maxBytes' AND property_value = '10485760';
--
-- On a pilot install that ran it, the value is still '10485760' -- so either the row did not
-- exist when it ran (an UPDATE against a missing row is a silent no-op, not an error) or it was
-- re-seeded afterwards. Either way the operator-visible outcome was an upload cap that stayed at
-- 10 MB while a folder drop zone advertised more, and nothing surfaced the discrepancy.
--
-- This uses INSERT .. ON CONFLICT so a missing row is created rather than silently skipped. The
-- DO UPDATE is still guarded on the 10 MB default, so an operator who has deliberately tuned this
-- value keeps their setting -- the same intent as UPGRADE_20260815.1000, minus the failure mode.
--
-- 50 MB is the figure the ISSM approved for the folder upload limit (see UPGRADE_20260815.1000's
-- own note). system.upload.maxBytes is the single server-enforced ceiling for the folder drop
-- zone, image upload, and dataset upload paths, so all three rise together. The media API path is
-- separately bounded by its own 30 MB @MultipartConfig hard ceiling and is unaffected.
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (10, 'Maximum upload size (bytes)', 'system.upload.maxBytes', '52428800')
ON CONFLICT (property_name) DO UPDATE
  SET property_value = '52428800'
  WHERE site_properties.property_value = '10485760';
