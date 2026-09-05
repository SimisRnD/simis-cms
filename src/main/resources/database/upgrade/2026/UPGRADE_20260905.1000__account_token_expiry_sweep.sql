-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Second sweep: date any account token that still has no expiry.
--
-- UPGRADE_20260904.1400 dated every outstanding token, and the code shipped alongside it stopped
-- minting undated ones. A narrow window sits between those two facts. Migrations run on the new
-- slot at startup, while the OLD instance is still serving -- so a sign-up or an invitation
-- completed in the minutes between the migration and the slot swap was written by the previous
-- code and carries no expiry, after the sweep that would have caught it had already run.
--
-- That matters now because the companion code change makes a missing expiry fail closed:
-- findByAccountToken no longer accepts "account_token_expires IS NULL". Without this sweep, a link
-- issued during that window would simply stop working, with nothing to explain why. Dating the
-- stragglers first means the stricter predicate cannot strand anyone.
--
-- Deliberately identical in shape to the first sweep, and idempotent for the same reason: only rows
-- with no expiry are touched, so a token that already carries one keeps it and no window is ever
-- extended twice. On a database where the first sweep did its job this updates nothing.
DO $$
DECLARE
  cleared INTEGER := 0;
  dated INTEGER := 0;
BEGIN
  UPDATE users
     SET account_token = NULL
   WHERE account_token IS NOT NULL
     AND account_token_expires IS NULL
     AND validated IS NOT NULL;
  GET DIAGNOSTICS cleared = ROW_COUNT;

  UPDATE users
     SET account_token_expires = NOW() + INTERVAL '7 days'
   WHERE account_token IS NOT NULL
     AND account_token_expires IS NULL;
  GET DIAGNOSTICS dated = ROW_COUNT;

  RAISE NOTICE 'Account token sweep: cleared % on validated accounts, dated % pending', cleared, dated;
END $$;
