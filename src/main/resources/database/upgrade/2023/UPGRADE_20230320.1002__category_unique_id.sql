
ALTER TABLE categories ADD CONSTRAINT categories_unique_id_key UNIQUE (collection_id, unique_id);
ALTER TABLE categories ALTER COLUMN unique_id SET NOT NULL;
