-- Content hash for duplicate detection. Reuses the same "ALGORITHM;hexdigest" format already used
-- by files.file_hash / item_files.file_hash / datasets.file_hash (FileSystemCommand.getFileChecksum())
-- rather than a second hashing convention. Nullable: existing rows are unhashed until backfilled via
-- the admin-triggered "Scan for Duplicates" action (see ScanForDuplicateImagesCommand) -- deliberately
-- not an automatic startup migration, since hashing every existing image's bytes off disk could
-- exceed DatabaseCommand's migration lock window on a large library.
ALTER TABLE images ADD COLUMN IF NOT EXISTS file_hash VARCHAR(1024);
CREATE INDEX IF NOT EXISTS images_file_hash_idx ON images(file_hash);
