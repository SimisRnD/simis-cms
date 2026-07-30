-- Optional blog-to-mailing-list association (issue #599): each blog can optionally be tied to
-- one mailing list, so publishing a post can eventually notify just that list's subscribers.
-- ON DELETE SET NULL: deleting a mailing list must not be blocked by, or cascade into deleting,
-- an unrelated blog that happens to be associated with it.
ALTER TABLE blogs ADD COLUMN mailing_list_id BIGINT REFERENCES mailing_lists(list_id) ON DELETE SET NULL;
CREATE INDEX blogs_mailing_list_idx ON blogs(mailing_list_id);
