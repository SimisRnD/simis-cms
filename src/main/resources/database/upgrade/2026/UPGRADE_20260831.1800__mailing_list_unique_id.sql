-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Follow-up to issue #1724: give mailing_lists the stable key that page configuration can point at.
--
-- The emailSubscribe widget carries its list as a *name* (<mailingList>Newsletter</mailingList>),
-- and name is an admin-editable field on the Admin/Mailing Lists form, so renaming a list broke
-- every page still naming the old value. PR #1727 made that fail closed rather than silently
-- recreating a duplicate; this is the fragility underneath it. blogs solved the same problem with
-- blog_unique_id -- the widget's blogUniqueId preference points at an id that survives a rename --
-- and this is that column for mailing_lists. Install parity: NEW_10070 (DDL) and NEW_10071 (the
-- seeded Newsletter row's 'newsletter' id).
--
-- Expand-only, deliberately: the column is added and backfilled before it is made NOT NULL, and
-- nothing is dropped or renamed, so an instance still running the previous build during a rolling
-- deploy keeps working -- it simply never reads or writes the new column.
ALTER TABLE mailing_lists ADD COLUMN unique_id VARCHAR(255);

-- Backfill, mirroring GenerateMailingListUniqueIdCommand (which mirrors
-- MakeContentUniqueIdCommand): lowercase, '&' becomes "and", spaces and slashes become '-',
-- anything else is dropped rather than replaced, runs of '-' collapse, and leading/trailing '-'
-- are trimmed. A name that reduces to nothing at all (punctuation only) falls back to 'list', and
-- a collision takes the next free '-2', '-3', ... suffix -- the same numbering the Java command
-- applies when an admin creates a second list that slugifies the same way. (One deliberate
-- difference: the Java version trims only a trailing '-', so a name literally starting with '-' or
-- '/' would keep a leading one. Trimming both ends here is strictly nicer and cannot collide,
-- because the loop below still probes for a free id.)
--
-- Row-at-a-time in plpgsql rather than a windowed UPDATE on purpose: numbering by ROW_NUMBER()
-- alone can still collide with a literal name (two lists called "News" plus one called "News 2"),
-- whereas probing for the next id that is not already taken cannot.
DO $$
DECLARE
  list RECORD;
  base TEXT;
  candidate TEXT;
  suffix INT;
BEGIN
  FOR list IN SELECT list_id, name FROM mailing_lists ORDER BY list_id LOOP
    base := COALESCE(NULLIF(
        TRIM(BOTH '-' FROM
          REGEXP_REPLACE(
            REGEXP_REPLACE(
              REGEXP_REPLACE(REPLACE(LOWER(list.name), '&', 'and'), '[ /]+', '-', 'g'),
              '[^a-z0-9-]', '', 'g'),
            '-+', '-', 'g')), ''), 'list');
    candidate := base;
    suffix := 1;
    WHILE EXISTS (SELECT 1 FROM mailing_lists WHERE unique_id = candidate) LOOP
      suffix := suffix + 1;
      candidate := base || '-' || suffix;
    END LOOP;
    UPDATE mailing_lists SET unique_id = candidate WHERE list_id = list.list_id;
  END LOOP;
END $$;

-- Every row now has a value, so the constraints can go on. UNIQUE is what makes this an id rather
-- than another free-text field; NOT NULL is what lets MailingListRepository.buildRecord() and
-- findByUniqueId() treat it as always present.
ALTER TABLE mailing_lists ADD CONSTRAINT mailing_lists_unique_id_key UNIQUE (unique_id);
ALTER TABLE mailing_lists ALTER COLUMN unique_id SET NOT NULL;
