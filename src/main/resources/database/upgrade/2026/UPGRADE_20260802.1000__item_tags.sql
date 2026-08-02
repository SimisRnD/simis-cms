-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Tags for items (issue #632), matching NEW_10024__new_items.sql for fresh installs

CREATE TABLE IF NOT EXISTS tags (
  tag_id BIGSERIAL PRIMARY KEY,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  name VARCHAR(255) NOT NULL,
  created_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  item_count BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS tags_uni_idx ON tags(collection_id, name);
CREATE INDEX IF NOT EXISTS tags_col_idx ON tags(collection_id);

CREATE TABLE IF NOT EXISTS item_tags (
  id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  tag_id BIGINT REFERENCES tags(tag_id) NOT NULL,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS item_tags_uidx ON item_tags(item_id, collection_id, tag_id);
CREATE INDEX IF NOT EXISTS item_tag_item_idx ON item_tags(item_id);
CREATE INDEX IF NOT EXISTS item_tag_tag_idx ON item_tags(tag_id);
