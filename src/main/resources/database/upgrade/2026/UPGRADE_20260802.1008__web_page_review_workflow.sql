-- Extends the governed publish workflow (Project #6 / content.review.required) to web pages
-- (issue #407): draft_status moves draft -> submitted -> (approved & published | rejected);
-- submitted_by and approved_by name the two people the separation-of-duties control keeps
-- distinct; release_reference is the approval authority recorded in the audit trail. Mirrors
-- content's own draft_status/submitted_by/approved_by/release_reference columns exactly -- see
-- UPGRADE_20260724.1002__content_review_workflow.sql and ContentReviewCommand/Reviewable.
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS draft_status VARCHAR(20);
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS submitted_by BIGINT DEFAULT -1;
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS approved_by BIGINT DEFAULT -1;
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS release_reference VARCHAR(255);

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (241, 'Require review approval to publish web pages', 'webPage.review.required', 'false', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
