-- Issue #2130, step 5: the table for files visitors attach to forms. Same definition as
-- NEW_10010__new_cms.sql, for databases that already exist; see that file for why it is kept apart
-- from the CMS file library. Empty until features.form-file-uploads is on and a form has a file field.
CREATE TABLE IF NOT EXISTS form_uploads (
  form_upload_id BIGSERIAL PRIMARY KEY,
  form_unique_id VARCHAR(255) NOT NULL,
  form_data_id BIGINT REFERENCES form_data(form_data_id) ON DELETE SET NULL,
  field_name VARCHAR(255) NOT NULL,
  original_filename VARCHAR(255),
  file_path VARCHAR(255) NOT NULL,
  content_type VARCHAR(50) NOT NULL,
  file_length BIGINT NOT NULL,
  width INTEGER,
  height INTEGER,
  thumbnail_path VARCHAR(255),
  thumbnail_content_type VARCHAR(50),
  scan_status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (scan_status IN ('pending', 'clean', 'rejected')),
  scan_checked TIMESTAMP(3),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS form_uploads_form_data_idx ON form_uploads(form_data_id);
CREATE INDEX IF NOT EXISTS form_uploads_form_unique_idx ON form_uploads(form_unique_id);
