-- Cap for paged remote dataset downloads; prevents unbounded heap accumulation
-- from sources that serve an excessive number of pages. Operators can tune this
-- via Admin > Site Properties without a code change.
INSERT INTO site_properties (property_order, property_label, property_name, property_value)
VALUES (10, 'Dataset max rows (paged download)', 'dataset.maxRows', '100000')
ON CONFLICT (property_name) DO NOTHING;
