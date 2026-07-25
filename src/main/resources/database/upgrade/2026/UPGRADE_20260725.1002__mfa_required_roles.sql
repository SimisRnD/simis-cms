-- Org-level MFA enforcement site setting (POA&M IA-2(1) / 800-171 3.5.3).
-- A comma-separated list of role codes whose members must have TOTP MFA enrolled before
-- reaching any page. Empty (the default) means off -- non-breaking opt-in per deployment,
-- matching the same rollout shape as content.review.required.
-- Enabling this property via the admin settings page (or SQL) is itself audited by
-- SitePropertiesEditorWidget as 'setting.update' in the audit trail.
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (1, 'Roles required to use MFA (comma-separated codes, e.g. admin,content-manager)', 'mfa.required.roles', '')
ON CONFLICT (property_name) DO NOTHING;
