-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Datasets

CREATE TABLE datasets (
  dataset_id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  filename VARCHAR(255) NOT NULL,
  path VARCHAR(255) NOT NULL,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  processed TIMESTAMP(3),
  processed_ms BIGINT DEFAULT 0,
  file_length BIGINT DEFAULT 0,
  row_count INTEGER DEFAULT 0,
  column_count INTEGER DEFAULT 0,
  file_type VARCHAR(50),
  collection_unique_id VARCHAR(255),
  rows_processed INTEGER DEFAULT 0,
  source_info TEXT,
  source_url VARCHAR(2048),
  column_config JSONB,
  category_id BIGINT DEFAULT NULL,
  records_path VARCHAR(255),
  scheduled_date TIMESTAMP,
  last_download TIMESTAMP,
  process_status INTEGER DEFAULT 0,
  process_message TEXT,
  schedule_enabled BOOLEAN DEFAULT false,
  schedule_frequency INTERVAL DEFAULT NULL,
  schedule_last_run TIMESTAMP(3),
  sync_enabled BOOLEAN DEFAULT false,
  sync_date TIMESTAMP(3),
  sync_status INTEGER DEFAULT 0,
  sync_message TEXT,
  sync_merge_type VARCHAR(20),
  unique_column_name VARCHAR(50),
  field_values JSONB,
  queue_status INTEGER DEFAULT 0,
  queue_date TIMESTAMP(3),
  queue_attempts INTEGER DEFAULT 0,
  queue_interval INTERVAL DEFAULT NULL,
  queue_message TEXT,
  paging_url_path VARCHAR(255)
);

CREATE INDEX datasets_sched_idx ON datasets(schedule_enabled);
CREATE INDEX datasets_sync_idx ON datasets(sync_enabled);
CREATE INDEX datasets_que_stat_idx ON datasets(queue_status);
