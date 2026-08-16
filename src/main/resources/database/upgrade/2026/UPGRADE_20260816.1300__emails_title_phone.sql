-- Adds title (job title) and phone columns to emails, alongside the existing organization
-- column, so the newsletter subscribe form can collect this like other B2B signup forms do,
-- without needing a separate table.
ALTER TABLE emails ADD COLUMN IF NOT EXISTS title VARCHAR(150);
ALTER TABLE emails ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
