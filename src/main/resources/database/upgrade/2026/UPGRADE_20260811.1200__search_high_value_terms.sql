-- Admin-curated watchlist of business-critical search terms (e.g. product/service names), so an
-- operator can confirm these specific terms are being found successfully and track their search
-- volume -- unlike search.zeroResultAlertThreshold/the zero-result and near-miss reports, which
-- are all about terms that are failing. Comma-separated, blank by default (feature stays inert
-- until an admin opts in), matching the funnel.contactForm.* site-property precedent.
INSERT INTO site_properties (property_label, property_name, property_value, property_type)
VALUES ('High-Value Search Terms (comma-separated)', 'search.highValueTerms', '', 'text')
ON CONFLICT (property_name) DO NOTHING;
