-- Create admin@example.com test user for testing authentication
-- Note: This uses a dummy password hash for testing; must be changed before production
INSERT INTO users (email, firstname, lastname, password, enabled, terms_agreed, validated)
VALUES ('admin@example.com', 'Admin', 'User', '$argon2id$v=19$m=19456,t=2,p=1$UqF7zXXo0hLCEu/3Gp7OFg$NpvXOdgrSGwzQvKsT0+5Sya6l4SZQWJl8jCgJwCkqBo', true, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;
