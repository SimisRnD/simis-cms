-- Issue #525: removes the unused "Web Conferencing Settings" (BigBlueButton) admin page and its
-- three site properties. Added June 2022 alongside the e-learning integrations, but nothing ever
-- consumed these values -- no meeting-launch code, no embedding, zero usage anywhere in the app.
DELETE FROM site_properties WHERE property_name IN (
  'conferencing.enabled',
  'conferencing.bbb.url',
  'conferencing.bbb.secret'
);
