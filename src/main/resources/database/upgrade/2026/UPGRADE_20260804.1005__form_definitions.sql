-- Issue #409: form builder admin UI -- database-backed field configuration for FormWidget, as an
-- alternative to the XML <fields> preference. Mirrors NEW_10010__new_cms.sql exactly (install/ and
-- upgrade/ must stay in sync -- see issue #431's precedent for what happens when they drift, and
-- DatabaseMigrationTest's tablesThatOnlyExistedInUpgradeMigrationsAreOnTheInstallPath()/
-- columnsThatOnlyExistedInUpgradeMigrationsAreOnTheInstallPath() for the regression class this
-- guards against).
--
-- form_fields.form_definition_id has no ON DELETE CASCADE -- deleting a form definition explicitly
-- removes its fields first, in a transaction (see FormDefinitionRepository#remove). form_data is
-- untouched by this table pair: submissions are matched to a form by form_unique_id (a plain string),
-- never by a foreign key to form_definitions, so existing forms and submissions are unaffected by
-- this migration.

CREATE TABLE IF NOT EXISTS form_definitions (
  form_definition_id BIGSERIAL PRIMARY KEY,
  unique_id VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  title VARCHAR(255),
  subtitle VARCHAR(255),
  button_name VARCHAR(100),
  success_title VARCHAR(255),
  success_message TEXT,
  email_to VARCHAR(512),
  use_captcha BOOLEAN DEFAULT FALSE,
  check_for_spam BOOLEAN DEFAULT TRUE,
  enabled BOOLEAN DEFAULT TRUE,
  created_by BIGINT REFERENCES users(user_id),
  modified_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS form_definitions_unique_id_idx ON form_definitions(unique_id);
CREATE INDEX IF NOT EXISTS form_definitions_enabled_idx ON form_definitions(enabled);

CREATE TABLE IF NOT EXISTS form_fields (
  form_field_id BIGSERIAL PRIMARY KEY,
  form_definition_id BIGINT NOT NULL REFERENCES form_definitions(form_definition_id),
  field_order INTEGER DEFAULT 100,
  name VARCHAR(255) NOT NULL,
  label VARCHAR(255) NOT NULL,
  field_type VARCHAR(30) DEFAULT 'text',
  required BOOLEAN DEFAULT FALSE,
  placeholder VARCHAR(255),
  default_value VARCHAR(255),
  options TEXT,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS form_fields_form_definition_idx ON form_fields(form_definition_id);
CREATE INDEX IF NOT EXISTS form_fields_order_idx ON form_fields(field_order);
