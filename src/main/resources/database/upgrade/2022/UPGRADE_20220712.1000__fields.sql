
ALTER TABLE users ADD latitude FLOAT DEFAULT 0;
ALTER TABLE users ADD longitude FLOAT DEFAULT 0;
ALTER TABLE users ADD geom geometry(Point,4326);
ALTER TABLE users ADD description TEXT;
ALTER TABLE users ADD description_text TEXT;
ALTER TABLE users ADD image_url VARCHAR(255);
ALTER TABLE users ADD video_url VARCHAR(255);
ALTER TABLE users ADD field_values JSONB;
CREATE INDEX users_geom_gix ON users USING GIST (geom);

ALTER TABLE datasets ADD download_status INTEGER DEFAULT 0;
ALTER TABLE datasets ADD process_status INTEGER DEFAULT 0;
ALTER TABLE datasets ADD process_message TEXT;
ALTER TABLE datasets ADD schedule_enabled BOOLEAN DEFAULT false;
ALTER TABLE datasets ADD schedule_frequency INTERVAL DEFAULT '1D';
ALTER TABLE datasets ADD schedule_time TIME DEFAULT '03:00';
ALTER TABLE datasets ADD schedule_last_run TIMESTAMP(3);
ALTER TABLE datasets ADD sync_enabled BOOLEAN DEFAULT false;
ALTER TABLE datasets ADD sync_date TIMESTAMP(3);
ALTER TABLE datasets ADD sync_status INTEGER DEFAULT 0;
ALTER TABLE datasets ADD sync_message TEXT;
CREATE INDEX datasets_sched_idx ON datasets(schedule_enabled);
CREATE INDEX datasets_sync_idx ON datasets(sync_enabled);

ALTER TABLE items ADD dataset_record_id VARCHAR(255);
ALTER TABLE items ADD description TEXT;
ALTER TABLE items ADD description_text TEXT;

CREATE OR REPLACE FUNCTION items_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
            setweight(to_tsvector('title_stem', new.name), 'A') ||
            setweight(to_tsvector(coalesce(new.keywords,'')), 'B') ||
            setweight(to_tsvector('title_stem', coalesce(new.summary,'')), 'C') ||
            setweight(to_tsvector('title_stem', coalesce(new.description_text,'')), 'D');
  return new;
end
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION blog_posts_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
            setweight(to_tsvector('title_stem', new.title), 'A') ||
            setweight(to_tsvector(coalesce(new.keywords,'')), 'B') ||
            setweight(to_tsvector('title_stem', coalesce(new.summary,'')), 'C') ||
            setweight(to_tsvector('title_stem', coalesce(new.body_text,'')), 'D');
  return new;
end
$$ LANGUAGE plpgsql;

UPDATE blog_posts SET tsv =
    setweight(to_tsvector('title_stem', title), 'A') ||
    setweight(to_tsvector(coalesce(keywords,'')), 'B') ||
    setweight(to_tsvector('title_stem', coalesce(summary,'')), 'C') ||
    setweight(to_tsvector('title_stem', coalesce(body_text,'')), 'D');
