-- bi.enabled ("Enable bi?") only ever gates the Superset embed (SupersetWidget checks it directly;
-- MetabaseWidget/MetabaseEmbedCommand check bi.metabase.enabled instead, and the powerBi widget
-- checks neither, needing no site property at all) -- the generic label read as a site-wide BI
-- kill switch it never was. Relabeled to match the already-correct "Enable Metabase?" convention.
-- The other two renames are grammar-only (URL/ID capitalization), no behavior change.
UPDATE site_properties SET property_label = 'Enable Superset?' WHERE property_name = 'bi.enabled';
UPDATE site_properties SET property_label = 'Superset URL' WHERE property_name = 'bi.superset.url';
UPDATE site_properties SET property_label = 'Superset ID' WHERE property_name = 'bi.superset.id';
UPDATE site_properties SET property_label = 'Metabase URL' WHERE property_name = 'bi.metabase.url';
