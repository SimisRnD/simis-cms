
ALTER TABLE images ADD COLUMN web_path VARCHAR(50);
UPDATE images SET web_path = to_char(created, 'YYYYMMDDHH24MISS');
ALTER TABLE images ALTER COLUMN web_path SET NOT NULL;
CREATE INDEX images_web_path_idx ON images(web_path);

ALTER TABLE files ADD COLUMN web_path VARCHAR(50);
UPDATE files SET web_path = to_char(created, 'YYYYMMDDHH24MISS');
ALTER TABLE files ALTER COLUMN web_path SET NOT NULL;
CREATE INDEX files_web_path_idx ON files(web_path);

ALTER TABLE file_versions ADD COLUMN web_path VARCHAR(50);
UPDATE file_versions SET web_path = to_char(created, 'YYYYMMDDHH24MISS');
ALTER TABLE file_versions ALTER COLUMN web_path SET NOT NULL;
CREATE INDEX file_ver_web_path_idx ON file_versions(web_path);

ALTER TABLE item_files ADD COLUMN web_path VARCHAR(50);
UPDATE item_files SET web_path = to_char(created, 'YYYYMMDDHH24MISS');
ALTER TABLE item_files ALTER COLUMN web_path SET NOT NULL;
CREATE INDEX i_files_web_path_idx ON item_files(web_path);

ALTER TABLE item_file_versions ADD COLUMN web_path VARCHAR(50);
UPDATE item_file_versions SET web_path = to_char(created, 'YYYYMMDDHH24MISS');
ALTER TABLE item_file_versions ALTER COLUMN web_path SET NOT NULL;
CREATE INDEX i_file_ver_web_path_idx ON item_file_versions(web_path);

ALTER TABLE datasets ADD COLUMN web_path VARCHAR(50);
UPDATE datasets SET web_path = to_char(created, 'YYYYMMDDHH24MISS');
ALTER TABLE datasets ALTER COLUMN web_path SET NOT NULL;
CREATE INDEX datasets_web_path_idx ON datasets(web_path);
