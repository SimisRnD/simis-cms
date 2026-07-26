-- P5.1: Media Assets Library
-- Stores uploaded media (images, PDFs) for insertion into content
--
-- Fields:
--   asset_id: UUID for client reference
--   asset_name: Original filename
--   asset_type: 'image' or 'pdf'
--   mime_type: e.g., 'image/jpeg', 'application/pdf'
--   file_size_bytes: For quota management
--   storage_path: Local path or Azure Blob URI
--   alt_text: Required for accessibility (WCAG 2.1)
--   tags: Comma-separated for organization
--   created_by: User ID (foreign key)
--   deleted_at: Soft-delete timestamp

CREATE TABLE IF NOT EXISTS media_assets (
  id BIGSERIAL PRIMARY KEY,
  asset_id VARCHAR(128) NOT NULL UNIQUE,
  asset_name VARCHAR(512) NOT NULL,
  asset_type VARCHAR(32) NOT NULL,
  mime_type VARCHAR(64),
  file_size_bytes BIGINT NOT NULL,
  storage_path TEXT NOT NULL,
  alt_text TEXT NOT NULL,
  tags TEXT,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX idx_media_asset_id ON media_assets(asset_id);
CREATE INDEX idx_media_created_at ON media_assets(created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_type ON media_assets(asset_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_created_by ON media_assets(created_by) WHERE deleted_at IS NULL;

-- Track where media is used in content (enables orphan detection, usage audit)
CREATE TABLE IF NOT EXISTS media_asset_usage (
  id BIGSERIAL PRIMARY KEY,
  asset_id BIGINT NOT NULL REFERENCES media_assets(id),
  content_id BIGINT NOT NULL REFERENCES content(id),
  embed_type VARCHAR(32) NOT NULL,
  used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(asset_id, content_id)
);

CREATE INDEX idx_usage_asset_id ON media_asset_usage(asset_id);
CREATE INDEX idx_usage_content_id ON media_asset_usage(content_id);

COMMIT;
