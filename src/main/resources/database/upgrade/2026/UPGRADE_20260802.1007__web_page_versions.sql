-- Web page version history (issue #405): one row per PUBLISH event, holding the OUTGOING page_xml
-- (the value about to be overwritten) so a prior published state can be listed, viewed, or restored.
-- Idempotent so it is safe on any existing install; fresh installs get the identical table from NEW_10010.
CREATE TABLE IF NOT EXISTS web_page_versions (
  web_page_version_id BIGSERIAL PRIMARY KEY,
  web_page_id BIGINT REFERENCES web_pages(web_page_id) ON DELETE CASCADE,
  page_xml TEXT,
  published_by BIGINT REFERENCES users(user_id),
  published_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  label VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS web_page_versions_web_idx ON web_page_versions(web_page_id, published_at DESC);

-- Caps how many prior versions are retained per page (WebPageRepository.publish() prunes to this
-- limit right after each snapshot), mirroring formData.failureRetentionDays/funnel.retentionDays.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (12, 'Web page version history limit (per page)', 'webPage.versionHistoryLimit', '20', 'text')
ON CONFLICT (property_name) DO NOTHING;
