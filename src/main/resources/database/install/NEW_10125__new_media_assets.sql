-- Issue #771: media_assets/media_asset_usage (added by
-- UPGRADE_20260726.1004__create_media_assets_table.sql) only ever existed as an upgrade/
-- migration, so a fresh install never created these tables at all -- MediaAssetRepository's
-- queries then failed with a SQLException that DB.java logs and swallows, returning an empty
-- result that looks exactly like "no files yet" instead of a hard failure. Mirrors that upgrade
-- migration's schema (including the media_asset_usage fix from a later, same-day migration) so a
-- fresh install has this schema from day one.

-- P5.1: Media Assets Library
-- Stores uploaded media (images, PDFs) for insertion into content
CREATE TABLE media_assets (
  id BIGSERIAL PRIMARY KEY,
  asset_id VARCHAR(128) NOT NULL UNIQUE,
  asset_name VARCHAR(512) NOT NULL,
  asset_type VARCHAR(32) NOT NULL,
  mime_type VARCHAR(64),
  file_size_bytes BIGINT NOT NULL,
  storage_path TEXT NOT NULL,
  alt_text TEXT NOT NULL,
  tags TEXT,
  created_by BIGINT NOT NULL REFERENCES users(user_id),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP
);

CREATE INDEX idx_media_asset_id ON media_assets(asset_id);
CREATE INDEX idx_media_created_at ON media_assets(created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_type ON media_assets(asset_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_created_by ON media_assets(created_by) WHERE deleted_at IS NULL;

-- Track where media is used in content (enables orphan detection, usage audit)
CREATE TABLE media_asset_usage (
  media_usage_id BIGSERIAL PRIMARY KEY,
  asset_id BIGINT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
  content_id BIGINT NOT NULL REFERENCES content(content_id) ON DELETE CASCADE,
  embed_type VARCHAR(32) NOT NULL,
  used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(asset_id, content_id)
);

CREATE INDEX idx_usage_asset_id ON media_asset_usage(asset_id);
CREATE INDEX idx_usage_content_id ON media_asset_usage(content_id);
