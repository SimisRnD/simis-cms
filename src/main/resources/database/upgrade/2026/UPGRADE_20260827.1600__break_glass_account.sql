-- Copyright 2026 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Marks an account as break-glass: its sign-ins (successful or failed) alert every other
-- administrator, and org-level MFA enforcement never redirects it to the enrollment page.
--
-- The exemption is deliberately narrow. Enforcement redirects every non-exempt request to the
-- enrollment page and exempts only that page, so an enforcement policy naming a role this account
-- holds would strand it exactly like any other admin -- which is the one situation a break-glass
-- account exists for. It is NOT exempt from MFA itself: if the account has MFA enrolled, the login
-- still demands a code, and its recovery codes cover a lost authenticator.
--
-- Added to install/NEW_10000__new_database.sql as well: a column that exists only here is missing
-- on every fresh install, because installs baseline the upgrade history rather than replaying it
-- (see DatabaseCommand.installDatabase).
ALTER TABLE users ADD COLUMN IF NOT EXISTS break_glass BOOLEAN DEFAULT false;

-- Mark the account the installer created, matched on the unique_id it is always given. Existing
-- sites get the same protection a fresh install now gets, without an admin having to know to set it.
UPDATE users SET break_glass = true WHERE unique_id = 'system-administrator';
