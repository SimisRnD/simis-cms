
ALTER TABLE categories ADD unique_id VARCHAR(255);
ALTER TABLE categories ADD image_url VARCHAR(255);

CREATE INDEX categories_uidx ON categories (collection_id, unique_id);
