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
  -- Bulk-actions archive state (issue #427), mirroring calendar_events.archived exactly -- see
  -- UPGRADE_20260804.1003__web_pages_archived.sql for the equivalent change to existing databases.
  archived TIMESTAMP(3) DEFAULT NULL,
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
  sitemap_changefreq VARCHAR(20),
  publish_at TIMESTAMP,
  expires_at TIMESTAMP,
  solution_type VARCHAR(255),
  -- Governed publish workflow (issue #407), mirroring content's own draft_status/submitted_by/
  -- approved_by/release_reference exactly -- see ContentReviewCommand/Reviewable.
  draft_status VARCHAR(20),
  submitted_by BIGINT DEFAULT -1,
  approved_by BIGINT DEFAULT -1,
  release_reference VARCHAR(255),
  -- issue #497 cheap-tier slice: see UPGRADE_20260810.1300__web_pages_internal_and_redirect_notes.sql
  -- for the equivalent change to existing databases, and why role_id_list above wasn't reused for this
  -- (persisted but never actually consulted anywhere for access control).
  internal BOOLEAN DEFAULT false,
  redirect_notes VARCHAR(500),
  -- Multi-language content variants (#414). locale is BCP 47;
  -- translation_group is shared by every locale variant of the same content.
  locale VARCHAR(35) NOT NULL DEFAULT 'en',
  translation_group VARCHAR(255)
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
  content_format INTEGER NOT NULL DEFAULT 0,
  draft_content_format INTEGER NOT NULL DEFAULT 0,
  draft_status VARCHAR(20),
  submitted_by BIGINT DEFAULT -1,
  approved_by BIGINT DEFAULT -1,
  release_reference VARCHAR(255),
  content_text TEXT,
  tsv TSVECTOR,
  -- Multi-language content variants (#414). locale is BCP 47;
  -- translation_group is shared by every locale variant of the same content.
  locale VARCHAR(35) NOT NULL DEFAULT 'en',
  translation_group VARCHAR(255)
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
  web_path VARCHAR(50) NOT NULL,
  -- Focal point as a 0-100 percentage of width/height (issue #411 PR3), so a fixed-aspect crop can
  -- center on the subject instead of blind-center-cropping. NOT NULL with a dead-center default so
  -- every row is always valid with no null-handling anywhere downstream. See
  -- UPGRADE_20260804.1901__image_focal_point.sql for existing databases.
  focal_x NUMERIC(5,2) NOT NULL DEFAULT 50.00,
  focal_y NUMERIC(5,2) NOT NULL DEFAULT 50.00,
  -- Content hash for duplicate detection (issue: image dedup tool), "ALGORITHM;hexdigest" format
  -- via FileSystemCommand.getFileChecksum(), same convention as files/item_files/datasets.file_hash.
  -- Nullable -- see UPGRADE_20260818.1600__image_file_hash.sql for existing databases.
  file_hash VARCHAR(1024),
  -- Library-level alt text, editable in the admin Media Library. Not yet wired into any public
  -- <img> rendering -- see UPGRADE_20260819.0900__image_alt_text.sql for existing databases.
  alt_text VARCHAR(255)
);
CREATE INDEX images_created_idx ON images(created);
CREATE INDEX images_web_path_idx ON images(web_path);
CREATE INDEX images_file_hash_idx ON images(file_hash);

-- Tags for images. Unlike items' tags (see NEW_10024__new_items.sql), images have no collection
-- concept -- a tag here is a single global label, not scoped per anything. See
-- UPGRADE_20260805.1250__image_tags.sql for existing databases.
-- No image_count column here (unlike items' tags): keeping a running counter in sync would mean
-- touching it at every image-tag assign/unassign AND image-delete code path. Since this is a small,
-- admin-only pool, the tag-management panel just counts image_tag_map rows live instead.
CREATE TABLE image_tags (
  image_tag_id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  created_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX image_tags_name_uidx ON image_tags(LOWER(name));

CREATE TABLE image_tag_map (
  id BIGSERIAL PRIMARY KEY,
  image_id BIGINT REFERENCES images(image_id) NOT NULL,
  image_tag_id BIGINT REFERENCES image_tags(image_tag_id) NOT NULL
);
CREATE UNIQUE INDEX image_tag_map_uidx ON image_tag_map(image_id, image_tag_id);
CREATE INDEX image_tag_map_image_idx ON image_tag_map(image_id);
CREATE INDEX image_tag_map_tag_idx ON image_tag_map(image_tag_id);

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

-- Rejected form submissions (issue #563): deliberately lean, no field_values -- most of this volume is
-- bot/spam noise (captcha failures, rate-limited requests) not worth persisting PII for. A rejection here
-- never has a corresponding form_data row -- that table only ever contains successfully-saved submissions.
CREATE TABLE form_submission_failures (
  failure_id BIGSERIAL PRIMARY KEY,
  form_unique_id VARCHAR(255),
  occurred TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  reason VARCHAR(30) NOT NULL,
  ip_address VARCHAR(200),
  url VARCHAR(512)
);
CREATE INDEX form_sub_fail_form_idx ON form_submission_failures(form_unique_id);
CREATE INDEX form_sub_fail_occurred_idx ON form_submission_failures(occurred);
CREATE INDEX form_sub_fail_reason_idx ON form_submission_failures(reason);

-- Form builder (issue #409): database-backed field configuration for FormWidget, as an alternative to
-- the XML <fields> preference. form_fields.form_definition_id has no ON DELETE CASCADE -- deleting a
-- form definition explicitly removes its fields first, in a transaction (see
-- FormDefinitionRepository#remove), mirroring how MenuTabRepository#remove handles menu_items and
-- MailingListRepository#remove handles mailing_list_members in this same file's neighborhood, rather
-- than image_variants' DB-level ON DELETE CASCADE. form_data is untouched by this table pair:
-- submissions are matched to a form by form_unique_id (a plain string), never by a foreign key to
-- form_definitions, so deleting a form definition never blocks on or orphans prior submissions.
CREATE TABLE form_definitions (
  form_definition_id BIGSERIAL PRIMARY KEY,
  unique_id VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  title VARCHAR(255),
  subtitle VARCHAR(255),
  button_name VARCHAR(100),
  success_title VARCHAR(255),
  success_message TEXT,
  email_to VARCHAR(512),
  use_captcha BOOLEAN DEFAULT FALSE,
  check_for_spam BOOLEAN DEFAULT TRUE,
  enabled BOOLEAN DEFAULT TRUE,
  show_privacy_notice BOOLEAN DEFAULT FALSE,
  send_confirmation_to_submitter BOOLEAN DEFAULT FALSE,
  confirmation_subject VARCHAR(255),
  confirmation_message TEXT,
  created_by BIGINT REFERENCES users(user_id),
  modified_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX form_definitions_unique_id_idx ON form_definitions(unique_id);
CREATE INDEX form_definitions_enabled_idx ON form_definitions(enabled);

-- field_type is one of: text, email, textarea, select, checkbox, date -- validated in application code,
-- not a DB CHECK constraint (matching how this file leaves other small admin-defined enums, e.g.
-- form_submission_failures.reason, unconstrained at the DB level). options stores select/checkbox
-- choices using the same comma-separated "key=value,key2=value2" string the XML <field list="..."/>
-- preference already produces (see FormFieldCommand#parseFieldContent), so both configuration sources
-- share one options format.
CREATE TABLE form_fields (
  form_field_id BIGSERIAL PRIMARY KEY,
  form_definition_id BIGINT NOT NULL REFERENCES form_definitions(form_definition_id),
  field_order INTEGER DEFAULT 100,
  name VARCHAR(255) NOT NULL,
  label VARCHAR(255) NOT NULL,
  field_type VARCHAR(30) DEFAULT 'text',
  required BOOLEAN DEFAULT FALSE,
  placeholder VARCHAR(255),
  default_value VARCHAR(255),
  options TEXT,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX form_fields_form_definition_idx ON form_fields(form_definition_id);
CREATE INDEX form_fields_order_idx ON form_fields(field_order);

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
CREATE INDEX web_pg_hits_wpid_idx ON web_page_hits(web_page_id);

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

-- Search analytics: zero-result queries and trending search terms (#424). Deliberately separate
-- from web_searches above -- see search_analytics's own upgrade migration for why.
CREATE TABLE search_analytics (
  search_analytics_id BIGSERIAL PRIMARY KEY,
  query VARCHAR(255) NOT NULL,
  search_type VARCHAR(50) NOT NULL,
  result_count INTEGER NOT NULL DEFAULT 0,
  page_path VARCHAR(255),
  facet_key VARCHAR(100),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX search_analytics_created_idx ON search_analytics(created);
CREATE INDEX search_analytics_query_idx ON search_analytics(query);

-- Conversion funnel events (issue #565, phase 1): one row per stage event, e.g. a contact-form page
-- view, a successful submission, or an admin marking a submission processed. funnel_key names the
-- logical funnel ('contact-form' for this phase) so later phases (newsletter signup, solution-page
-- engagement) can reuse this same table with a different funnel_key/stage set -- no schema change.
-- Deliberately a raw event log, not a pre-aggregated daily-counts table: recording is a single-row
-- insert (mirrors search_analytics/form_submission_failures), and session_id is kept so a later phase
-- can attempt same-session stage correlation without a migration, even though phase 1's own report
-- only needs simple per-stage COUNT(*) totals, not per-visitor stitching.
CREATE TABLE funnel_events (
  funnel_event_id BIGSERIAL PRIMARY KEY,
  funnel_key VARCHAR(50) NOT NULL,
  stage VARCHAR(30) NOT NULL,
  session_id VARCHAR(255),
  occurred TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX funnel_events_key_stage_idx ON funnel_events(funnel_key, stage);
CREATE INDEX funnel_events_occurred_idx ON funnel_events(occurred);

-- System health check history: one row per (service, check run), populated by SystemHealthJob
-- (issue #466). service_name is currently 'database' or 'filesystem' -- the two HealthCommand
-- checks that can flip from healthy to unhealthy after startup; see system_health_checks's own
-- upgrade migration for why 'startup' isn't tracked here.
CREATE TABLE system_health_checks (
  system_health_check_id BIGSERIAL PRIMARY KEY,
  service_name VARCHAR(50) NOT NULL,
  status VARCHAR(10) NOT NULL,
  response_time_ms INTEGER,
  error_message VARCHAR(500),
  checked_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX system_health_checks_checked_at_idx ON system_health_checks(checked_at);
CREATE INDEX system_health_checks_service_name_idx ON system_health_checks(service_name);

-- Recent service errors: one row per uncaught exception that reached ServiceErrorLoggingFilter
-- (issue #556). This is the "recent service errors" half of the Health Dashboard's acceptance
-- criteria -- errors previously only reached stdout/the application log, with nothing queryable
-- from inside the app itself.
CREATE TABLE service_errors (
  service_error_id BIGSERIAL PRIMARY KEY,
  request_uri VARCHAR(500),
  exception_class VARCHAR(255) NOT NULL,
  message VARCHAR(1000),
  stack_trace TEXT,
  occurred_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX service_errors_occurred_at_idx ON service_errors(occurred_at);

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
  -- Overrides the feed's composed "<site name> - <blog name>" title. Null keeps that default.
  feed_title VARCHAR(255),
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
  -- Curated link posts (#1420): when set, the headline, "read more" and the feed entry's
  -- rel="alternate" link point at the original article instead of this post's own page. The
  -- post still has its own permalink, which stays the feed entry's <id>.
  source_url VARCHAR(512),
  script_embed VARCHAR(512),
  tags_list VARCHAR(255),
  keywords VARCHAR(255),
  body_text TEXT,
  -- Governed publish workflow (issue #407, phase 2), mirroring web_pages'/content's own
  -- draft_status/submitted_by/approved_by/release_reference exactly -- see
  -- ContentReviewCommand/Reviewable. A blog post has no separate draft/live content split (unlike
  -- web_pages' page_xml/draft_page_xml), so these columns govern only the initial
  -- unpublished -> published transition.
  draft_status VARCHAR(20),
  submitted_by BIGINT DEFAULT -1,
  approved_by BIGINT DEFAULT -1,
  release_reference VARCHAR(255),
  -- Multi-language content variants (#414). locale is BCP 47;
  -- translation_group is shared by every locale variant of the same content.
  locale VARCHAR(35) NOT NULL DEFAULT 'en',
  translation_group VARCHAR(255),
  -- Syndication opt-out: a post stays published, searchable, and at its own URL, but is left
  -- out of the RSS/Atom feeds. Archiving already hides a post everywhere at once, which is the
  -- wrong tool when the post should remain readable -- see FeedServlet.
  exclude_from_feed BOOLEAN NOT NULL DEFAULT false
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
-- Mirrors UPGRADE_20260802.1010__blog_post_tags_unique_index.sql (issue #633) so a fresh install
-- gets the same guarantee against duplicate (post_id, tag_id) rows as an upgraded database.
CREATE UNIQUE INDEX IF NOT EXISTS blog_post_tags_uidx ON blog_post_tags(post_id, tag_id);


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
  has_categories BOOLEAN DEFAULT FALSE,
  allowed_extensions VARCHAR(500)
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
  category_id BIGINT REFERENCES folder_categories(category_id),
  web_path VARCHAR(50) NOT NULL
);
CREATE INDEX files_tsv_idx ON files USING gin(tsv);
CREATE INDEX files_folder_id_idx ON files(folder_id);
CREATE INDEX files_created_idx ON files(created);
CREATE INDEX files_title_idx ON files(title);
CREATE INDEX files_sub_folder_idx ON files(sub_folder_id);
CREATE INDEX files_category_idx ON files(category_id);
CREATE INDEX files_web_path_idx ON files(web_path);

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
  category_id BIGINT REFERENCES folder_categories(category_id),
  web_path VARCHAR(50) NOT NULL
);
CREATE INDEX file_ver_file_id_idx ON file_versions(file_id);
CREATE INDEX file_ver_fold_id_idx ON file_versions(folder_id);
CREATE INDEX file_ver_created_idx ON file_versions(created);
CREATE INDEX file_ver_sub_fold_idx ON file_versions(sub_folder_id);
CREATE INDEX file_ver_web_path_idx ON file_versions(web_path);

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

-- Web page version history (#405): one row per publish, holding the outgoing page_xml that was
-- just overwritten -- so a prior published state can be viewed, compared, or restored. Rows are
-- pruned to a configurable cap (webPage.versionHistoryLimit) on insert; cascades on page deletion.
CREATE TABLE web_page_versions (
  web_page_version_id BIGSERIAL PRIMARY KEY,
  web_page_id BIGINT REFERENCES web_pages(web_page_id) ON DELETE CASCADE,
  page_xml TEXT,
  published_by BIGINT REFERENCES users(user_id),
  published_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  label VARCHAR(255)
);
CREATE INDEX web_page_versions_web_idx ON web_page_versions(web_page_id, published_at DESC);

-- Content block version history (#406): one row per ContentRepository.publish() call, holding the
-- OUTGOING content (the value about to be overwritten), rendered to plain HTML so a block that
-- mixes Delta and legacy-HTML publishes over time still has a uniformly diffable history. Rows are
-- pruned to a configurable cap (content.versionHistoryLimit) on insert; cascades on content
-- deletion. Mirrors web_page_versions above, and must stay identical to the table created by
-- UPGRADE_20260804.1010__content_versions.sql for existing installs.
CREATE TABLE content_versions (
  content_version_id BIGSERIAL PRIMARY KEY,
  content_id BIGINT REFERENCES content(content_id) ON DELETE CASCADE,
  content TEXT,
  approved_by BIGINT REFERENCES users(user_id),
  published_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
  release_reference VARCHAR(255)
);
CREATE INDEX content_versions_content_idx ON content_versions(content_id, published_at DESC);

-- Draft preview links (#419): a time-limited bearer token that lets an anonymous visitor holding
-- the link view a page's current draftPageXml at its real URL, before it's reviewed or published.
-- Deliberately NOT tied to a specific web_page_versions row -- the preview always reflects
-- whatever is currently in draftPageXml, the same live-updating view an editor already gets in
-- pageEditMode (see PageServlet's parseFreshDraft usage). Expiry is enforced SQL-side by every
-- lookup, so an expired row is simply inert rather than requiring a cleanup job to be correct.
-- page_path pins the token to the exact URL it was minted for -- web_page_id alone is not enough
-- because a wildcard page (link ending "/*", e.g. "/news/*") backs many distinct URLs from one row,
-- and a token scoped only to web_page_id would validate against every one of them, not just the
-- single URL the link recipient was shown (review finding on this issue). Every outstanding token
-- for a page is also deleted the moment its draft is published or discarded (see
-- WebPageRepository.publish()/removeDraft()), so a still-unexpired link can never later resurface
-- a different, unrelated draft than the one it was generated for.
CREATE TABLE web_page_preview_tokens (
  web_page_preview_token_id BIGSERIAL PRIMARY KEY,
  web_page_id BIGINT REFERENCES web_pages(web_page_id) ON DELETE CASCADE,
  page_path VARCHAR(255) NOT NULL,
  token VARCHAR(255) UNIQUE NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  created_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX web_page_preview_tokens_token_idx ON web_page_preview_tokens(token);

-- Core Web Vitals RUM (Real User Monitoring, #429)
-- Raw metrics collected from real page loads, one row per metric per page load
CREATE TABLE web_vitals (
  id BIGSERIAL PRIMARY KEY,
  url VARCHAR(2048) NOT NULL,
  metric_type VARCHAR(50) NOT NULL,  -- 'LCP', 'CLS', 'INP', 'FCP', 'TTFB'
  value NUMERIC(10, 2) NOT NULL,     -- metric value (milliseconds for timing, unitless for CLS)
  rating VARCHAR(20),                 -- 'good', 'needs-improvement', 'poor'
  session_id VARCHAR(64),             -- visitor session (optional, for correlation)
  web_page_id BIGINT REFERENCES web_pages(web_page_id) ON DELETE CASCADE,
  user_agent_hash VARCHAR(64),
  viewport_width SMALLINT,
  connection_type VARCHAR(16),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT metric_type_check CHECK (metric_type IN ('LCP', 'CLS', 'INP', 'FCP', 'TTFB'))
);
CREATE INDEX idx_web_vitals_url_metric_created ON web_vitals(url, metric_type, created_at DESC);
CREATE INDEX idx_web_vitals_created ON web_vitals(created_at DESC);
CREATE INDEX idx_web_vitals_metric ON web_vitals(metric_type);
CREATE INDEX idx_web_vitals_web_page_id ON web_vitals(web_page_id);

-- Pre-computed p50/p75/p95 per URL per metric, refreshed nightly from raw web_vitals rows
CREATE TABLE web_vitals_aggregates (
  id BIGSERIAL PRIMARY KEY,
  url VARCHAR(2048) NOT NULL,
  metric_type VARCHAR(50) NOT NULL,
  p50_value NUMERIC(10, 2),
  p75_value NUMERIC(10, 2),
  p95_value NUMERIC(10, 2),
  sample_count INTEGER NOT NULL DEFAULT 0,
  aggregated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT web_vitals_aggregates_metric_type_check CHECK (metric_type IN ('LCP', 'CLS', 'INP', 'FCP', 'TTFB')),
  CONSTRAINT web_vitals_aggregates_url_metric_day_unique UNIQUE (url, metric_type, aggregated_at)
);
CREATE INDEX idx_web_vitals_aggregates_url_metric ON web_vitals_aggregates(url, metric_type);
CREATE INDEX idx_web_vitals_aggregates_aggregated_at ON web_vitals_aggregates(aggregated_at DESC);
