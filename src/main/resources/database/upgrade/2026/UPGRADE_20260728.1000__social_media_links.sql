-- issue #516: replace the fixed set of hardcoded social.*.url properties (Facebook, Instagram,
-- LinkedIn, Twitter, Flickr, YouTube) with an admin-editable list of (platform, url) pairs, so any
-- platform can be added without a code change. Contact info (social.email/social.phone/social.subscribe.url)
-- and the Instagram feed-embed integration (social.instagram.accessToken/facebookPageValue) are
-- unrelated to platform links and are left untouched.

CREATE TABLE IF NOT EXISTS social_media_links (
  social_media_link_id BIGSERIAL PRIMARY KEY,
  platform_name VARCHAR(100) NOT NULL,
  url VARCHAR(512) NOT NULL,
  link_order INTEGER DEFAULT 100,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS social_media_links_order_idx ON social_media_links(link_order);

-- Carry forward any existing, already-configured links so upgrading doesn't drop them. Only inserts
-- if the old property has a real (non-empty) value and hasn't already been migrated (safe to re-run).
INSERT INTO social_media_links (platform_name, url, link_order)
SELECT 'Facebook', property_value, 20 FROM site_properties
WHERE property_name = 'social.facebook.url' AND property_value IS NOT NULL AND property_value <> ''
  AND NOT EXISTS (SELECT 1 FROM social_media_links WHERE platform_name = 'Facebook');

INSERT INTO social_media_links (platform_name, url, link_order)
SELECT 'Instagram', property_value, 25 FROM site_properties
WHERE property_name = 'social.instagram.url' AND property_value IS NOT NULL AND property_value <> ''
  AND NOT EXISTS (SELECT 1 FROM social_media_links WHERE platform_name = 'Instagram');

INSERT INTO social_media_links (platform_name, url, link_order)
SELECT 'LinkedIn', property_value, 30 FROM site_properties
WHERE property_name = 'social.linkedin.url' AND property_value IS NOT NULL AND property_value <> ''
  AND NOT EXISTS (SELECT 1 FROM social_media_links WHERE platform_name = 'LinkedIn');

INSERT INTO social_media_links (platform_name, url, link_order)
SELECT 'Twitter', property_value, 35 FROM site_properties
WHERE property_name = 'social.twitter.url' AND property_value IS NOT NULL AND property_value <> ''
  AND NOT EXISTS (SELECT 1 FROM social_media_links WHERE platform_name = 'Twitter');

INSERT INTO social_media_links (platform_name, url, link_order)
SELECT 'Flickr', property_value, 40 FROM site_properties
WHERE property_name = 'social.flickr.url' AND property_value IS NOT NULL AND property_value <> ''
  AND NOT EXISTS (SELECT 1 FROM social_media_links WHERE platform_name = 'Flickr');

INSERT INTO social_media_links (platform_name, url, link_order)
SELECT 'YouTube', property_value, 45 FROM site_properties
WHERE property_name = 'social.youtube.url' AND property_value IS NOT NULL AND property_value <> ''
  AND NOT EXISTS (SELECT 1 FROM social_media_links WHERE platform_name = 'YouTube');

-- The old fixed fields are now redundant with the dynamic list above
DELETE FROM site_properties WHERE property_name IN (
  'social.facebook.url', 'social.instagram.url', 'social.linkedin.url',
  'social.twitter.url', 'social.flickr.url', 'social.youtube.url'
);
