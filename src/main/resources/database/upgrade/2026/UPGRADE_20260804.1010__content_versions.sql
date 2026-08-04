-- Content block version history (issue #406): one row per ContentRepository.publish() call, holding
-- the OUTGOING content (the value about to be overwritten), rendered to plain HTML so a block that
-- mixes Delta and legacy-HTML publishes over time still has a uniformly diffable history.
-- Idempotent so it is safe on any existing install; fresh installs get the identical table from
-- NEW_10010, mirroring UPGRADE_20260802.1007__web_page_versions.sql's pattern for #405.
CREATE TABLE IF NOT EXISTS content_versions (
  content_version_id BIGSERIAL PRIMARY KEY,
  content_id BIGINT REFERENCES content(content_id) ON DELETE CASCADE,
  content TEXT,
  approved_by BIGINT REFERENCES users(user_id),
  published_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  release_reference VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS content_versions_content_idx ON content_versions(content_id, published_at DESC);

-- Caps how many prior versions are retained per content block (ContentRepository.publish() prunes to
-- this limit right after each snapshot), mirroring webPage.versionHistoryLimit.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (13, 'Content block version history limit (per block)', 'content.versionHistoryLimit', '20', 'text')
ON CONFLICT (property_name) DO NOTHING;
