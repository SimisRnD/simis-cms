-- Governed publish path (Project #6, Phase 1): the review-workflow state on a content draft.
-- draft_status moves draft -> submitted -> (approved & published | rejected); submitted_by and
-- approved_by name the two people the separation-of-duties control keeps distinct; release_reference
-- records the release authority ("cleared per case ..."). Defaults leave existing content in the
-- unsubmitted state, so nothing changes until content is submitted through the workflow. These are the
-- current-state snapshot; the durable who/when record is the append-only audit trail.
ALTER TABLE content ADD COLUMN IF NOT EXISTS draft_status VARCHAR(20);
ALTER TABLE content ADD COLUMN IF NOT EXISTS submitted_by BIGINT DEFAULT -1;
ALTER TABLE content ADD COLUMN IF NOT EXISTS approved_by BIGINT DEFAULT -1;
ALTER TABLE content ADD COLUMN IF NOT EXISTS release_reference VARCHAR(255);
