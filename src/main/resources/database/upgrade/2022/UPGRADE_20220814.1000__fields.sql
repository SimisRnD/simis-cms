ALTER TABLE collections ADD item_url_text VARCHAR(50);
ALTER TABLE categories ADD item_url_text VARCHAR(50);

ALTER TABLE datasets ADD record_count INTEGER DEFAULT 0;
ALTER TABLE datasets ADD sync_record_count INTEGER DEFAULT 0;
ALTER TABLE datasets ADD sync_add_count INTEGER DEFAULT 0;
ALTER TABLE datasets ADD sync_update_count INTEGER DEFAULT 0;
ALTER TABLE datasets ADD sync_delete_count INTEGER DEFAULT 0;
ALTER TABLE datasets ADD file_hash VARCHAR(1024);

CREATE TABLE dataset_sync_log (
  log_id BIGSERIAL PRIMARY KEY,
  dataset_id BIGINT REFERENCES datasets(dataset_id) NOT NULL,
  sync_date TIMESTAMP(3) NOT NULL,
  sync_add_count INTEGER DEFAULT 0,
  sync_update_count INTEGER DEFAULT 0,
  sync_delete_count INTEGER DEFAULT 0
);

CREATE INDEX dataset_sy_log_dt_idx ON dataset_sync_log(sync_date);
