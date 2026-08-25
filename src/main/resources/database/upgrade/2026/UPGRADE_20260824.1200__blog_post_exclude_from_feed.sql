-- Per-post syndication opt-out for blog posts.
--
-- Until now the only way to keep a post out of the RSS/Atom feed was to archive or unpublish it,
-- and both of those remove it from every public surface at once -- FeedServlet, BlogPostListWidget,
-- SitemapServlet and LlmsTxtServlet all filter on the same published/archived state. That is the
-- wrong instrument for "this post should stay on the site and stay searchable, it just should not
-- be pushed to subscribers": older announcements that are still legitimate reference material but
-- are not what a feed reader signed up for.
--
-- Defaults to false so every existing post keeps syndicating exactly as it does today; this is
-- purely additive and no backfill is required.
ALTER TABLE blog_posts
  ADD COLUMN IF NOT EXISTS exclude_from_feed BOOLEAN NOT NULL DEFAULT false;
