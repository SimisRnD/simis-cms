
UPDATE site_properties SET property_label = 'Text to display' WHERE property_name = 'site.header.line1';
UPDATE site_properties SET property_label = 'Optional title for link' WHERE property_name = 'site.header.link';
UPDATE site_properties SET property_label = 'Optional page to link to (/page)' WHERE property_name = 'site.header.page';

UPDATE site_properties SET property_label = 'Bar Background Color' WHERE property_name = 'theme.utilitybar.backgroundColor';
UPDATE site_properties SET property_label = 'Bar Text Color' WHERE property_name = 'theme.utilitybar.text.color';
UPDATE site_properties SET property_label = 'Bar Link Color' WHERE property_name = 'theme.utilitybar.link.color';
