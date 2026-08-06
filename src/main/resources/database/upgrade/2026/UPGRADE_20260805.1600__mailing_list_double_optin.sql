-- Double opt-in for mailing list signups: no path today confirms that the address owner actually
-- requested the subscription -- the public signup form, the ajax signup endpoint, and the
-- checkout newsletter checkbox all activate a membership immediately on nothing but a bare
-- typed-in address, with no verification step at all.
--
-- confirmed:              when the address owner clicked the confirm link. NULL means still
--                          pending (or the membership bypassed confirmation entirely, e.g. CSV
--                          import / admin manual-add -- see MailingListConfirmationCommand).
-- confirm_token:           single-use link token, mirroring unsubscribe_token's shape. Cleared on
--                          confirmation (MailingListMemberRepository.confirmByToken()).
-- confirm_token_expires:   matches UserRepository.account_token_expires' expiry-checked-in-SQL
--                          pattern (see findByConfirmToken()) so a stale link stops working
--                          instead of remaining valid forever.
ALTER TABLE mailing_list_members ADD COLUMN IF NOT EXISTS confirmed TIMESTAMP(3);
ALTER TABLE mailing_list_members ADD COLUMN IF NOT EXISTS confirm_token VARCHAR(255);
ALTER TABLE mailing_list_members ADD COLUMN IF NOT EXISTS confirm_token_expires TIMESTAMP(3);

CREATE UNIQUE INDEX IF NOT EXISTS mail_lis_mem_confirm_tok_idx ON mailing_list_members(confirm_token);

-- Configurable confirm-link expiry, matching the mailing-list.quarantine.alertThresholdPercent /
-- formData.failureRetentionDays configurable-default precedent.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (27, 'Mailing List Confirmation Link Expiry (days)', 'mailing-list.confirmation.expiryDays', '7', 'text')
ON CONFLICT (property_name) DO NOTHING;
