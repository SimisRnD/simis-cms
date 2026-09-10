-- Issue #1688: turns the web_pages "internal" flag from a label into an access control.
--
-- Before this, ticking "Internal" on /admin/web-page filtered the admin list and drew a badge, and
-- restricted nobody. InternalPageAccessCommand now refuses an internal page unless the requester is
-- in the group named here (or in the content-editor tier, which always gets through so a
-- misconfiguration stays recoverable from the UI).
--
-- The value is a groups.unique_id, not a display name -- UserSession.hasGroup matches on unique_id.
--
-- EMPTY IS THE OFF SWITCH, and is what this upgrade seeds, so applying it changes nothing: every
-- internal page keeps behaving exactly as it did. The restriction begins only when an administrator
-- picks a group on /admin/security-properties. Blank must also stay *reachable* from a broken
-- state, which is why SitePropertiesEditorWidget accepts blank unconditionally while validating any
-- non-blank value.
--
-- The "security." prefix is load-bearing rather than cosmetic: it routes the field to
-- /admin/security-properties without an admin-layout.xml edit, and it puts saves behind step-up
-- re-authentication (SitePropertiesEditorWidget.java:67).
--
-- Version 20260831.1400 rather than .1000: two other open branches already claim .1000, and Flyway
-- refuses to run when two migrations share a version.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
-- Order 25 is the free slot between security.password.requireComplexity (20) and
-- security.iframe.allowedHosts (30), so this lands with the other page/content controls without
-- renumbering rows this migration has no business touching.
VALUES (25, 'Group allowed to view internal pages', 'security.internalPages.group', '', 'group')
ON CONFLICT (property_name) DO NOTHING;
