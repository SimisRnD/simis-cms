-- Issue #521: Moodle is the only e-learning integration with a real, working implementation
-- (RemoteCourseListWidget, CalendarAjaxMoodleEvents) -- reorder it to sort first on the
-- /admin/elearning-properties page. LRS and PERLS are kept (not removed) for historical/future
-- purposes, just sorted after Moodle.

UPDATE site_properties SET property_order = 10 WHERE property_name = 'elearning.moodle.enabled';
UPDATE site_properties SET property_order = 12 WHERE property_name = 'elearning.moodle.url';
UPDATE site_properties SET property_order = 14 WHERE property_name = 'elearning.moodle.token';

UPDATE site_properties SET property_order = 20 WHERE property_name = 'elearning.xapi.enabled';
UPDATE site_properties SET property_order = 22 WHERE property_name = 'elearning.lrs.url';
UPDATE site_properties SET property_order = 23 WHERE property_name = 'elearning.lrs.key';
UPDATE site_properties SET property_order = 24 WHERE property_name = 'elearning.lrs.secret';
UPDATE site_properties SET property_order = 26 WHERE property_name = 'elearning.lrs.authHeader';
