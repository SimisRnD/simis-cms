-- Issue #641: admin-manageable, database-backed IP allow list (previously the allow list
-- was a server-side file only, config/cms/ip-allow-list.csv, with no admin UI). Mirrors the
-- existing block_list table so the allow list gets the same CRUD/CSV/paging admin UI.

CREATE TABLE allow_list (
  allow_list_id BIGSERIAL PRIMARY KEY,
  ip_address VARCHAR(200) UNIQUE NOT NULL,
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  reason VARCHAR(255)
);
