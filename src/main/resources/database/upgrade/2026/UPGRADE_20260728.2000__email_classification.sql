-- Email deliverability classification (#574): stores the result of a per-address validation
-- call (e.g. ZeroBounce) directly on emails, not on mailing_list_members, because deliverability
-- is a property of the address itself, not of any one list membership -- emails.email is already
-- UNIQUE, giving exactly the granularity a vendor classifies at.
--
-- validation_status:     the vendor's primary classification, stored verbatim
--                         (e.g. valid, invalid, catch-all, unknown, spamtrap, abuse, do_not_mail).
-- validation_sub_status: the vendor's finer-grained reason code, stored verbatim, blank when the
--                         vendor has none to report.
-- validated_at:          when the address was last run through validation -- a "last checked"
--                         marker, not a "last known-good" marker. NULL means never validated,
--                         which is how the classification job finds its backlog.
--
-- Deliberately vendor-neutral column names (not "zerobounce_status") to match sync_date's own
-- precedent on this table, which stayed generic despite only serving MailChimp so far.
--
-- Feeds #562 (flagged as spam/bounced breakdown) and unblocks #564 (mailing list hygiene).
-- Idempotent so it is safe to re-run; fresh installs get the identical columns from NEW_10070
-- instead.
ALTER TABLE emails ADD COLUMN IF NOT EXISTS validation_status VARCHAR(20);
ALTER TABLE emails ADD COLUMN IF NOT EXISTS validation_sub_status VARCHAR(50);
ALTER TABLE emails ADD COLUMN IF NOT EXISTS validated_at TIMESTAMP(3);

CREATE INDEX IF NOT EXISTS emails_validation_status_idx ON emails(validation_status);

-- ZeroBounce API key, following the exact shape of the MailChimp API key row it sits beside in
-- NEW_10000. Encrypted at rest -- see SecretSitePropertiesCommand.SECRET_PROPERTY_NAMES, which
-- gets the matching 'mailing-list.zerobounce.apiKey' entry in this same change.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (24, 'ZeroBounce API Key', 'mailing-list.zerobounce.apiKey', '', 'text')
ON CONFLICT (property_name) DO NOTHING;
