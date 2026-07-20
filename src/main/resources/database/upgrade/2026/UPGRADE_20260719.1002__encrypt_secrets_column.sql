-- Widen property_value so encrypted secret values fit. AES-256-GCM ciphertext, base64-encoded with the
-- "enc:" prefix, is roughly 1.4-1.8x the plaintext length, which can exceed the previous VARCHAR(255)
-- for longer integration tokens. TEXT has no practical limit and is a no-op for existing plaintext rows.
ALTER TABLE site_properties ALTER COLUMN property_value TYPE TEXT;
