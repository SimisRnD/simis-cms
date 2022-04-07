-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Projects

-- Basic Kanban, Idea Management, Issue Triage
CREATE TABLE default_project_types (
  type_id SERIAL PRIMARY KEY,
  title VARCHAR(100) NOT NULL
);

-- Backlog, To Do, In Progress, Code Review, Awaiting QA, Ready to Merge, Done
CREATE TABLE default_project_status (
  status_id SERIAL PRIMARY KEY,
  type_id INTEGER REFERENCES default_project_types(type_id),
  title VARCHAR(100) NOT NULL,
  status_type VARCHAR(30),
  level INTEGER NOT NULL
);

-- Label Name, Description, Color, Count
-- Ex: standard, expedite, fixed delivery date
CREATE TABLE lookup_project_card_labels (
  label_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  level INTEGER NOT NULL,
  color VARCHAR(6),
  card_count INTEGER DEFAULT 0
);
CREATE INDEX lookup_prj_cd_lbl_it_id_idx ON lookup_project_card_labels(item_id);

CREATE TABLE projects (
  project_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id),
  project_unique_id VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  archived_by BIGINT REFERENCES users(user_id),
  archive_date TIMESTAMP(3),
  enabled BOOLEAN DEFAULT true,
  status_message VARCHAR(512),
  status_data JSONB,
  card_count INTEGER DEFAULT 0
);
CREATE INDEX projects_item_id_idx ON projects(item_id);

CREATE TABLE project_milestones (
  milestone_id BIGSERIAL PRIMARY KEY,
  project_id BIGINT REFERENCES projects(project_id) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  order_id INTEGER DEFAULT 1,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  archived_by BIGINT REFERENCES users(user_id),
  archive_date TIMESTAMP(3),
  start_date TIMESTAMP(3),
  end_date TIMESTAMP(3),
  enabled BOOLEAN DEFAULT true,
  card_count INTEGER DEFAULT 0
);
CREATE INDEX project_mile_prj_idx ON project_milestones(project_id);

CREATE TABLE project_status (
  status_id BIGSERIAL PRIMARY KEY,
  project_id BIGINT REFERENCES projects(project_id) NOT NULL,
  name VARCHAR(255) NOT NULL,
  order_id INTEGER DEFAULT 1,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  archived_by BIGINT REFERENCES users(user_id),
  archive_date TIMESTAMP(3),
  enabled BOOLEAN DEFAULT true,
  status_type INTEGER,
  card_count INTEGER DEFAULT 0
);
CREATE INDEX project_col_prj_idx ON project_status(project_id);

CREATE TABLE project_cards (
  card_id BIGSERIAL PRIMARY KEY,
  project_id BIGINT REFERENCES projects(project_id) NOT NULL,
  status_id BIGINT REFERENCES project_status(status_id) NOT NULL,
  milestone_id BIGINT REFERENCES project_milestones(milestone_id),
  content TEXT NOT NULL,
  order_id INTEGER DEFAULT 1,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  archived_by BIGINT REFERENCES users(user_id),
  archive_date TIMESTAMP(3),
  enabled BOOLEAN DEFAULT true,
  card_type INTEGER,
  link_id BIGINT,
  estimated_points INTEGER DEFAULT 0,
  points_remaining INTEGER DEFAULT 0,
  labels JSONB,
  due_date TIMESTAMP(3)
);
CREATE INDEX project_cards_prj_idx ON project_cards(project_id);
CREATE INDEX project_cards_sta_idx ON project_cards(status_id);
CREATE INDEX project_cards_mil_idx ON project_cards(milestone_id);

-- Matt added this to...
-- Matt moved this from ... to ...
--
CREATE TABLE project_card_history (
  history_id BIGSERIAL PRIMARY KEY,
  project_id BIGINT REFERENCES projects(project_id) NOT NULL,
  card_id BIGINT REFERENCES project_cards(card_id) NOT NULL,
  status_id BIGINT REFERENCES project_status(status_id) NOT NULL,
  milestone_id BIGINT REFERENCES project_milestones(milestone_id),
  start_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  end_date TIMESTAMP(3),
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX project_ca_his_prj_idx ON project_card_history(project_id);
CREATE INDEX project_ca_his_car_idx ON project_card_history(card_id);

CREATE TABLE project_card_comments (
  comment_id BIGSERIAL PRIMARY KEY,
  project_id BIGINT REFERENCES projects(project_id) NOT NULL,
  card_id BIGINT REFERENCES project_cards(card_id) NOT NULL,
  message_text TEXT NOT NULL,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) DEFAULT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX project_ca_com_prj_idx ON project_card_comments(project_id);
CREATE INDEX project_ca_com_car_idx ON project_card_comments(card_id);

CREATE TABLE project_card_assignments (
  assignment_id BIGSERIAL PRIMARY KEY,
  project_id BIGINT REFERENCES projects(project_id) NOT NULL,
  card_id BIGINT REFERENCES project_cards(card_id) NOT NULL,
  assigned_to BIGINT REFERENCES users(user_id),
  assigned_by BIGINT REFERENCES users(user_id),
  assigned TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  archived_by BIGINT REFERENCES users(user_id),
  archive_date TIMESTAMP(3),
  enabled BOOLEAN DEFAULT true
);
CREATE INDEX project_ca_asgn_car_idx ON project_card_assignments(card_id);

CREATE TABLE project_card_labels (
  project_card_label_id BIGSERIAL PRIMARY KEY,
  project_id BIGINT REFERENCES projects(project_id) NOT NULL,
  card_id BIGINT REFERENCES project_cards(card_id) NOT NULL,
  label_id BIGINT REFERENCES lookup_project_card_labels(label_id) NOT NULL,
  created_by BIGINT REFERENCES users(user_id) DEFAULT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX project_ca_label_car_id_idx ON project_card_labels(card_id);
