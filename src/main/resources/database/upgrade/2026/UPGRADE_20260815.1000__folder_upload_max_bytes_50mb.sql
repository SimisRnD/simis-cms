-- Raise the shared upload cap from the 10 MB default to 50 MB (issue #1198).
--
-- system.upload.maxBytes is the single server-enforced upload ceiling for every upload path
-- (folder drop zone, image upload, dataset upload, media API); it was introduced at 10 MB by
-- UPGRADE_20260725.1004. The ISSM set the folder upload limit to 50 MB; because this is one
-- shared property, image and dataset uploads rise to 50 MB as well. The media API path is
-- separately bounded by its own 30 MB @MultipartConfig hard ceiling and is unaffected.
--
-- Only bump installations still on the untouched 10 MB default, so an operator who has already
-- tuned this value in Admin > Site Properties keeps their setting.
UPDATE site_properties
SET property_value = '52428800'
WHERE property_name = 'system.upload.maxBytes'
  AND property_value = '10485760';
