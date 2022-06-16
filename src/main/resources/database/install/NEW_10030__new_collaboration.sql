-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Collaboration

CREATE TABLE lookup_collection_role (
  role_id SERIAL PRIMARY KEY,
  collection_id BIGINT REFERENCES collections(collection_id),
  code VARCHAR(20) NOT NULL,
  title VARCHAR(100),
  archived TIMESTAMP(3),
  level INTEGER NOT NULL
);
CREATE UNIQUE INDEX lookup_coll_role_uni_idx ON lookup_collection_role(code, collection_id);

-- Admins, Managers, Participants, Guests, etc.
--
INSERT INTO lookup_collection_role (level, code, title) VALUES (10, 'admin', 'Admin');
INSERT INTO lookup_collection_role (level, code, title) VALUES (20, 'owner', 'Owner');
INSERT INTO lookup_collection_role (level, code, title) VALUES (30, 'manager', 'Manager');
INSERT INTO lookup_collection_role (level, code, title) VALUES (34, 'supervisor', 'Supervisor');
INSERT INTO lookup_collection_role (level, code, title) VALUES (38, 'instructor', 'Instructor');
INSERT INTO lookup_collection_role (level, code, title) VALUES (40, 'employee', 'Employee');
INSERT INTO lookup_collection_role (level, code, title) VALUES (50, 'moderator', 'Moderator');
INSERT INTO lookup_collection_role (level, code, title) VALUES (60, 'expert', 'Expert');
INSERT INTO lookup_collection_role (level, code, title) VALUES (70, 'assigned', 'Assigned');
INSERT INTO lookup_collection_role (level, code, title) VALUES (80, 'vip', 'VIP');
INSERT INTO lookup_collection_role (level, code, title) VALUES (84, 'learner', 'Learner');
INSERT INTO lookup_collection_role (level, code, title) VALUES (90, 'member', 'Member');
INSERT INTO lookup_collection_role (level, code, title) VALUES (100, 'guest', 'Guest');

CREATE TABLE members (
  member_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(user_id) NOT NULL,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  requested TIMESTAMP(3),
  approved_by BIGINT REFERENCES users(user_id),
  approved TIMESTAMP(3),
  archived_by BIGINT REFERENCES users(user_id),
  archived TIMESTAMP(3),
  last_viewed TIMESTAMP(3)
);
CREATE UNIQUE INDEX members_unique_idx ON members (user_id, item_id);
CREATE INDEX members_user_id_idx ON members(user_id);
CREATE INDEX members_item_id_idx ON members(item_id);
CREATE INDEX members_coll_id_idx ON members(collection_id);
CREATE INDEX members_request_idx ON members(requested);
CREATE INDEX members_approve_idx ON members(approved);
CREATE INDEX members_archive_idx ON members(archived);

CREATE TABLE member_roles (
  member_role_id BIGSERIAL PRIMARY KEY,
  member_id BIGINT REFERENCES members(member_id) NOT NULL,
  role_id INTEGER REFERENCES lookup_collection_role(role_id) NOT NULL,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  user_id BIGINT REFERENCES users(user_id) NOT NULL,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX member_role_uni_idx ON member_roles (member_id, role_id);
CREATE INDEX member_roles_memb_idx ON member_roles(member_id);
CREATE INDEX member_roles_item_idx ON member_roles(item_id);
CREATE INDEX member_roles_user_idx ON member_roles(user_id);
