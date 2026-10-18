-- Optional ticket details for a calendar event: the lowest price to attend, its currency, whether
-- tickets are on sale, and when sales open. Same columns as NEW_10010__new_cms.sql, added here for
-- databases that already exist.
--
-- These exist because Search Console reports "price", "priceCurrency", "availability" and
-- "validFrom" as missing in "offers" on every Event whose offer carries only a sign-up URL -- four
-- warnings per event, and nothing on record could truthfully fill them. They are nullable and
-- unset; StructuredDataCommand emits an offer only once a price is entered, and the event page shows
-- the same price, since structured data must describe what a visitor can see.

ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS ticket_price NUMERIC(10,2);
ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS ticket_currency VARCHAR(3);
ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS ticket_availability VARCHAR(20);
ALTER TABLE calendar_events ADD COLUMN IF NOT EXISTS ticket_sales_start TIMESTAMP(3);
