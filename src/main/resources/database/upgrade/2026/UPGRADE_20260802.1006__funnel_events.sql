-- Conversion funnel events (issue #565, phase 1): one row per stage event, e.g. a contact-form page
-- view, a successful submission, or an admin marking a submission processed. funnel_key names the
-- logical funnel ('contact-form' for this phase) so later phases (newsletter signup, solution-page
-- engagement) can reuse this same table with a different funnel_key/stage set -- no schema change.
-- Deliberately a raw event log, not a pre-aggregated daily-counts table: recording is a single-row
-- insert (mirrors search_analytics/form_submission_failures), and session_id is kept so a later phase
-- can attempt same-session stage correlation without a migration, even though phase 1's own report
-- only needs simple per-stage COUNT(*) totals, not per-visitor stitching.
-- Idempotent so it is safe on any existing install; fresh installs get the identical table from NEW_10010.
CREATE TABLE IF NOT EXISTS funnel_events (
  funnel_event_id BIGSERIAL PRIMARY KEY,
  funnel_key VARCHAR(50) NOT NULL,
  stage VARCHAR(30) NOT NULL,
  session_id VARCHAR(255),
  occurred TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS funnel_events_key_stage_idx ON funnel_events(funnel_key, stage);
CREATE INDEX IF NOT EXISTS funnel_events_occurred_idx ON funnel_events(occurred);

-- Pairs a page path and a formUniqueId to instrument as the contact-form funnel (view -> submitted ->
-- processed). Blank by default -- every site names its contact page/form differently, so recording
-- must stay off until an admin opts in with real values, the same way the pre-existing #563
-- conversion-rate tile ships commented out until configured.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (1, 'Contact Form Funnel: Page Path', 'funnel.contactForm.pagePath', '', 'text')
ON CONFLICT (property_name) DO NOTHING;
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (2, 'Contact Form Funnel: Form Unique ID', 'funnel.contactForm.formUniqueId', '', 'text')
ON CONFLICT (property_name) DO NOTHING;

-- Retention window for FunnelEventRetentionJob, mirroring formData.failureRetentionDays -- this is
-- anonymous-traffic telemetry with unbounded growth potential once a site opts in above, not a
-- compliance-grade evidentiary trail (see FunnelEventRepository.deleteOlderThan).
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (3, 'Funnel Event Retention (days)', 'funnel.retentionDays', '90', 'text')
ON CONFLICT (property_name) DO NOTHING;
