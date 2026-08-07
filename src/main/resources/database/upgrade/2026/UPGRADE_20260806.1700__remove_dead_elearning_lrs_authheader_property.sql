-- elearning.lrs.authHeader ("LRS Auth Header") was seeded and registered as a secret, but nothing
-- in the application ever reads it back -- ElearningCommand.isLRSEnabled() only ever consults
-- elearning.lrs.url/key/secret. An admin filling this in would see no effect at all.
DELETE FROM site_properties WHERE property_name = 'elearning.lrs.authHeader';
