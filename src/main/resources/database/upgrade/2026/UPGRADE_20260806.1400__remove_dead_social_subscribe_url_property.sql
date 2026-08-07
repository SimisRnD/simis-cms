-- social.subscribe.url ("Email Subscribe Link") was seeded alongside social.email/social.phone but
-- was never read anywhere in the application -- no JSP, widget, or command referenced it. Unlike
-- those two (rendered in layout-footer-standard.jspf), it had no wiring at all.
DELETE FROM site_properties WHERE property_name = 'social.subscribe.url';
