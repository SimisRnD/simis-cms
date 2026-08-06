-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- The form_data table has never had a retention/cleanup job -- submissions can carry PII (whatever
-- fields the form collects) and rows stayed forever. Retention here only applies to rows that have
-- reached a terminal state (processed IS NOT NULL OR dismissed IS NOT NULL); rows still awaiting
-- review are never deleted, regardless of age, since they represent unactioned work an admin may
-- still need to see. Mirrors formData.failureRetentionDays (same default/bounds, see
-- FormDataRepository.resolveRetentionDays). Idempotent so it is safe on any existing install; fresh
-- installs get the identical row from NEW_10000.
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (14, 'Form data retention (days, terminal-state only)', 'formData.retentionDays', '90')
ON CONFLICT (property_name) DO NOTHING;
