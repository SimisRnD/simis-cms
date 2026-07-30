-- Org-level MFA enforcement (#297, IA-2(1) / 800-171 3.5.3 / OMB M-22-09).
--
-- mfa.required.roles  : comma-separated role codes whose members must enrol MFA before accessing
--                       the application. Default empty -- no enforcement; opt-in per deployment.
--                       Changing this value is audited by the site-properties audit trail
--                       (setting.update events), which is the SSP governance evidence for IA-2(1).
--
-- mfa.enrollment.url  : the page that non-enrolled users are redirected to. Must not itself
--                       require MFA or users will be locked out. Default /my-profile.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (28, 'Roles that must enrol in MFA (comma-separated)', 'mfa.required.roles', '', 'text')
ON CONFLICT (property_name) DO NOTHING;

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (29, 'MFA enrollment URL', 'mfa.enrollment.url', '/my-profile', 'web-page')
ON CONFLICT (property_name) DO NOTHING;
