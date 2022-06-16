
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (1, 'Enable e-learning?', 'elearning.enabled', 'true', 'boolean');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'Enable LRS xAPI?', 'elearning.xapi.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'LRS URL', 'elearning.lrs.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (13, 'LRS Key', 'elearning.lrs.key', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (14, 'LRS Secret', 'elearning.lrs.secret', '', 'text');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (20, 'Enable Moodle?', 'elearning.moodle.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (22, 'Moodle URL', 'elearning.moodle.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (24, 'Moodle Token', 'elearning.moodle.token', '', 'text');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (30, 'Enable PERLS?', 'elearning.perls.enabled', 'false', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (32, 'PERLS URL', 'elearning.perls.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (34, 'PERLS Client Id', 'elearning.perls.clientId', '', 'text');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (36, 'PERLS Secret', 'elearning.perls.secret', '', 'text');

INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (1, 'Enable web conferencing?', 'conferencing.enabled', 'true', 'boolean');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (10, 'BBB URL', 'conferencing.bbb.url', '', 'url');
INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES (12, 'BBB Secret', 'conferencing.bbb.secret', '', 'text');

INSERT INTO lookup_collection_role (level, code, title) VALUES (34, 'supervisor', 'Supervisor');
INSERT INTO lookup_collection_role (level, code, title) VALUES (38, 'instructor', 'Instructor');
INSERT INTO lookup_collection_role (level, code, title) VALUES (84, 'learner', 'Learner');
