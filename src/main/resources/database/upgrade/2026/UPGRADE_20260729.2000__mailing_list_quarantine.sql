-- Automated mailing list hygiene (#564): quarantines (archives, never deletes) list memberships
-- whose linked email has a confirmed-bad deliverability classification (#574's validation_status),
-- instead of the manual-purge-only workflow that exists today.
--
-- quarantined:        when MailingListQuarantineJob flagged this membership, mirroring
--                      unsubscribed's own column shape on this same table. NULL means not
--                      quarantined.
-- quarantine_reason:   the triggering emails.validation_status value, stored verbatim (invalid,
--                      spamtrap, abuse, or do_not_mail). catch-all and unknown never trigger
--                      quarantine -- ZeroBounce itself isn't calling those undeliverable, just
--                      unresolved, so quarantining them would risk archiving real subscribers.
--
-- Quarantine also sets is_valid = false, exactly as unsubscribe() already does, so a quarantined
-- membership automatically drops out of MailingListMemberRepository.countActiveSubscribers()
-- with no change to that method. It deliberately does NOT touch the unsubscribed column --
-- quarantine is a distinct reason a membership stopped being active (the person never chose to
-- leave), so it must not be double-counted by countUnsubscribed() either.
ALTER TABLE mailing_list_members ADD COLUMN IF NOT EXISTS quarantined TIMESTAMP(3);
ALTER TABLE mailing_list_members ADD COLUMN IF NOT EXISTS quarantine_reason VARCHAR(50);

CREATE INDEX IF NOT EXISTS mail_lis_mem_quarantined_idx ON mailing_list_members(quarantined);

-- Configurable alert threshold for the quarantine-rate dashboard tile, matching the
-- audit.retentionDays configurable-threshold precedent (see AuditLogRepository.resolveRetentionDays).
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (26, 'Mailing List Quarantine Alert Threshold (%)', 'mailing-list.quarantine.alertThresholdPercent', '10', 'text')
ON CONFLICT (property_name) DO NOTHING;
