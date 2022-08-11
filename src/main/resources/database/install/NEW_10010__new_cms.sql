-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Content Management System

CREATE TABLE themes (
  theme_id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) UNIQUE NOT NULL,
  entries JSONB,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE web_containers (
  container_id BIGSERIAL PRIMARY KEY,
  container_name VARCHAR(100) UNIQUE NOT NULL,
  label VARCHAR(100),
  image_path VARCHAR(150),
  draft BOOLEAN DEFAULT false,
  container_xml TEXT NOT NULL,
  draft_xml TEXT
);
CREATE INDEX web_containers_nm_idx ON web_containers(container_name);

CREATE TABLE menu_tabs (
  menu_tab_id BIGSERIAL PRIMARY KEY,
  tab_order INTEGER DEFAULT 100,
  name VARCHAR(255) NOT NULL,
  link VARCHAR(255),
  page_title VARCHAR(255),
  page_keywords VARCHAR(255),
  page_description VARCHAR(255),
  draft BOOLEAN DEFAULT false,
  enabled BOOLEAN DEFAULT true,
  role_id_list VARCHAR(50) DEFAULT NULL,
  comments TEXT,
  icon VARCHAR(20)
);
CREATE INDEX menu_tabs_order_idx ON menu_tabs(tab_order);
CREATE INDEX menu_tabs_active_idx ON menu_tabs(draft, enabled);

INSERT INTO menu_tabs (tab_order, name, link) VALUES (1, 'Home', '/');
-- INSERT INTO menu_tabs (tab_order, name, link) VALUES (2, 'About Us', '/about');
-- INSERT INTO menu_tabs (tab_order, name, link) VALUES (3, 'Calendar', '/calendar');
-- INSERT INTO menu_tabs (tab_order, name, link) VALUES (4, 'Community', '/community');
-- INSERT INTO menu_tabs (tab_order, name, link) VALUES (5, 'Directories', '/directories');


CREATE TABLE menu_items (
  menu_item_id BIGSERIAL PRIMARY KEY,
  menu_tab_id BIGINT REFERENCES menu_tabs(menu_tab_id),
  item_order INTEGER DEFAULT 100,
  name VARCHAR(255),
  link VARCHAR(255),
  page_title VARCHAR(255),
  page_keywords VARCHAR(255),
  page_description VARCHAR(255),
  draft BOOLEAN DEFAULT false,
  enabled BOOLEAN DEFAULT true,
  role_id_list VARCHAR(50) DEFAULT NULL,
  comments TEXT
);
CREATE INDEX menu_items_ord_idx ON menu_items(item_order);
CREATE INDEX menu_items_act_idx ON menu_items(draft, enabled);
CREATE INDEX menu_items_tab_idx ON menu_items(menu_tab_id);

CREATE TABLE web_page_templates (
  template_id BIGSERIAL PRIMARY KEY,
  template_order INTEGER DEFAULT 10,
  name VARCHAR(100) NOT NULL,
  image_path VARCHAR(150) NOT NULL,
  page_xml TEXT NOT NULL,
  description TEXT,
  rules JSONB,
  css TEXT,
  category VARCHAR(100)
);


/* consider file-based templates like emails)
CREATE TABLE email_templates (
  template_id BIGSERIAL PRIMARY KEY,
  template_order INTEGER DEFAULT 10,
  template_name VARCHAR(50) UNIQUE NOT NULL,
  name VARCHAR(100) NOT NULL,
  comments VARCHAR(255),
  subject VARCHAR(200),
  html_content TEXT,
  text_content TEXT
);

INSERT INTO email_templates (template_name, name, subject, html_content, text_content)
  VALUES (
  'new.user.email.validation',
  'Validation email for new users',
  '${siteName} - Please confirm your email address',
  '',
  '');
*/


CREATE TABLE web_pages (
  web_page_id BIGSERIAL PRIMARY KEY,
  link VARCHAR(255) UNIQUE NOT NULL,
  redirect_url VARCHAR(255),
  page_title VARCHAR(255),
  page_keywords VARCHAR(255),
  page_description VARCHAR(255),
  draft BOOLEAN DEFAULT false,
  enabled BOOLEAN DEFAULT true,
  created_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  role_id_list VARCHAR(100) DEFAULT NULL,
  template VARCHAR(255),
  page_xml TEXT,
  comments TEXT,
  draft_page_xml TEXT,
  page_image_url VARCHAR(255),
  searchable BOOLEAN DEFAULT true,
  show_in_sitemap BOOLEAN DEFAULT true,
  has_redirect BOOLEAN DEFAULT false,
  sitemap_priority NUMERIC(2,1) DEFAULT 0.5,
  sitemap_changefreq VARCHAR(20)
);
CREATE INDEX web_pages_link_idx ON web_pages(link);
CREATE INDEX web_pages_search_idx ON web_pages(searchable);
CREATE INDEX web_pages_draft_idx ON web_pages(draft);
CREATE INDEX web_pages_enabled_idx ON web_pages(enabled);
CREATE INDEX web_pages_sitemap_idx ON web_pages(show_in_sitemap);
CREATE INDEX web_pages_redirect_idx ON web_pages(has_redirect);

CREATE TABLE content (
  content_id BIGSERIAL PRIMARY KEY,
  content_unique_id VARCHAR(255) UNIQUE,
  content TEXT,
  created_by BIGINT REFERENCES users(user_id),
  modified_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  draft_content TEXT,
  content_text TEXT,
  tsv TSVECTOR
);
CREATE INDEX content_uni_idx ON content(content_unique_id);
CREATE INDEX content_tsv_idx ON content USING gin(tsv);

CREATE TEXT SEARCH DICTIONARY content_stem (
    TEMPLATE = snowball,
    Language = english
);
CREATE TEXT SEARCH CONFIGURATION content_stem (copy = english);
ALTER TEXT SEARCH CONFIGURATION content_stem
   ALTER MAPPING FOR asciihword, asciiword, hword, hword_asciipart, hword_part, word
   WITH content_stem;

CREATE OR REPLACE FUNCTION content_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
    setweight(to_tsvector('content_stem', new.content_text), 'A');
  return new;
end
$$ LANGUAGE plpgsql;

CREATE TRIGGER tsvectorupdate BEFORE INSERT OR UPDATE
ON content FOR EACH ROW EXECUTE PROCEDURE content_tsv_trigger();

-- INSERT INTO content (content_unique_id, content) VALUES ('setup-hello', 'Hello from the setup content database!');
-- INSERT INTO content (content_unique_id, content) VALUES ('login-hello', 'Hello from the Login Page''s Content Widget database!');
-- INSERT INTO content (content_unique_id, content) VALUES ('register-hello', 'Hello from the Register Page''s Content Widget database!');

CREATE TABLE images (
  image_id BIGSERIAL PRIMARY KEY,
  filename VARCHAR(255) NOT NULL,
  path VARCHAR(255) NOT NULL,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  processed TIMESTAMP(3),
  file_length BIGINT DEFAULT 0,
  file_type VARCHAR(20),
  width INTEGER NOT NULL,
  height INTEGER NOT NULL,
  processed_path VARCHAR(255),
  processed_file_length BIGINT DEFAULT 0,
  processed_file_type VARCHAR(20),
  processed_width INTEGER NOT NULL DEFAULT 0,
  processed_height INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX images_created_idx ON images(created);

CREATE TABLE form_data (
  form_data_id BIGSERIAL PRIMARY KEY,
  form_unique_id VARCHAR(255),
  field_values JSONB,
  ip_address VARCHAR(200),
  created_by BIGINT REFERENCES users(user_id),
  modified_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  claimed TIMESTAMP(3) DEFAULT NULL,
  claimed_by BIGINT REFERENCES users(user_id),
  dismissed TIMESTAMP(3) DEFAULT NULL,
  url VARCHAR(512),
  query_params VARCHAR(512),
  flagged_as_spam BOOLEAN DEFAULT FALSE,
  session_id VARCHAR(255),
  dismissed_by BIGINT REFERENCES users(user_id),
  processed TIMESTAMP(3) DEFAULT NULL,
  processed_by BIGINT REFERENCES users(user_id),
  processed_system VARCHAR(255)
);
CREATE INDEX form_data_created_idx ON form_data(created);
CREATE INDEX form_data_session_idx ON form_data(session_id);
CREATE INDEX form_data_claimed_idx ON form_data(claimed);
CREATE INDEX form_data_claimed_by_idx ON form_data(claimed_by);
CREATE INDEX form_data_dismissed_idx ON form_data(dismissed);
CREATE INDEX form_data_processed_idx ON form_data(processed);

-- We want to know popular page_path
-- We want to know popular web_page_id
-- We want to know geolocation of ip_address
-- We want to know if this is a user or not
CREATE TABLE web_page_hits (
  hit_id BIGSERIAL PRIMARY KEY,
  method VARCHAR(6),
  page_path VARCHAR(255),
  web_page_id BIGINT,
  ip_address VARCHAR(200),
  hit_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  session_id VARCHAR(255),
  is_logged_in BOOLEAN DEFAULT FALSE
);

CREATE INDEX web_pg_hits_dt_idx ON web_page_hits(hit_date);
CREATE INDEX web_pg_hits_ss_idx ON web_page_hits(session_id);

CREATE TABLE web_page_hit_snapshots (
  snapshot_id BIGSERIAL PRIMARY KEY,
  snapshot_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  date_value VARCHAR(10) UNIQUE NOT NULL,
  unique_sessions BIGINT DEFAULT 0,
  web_page_hits BIGINT DEFAULT 0,
  content_hits BIGINT DEFAULT 0,
  item_hits BIGINT DEFAULT 0
--   file_hits BIGINT DEFAULT 0
);

CREATE INDEX web_pg_hit_snp_dt_idx ON web_page_hit_snapshots(snapshot_date);

CREATE TABLE web_searches (
  search_id BIGSERIAL PRIMARY KEY,
  page_path VARCHAR(255),
  query VARCHAR(255),
  ip_address VARCHAR(200),
  search_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  session_id VARCHAR(255),
  is_logged_in BOOLEAN DEFAULT FALSE
);

--
-- CREATE TABLE content_hits (
--   hit_id BIGSERIAL PRIMARY KEY,
--   content_id BIGINT,
--   ip_address VARCHAR(200),
--   hit_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
--   is_logged_in BOOLEAN DEFAULT FALSE,
--   is_rest BOOLEAN DEFAULT FALSE
-- );
--
-- CREATE TABLE item_hits (
--   hit_id BIGSERIAL PRIMARY KEY,
--   item_id BIGINT,
--   ip_address VARCHAR(200),
--   hit_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
--   is_logged_in BOOLEAN DEFAULT FALSE,
--   is_rest BOOLEAN DEFAULT FALSE
-- );

CREATE TABLE table_of_contents (
  toc_id BIGSERIAL PRIMARY KEY,
  toc_unique_id VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  entries JSONB,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX table_con_uniq_id_idx ON table_of_contents(toc_unique_id);

-- Blogs (Blog, Press Releases, News, etc.)
CREATE TABLE blogs (
  blog_id BIGSERIAL PRIMARY KEY,
  blog_unique_id VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  enabled BOOLEAN DEFAULT true
);
CREATE INDEX blogs_unique_id_idx ON blogs(blog_unique_id);

CREATE TABLE lookup_blog_post_tags (
  tag_id BIGSERIAL PRIMARY KEY,
  blog_id BIGINT REFERENCES blogs(blog_id) NOT NULL,
  tag_unique_id VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX lookup_bl_po_tag_uidx ON lookup_blog_post_tags (blog_id, tag_unique_id);

CREATE TABLE blog_posts (
  post_id BIGSERIAL PRIMARY KEY,
  blog_id BIGINT REFERENCES blogs(blog_id) NOT NULL,
  post_unique_id VARCHAR(255) NOT NULL,
  title VARCHAR(255) NOT NULL,
  body TEXT,
  summary TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  published TIMESTAMP(3) DEFAULT NULL,
  archived TIMESTAMP(3) DEFAULT NULL,
  start_date TIMESTAMP(3) DEFAULT NULL,
  end_date TIMESTAMP(3) DEFAULT NULL,
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
  geom geometry(Point,4326),
  tsv TSVECTOR,
  image_url VARCHAR(255),
  video_url VARCHAR(255),
  video_embed VARCHAR(512),
  script_embed VARCHAR(512),
  tags_list VARCHAR(255),
  keywords VARCHAR(255),
  body_text TEXT
);
CREATE UNIQUE INDEX blog_posts_unique_idx ON blog_posts(blog_id, post_unique_id);
CREATE INDEX blog_posts_geom_gix ON blog_posts USING GIST (geom);
CREATE INDEX blog_posts_tsv_idx ON blog_posts USING gin(tsv);
CREATE INDEX blog_posts_pub_idx ON blog_posts(published);
CREATE INDEX blog_posts_start_idx ON blog_posts(start_date);
CREATE INDEX blog_posts_end_idx ON blog_posts(end_date);

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

CREATE TRIGGER tsvectorupdate BEFORE INSERT OR UPDATE
ON blog_posts FOR EACH ROW EXECUTE PROCEDURE blog_posts_tsv_trigger();


CREATE TABLE blog_post_tags (
  post_tag_id BIGSERIAL PRIMARY KEY,
  post_id BIGINT REFERENCES blog_posts(post_id),
  tag_id BIGINT REFERENCES lookup_blog_post_tags(tag_id)
);


-- Calendars (Events, Training, etc.)
CREATE TABLE calendars (
  calendar_id BIGSERIAL PRIMARY KEY,
  calendar_unique_id VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  color VARCHAR(7),
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  enabled BOOLEAN DEFAULT true,
  event_count INTEGER DEFAULT 0
);
CREATE TABLE calendar_events (
  event_id BIGSERIAL PRIMARY KEY,
  calendar_id BIGINT REFERENCES calendars(calendar_id) NOT NULL,
  event_unique_id VARCHAR(255) NOT NULL,
  title VARCHAR(255) NOT NULL,
  body TEXT,
  summary TEXT,
  created_by BIGINT REFERENCES users(user_id) NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id) NOT NULL,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  published TIMESTAMP(3) DEFAULT NULL,
  archived TIMESTAMP(3) DEFAULT NULL,
  all_day BOOLEAN DEFAULT false,
  start_date TIMESTAMP(3) NOT NULL,
  end_date TIMESTAMP(3) NOT NULL,
  details_url VARCHAR(255),
  sign_up_url VARCHAR(255),
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
  geom geometry(Point,4326),
  tsv TSVECTOR,
  image_url VARCHAR(255),
  video_url VARCHAR(255),
  video_embed VARCHAR(512),
  script_embed VARCHAR(512),
  tags_list VARCHAR(255)
);
CREATE UNIQUE INDEX cal_events_unique_idx ON calendar_events(calendar_id, event_unique_id);
CREATE INDEX cal_events_geom_gix ON calendar_events USING GIST (geom);
CREATE INDEX cal_events_tsv_idx ON calendar_events USING gin(tsv);
CREATE INDEX cal_events_pub_idx ON calendar_events(published);
CREATE INDEX cal_events_start_idx ON calendar_events(start_date);
CREATE INDEX cal_events_end_idx ON calendar_events(end_date);

CREATE OR REPLACE FUNCTION cal_events_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
    setweight(to_tsvector('title_stem', new.title), 'A') ||
    setweight(to_tsvector('title_stem', coalesce(new.summary,'')), 'B') ||
    setweight(to_tsvector('title_stem', coalesce(new.body,'')), 'D');
  return new;
end
$$ LANGUAGE plpgsql;

CREATE TRIGGER tsvectorupdate BEFORE INSERT OR UPDATE
ON calendar_events FOR EACH ROW EXECUTE PROCEDURE cal_events_tsv_trigger();

-- 2018/11/27 1530121196430-2.png or 99FFC992-1234-4CF9-B1F9-E5EF049D00C5.pdf
-- Version
-- Create a folder for all sorts of digital assets

CREATE TABLE folders (
  folder_id BIGSERIAL PRIMARY KEY,
  folder_unique_id VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) UNIQUE NOT NULL,
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
CREATE INDEX folders_nm_idx ON folders(name);
CREATE INDEX folders_ag_idx ON folders(has_allowed_groups);
CREATE INDEX folders_agu_idx ON folders(allows_guests);

CREATE TABLE folder_groups (
  allowed_id BIGSERIAL PRIMARY KEY,
  folder_id BIGINT REFERENCES folders(folder_id) NOT NULL,
  group_id BIGINT REFERENCES groups(group_id) NOT NULL,
  privacy_type INTEGER NOT NULL,
  view_all BOOLEAN DEFAULT false,
  add_permission BOOLEAN DEFAULT false,
  edit_permission BOOLEAN DEFAULT false,
  delete_permission BOOLEAN DEFAULT false
);
CREATE INDEX fldr_group_fol_idx ON folder_groups(folder_id);
CREATE INDEX fldr_group_grp_idx ON folder_groups(group_id);
CREATE INDEX fldr_group_view_idx ON folder_groups(view_all);
CREATE INDEX fldr_group_add_idx ON folder_groups(add_permission);
CREATE INDEX fldr_group_edit_idx ON folder_groups(edit_permission);
CREATE INDEX fldr_group_delete_idx ON folder_groups(delete_permission);

CREATE TABLE folder_categories (
  category_id BIGSERIAL PRIMARY KEY,
  folder_id BIGINT REFERENCES folders(folder_id) NOT NULL,
  name VARCHAR(255) NOT NULL,
  enabled BOOLEAN DEFAULT true
);

CREATE TABLE sub_folders (
  sub_folder_id BIGSERIAL PRIMARY KEY,
  folder_id BIGINT REFERENCES folders(folder_id) NOT NULL,
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
CREATE INDEX sub_folders_start_idx ON sub_folders(start_date);

CREATE TABLE files (
  file_id BIGSERIAL PRIMARY KEY,
  folder_id BIGINT REFERENCES folders(folder_id) NOT NULL,
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
  sub_folder_id BIGINT REFERENCES sub_folders(sub_folder_id),
  category_id BIGINT REFERENCES folder_categories(category_id)
);
CREATE INDEX files_tsv_idx ON files USING gin(tsv);
CREATE INDEX files_folder_id_idx ON files(folder_id);
CREATE INDEX files_created_idx ON files(created);
CREATE INDEX files_title_idx ON files(title);
CREATE INDEX files_sub_folder_idx ON files(sub_folder_id);
CREATE INDEX files_category_idx ON files(category_id);

CREATE TEXT SEARCH DICTIONARY file_stem (
    TEMPLATE = snowball,
    Language = english
);
CREATE TEXT SEARCH CONFIGURATION file_stem (copy = english);
ALTER TEXT SEARCH CONFIGURATION file_stem
   ALTER MAPPING FOR asciihword, asciiword, hword, hword_asciipart, hword_part, word
   WITH file_stem;

CREATE OR REPLACE FUNCTION files_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
    setweight(to_tsvector('file_stem', new.filename), 'A') ||
    setweight(to_tsvector('file_stem', coalesce(new.title,'')), 'B') ||
    setweight(to_tsvector('file_stem', coalesce(new.summary,'')), 'C') ||
    setweight(to_tsvector('file_stem', coalesce(new.document_text,'')), 'D');
  return new;
end
$$ LANGUAGE plpgsql;

CREATE TRIGGER tsvectorupdate BEFORE INSERT OR UPDATE
ON files FOR EACH ROW EXECUTE PROCEDURE files_tsv_trigger();

CREATE TABLE file_versions (
  version_id BIGSERIAL PRIMARY KEY,
  file_id BIGINT REFERENCES files(file_id) NOT NULL,
  folder_id BIGINT REFERENCES folders(folder_id) NOT NULL,
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
  sub_folder_id BIGINT REFERENCES sub_folders(sub_folder_id),
  category_id BIGINT REFERENCES folder_categories(category_id)
);
CREATE INDEX file_ver_file_id_idx ON file_versions(file_id);
CREATE INDEX file_ver_fold_id_idx ON file_versions(folder_id);
CREATE INDEX file_ver_created_idx ON file_versions(created);
CREATE INDEX file_ver_sub_fold_idx ON file_versions(sub_folder_id);

-- We want to know popular files
-- We want to know geolocation of ip_address
-- We want to know if this is a user or not
-- CREATE TABLE file_downloads (
--   id BIGSERIAL PRIMARY KEY,
--   file_id BIGINT,
--   version_id BIGINT,
--   download_by BIGINT REFERENCES users(user_id),
--   download_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
--   ip_address VARCHAR(200),
--   session_id VARCHAR(255),
--   is_logged_in BOOLEAN DEFAULT FALSE
-- );
-- CREATE INDEX file_downloads_dt_idx ON file_downloads(download_date);

-- We want to see a time-series graph of file downloads
-- CREATE TABLE file_download_snapshots (
--   snapshot_id BIGSERIAL PRIMARY KEY,
--   snapshot_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
--   date_value VARCHAR(10) NOT NULL,
--   file_id BIGINT,
--   downloads BIGINT DEFAULT 0
-- );
-- CREATE INDEX file_dl_snp_dt_idx ON file_download_snapshots(snapshot_date);
-- CREATE INDEX file_dl_snp_fid_idx ON file_download_snapshots(file_id);

-- Image Categories/Images
-- Video Categories/Videos

CREATE TABLE stylesheets (
  stylesheet_id BIGSERIAL PRIMARY KEY,
  web_page_id BIGINT REFERENCES web_pages(web_page_id),
  css TEXT,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX stylesheets_web_idx ON stylesheets(web_page_id);
