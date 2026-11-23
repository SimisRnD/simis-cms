-- Issue #2130, step 1: a "file" type for form builder fields. Same columns and flag as
-- NEW_10010__new_cms.sql and NEW_10150__new_feature_flag_properties.sql, added here for databases that
-- already exist.
--
-- accepted_kinds (a comma list, e.g. "image") and max_bytes hold a file field's limits and stay null on
-- every other type (see FormFileFieldCommand). Existing fields are untouched.
--
-- features.form-file-uploads offers the type in the form editor and defaults to off. Turning it on does
-- not yet let a visitor send a file: FormWidget leaves file fields off every live form until the upload
-- pipeline (type check, sanitize, malware scan, gated download) is built.
ALTER TABLE form_fields ADD COLUMN IF NOT EXISTS accepted_kinds TEXT;
ALTER TABLE form_fields ADD COLUMN IF NOT EXISTS max_bytes BIGINT;

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (30, 'Enable file upload fields in the form editor', 'features.form-file-uploads', 'false', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
