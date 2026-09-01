# Database migrations

Two independent Flyway tracks, each with its own history table:

| track | prefix | history table | when it runs |
| --- | --- | --- | --- |
| `install/` | `NEW_` | `flyway_install` | once, only when the database is not yet installed |
| `upgrade/` | `UPGRADE_` | `flyway_history` | on every start, for databases that are already installed |

A schema change usually needs **both** — one file per track, added in the **same pull request**.

## Add both files together, in the same PR

This is not style. A change whose two files are added at different times can reach **neither** track,
because a fresh install baselines the upgrade track above every upgrade that exists at install time:

```
Du   the upgrade file is added
     ...  a database installed anywhere in here gets the change from NEITHER track  ...
Di   the install file is added
```

The upgrade sits at or below that database's baseline and is recorded as already applied without
running; the install track never runs again once a database is installed. Both files exist, both are
correct, and nothing reports it.

That happened to `web_pages`' full-text index — the upgrade file was written 26 July, the install
file 26 August, and a database installed on 13 August got neither. Page-title search returned nothing
site-wide for weeks with no error anywhere (issues #1745, #1753).

`SchemaIntegrityCommand` now reports this class of gap at startup, but only for objects it knows to
look for. Adding both files together is what prevents it.

## Test a migration that transforms data

An `UPGRADE_` migration **only runs in CI if someone writes a test for it** — nothing else executes
them (issue #1755). A migration that is syntactically broken, or that silently does nothing, passes
the full suite.

That matters most for migrations that move data rather than only create objects: a backfill that
quietly updates zero rows looks exactly like a backfill that worked.

**CI now requires the test rather than trusting you to remember.**
`tools/check-migration-test-coverage.py` fails the build when a migration added or changed in the
PR contains an `UPDATE`, a `DELETE`, or an `INSERT ... SELECT` and no test mentions its version.
Reference the version string in the test — `applyOnly("20260801.1000")` is enough for the gate to
see it.

Pure DDL and `INSERT ... VALUES` seed rows are deliberately **not** required. A `CREATE TABLE` that
fails is loud on the next deploy, seeds have their own guard in `SchemaInstallUpgradeParityTest`,
and a gate that fires on every migration becomes noise people learn to route around.

To see where the whole track stands:

```
python3 tools/check-migration-test-coverage.py --all
```

At the time the gate was added: 169 migrations, 50 of them transforming data, 39 of those with no
test. Those 39 are grandfathered — the gate only looks at what a PR changes — and are worth
chipping away at, oldest-riskiest first.

`MigrationTestHarness` makes this a fixture, a call, and assertions. The worked example is
`ItemOrderMigrationTest`:

```java
@BeforeAll
static void migrate() {
  harness = MigrationTestHarness.start("the items.item_order migration test");

  // build only what the migration expects to find
  harness.execute(
      "CREATE TABLE items (item_id BIGSERIAL PRIMARY KEY, collection_id BIGINT NOT NULL,"
          + " name VARCHAR(255) NOT NULL)",
      "INSERT INTO items (collection_id, name) VALUES (1, 'Cherry'), (1, 'apple'), (1, 'Banana')");

  migrateResult = harness.applyOnly("20260801.1000");
}

@AfterAll
static void stopDatabase() {
  if (harness != null) {
    harness.close();
  }
}
```

`applyOnly` marks every earlier migration as applied without running it and sets the target as a
ceiling, so exactly one migration executes. **Pass only the target version** — the harness reads the
migration files to work out where to baseline. Earlier tests declared that baseline by hand as a
second constant, and both carried comments warning that if the two disagreed, `outOfOrder(true)`
would apply everything dated after the baseline instead of the one migration under test.

Assert what the migration *did*, not just that it ran: the column exists **and** existing rows were
backfilled correctly, including the ordering and partitioning the backfill claims to apply.

The test is skipped, not failed, when Docker is unavailable.

## Version numbers

Versions must be unique **within** a track; the two tracks are independent, so an install and an
upgrade migration may share a number and several legitimately do.

Two branches picking the same version is the common failure, and it only appears once both are in
the same tree — in whichever PR merges second. `tools/check-duplicate-migration-versions.py` catches
it in about a second; it runs in CI and is worth running locally before opening a PR.
