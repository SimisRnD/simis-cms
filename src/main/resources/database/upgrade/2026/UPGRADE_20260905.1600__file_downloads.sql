-- One row per file download, so "what is getting downloaded" can be reported over a time window.
--
-- files.download_count already existed but is a cumulative counter with no dates on it, so it can
-- answer "most downloaded ever" and nothing else -- no last-7-days, no trend. The download requests
-- are not in web_page_hits either: DownloadFileWidget calls setHandledResponse(true), and
-- PageServlet returns as soon as a widget handles the response, which is before it records a hit.
-- So before this table there was no time-stamped record of a download anywhere in the database.
--
-- The shape follows the commented-out sketch that sat in install/NEW_10010__new_cms.sql under the
-- note "We want to know popular files", with one deliberate change: no ip_address column. The
-- sketch collected an IP for geolocation, which this report does not need, and an IP is PII that
-- would then need its own scrub. session_id is kept because the Content Analytics page states that
-- every number on it excludes known bots, and that exclusion is a join to sessions.is_bot.
--
-- Rows are pruned by FileDownloadRetentionJob using the same analytics.retentionDays window that
-- governs web_page_hits, so downloads do not outlive page views.
--
-- Paired with the same DDL in the other track, per issue #1478: install/NEW_10200 and this file.

CREATE TABLE file_downloads (
  file_download_id BIGSERIAL PRIMARY KEY,
  file_id BIGINT NOT NULL,
  version_id BIGINT,
  download_by BIGINT REFERENCES users(user_id),
  download_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  session_id VARCHAR(255)
);
CREATE INDEX file_downloads_dt_idx ON file_downloads(download_date);
CREATE INDEX file_downloads_fid_idx ON file_downloads(file_id);
