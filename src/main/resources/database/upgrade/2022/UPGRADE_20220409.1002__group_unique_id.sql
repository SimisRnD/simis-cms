
ALTER TABLE groups ADD CONSTRAINT groups_unique_id_key UNIQUE (unique_id);
ALTER TABLE groups ALTER COLUMN unique_id SET NOT NULL;
