-- Governed publish path (Project #6, Phase 1): the site-wide switch for mandatory review approval.
-- Default 'false' -- existing sites keep publishing directly, so this changes nothing on upgrade. An
-- ISSM turns it on as a conscious, auditable governance decision; when on, only an approved draft may
-- be published and there is no bypass (enforced in ContentReviewCommand.mayPublish / ContentHtmlCommand).
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (27, 'Require review approval before publishing content', 'content.review.required', 'false', 'boolean')
ON CONFLICT (property_name) DO NOTHING;
