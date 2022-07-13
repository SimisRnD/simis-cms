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
  schedule_type INTEGER DEFAULT NULL,
  scheduled_date TIMESTAMP,
  last_download TIMESTAMP,
  download_status INTEGER DEFAULT 0,
  process_status INTEGER DEFAULT 0,
  process_message TEXT,
  schedule_enabled BOOLEAN DEFAULT false,
  schedule_frequency INTERVAL DEFAULT '1D',
  schedule_time TIME DEFAULT '03:00',
  schedule_last_run TIMESTAMP(3),
  sync_enabled BOOLEAN DEFAULT false,
  sync_date TIMESTAMP(3),
  sync_status INTEGER DEFAULT 0,
  sync_message TEXT
);

CREATE INDEX datasets_sched_idx ON datasets(schedule_enabled);
CREATE INDEX datasets_sync_idx ON datasets(sync_enabled);
