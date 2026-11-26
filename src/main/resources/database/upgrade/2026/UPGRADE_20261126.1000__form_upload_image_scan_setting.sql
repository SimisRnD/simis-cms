-- Issue #2130: let an administrator decide that images attached to forms need no malware scan, for
-- databases that already exist. Same rows as NEW_10010__new_cms.sql and
-- NEW_10140__new_security_properties.sql.
--
-- Images are rebuilt from their pixels before the scan runs (SanitizeFormUploadCommand), so for them
-- the scan mostly re-checks a file the platform already drew. Seeded 'true', so nothing changes until
-- someone turns it off on /admin/security-properties. An image stored without a scan is recorded as
-- 'not_required', never as 'clean'.

ALTER TABLE form_uploads DROP CONSTRAINT IF EXISTS form_uploads_scan_status_check;
ALTER TABLE form_uploads ADD CONSTRAINT form_uploads_scan_status_check
  CHECK (scan_status IN ('pending', 'clean', 'rejected', 'not_required'));

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (62, 'Require a malware scan for image uploads', 'security.formUploads.imageScanRequired', 'true', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
