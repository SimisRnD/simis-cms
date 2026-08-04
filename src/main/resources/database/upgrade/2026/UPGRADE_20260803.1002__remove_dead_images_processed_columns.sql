-- Issue #926: removes five columns from an abandoned early attempt at image-variant/processing
-- support. Nothing ever read or wrote them -- no Java, JSP, or reporting usage anywhere in the
-- app. (The table's other "processed"-named column, plain `processed`, is a separate, still-live
-- field and is not touched here.)
ALTER TABLE images DROP COLUMN processed_path;
ALTER TABLE images DROP COLUMN processed_file_length;
ALTER TABLE images DROP COLUMN processed_file_type;
ALTER TABLE images DROP COLUMN processed_width;
ALTER TABLE images DROP COLUMN processed_height;
