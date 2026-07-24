-- Stamps stored content with a format version so the visual editor's Quill Delta content is
-- distinguishable from legacy HTML on render (see DeltaContentCommand / ContentHtmlCommand.toHtml).
-- Two columns because a page mid-conversion has an HTML published version and a Delta draft at the
-- same time -- the governed publish path keeps the draft off the live version until it is approved.
-- Both default to 0 (legacy HTML), so an existing site renders exactly as it did; a row becomes
-- format 2 only when content is saved through the visual editor.
ALTER TABLE content ADD COLUMN IF NOT EXISTS content_format INTEGER NOT NULL DEFAULT 0;
ALTER TABLE content ADD COLUMN IF NOT EXISTS draft_content_format INTEGER NOT NULL DEFAULT 0;
