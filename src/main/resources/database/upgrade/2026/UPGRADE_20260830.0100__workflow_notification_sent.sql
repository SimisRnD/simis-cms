-- Records a workflow side effect that must happen at most once, so a replayed playbook cannot
-- repeat it. A workflow step claims a key here before acting; the claim is the INSERT, so two
-- attempts race on the primary key and exactly one wins.
--
-- Issue 1643: a false `when` guard reported the playbook FAILED, the enclosing JobRunr job retried
-- it whole, and the contact-form notification was sent twice on every submission. The retry cause
-- is fixed separately; this table is what makes a repeat send impossible rather than unlikely,
-- for that playbook and any future one.
CREATE TABLE workflow_notification_sent (
  notification_key VARCHAR(255) PRIMARY KEY NOT NULL,
  sent_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);
