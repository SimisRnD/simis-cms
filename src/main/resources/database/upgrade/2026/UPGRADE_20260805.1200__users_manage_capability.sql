-- Adds a dedicated "users:manage" capability for /admin/users and its sibling Users/Groups pages
-- (/admin/user-details, /admin/unsuspend-requests, /admin/modify-user, /admin/groups,
-- /admin/group). Issue #733 wired the page-level capability="..." gate onto 3 admin:manage pages
-- only; the Users/Groups admin surface was left reachable by legacy role="..." alone, with no
-- hasPermission() path at all. Deliberately its own capability code, not admin:manage or
-- community:manage, scoped specifically to user-management access rather than bundled with
-- unrelated features those capabilities also gate.
INSERT INTO capabilities (code, category, description) VALUES
  ('users:manage', 'users', 'Manage user accounts, user groups, and unsuspend requests');

-- admin: keep the capabilities table a complete matrix, matching every other capability (#701's
-- "admin implicitly has every capability" pattern). Not a behavior change - admin already reaches
-- every one of these pages via its role="admin" attribute regardless of this row.
INSERT INTO role_capabilities (role_id, capability_id)
SELECT lr.role_id, c.capability_id
FROM lookup_role lr, capabilities c
WHERE lr.code = 'admin' AND c.code = 'users:manage';

-- No community-manager row: that role's existing role="admin,community-manager" attribute
-- already covers /admin/users, /admin/user-details, /admin/unsuspend-requests, and
-- /admin/modify-user, but NOT /admin/groups or /admin/group (role="admin" only). Mapping
-- community-manager to this single capability would silently widen its access to the Groups
-- pages once capability="users:manage" is added there - this migration only adds a new,
-- independently grantable capability, it does not change what any existing role can reach.
