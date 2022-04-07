-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Issues

CREATE TABLE default_issue_labels (
  label_id SERIAL PRIMARY KEY,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  level INTEGER NOT NULL,
  color VARCHAR(6)
);

-- Label Name, Description, Color, Count
CREATE TABLE lookup_item_issue_labels (
  label_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  level INTEGER NOT NULL,
  color VARCHAR(6),
  issue_count INTEGER DEFAULT 0
);
CREATE INDEX lookup_it_is_lbl_it_id_idx ON lookup_item_issue_labels(item_id);

CREATE TABLE issues (
  issue_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  item_issue_number INTEGER NOT NULL,
  name VARCHAR(255) NOT NULL,
  message_text TEXT NOT NULL,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  archived_by BIGINT REFERENCES users(user_id),
  archive_date TIMESTAMP(3),
  enabled BOOLEAN DEFAULT true,
  status VARCHAR(512),
  activity_count INTEGER DEFAULT 0,
  comment_count INTEGER DEFAULT 0,
  assigned_to JSONB,
  labels JSONB,
  due_date TIMESTAMP(3)
  -- approved by; resolution date
);
CREATE INDEX issues_item_id_idx ON issues(item_id);
CREATE INDEX issues_item_is_num_idx ON issues(item_id, item_issue_number);

-- Assigned to X, Self-assigned, Added label, Removed label, Closed
CREATE TABLE issue_history (
  history_id BIGSERIAL PRIMARY KEY,
  issue_id BIGINT REFERENCES issues(issue_id) NOT NULL,
  activity_type VARCHAR(50) NOT NULL,
  event_text VARCHAR(254) NOT NULL,
  created_by BIGINT REFERENCES users(user_id) DEFAULT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
  --  has_attachment BOOLEAN DEFAULT false,
  --  has_reactions BOOLEAN DEFAULT false,
  --  reaction_count INTEGER DEFAULT 0
);
CREATE INDEX issue_his_iss_id_idx ON issue_history(issue_id);

-- {createdBy} self-assigned this {created}
-- {createdBy} added the [{label:10}] label {created}
-- {createdBy} closed this {created}
-- [rajkowski](/rajkowski) self-assigned this [2 minutes ago](#event-2144252065)
-- [rajkowski](/rajkowski) added the [bug](/Group/cary-connects-mobile/labels/bug)  label [2 minutes ago](#event-2144252179)
-- [<img alt="@rajkowski" width="16" height="16" src="https://avatars1.githubusercontent.com/u/10373492?s=60&amp;v=4">](/rajkowski) [rajkowski](/rajkowski) closed this [just now](#event-2144253312)

CREATE TABLE issue_comments (
  comment_id BIGSERIAL PRIMARY KEY,
  issue_id BIGINT REFERENCES issues(issue_id) NOT NULL,
  message_text TEXT NOT NULL,
  created_by BIGINT REFERENCES users(user_id) DEFAULT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) DEFAULT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
  --  has_attachment BOOLEAN DEFAULT false,
  --  has_reactions BOOLEAN DEFAULT false,
  --  reaction_count INTEGER DEFAULT 0
);
CREATE INDEX issue_comm_iss_id_idx ON issue_comments(issue_id);

CREATE TABLE issue_assignments (
  assignment_id BIGSERIAL PRIMARY KEY,
  issue_id BIGINT REFERENCES issues(issue_id) NOT NULL,
  assigned_to BIGINT REFERENCES users(user_id),
  assigned_by BIGINT REFERENCES users(user_id),
  assigned TIMESTAMP(3)
);
CREATE INDEX issue_asgn_iss_id_idx ON issue_assignments(issue_id);

CREATE TABLE issue_labels (
  issue_label_id BIGSERIAL PRIMARY KEY,
  issue_id BIGINT REFERENCES issues(issue_id) NOT NULL,
  label_id BIGINT REFERENCES lookup_item_issue_labels(label_id) NOT NULL,
  created_by BIGINT REFERENCES users(user_id) DEFAULT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX issue_label_iss_id_idx ON issue_labels(issue_id);
