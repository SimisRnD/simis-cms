-- The visual editor's guardrailed content-author tier (builder-vs-editor split, Project #6). A
-- content-editor may edit content but not change page layout/structure -- that stays with the builder
-- tier (admin, content-manager). Level 70 places it just below content-manager (80) in the role
-- hierarchy. The capability is closed-by-default in code (EditorPermissionCommand): holding the role
-- grants content editing; its absence denies it. Idempotent -- lookup_role has no unique key on code.
INSERT INTO lookup_role (level, code, title)
SELECT 70, 'content-editor', 'Content Editor'
WHERE NOT EXISTS (SELECT 1 FROM lookup_role WHERE code = 'content-editor');
