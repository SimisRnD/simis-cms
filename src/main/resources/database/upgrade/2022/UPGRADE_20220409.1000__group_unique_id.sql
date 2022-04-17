
ALTER TABLE groups ADD unique_id VARCHAR(255);
CREATE INDEX groups_unique_id ON groups(unique_id);
UPDATE groups SET unique_id = 'users' WHERE name = 'All Users';
