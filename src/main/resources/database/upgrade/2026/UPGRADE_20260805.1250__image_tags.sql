-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Tags for images, matching NEW_10010__new_cms.sql for fresh installs. Unlike items' tags (see
-- UPGRADE_20260802.1000__item_tags.sql), images have no collection concept -- a tag here is a
-- single global label, not scoped per anything.

-- No image_count column here (unlike items' tags): keeping a running counter in sync would mean
-- touching it at every image-tag assign/unassign AND image-delete code path. Since this is a small,
-- admin-only pool, the tag-management panel just counts image_tag_map rows live instead.
CREATE TABLE IF NOT EXISTS image_tags (
  image_tag_id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  created_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS image_tags_name_uidx ON image_tags(LOWER(name));

CREATE TABLE IF NOT EXISTS image_tag_map (
  id BIGSERIAL PRIMARY KEY,
  image_id BIGINT REFERENCES images(image_id) NOT NULL,
  image_tag_id BIGINT REFERENCES image_tags(image_tag_id) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS image_tag_map_uidx ON image_tag_map(image_id, image_tag_id);
CREATE INDEX IF NOT EXISTS image_tag_map_image_idx ON image_tag_map(image_id);
CREATE INDEX IF NOT EXISTS image_tag_map_tag_idx ON image_tag_map(image_tag_id);
