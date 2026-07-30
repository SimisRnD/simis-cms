## Summary
<!-- What problem does this solve, and why? -->

## Test plan
<!-- How was this verified? e.g. new/updated unit tests, `ant -lib lib/war -lib lib/tests ci-test`, manual/live verification steps. -->

- [ ] Does this migration require an expand/contract split? (destructive schema change —
      `DROP COLUMN`/`DROP TABLE`/rename — landing in the same version as the Java field or
      accessor it backs). If yes, see the "Expand/Contract Migrations" runbook in
      `simis-cms-runbooks` and split into an expand PR (add new structure, deploy compat code)
      followed by a later contract PR (remove old structure) instead of doing both at once.
