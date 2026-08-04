-- Issue #411: derived, resized renditions of an uploaded image (thumbnail/medium/large), so a
-- page can serve an appropriately-sized file via <img srcset> instead of the full-resolution
-- original. This is deliberately a NEW table, not a reuse of file_versions -- file_versions is
-- content history (independently-uploaded replacements meant for a restore workflow), while a
-- variant is a derived, regenerable-from-the-original rendition that coexists with its source.
-- Variants are generated asynchronously (see ImageVariantJob); images.processed is stamped once
-- generation finishes, so a page can tell "not resized yet" from "no variant made sense" (e.g. the
-- original is already smaller than the variant's target size).
--
-- ON DELETE CASCADE: DeleteImageCommand looks up and removes each variant's physical file itself
-- before removing the images row, so the cascade only ever needs to clean up rows whose files are
-- already gone.
CREATE TABLE image_variants (
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
CREATE UNIQUE INDEX image_variants_image_id_variant_type_idx ON image_variants(image_id, variant_type);
