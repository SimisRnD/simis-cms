-- Same share_image_url column as NEW_10010__new_cms.sql, for databases that already exist.
--
-- A post has one image today, and it does three jobs it cannot do equally well: the banner on the
-- post, the thumbnail in list views, and the og:image/twitter:image a link preview renders. Those
-- want different shapes. Banners are authored at whatever suits the content, while a social card is
-- 1.91:1 -- so a tall poster shared to LinkedIn or X loses most of its height to the crop. Measured
-- on the pilot before this change: two event graphics at 0.80:1 lost 58% of their height, taking
-- the event name and the date with it.
--
-- Nullable with no default and no backfill. Every consumer falls back to image_url, so existing
-- posts render exactly as they did and the column only changes anything once an editor sets one.

ALTER TABLE blog_posts ADD COLUMN IF NOT EXISTS share_image_url VARCHAR(255);
