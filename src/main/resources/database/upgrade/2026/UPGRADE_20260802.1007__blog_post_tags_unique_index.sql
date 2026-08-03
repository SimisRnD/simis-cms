-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Issue #633: blog_post_tags has existed since NEW_10010__new_cms.sql but was never wired up by
-- BlogPostRepository, so it has always been empty in production -- safe to index now. Without this,
-- a repeated/duplicated tag-assignment save (double submit, retried request) could insert the same
-- (post_id, tag_id) pair more than once; the application reconciliation logic in BlogPostRepository
-- is diff-based and therefore idempotent on its own, but this index is a database-level backstop
-- against a genuine race between two concurrent saves, matching item_tags_uidx's role for the
-- equivalent items tag table (issue #632).
CREATE UNIQUE INDEX IF NOT EXISTS blog_post_tags_uidx ON blog_post_tags(post_id, tag_id);
