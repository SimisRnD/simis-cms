-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Mailing Lists

-- Web Sign-ups
-- Online Customers
-- Store Customers
-- Influencers

CREATE TABLE emails (
  email_id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  first_name VARCHAR(100),
  last_name VARCHAR(100),
  organization VARCHAR(100),
  source VARCHAR(50),
  ip_address VARCHAR(200),
  session_id VARCHAR(255),
  user_agent VARCHAR(500),
  referer VARCHAR(255),
  continent VARCHAR(20),
  country_iso VARCHAR(2),
  country VARCHAR(100),
  city VARCHAR(100),
  state_iso VARCHAR(3),
  state VARCHAR(100),
  postal_code VARCHAR(50),
  timezone VARCHAR(50),
  latitude float,
  longitude float,
  metro_code INTEGER,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  subscribed TIMESTAMP(3),
  unsubscribed TIMESTAMP(3),
  unsubscribe_reason VARCHAR(100),
  last_emailed TIMESTAMP(3),
  last_interaction TIMESTAMP(3),
  last_order TIMESTAMP(3),
  number_of_orders INTEGER DEFAULT 0,
  total_spent NUMERIC(15,6) DEFAULT 0,
  tags JSONB,
  sync_date TIMESTAMP(3)
);

CREATE INDEX emails_created_idx ON emails(created);
CREATE INDEX emails_email_idx ON emails(email);
CREATE INDEX emails_sub_idx ON emails(subscribed);
CREATE INDEX emails_unsub_idx ON emails(unsubscribed);
CREATE INDEX emails_last_ord_idx ON emails(last_order);
CREATE INDEX emails_num_ord_idx ON emails(number_of_orders);
CREATE INDEX emails_total_sp_idx ON emails(total_spent);
CREATE INDEX emails_sync_date_idx ON emails(sync_date);

CREATE TABLE mailing_lists (
  list_id BIGSERIAL PRIMARY KEY,
  list_order INTEGER DEFAULT 100,
  name VARCHAR(200) NOT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT,
  member_count INTEGER DEFAULT 0,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  last_emailed TIMESTAMP(3),
  show_online BOOLEAN DEFAULT false,
  enabled BOOLEAN DEFAULT true
);
CREATE INDEX mail_list_ord_idx ON mailing_lists(list_order);
CREATE INDEX mail_list_cre_idx ON mailing_lists(created);
CREATE INDEX mail_list_online_idx ON mailing_lists(show_online);
CREATE INDEX mail_list_enable_idx ON mailing_lists(enabled);

CREATE TABLE mailing_list_members (
  member_id BIGSERIAL PRIMARY KEY,
  list_id BIGINT REFERENCES mailing_lists(list_id),
  email_id BIGINT REFERENCES emails(email_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  modified_by BIGINT REFERENCES users(user_id),
  last_emailed TIMESTAMP(3),
  last_click TIMESTAMP(3),
  unsubscribed TIMESTAMP(3),
  unsubscribed_by BIGINT REFERENCES users(user_id),
  unsubscribe_reason VARCHAR(100),
  is_valid BOOLEAN DEFAULT true
);
CREATE UNIQUE INDEX mail_lis_mem_uniq_idx ON mailing_list_members(list_id, email_id);
CREATE INDEX mail_lis_mem_lid_idx ON mailing_list_members(list_id);
CREATE INDEX mail_lis_mem_eid_idx ON mailing_list_members(email_id);
CREATE INDEX mail_lis_mem_val_idx ON mailing_list_members(is_valid);

CREATE TABLE mailing_list_history (
  history_id BIGSERIAL PRIMARY KEY,
  list_id BIGINT REFERENCES mailing_lists(list_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT REFERENCES users(user_id),
  service VARCHAR(20),
  email_count INTEGER DEFAULT 0
);
CREATE INDEX mail_lis_his_lid_idx ON mailing_list_history(list_id);
CREATE INDEX mail_lis_his_cre_idx ON mailing_list_history(created);

CREATE TABLE mailing_list_sent (
  item_id BIGSERIAL PRIMARY KEY,
  email_id BIGINT REFERENCES emails(email_id),
  list_id BIGINT REFERENCES mailing_lists(list_id),
  history_id BIGINT REFERENCES mailing_list_history(history_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX mail_list_sent_em_idx ON mailing_list_sent(email_id);
CREATE INDEX mail_list_sent_li_idx ON mailing_list_sent(list_id);
CREATE INDEX mail_list_sent_hi_idx ON mailing_list_sent(history_id);
CREATE INDEX mail_list_sent_cr_idx ON mailing_list_sent(created);

