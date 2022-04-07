-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Items

CREATE TABLE items (
  item_id BIGSERIAL PRIMARY KEY,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  unique_id VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  summary TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  archived TIMESTAMP(3) DEFAULT NULL,
  latitude FLOAT DEFAULT 0,
  longitude FLOAT DEFAULT 0,
  location_name VARCHAR(255),
  street VARCHAR(100),
  address_line_2 VARCHAR(100),
  address_line_3 VARCHAR(100),
  city VARCHAR(100),
  state VARCHAR(100),
  country VARCHAR(100),
  postal_code VARCHAR(100),
  county VARCHAR(100),
  phone_number VARCHAR(30),
  cost NUMERIC(15,6) DEFAULT 0,
  expected_date TIMESTAMP(3) DEFAULT NULL,
  start_date TIMESTAMP(3) DEFAULT NULL,
  end_date TIMESTAMP(3) DEFAULT NULL,
  expiration_date TIMESTAMP(3) DEFAULT NULL,
  url VARCHAR(255),
  barcode VARCHAR(1024),
  keywords VARCHAR(255),
  assigned_to BIGINT REFERENCES users(user_id),
  assigned TIMESTAMP(3) DEFAULT NULL,
  dataset_id BIGINT,
  geom geometry(Point,4326),
  tsv TSVECTOR,
  image_url VARCHAR(255),
  category_id BIGINT REFERENCES categories(category_id),
  email VARCHAR(255),
  field_values JSONB,
  archived_by BIGINT REFERENCES users(user_id),
  approved_by BIGINT REFERENCES users(user_id),
  approved TIMESTAMP(3) DEFAULT NULL,
  source VARCHAR(255)
);
CREATE INDEX items_col_id_idx ON items(collection_id);
CREATE INDEX items_uni_id_idx ON items(unique_id);
CREATE INDEX items_archived_idx ON items(archived);
CREATE INDEX items_lname_idx ON items(LOWER(name));
CREATE INDEX items_geom_gix ON items USING GIST (geom);
CREATE INDEX items_tsv_idx ON items USING gin(tsv);
CREATE INDEX items_cat_id_idx ON items(category_id);
CREATE INDEX items_approved_idx ON items(approved);

-- UPDATE items SET geom = ST_SetSRID(ST_MakePoint(latitude, longitude), 4326) WHERE latitude <> 0 OR longitude <> 0 AND geom IS NULL;
-- UPDATE items SET tsv = SETWEIGHT(TO_TSVECTOR(name), 'A') || SETWEIGHT(TO_TSVECTOR(coalesce(keywords,'')), 'B') || SETWEIGHT(TO_TSVECTOR(coalesce(summary,'')), 'D');

CREATE TEXT SEARCH DICTIONARY title_stem (
  TEMPLATE = snowball,
  Language = english
  );
CREATE TEXT SEARCH CONFIGURATION title_stem (copy = english);
ALTER TEXT SEARCH CONFIGURATION title_stem
  ALTER MAPPING FOR asciihword, asciiword, hword, hword_asciipart, hword_part, word
    WITH title_stem;

CREATE OR REPLACE FUNCTION items_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
          setweight(to_tsvector('title_stem', new.name), 'A') ||
          setweight(to_tsvector(coalesce(new.keywords,'')), 'B') ||
          setweight(to_tsvector('title_stem', coalesce(new.summary,'')), 'D');
  return new;
end
$$ LANGUAGE plpgsql;

CREATE TRIGGER tsvectorupdate BEFORE INSERT OR UPDATE
  ON items FOR EACH ROW EXECUTE PROCEDURE items_tsv_trigger();


CREATE TABLE item_categories (
  id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  category_id BIGINT REFERENCES categories(category_id) NOT NULL,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  dataset_id BIGINT
);
CREATE UNIQUE INDEX item_categories_uidx ON item_categories(item_id, collection_id, category_id);
CREATE INDEX item_cat_item_idx ON item_categories(item_id);
CREATE INDEX item_cat_cat_idx ON item_categories(category_id);


CREATE TABLE lookup_relationship_types (
  type_id SERIAL PRIMARY KEY,
  collection_id BIGINT NOT NULL,
  related_collection_id BIGINT NOT NULL,
  title VARCHAR(100)
);

CREATE TABLE item_relationships (
  relationship_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  related_item_id BIGINT REFERENCES items(item_id) NOT NULL,
  related_collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  relationship_type INTEGER REFERENCES lookup_relationship_types(type_id),
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  is_active BOOLEAN DEFAULT true,
  start_date TIMESTAMP(3) DEFAULT NULL,
  end_date TIMESTAMP(3) DEFAULT NULL,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX item_relat_idx ON item_relationships(item_id, related_item_id, relationship_type);


CREATE TABLE item_activity_stream (
  activity_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  activity_type VARCHAR(50) NOT NULL,
  message_text TEXT NOT NULL,
  source VARCHAR(50),
  source_link VARCHAR(300),
  created_by BIGINT REFERENCES users(user_id) DEFAULT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) DEFAULT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
--   has_attachment BOOLEAN DEFAULT false,
--   has_reactions BOOLEAN DEFAULT false
);

CREATE INDEX item_act_st_item_id ON item_activity_stream(item_id);
CREATE INDEX item_act_st_act_type ON item_activity_stream(activity_type);
CREATE INDEX item_act_st_createdby ON item_activity_stream(created_by);

-- CREATE TABLE item_menu_tabs (order, name, role, each with a page_xml? also in collection); ItemMenuTab
-- CREATE TABLE item_stylesheets
-- CREATE TABLE item_content... ItemContentWidget
-- CREATE TABLE item_images
-- CREATE TABLE item_form_data
-- CREATE TABLE item_table_of_contents (useful links for navigating page content)
-- CREATE TABLE item_blog_posts
-- CREATE TABLE item_calendar_events

CREATE TABLE item_folders (
  folder_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  folder_unique_id VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  summary TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  file_count INTEGER NOT NULL DEFAULT 0,
  privacy_types VARCHAR(100),
  has_allowed_groups BOOLEAN DEFAULT FALSE,
  allows_guests BOOLEAN DEFAULT FALSE,
  guest_privacy_type INTEGER NOT NULL,
  enabled BOOLEAN DEFAULT true,
  has_categories BOOLEAN DEFAULT FALSE
);
CREATE INDEX i_folders_itm_idx ON item_folders(item_id);
CREATE INDEX i_folders_uid_idx ON item_folders(folder_unique_id);
CREATE INDEX i_folders_nm_idx ON item_folders(name);
CREATE INDEX i_folders_ag_idx ON item_folders(has_allowed_groups);
CREATE INDEX i_folders_agu_idx ON item_folders(allows_guests);
CREATE UNIQUE INDEX i_folders_unique ON item_folders(item_id, folder_unique_id);

CREATE TABLE item_folder_groups (
  allowed_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  folder_id BIGINT REFERENCES item_folders(folder_id) NOT NULL,
  group_id BIGINT REFERENCES groups(group_id) NOT NULL,
  privacy_type INTEGER NOT NULL,
  view_all BOOLEAN DEFAULT false,
  add_permission BOOLEAN DEFAULT false,
  edit_permission BOOLEAN DEFAULT false,
  delete_permission BOOLEAN DEFAULT false
);
CREATE INDEX i_fldr_group_fol_idx ON item_folder_groups(folder_id);
CREATE INDEX i_fldr_group_grp_idx ON item_folder_groups(group_id);
CREATE INDEX i_fldr_group_view_idx ON item_folder_groups(view_all);
CREATE INDEX i_fldr_group_add_idx ON item_folder_groups(add_permission);
CREATE INDEX i_fldr_group_edit_idx ON item_folder_groups(edit_permission);
CREATE INDEX i_fldr_group_delete_idx ON item_folder_groups(delete_permission);

CREATE TABLE item_folder_categories (
  category_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  folder_id BIGINT REFERENCES item_folders(folder_id) NOT NULL,
  name VARCHAR(255) NOT NULL,
  enabled BOOLEAN DEFAULT true
);

CREATE TABLE item_sub_folders (
  sub_folder_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  folder_id BIGINT REFERENCES item_folders(folder_id) NOT NULL,
  name VARCHAR(255) NOT NULL,
  summary TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  start_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  end_date TIMESTAMP(3) DEFAULT NULL,
  file_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX i_sub_folders_start_idx ON item_sub_folders(start_date);

CREATE TABLE item_files (
  file_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  folder_id BIGINT REFERENCES item_folders(folder_id) NOT NULL,
  filename VARCHAR(255) NOT NULL,
  title VARCHAR(1000),
  barcode VARCHAR(1024),
  version VARCHAR(15),
  extension VARCHAR(15),
  path VARCHAR(255),
  file_length BIGINT DEFAULT 0,
  file_type VARCHAR(50),
  mime_type VARCHAR(100),
  file_hash VARCHAR(1024),
  width INTEGER,
  height INTEGER,
  summary TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  processed TIMESTAMP(3) DEFAULT NULL,
  expiration_date TIMESTAMP(3) DEFAULT NULL,
  privacy_type INTEGER NOT NULL,
  default_token VARCHAR(255),
  version_count INTEGER DEFAULT 0,
  download_count BIGINT DEFAULT 0,
  document_text TEXT,
  tsv TSVECTOR,
  sub_folder_id BIGINT REFERENCES item_sub_folders(sub_folder_id),
  category_id BIGINT REFERENCES item_folder_categories(category_id)
);
CREATE INDEX i_files_tsv_idx ON item_files USING gin(tsv);
CREATE INDEX i_files_folder_id_idx ON item_files(folder_id);
CREATE INDEX i_files_created_idx ON item_files(created);
CREATE INDEX i_files_title_idx ON item_files(title);
CREATE INDEX i_files_sub_folder_idx ON item_files(sub_folder_id);
CREATE INDEX i_files_category_idx ON item_files(category_id);

CREATE TEXT SEARCH DICTIONARY item_file_stem (
  TEMPLATE = snowball,
  Language = english
  );
CREATE TEXT SEARCH CONFIGURATION item_file_stem (copy = english);
ALTER TEXT SEARCH CONFIGURATION item_file_stem
  ALTER MAPPING FOR asciihword, asciiword, hword, hword_asciipart, hword_part, word
    WITH item_file_stem;

CREATE OR REPLACE FUNCTION item_files_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
            setweight(to_tsvector('item_file_stem', new.filename), 'A') ||
            setweight(to_tsvector('item_file_stem', coalesce(new.title,'')), 'B') ||
            setweight(to_tsvector('item_file_stem', coalesce(new.summary,'')), 'C') ||
            setweight(to_tsvector('item_file_stem', coalesce(new.document_text,'')), 'D');
  return new;
end
$$ LANGUAGE plpgsql;

CREATE TRIGGER tsvectorupdate BEFORE INSERT OR UPDATE
  ON item_files FOR EACH ROW EXECUTE PROCEDURE item_files_tsv_trigger();

CREATE TABLE item_file_versions (
  version_id BIGSERIAL PRIMARY KEY,
  item_id BIGINT REFERENCES items(item_id) NOT NULL,
  file_id BIGINT REFERENCES item_files(file_id) NOT NULL,
  folder_id BIGINT REFERENCES item_folders(folder_id) NOT NULL,
  filename VARCHAR(255) NOT NULL,
  title VARCHAR(1000),
  version VARCHAR(15),
  extension VARCHAR(15),
  path VARCHAR(255),
  file_length BIGINT DEFAULT 0,
  file_type VARCHAR(50),
  mime_type VARCHAR(100),
  file_hash VARCHAR(1024),
  width INTEGER,
  height INTEGER,
  summary TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  download_count BIGINT DEFAULT 0,
  sub_folder_id BIGINT REFERENCES item_sub_folders(sub_folder_id),
  category_id BIGINT REFERENCES item_folder_categories(category_id)
);
CREATE INDEX i_file_ver_file_id_idx ON item_file_versions(file_id);
CREATE INDEX i_file_ver_fold_id_idx ON item_file_versions(folder_id);
CREATE INDEX i_file_ver_created_idx ON item_file_versions(created);
CREATE INDEX i_file_ver_sub_fold_idx ON item_file_versions(sub_folder_id);

-- CREATE TABLE item_mailing_list_members
-- CREATE TABLE item_wiki_pages
-- CREATE TABLE item_issues
-- CREATE TABLE item_projects
