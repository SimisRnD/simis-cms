-- Issue #455: the integration registry's one-click install/uninstall for a WEBHOOK_URL-type
-- integration (e.g. Slack) works by creating/removing a tagged webhook_subscription row.
-- integration_id records which registry integration created a row, so uninstall can find and
-- remove exactly the rows it created and no manually-created ones.
ALTER TABLE webhook_subscription ADD COLUMN integration_id VARCHAR(100);
CREATE INDEX webhook_subscription_integration_idx ON webhook_subscription(integration_id);

-- Issue #455: a Slack incoming-webhook URL is itself a bearer credential (anyone with it can post
-- to that channel) -- unlike a typical outbound webhook subscription's url, which is just a
-- destination the admin chooses. WebhookSubscriptionRepository now encrypts url the same way it
-- already encrypts secret (SecretCryptoCommand, AES-256-GCM, enc:-prefixed). VARCHAR(2000) is too
-- narrow for the encrypted (base64) form of a long url, so this widens the column to TEXT; existing
-- rows keep reading correctly as legacy plaintext (SecretCryptoCommand.decrypt returns a
-- non-enc:-prefixed value unchanged) and are encrypted the next time they're saved.
ALTER TABLE webhook_subscription ALTER COLUMN url TYPE TEXT;
