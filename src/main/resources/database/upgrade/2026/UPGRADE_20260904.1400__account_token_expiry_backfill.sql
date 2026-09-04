-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Give every outstanding account token an expiry.
--
-- UserRepository.add() minted account_token without account_token_expires, the column has no
-- DEFAULT, and findByAccountToken treats a missing expiry as valid indefinitely
-- ("account_token_expires IS NULL OR account_token_expires > NOW()"). The 24-hour lifetime added by
-- UPGRADE_20260725.1001 therefore only ever applied to tokens from createAccountToken -- password
-- reset and unsuspend. Tokens issued at self sign-up and by admin account creation had none. The
-- companion code change dates them at creation; this dates the rows already written.
--
-- Two populations, treated differently on purpose.
--
-- Already-activated accounts (validated IS NOT NULL) should not be holding a token at all --
-- updateValidated and updatePassword both clear it on completion, so one still present is a
-- leftover from before those paths dated it. Nothing is waiting on it, so it is cleared outright
-- rather than dated. Clearing is the same operation completing the flow would have performed.
--
-- Genuinely pending accounts keep a working link: they are given a fresh window rather than being
-- expired immediately, so invitations someone is still waiting on do not all die at deploy and turn
-- into reissue requests. The window matches UserRepository.ACTIVATION_TOKEN_MILLIS (7 days). This is
-- a deliberate, bounded extension of tokens that until now had no limit at all -- the alternative,
-- expiring them on the spot, is stricter but breaks every outstanding invitation.
--
-- Only rows with no expiry are touched. A token that already carries one -- every password reset,
-- and anything written by the fixed code -- keeps the expiry it was given, so re-running changes
-- nothing and no window is ever extended twice.
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

  RAISE NOTICE 'Account tokens: cleared % on already-validated accounts, dated % pending', cleared, dated;
END $$;
