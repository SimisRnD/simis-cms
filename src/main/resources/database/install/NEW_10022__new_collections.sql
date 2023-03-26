-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Collections

CREATE TABLE collections (
  collection_id BIGSERIAL PRIMARY KEY,
  unique_id VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) UNIQUE NOT NULL,
  description TEXT,
  created_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  category_count BIGINT NOT NULL DEFAULT 0,
  item_count BIGINT NOT NULL DEFAULT 0,
  has_allowed_groups BOOLEAN DEFAULT FALSE,
  allows_guests BOOLEAN DEFAULT FALSE,
  guest_privacy_type INTEGER NOT NULL,
  listings_link VARCHAR(255),
  image_url VARCHAR(255),
  header_xml TEXT,
  icon VARCHAR(20),
  show_listings_link BOOLEAN DEFAULT FALSE,
  show_search BOOLEAN DEFAULT FALSE,
  header_text_color VARCHAR(30) DEFAULT '#ffffff',
  header_bg_color VARCHAR(30) DEFAULT '#666666',
  menu_text_color VARCHAR(30) DEFAULT '#ffffff',
  menu_bg_color VARCHAR(30) DEFAULT 'transparent',
  menu_border_color VARCHAR(30) DEFAULT 'transparent',
  menu_active_text_color VARCHAR(30) DEFAULT '#ffffff',
  menu_active_bg_color VARCHAR(30) DEFAULT 'transparent',
  menu_active_border_color VARCHAR(30) DEFAULT '#ff9900',
  menu_hover_text_color VARCHAR(30) DEFAULT '#ffffff',
  menu_hover_bg_color VARCHAR(30) DEFAULT 'transparent',
  menu_hover_border_color VARCHAR(30) DEFAULT '#ff9900',
  field_values JSONB,
  item_url_text VARCHAR(50)
);
CREATE INDEX collections_nm_idx ON collections(name);
CREATE INDEX collections_ag_idx ON collections(has_allowed_groups);
CREATE INDEX collections_agu_idx ON collections(allows_guests);

CREATE TABLE categories (
  category_id BIGSERIAL PRIMARY KEY,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  created_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  item_count BIGINT NOT NULL DEFAULT 0,
  is_primary BOOLEAN DEFAULT true,
  icon VARCHAR(20),
  header_text_color VARCHAR(30) DEFAULT '#ffffff',
  header_bg_color VARCHAR(30) DEFAULT '#666666',
  item_url_text VARCHAR(50),
  unique_id VARCHAR(255) NOT NULL,
  image_url VARCHAR(255)
);
CREATE UNIQUE INDEX categories_uni_idx ON categories(collection_id, name);
CREATE UNIQUE INDEX categories_uidx ON categories (collection_id, unique_id);
CREATE INDEX categories_col_idx ON categories(collection_id);
CREATE INDEX categories_nm_idx ON categories(name);
CREATE INDEX categories_prim_idx ON categories(is_primary);

CREATE TABLE collection_relationships (
  relationship_id BIGSERIAL PRIMARY KEY,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  related_collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  is_active BOOLEAN DEFAULT true,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX col_relat_col_rel_idx ON collection_relationships(collection_id, related_collection_id);
CREATE INDEX col_relat_col_id_idx ON collection_relationships(collection_id);
CREATE INDEX col_relat_rcol_id_idx ON collection_relationships(related_collection_id);
CREATE INDEX col_relat_is_act_idx ON collection_relationships(is_active);

CREATE TABLE collection_groups (
  allowed_id BIGSERIAL PRIMARY KEY,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  group_id BIGINT REFERENCES groups(group_id) NOT NULL,
  privacy_type INTEGER NOT NULL,
  view_all BOOLEAN DEFAULT false,
  add_permission BOOLEAN DEFAULT false,
  edit_permission BOOLEAN DEFAULT false,
  delete_permission BOOLEAN DEFAULT false
);
CREATE INDEX col_group_col_idx ON collection_groups(collection_id);
CREATE INDEX col_group_grp_idx ON collection_groups(group_id);
CREATE INDEX col_group_view_idx ON collection_groups(view_all);
CREATE INDEX col_group_add_idx ON collection_groups(add_permission);
CREATE INDEX col_group_edit_idx ON collection_groups(edit_permission);
CREATE INDEX col_group_delete_idx ON collection_groups(delete_permission);

-- CREATE TABLE collection_tab_templates (
--   template_id BIGSERIAL PRIMARY KEY,
--   name VARCHAR(100) NOT NULL,
--   image_path VARCHAR(150) NOT NULL,
--   page_xml TEXT NOT NULL
-- );

CREATE TABLE collection_tabs (
  tab_id BIGSERIAL PRIMARY KEY,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  tab_order INTEGER DEFAULT 100,
  name VARCHAR(255) NOT NULL,
  link VARCHAR(255),
  page_title VARCHAR(255),
  page_keywords VARCHAR(255),
  page_description VARCHAR(255),
  draft BOOLEAN DEFAULT false,
  enabled BOOLEAN DEFAULT true,
  page_xml TEXT,
  role_id_list VARCHAR(100) DEFAULT NULL,
  page_image_url VARCHAR(255)
);
CREATE INDEX col_tabs_col_idx ON collections(collection_id);

CREATE TABLE collection_tab_groups (
  allowed_id BIGSERIAL PRIMARY KEY,
  collection_id BIGINT REFERENCES collections(collection_id) NOT NULL,
  tab_id BIGINT REFERENCES collection_tabs(tab_id) NOT NULL,
  group_id BIGINT REFERENCES groups(group_id) NOT NULL
);
CREATE INDEX col_tab_group_col_idx ON collection_tab_groups(collection_id);
CREATE INDEX col_tab_group_grp_idx ON collection_tab_groups(group_id);
