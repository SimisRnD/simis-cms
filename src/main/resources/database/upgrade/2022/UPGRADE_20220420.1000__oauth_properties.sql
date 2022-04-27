
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'OpenAuth Provider', 'oauth.provider', 'None', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'OpenAuth Client Id', 'oauth.clientId', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (14, 'OpenAuth Client Secret', 'oauth.clientSecret', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (16, 'OpenAuth Service URL', 'oauth.serviceUrl', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (18, 'OpenAuth Redirect Guests', 'oauth.redirectGuests', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'OpenAuth Enabled', 'oauth.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'OpenAuth Role Attribute', 'oauth.role.attribute', 'roles', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (24, 'OpenAuth Group Attribute', 'oauth.group.attribute', 'groups', 'text');

CREATE INDEX user_tokens_token_idx ON user_tokens(token);

CREATE TABLE oauth_tokens (
  token_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(user_id) NOT NULL,
  user_token_id BIGINT REFERENCES user_tokens(token_id) NOT NULL,
  provider VARCHAR(50) NOT NULL,
  access_token TEXT NOT NULL,
  token_type VARCHAR(100) NOT NULL,
  expires_in INTEGER DEFAULT NULL,
  refresh_token TEXT,
  refresh_expires_in INTEGER DEFAULT NULL,
  scope VARCHAR(100),
  expires TIMESTAMP(3),
  refresh_expires TIMESTAMP(3),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  enabled BOOLEAN DEFAULT true
);
CREATE INDEX oauth_user_id_idx ON oauth_tokens(user_id);
CREATE INDEX oauth_provider_idx ON oauth_tokens(provider);

ALTER TABLE lookup_role ADD oauth_path VARCHAR(255);
ALTER TABLE groups ADD oauth_path VARCHAR(255);
