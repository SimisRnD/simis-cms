-- P5: Media Library Database Schema
-- Created: 2026-07-26
-- Purpose: Store media assets (images, PDFs) for library browsing and insertion
--
-- Flyway Version: V20260726_1008__create_media_assets_table
-- Run as: SELECT INTO simis_cms database

-- Create media_assets table
CREATE TABLE IF NOT EXISTS media_assets (
  id BIGSERIAL PRIMARY KEY,
  asset_id VARCHAR(128) NOT NULL UNIQUE, -- UUID for client-side reference
  asset_name VARCHAR(512) NOT NULL, -- Filename with extension
  asset_type VARCHAR(32) NOT NULL, -- 'image', 'pdf'
  mime_type VARCHAR(64), -- 'image/jpeg', 'application/pdf', etc.
  file_size_bytes BIGINT NOT NULL, -- For sorting/filtering
  storage_path TEXT NOT NULL, -- Local dev: /uploads/..., Prod: Azure Blob Storage URI
  alt_text TEXT NOT NULL, -- Required for accessibility
  tags TEXT, -- Comma-separated tags for organization
  created_by BIGINT NOT NULL, -- User ID who uploaded
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP, -- Soft delete
  CONSTRAINT fk_media_user FOREIGN KEY (created_by) REFERENCES user_account(id)
);

-- Indexes for common queries
CREATE INDEX idx_media_asset_id ON media_assets(asset_id);
CREATE INDEX idx_media_created_at ON media_assets(created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_type ON media_assets(asset_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_tags ON media_assets(tags) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_created_by ON media_assets(created_by) WHERE deleted_at IS NULL;

-- Create media_asset_usage table (tracks which content regions use which media)
-- Enables audit trail and orphan detection
CREATE TABLE IF NOT EXISTS media_asset_usage (
  id BIGSERIAL PRIMARY KEY,
  asset_id BIGINT NOT NULL,
  content_id BIGINT NOT NULL, -- Content.id that references this media
  embed_type VARCHAR(32) NOT NULL, -- 'image', 'inline', etc.
  used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_usage_asset FOREIGN KEY (asset_id) REFERENCES media_assets(id),
  CONSTRAINT fk_usage_content FOREIGN KEY (content_id) REFERENCES content(id),
  UNIQUE (asset_id, content_id)
);

-- Index for finding where a media asset is used
CREATE INDEX idx_usage_asset_id ON media_asset_usage(asset_id);
CREATE INDEX idx_usage_content_id ON media_asset_usage(content_id);

-- Audit log entry for media (optional, for compliance)
-- Integrates with existing audit_log table
-- INSERT into audit_log on media uploads/deletes handled by MediaUploadCommand

COMMIT;
