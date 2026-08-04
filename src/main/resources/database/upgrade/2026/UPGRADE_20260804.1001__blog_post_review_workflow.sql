-- Extends the governed publish workflow (Project #6 / content.review.required, then webPage.review.
-- required) to blog posts (issue #407, phase 2): draft_status moves draft -> submitted ->
-- (approved & published | rejected); submitted_by and approved_by name the two people the
-- separation-of-duties control keeps distinct; release_reference is the approval authority recorded
-- in the audit trail. Mirrors web_pages' own draft_status/submitted_by/approved_by/release_reference
-- columns exactly -- see UPGRADE_20260802.1008__web_page_review_workflow.sql and
-- ContentReviewCommand/Reviewable. As with that migration (and the original
-- UPGRADE_20260724.1002__content_review_workflow.sql), no submitted_at/approved_at columns are
-- added: these four columns are the current-state snapshot, and the durable who/when record is the
-- append-only audit trail.
ALTER TABLE blog_posts ADD COLUMN IF NOT EXISTS draft_status VARCHAR(20);
ALTER TABLE blog_posts ADD COLUMN IF NOT EXISTS submitted_by BIGINT DEFAULT -1;
ALTER TABLE blog_posts ADD COLUMN IF NOT EXISTS approved_by BIGINT DEFAULT -1;
ALTER TABLE blog_posts ADD COLUMN IF NOT EXISTS release_reference VARCHAR(255);

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (242, 'Require review approval to publish blog posts', 'blogPost.review.required', 'false', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
