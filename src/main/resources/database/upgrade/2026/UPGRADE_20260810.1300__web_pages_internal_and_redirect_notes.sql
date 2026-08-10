-- issue #497 cheap-tier slice: "Hide Internal Pages" filter needs a real, settable signal -- WebPage
-- already had a role_id_list column that looked like it could serve this, but it turned out to be
-- persisted and never actually consulted anywhere for access control (dead field). This is a real
-- column instead. redirect_notes lets an admin document why a 301 exists, so "is this old URL still
-- needed or is it clutter?" (the issue's "Redirect Confusion" section) is no longer undocumented.
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS internal BOOLEAN DEFAULT false;
ALTER TABLE web_pages ADD COLUMN IF NOT EXISTS redirect_notes VARCHAR(500);
