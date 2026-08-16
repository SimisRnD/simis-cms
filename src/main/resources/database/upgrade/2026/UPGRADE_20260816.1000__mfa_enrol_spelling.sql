-- Standardize "enrol"/"enrolment" (British) to "enroll"/"enrollment" (American), matching the
-- spelling used everywhere else in the platform (issue reported directly against the live
-- MFA Enforcement Settings page). The label read "Roles that must enrol in MFA" while its
-- sibling field on the same page read "MFA enrollment URL" -- an inconsistency visible on one
-- screen. Same reasoning as UPGRADE_20260814.1500__sentence_case_property_labels.sql: display-only,
-- unconditional on property_name, matches the same change to the install seed.

UPDATE site_properties SET property_label = 'Roles that must enroll in MFA (comma-separated)' WHERE property_name = 'mfa.required.roles';
