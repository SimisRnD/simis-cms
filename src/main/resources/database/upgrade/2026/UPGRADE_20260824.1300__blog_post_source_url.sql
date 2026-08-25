-- Curated link posts: an optional original-article URL for a blog post.
--
-- A curation feed is a list of other people's articles with a sentence or two of commentary. The
-- reader wants the article, so the headline should reach it in one click rather than landing on a
-- stub page that only holds the commentary and another link. The same applies to the feed: an
-- Atom entry's rel="alternate" is what a reader opens, and pointing it at a stub makes a
-- link-feed useless to subscribers.
--
-- The post keeps its own permalink regardless. That permalink stays the feed entry's <id>, which
-- must be stable and unique: deriving <id> from source_url instead would collide whenever two
-- posts cite the same article, and would change identity if a source URL were ever corrected --
-- both of which make feed readers either drop entries or re-notify for old ones.
--
-- NULL means "behave exactly as before", so every existing post is unaffected and no backfill is
-- needed. Values are restricted to http(s) at save time (UrlCommand.isUrlValid) and sanitized
-- again at render, since the value is placed into an href.
ALTER TABLE blog_posts
  ADD COLUMN IF NOT EXISTS source_url VARCHAR(512);
