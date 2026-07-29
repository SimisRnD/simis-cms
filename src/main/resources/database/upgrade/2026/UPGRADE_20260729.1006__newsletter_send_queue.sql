-- Issues #600/#500: an actual "send" mechanism for the newsletter -- mailing_list_history and
-- mailing_list_sent have existed since the original 2019 design (NEW_10070__new_mailing_lists.sql)
-- but nothing has ever written to either table. This extends them to serve as the send queue:
-- mailing_list_history becomes the batch header (one row per send), mailing_list_sent becomes the
-- per-recipient queue/log row (one row per member of that batch, tracked from queued through sent
-- or failed) rather than only ever representing an already-completed send.

ALTER TABLE mailing_list_history ADD COLUMN IF NOT EXISTS subject VARCHAR(255);
ALTER TABLE mailing_list_history ADD COLUMN IF NOT EXISTS blog_post_id BIGINT REFERENCES blog_posts(post_id);

ALTER TABLE mailing_list_sent ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'queued';
ALTER TABLE mailing_list_sent ADD COLUMN IF NOT EXISTS attempt_count INTEGER DEFAULT 0;
ALTER TABLE mailing_list_sent ADD COLUMN IF NOT EXISTS error_message VARCHAR(500);
ALTER TABLE mailing_list_sent ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP(3);
ALTER TABLE mailing_list_sent ADD COLUMN IF NOT EXISTS modified TIMESTAMP(3);

CREATE INDEX IF NOT EXISTS mail_list_sent_status_idx ON mailing_list_sent(status);

-- Single-use, per-member unsubscribe link (mutate-on-GET, no login required -- see
-- UserRepository.account_token for the analogous pattern). Scoped to the membership row, not the
-- email address, since unsubscribe is naturally per-list.
ALTER TABLE mailing_list_members ADD COLUMN IF NOT EXISTS unsubscribe_token VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS mail_lis_mem_unsub_tok_idx ON mailing_list_members(unsubscribe_token);
