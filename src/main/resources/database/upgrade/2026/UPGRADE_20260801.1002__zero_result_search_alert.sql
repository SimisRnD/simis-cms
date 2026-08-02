-- Issue #566: configurable alert threshold for the zero-result-search-count dashboard tile,
-- matching the mailing-list.quarantine.alertThresholdPercent precedent (see
-- UPGRADE_20260729.2000__mailing_list_quarantine.sql). This one is a raw count rather than a
-- percent -- a handful of zero-result searches a day is normal noise, so a rate doesn't make sense
-- against a low-volume denominator the way it does for mailing-list quarantine.
INSERT INTO site_properties (property_label, property_name, property_value, property_type)
VALUES ('Zero-Result Search Alert Threshold (count/24h)', 'search.zeroResultAlertThreshold', '20', 'text')
ON CONFLICT (property_name) DO NOTHING;
