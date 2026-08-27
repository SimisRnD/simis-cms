-- Adds the security.iframe.allowedHosts site property, which names the hosts whose iframe embeds
-- this site permits. Two things read the same list: HtmlCommand strips an embed from any other host
-- when content is saved, and PageServlet emits it as the Content-Security-Policy frame-src
-- directive so the browser refuses to load one. Neither layer is redundant -- the sanitizer tells
-- an author while they can still fix it, and frame-src still applies to content that was stored
-- some other way (published before a host was removed, written directly to the database, or slipped
-- past a sanitizer bug).
--
-- Seeds empty, which is the safe default and changes nothing an existing site does today. Empty
-- does not mean "allow everything": AllowedIframeHostCommand always includes the hosts the
-- platform's own widgets require (the Video widget's youtube-nocookie.com and player.vimeo.com),
-- and adds the configured Metabase host when bi.metabase.enabled is true, so the Video and
-- Metabase widgets keep working on every existing site without an admin touching this.
--
-- A site embedding some other vendor -- a careers board, a scheduling form, a map -- has to add
-- that host here before those embeds will render. That is the intended behavior of turning the
-- directive on, but it is a behavior change for those sites, so it is worth checking published
-- content for third-party iframes before upgrading.
--
-- property_order 30 puts it after the two password properties on /admin/security-properties,
-- matching where NEW_10000__new_database.sql puts it on a fresh install. Plain idempotent insert,
-- since the property never existed before this migration.
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type)
VALUES (30, 'Additional iframe embed hosts', 'security.iframe.allowedHosts', '', 'text')
ON CONFLICT (property_name) DO NOTHING;
