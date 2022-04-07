-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Internal Experience API (xAPI)

CREATE TABLE xapi_statements (
  statement_id BIGSERIAL PRIMARY KEY,
  message VARCHAR(512),
  message_snapshot VARCHAR(512),
  actor_id BIGINT REFERENCES users(user_id),
  verb VARCHAR(50) NOT NULL,
  object VARCHAR(50) NOT NULL,
  object_id BIGINT,
  occurred_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  authority VARCHAR(255),
  user_context BIGINT REFERENCES users(user_id),
  item_context BIGINT REFERENCES items(item_id),
  project_context BIGINT REFERENCES projects(project_id),
  issue_context BIGINT REFERENCES issues(issue_id),
  category_context VARCHAR(50),
  other_context VARCHAR(50),
  result VARCHAR(255),
  parent_statement_id BIGINT,
  registration_code VARCHAR(50)
);

CREATE INDEX xapi_stmt_created ON xapi_statements(created);
CREATE INDEX xapi_stmt_occurred ON xapi_statements(occurred_at);
CREATE INDEX xapi_stmt_reg_code ON xapi_statements(registration_code);

CREATE TABLE xapi_storage (
  id BIGSERIAL PRIMARY KEY,
  raw_statement JSONB,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  statement_id BIGINT REFERENCES xapi_statements(statement_id),
  processed TIMESTAMP(3)
);

CREATE INDEX xapi_storage_processed ON xapi_storage(processed);
