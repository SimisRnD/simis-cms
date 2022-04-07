-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Wikis

CREATE TABLE wikis (
  wiki_id BIGSERIAL PRIMARY KEY,
  wiki_unique_id VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  enabled BOOLEAN DEFAULT true,
  starting_page BIGINT
);
CREATE INDEX wikis_unique_id_idx ON wikis(wiki_unique_id);

CREATE TABLE wiki_pages (
  wiki_page_id BIGSERIAL PRIMARY KEY,
  wiki_id BIGINT REFERENCES wikis(wiki_id) NOT NULL,
  page_unique_id VARCHAR(255) NOT NULL,
  title VARCHAR(255) NOT NULL,
  body TEXT,
  summary TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  tsv TSVECTOR
);
CREATE UNIQUE INDEX wiki_pages_unique_idx ON wiki_pages(wiki_id, page_unique_id);
CREATE INDEX wiki_pages_tsv_idx ON wiki_pages USING gin(tsv);

CREATE OR REPLACE FUNCTION wiki_pages_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
    setweight(to_tsvector('title_stem', new.title), 'A') ||
    setweight(to_tsvector('title_stem', coalesce(new.body,'')), 'B');
  return new;
end
$$ LANGUAGE plpgsql;

CREATE TRIGGER tsvectorupdate BEFORE INSERT OR UPDATE
ON wiki_pages FOR EACH ROW EXECUTE PROCEDURE wiki_pages_tsv_trigger();

CREATE TABLE wiki_page_revisions (
  revision_id BIGSERIAL PRIMARY KEY,
  wiki_page_id BIGINT REFERENCES wiki_pages(wiki_page_id) NOT NULL,
  wiki_id BIGINT REFERENCES wikis(wiki_id) NOT NULL,
  body TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
