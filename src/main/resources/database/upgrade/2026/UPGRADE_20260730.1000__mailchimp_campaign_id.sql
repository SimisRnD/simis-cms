-- Issue #600 rework: send a newsletter as a real MailChimp Campaign (instead of only the local
-- SMTP send queue) when MailChimp is the configured mailing-list service. mailing_list_history
-- already records one row per send batch; this stores which MailChimp campaign it corresponds to.
ALTER TABLE mailing_list_history ADD COLUMN mailchimp_campaign_id VARCHAR(50);
