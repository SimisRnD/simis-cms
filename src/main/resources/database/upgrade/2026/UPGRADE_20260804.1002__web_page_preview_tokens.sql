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
-- Idempotent so it is safe on any existing install; fresh installs get the identical table from NEW_10010.
CREATE TABLE IF NOT EXISTS web_page_preview_tokens (
  web_page_preview_token_id BIGSERIAL PRIMARY KEY,
  web_page_id BIGINT REFERENCES web_pages(web_page_id) ON DELETE CASCADE,
  page_path VARCHAR(255) NOT NULL,
  token VARCHAR(255) UNIQUE NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  created_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS web_page_preview_tokens_token_idx ON web_page_preview_tokens(token);

-- Issue #419: how long a generated draft-preview link stays valid before it stops showing the
-- draft and silently falls back to the live page. Under the "security" prefix so it surfaces on
-- the Security Settings page and requires step-up auth to change, like security.ipRequestRateAlertThreshold.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (40, 'Draft Preview Link Expiry (hours)', 'security.previewLinkTtlHours', '24', 'text')
ON CONFLICT (property_name) DO NOTHING;
