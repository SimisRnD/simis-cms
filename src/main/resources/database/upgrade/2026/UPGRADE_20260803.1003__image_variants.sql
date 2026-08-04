-- Issue #411: derived, resized renditions of an uploaded image. Mirrors
-- NEW_10160__new_image_variants.sql exactly (install/ and upgrade/ must stay in sync).

CREATE TABLE IF NOT EXISTS image_variants (
  image_variant_id BIGSERIAL PRIMARY KEY,
  image_id BIGINT NOT NULL REFERENCES images(image_id) ON DELETE CASCADE,
  variant_type VARCHAR(20) NOT NULL,
  path VARCHAR(255) NOT NULL,
  file_length BIGINT DEFAULT 0,
  file_type VARCHAR(20),
  width INTEGER NOT NULL,
  height INTEGER NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS image_variants_image_id_variant_type_idx ON image_variants(image_id, variant_type);
