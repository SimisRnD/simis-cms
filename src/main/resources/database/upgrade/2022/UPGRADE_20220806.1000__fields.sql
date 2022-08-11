ALTER TABLE datasets DROP COLUMN schedule_type;
ALTER TABLE datasets ADD sync_merge_type VARCHAR(20);
ALTER TABLE datasets ADD unique_column_name VARCHAR(50);
ALTER TABLE datasets ADD field_values JSONB;
ALTER TABLE datasets ADD queue_status INTEGER DEFAULT 0;
ALTER TABLE datasets ADD queue_date TIMESTAMP(3);
ALTER TABLE datasets ADD queue_attempts INTEGER DEFAULT 0;
ALTER TABLE datasets ADD queue_interval INTERVAL DEFAULT 'PT5M';
ALTER TABLE datasets ADD queue_message TEXT;
ALTER TABLE datasets DROP COLUMN download_status;
ALTER TABLE datasets DROP COLUMN schedule_time;

CREATE INDEX datasets_que_stat_idx ON datasets(queue_status);

ALTER TABLE items ADD sync_date TIMESTAMP(3);
ALTER TABLE items DROP COLUMN dataset_record_id;
ALTER TABLE items ADD dataset_key_value VARCHAR(255);

CREATE INDEX items_sync_dat_idx ON items(sync_date);
CREATE INDEX items_dat_key_idx ON items(dataset_key_value);
