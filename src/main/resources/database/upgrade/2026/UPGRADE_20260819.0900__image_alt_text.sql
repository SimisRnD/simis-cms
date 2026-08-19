-- Alt text for image library assets. Library-only for this pass -- editable in the admin Media
-- Library (/admin/images), not yet wired into any public-facing <img> rendering, since the entities
-- that render Image-Library-backed images (Item/BlogPost/Product, etc.) currently store only a
-- flat imageUrl string, not an image_id reference back to this table. See the alt-text modal's own
-- note for why. Nullable -- most images will start with no alt text set.
ALTER TABLE images ADD COLUMN IF NOT EXISTS alt_text VARCHAR(255);
