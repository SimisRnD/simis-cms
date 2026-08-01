/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.application.admin;

import com.simisinc.platform.ApplicationInfo;
import com.simisinc.platform.domain.model.DatabaseVersion;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.instance.InstanceManager;
import com.simisinc.platform.infrastructure.persistence.DatabaseVersionRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;

/**
 * Installs and upgrades the database
 *
 * @author matt rajkowski
 * @created 6/21/18 2:36 PM
 */
public class DatabaseCommand {

  private static Log LOG = LogFactory.getLog(DatabaseCommand.class);

  private static final String MIGRATION_LOCK_NAME = "flyway_migration";

  // How long this node's own lock claim is valid for before it's considered stale. Matches the
  // duration passed to LockManager.lock(); the finally block below releases it explicitly as soon
  // as migrations finish, so this is just the upper bound if the process dies mid-migration.
  private static final Duration LOCK_DURATION = Duration.ofMinutes(5);

  // Total time to keep retrying lock acquisition before giving up. Deliberately longer than
  // LOCK_DURATION so that even if the node holding the lock dies without releasing it, this node
  // will still see the lock as expired and successfully re-acquire it before timing out.
  private static final Duration LOCK_ACQUIRE_TIMEOUT = Duration.ofMinutes(6);

  private static final Duration LOCK_RETRY_INTERVAL = Duration.ofSeconds(5);

  public static boolean initialize(Properties databaseProperties) {
    return initialize(databaseProperties, LOCK_DURATION, LOCK_ACQUIRE_TIMEOUT, LOCK_RETRY_INTERVAL);
  }

  /**
   * Package-private overload so tests can exercise the lock-acquisition-failure path (including
   * the timeout) with small durations instead of the real multi-minute production values.
   */
  static boolean initialize(Properties databaseProperties, Duration lockDuration, Duration lockAcquireTimeout,
      Duration lockRetryInterval) {

    // V (One-Time Version files)
    // R (Repeatable every upgrade)
    // Java-based: public class V1_2__Another_user implements JdbcMigration

    String jdbcUrl =
        "jdbc:postgresql://" +
            databaseProperties.getProperty("dataSource.serverName") + ":" +
            databaseProperties.getProperty("dataSource.portNumber") + "/" +
            databaseProperties.getProperty("dataSource.databaseName");

    // Multi-instance deployment: web-only nodes (CMS_NODE_TYPE=web) never run migrations
    // themselves -- not even unprotected -- they wait for the primary node to finish instead.
    // Primary nodes acquire a distributed lock to serialize Flyway execution and prevent
    // concurrent migrations.
    boolean isWebNode = InstanceManager.isWebNodeOnly();
    String lockUuid = null;
    boolean acquired = false;

    if (!isWebNode) {
      if (!LockManager.lockTableExists()) {
        // The distributed_lock table itself doesn't exist yet, which only happens on a database
        // that has never completed a first install -- that table is created by the very install
        // migration this lock would otherwise be guarding, so there is nothing to have raced with
        // yet. Proceed straight to migrations, which will create the table so that this and every
        // other node are properly guarded from here on.
        LOG.info("Distributed lock table does not exist yet (first-time install); proceeding without a migration lock.");
      } else {
        // Primary node, and the lock table exists (or its existence could not be confirmed --
        // LockManager#lockTableExists fails closed in that case): acquire the distributed lock
        // before migrations, retrying until either this node gets it or lockAcquireTimeout
        // elapses. An uncontended node acquires it on the very first attempt, so this adds no
        // delay to the common single-node boot path.
        lockUuid = acquireMigrationLock(MIGRATION_LOCK_NAME, lockDuration, lockAcquireTimeout, lockRetryInterval);
        if (lockUuid == null) {
          // Never got the lock within the timeout: another node may be stuck mid-migration. Running
          // migrations without the lock here would defeat the entire point of serializing them, so
          // refuse to start rather than risk two nodes migrating concurrently.
          LOG.error("Could not acquire migration lock '" + MIGRATION_LOCK_NAME + "' within " + lockAcquireTimeout
              + "; refusing to run migrations without it. This node will not start.");
          return false;
        }
        LOG.info("Acquired migration lock: " + lockUuid);
        acquired = true;
      }
    } else {
      LOG.info("Web-only node detected (CMS_NODE_TYPE=web); skipping migration lock acquisition");
    }

    try {
      if (isWebNode) {
        // A true web-only node must not call isInstalled()/installDatabase()/upgrade() at all --
        // doing so unconditionally (regardless of isWebNode) was the pre-existing bug: it let a
        // web node run Flyway itself with zero lock protection, not even an attempt, which both
        // violated issue #396's own acceptance criterion and made the deployment runbook's "web
        // nodes skip migrations" claim false. Wait for the primary node's migrations to reach a
        // fully-applied state instead, bounded by the same timeout/retry budget used for lock
        // acquisition above.
        LOG.info("Waiting for the primary node to complete migrations...");
        if (!waitForPrimaryMigration(jdbcUrl, databaseProperties, lockAcquireTimeout, lockRetryInterval)) {
          LOG.error("Primary node did not complete migrations within " + lockAcquireTimeout
              + "; refusing to start against a schema that may not be ready. This node will not start.");
          return false;
        }
        LOG.info("Primary node's migrations are complete.");
        return true;
      }

      if (!isInstalled()) {
        LOG.info("New system detected, installing the database... " + ApplicationInfo.VERSION);
        boolean installResult = installDatabase(jdbcUrl, databaseProperties);
        if (!installResult) {
          return false;
        }
        // An entry is required
        DatabaseVersion databaseVersion = new DatabaseVersion("Initial Setup", ApplicationInfo.VERSION);
        DatabaseVersionRepository.save(databaseVersion);
      } else {
        LOG.info("Checking for database upgrades... " + jdbcUrl);
        if (!upgrade(jdbcUrl, databaseProperties)) {
          return false;
        }
      }
      return true;
    } finally {
      // Release lock on success or failure
      if (acquired && lockUuid != null) {
        if (LockManager.unlock(MIGRATION_LOCK_NAME, lockUuid)) {
          LOG.info("Released migration lock: " + lockUuid);
        } else {
          LOG.warn("Failed to release migration lock: " + lockUuid);
        }
      }
    }
  }

  /**
   * Attempts to acquire the named distributed lock, retrying at {@code retryInterval} until either
   * the lock is acquired or {@code totalTimeout} elapses. {@link LockManager#lock} is a single
   * non-blocking try (it returns immediately whether or not the lock was free), so this loop is
   * what turns it into a bounded blocking acquire.
   *
   * @return the acquired lock's uuid, or {@code null} if the lock could not be acquired within
   *         {@code totalTimeout}
   */
  static String acquireMigrationLock(String lockName, Duration lockDuration, Duration totalTimeout, Duration retryInterval) {
    long deadline = System.currentTimeMillis() + totalTimeout.toMillis();
    String lockUuid = LockManager.lock(lockName, lockDuration);
    boolean firstAttempt = true;
    while (lockUuid == null) {
      if (System.currentTimeMillis() >= deadline) {
        return null;
      }
      if (firstAttempt) {
        LOG.warn("Could not acquire migration lock '" + lockName + "'; another node is migrating. Retrying every "
            + retryInterval.getSeconds() + "s (timeout " + totalTimeout.getSeconds() + "s)...");
        firstAttempt = false;
      }
      try {
        Thread.sleep(retryInterval.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
      lockUuid = LockManager.lock(lockName, lockDuration);
    }
    return lockUuid;
  }

  /**
   * Polls, at {@code retryInterval}, until the primary node's migrations are confirmed complete or
   * {@code timeout} elapses. Used by web-only nodes (CMS_NODE_TYPE=web), which never call {@link
   * #isInstalled()}/{@link #installDatabase}/{@link #upgrade} themselves -- only the primary node
   * does, under the distributed lock acquired earlier in {@link #initialize}.
   *
   * @return true once migrations are confirmed complete, false if {@code timeout} elapses first
   */
  static boolean waitForPrimaryMigration(String jdbcUrl, Properties databaseProperties, Duration timeout,
      Duration retryInterval) {
    return waitForPrimaryMigration(() -> isMigrationUpToDate(jdbcUrl, databaseProperties), timeout, retryInterval);
  }

  /**
   * Package-private so the retry/timeout behavior itself can be tested against a fake {@code
   * migrationComplete} check instead of a real Flyway/database round trip -- the same way {@link
   * #acquireMigrationLock} is tested against a mocked {@link LockManager} rather than a real one.
   */
  static boolean waitForPrimaryMigration(BooleanSupplier migrationComplete, Duration timeout, Duration retryInterval) {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    boolean firstAttempt = true;
    while (true) {
      if (migrationComplete.getAsBoolean()) {
        return true;
      }
      if (System.currentTimeMillis() >= deadline) {
        return false;
      }
      if (firstAttempt) {
        LOG.info("Primary node's migrations are not yet complete; retrying every " + retryInterval.getSeconds()
            + "s (timeout " + timeout.getSeconds() + "s)...");
        firstAttempt = false;
      }
      try {
        Thread.sleep(retryInterval.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }

  /**
   * Reports whether the primary node's migrations have already been fully applied, using Flyway's
   * own read-only {@code info()} against the identical upgrade migration set/table that {@link
   * #upgrade} applies -- built from the same {@link #buildUpgradeFlyway}, so a web node's notion of
   * "done" can never drift from what the primary actually runs. Also covers the fresh-install case:
   * before the primary has installed anything, the flyway_history table (and possibly the schema
   * itself) doesn't exist yet, so Flyway reports every migration as pending and this returns false,
   * same as a real in-progress upgrade.
   */
  private static boolean isMigrationUpToDate(String jdbcUrl, Properties databaseProperties) {
    try {
      return buildUpgradeFlyway(jdbcUrl, databaseProperties).info().pending().length == 0;
    } catch (Exception e) {
      // A transient connectivity issue while reading migration state is not evidence either way --
      // keep waiting/retrying rather than treating it as "up to date".
      LOG.warn("Could not determine migration status while waiting for the primary node: " + e.getMessage());
      return false;
    }
  }

  private static boolean installDatabase(String jdbcUrl, Properties databaseProperties) {
    {
      // Install the new database
      Flyway flyway = Flyway.configure()
          .table("flyway_install")
          .sqlMigrationPrefix("NEW_")
          .repeatableSqlMigrationPrefix("DO_")
          .dataSource(jdbcUrl, databaseProperties.getProperty("dataSource.user"), databaseProperties.getProperty("dataSource.password"))
          .locations("classpath:database/install", "com/simisinc/platform/infrastructure/database/install")
          .placeholderReplacement(false)
          .cleanDisabled(true)
          .load();
      flyway.migrate();
      LOG.info("Database installation completed");
    }
    {
      // For fresh installs, baseline to a high version to prevent old migrations from running.
      // New installs don't need UPGRADE migrations since they start with the latest schema from NEW_* migrations.
      //
      // The baseline must be at least as high as every UPGRADE_* migration that exists on the
      // classpath, or a fresh install silently never applies (and never even attempts) any
      // migration dated after ApplicationInfo.VERSION -- with no error, since Flyway treats
      // "version <= baseline" as already handled. ApplicationInfo.VERSION is a hand-bumped
      // constant, not tied to real time, so it drifts behind the newest migration file whenever
      // nobody remembers to bump it (it did: this uncovered a stretch of upgrade/ migrations
      // dated after the last VERSION bump). Resolving the actual highest migration version at
      // baseline time removes the need for that manual synchronization -- ApplicationInfo.VERSION
      // still applies as the floor, so behavior is unchanged whenever it's already high enough.
      MigrationVersion baselineVersion = resolveInstallBaselineVersion(jdbcUrl, databaseProperties, databaseUpgradeLocations());
      Flyway flyway = Flyway.configure()
          .table("flyway_history")
          .sqlMigrationPrefix("UPGRADE_")
          .repeatableSqlMigrationPrefix("REPEAT_")
          .dataSource(jdbcUrl, databaseProperties.getProperty("dataSource.user"), databaseProperties.getProperty("dataSource.password"))
          .locations(databaseUpgradeLocations())
          .placeholderReplacement(false)
          .cleanDisabled(true)
          .baselineVersion(baselineVersion)
          .load();
      flyway.baseline();
      LOG.info("Database baseline completed at version " + baselineVersion);
    }
    return true;
  }

  /**
   * Returns the higher of ApplicationInfo.VERSION and the highest-versioned UPGRADE_* migration
   * resolved at the given locations, so a fresh install's baseline can never sit below a
   * migration that actually exists on the classpath.
   */
  static MigrationVersion resolveInstallBaselineVersion(String jdbcUrl, Properties databaseProperties, String... locations) {
    MigrationVersion highest = MigrationVersion.fromVersion(ApplicationInfo.VERSION);
    Flyway scanOnly = Flyway.configure()
        .table("flyway_history")
        .sqlMigrationPrefix("UPGRADE_")
        .repeatableSqlMigrationPrefix("REPEAT_")
        .dataSource(jdbcUrl, databaseProperties.getProperty("dataSource.user"), databaseProperties.getProperty("dataSource.password"))
        .locations(locations)
        .placeholderReplacement(false)
        .load();
    for (MigrationInfo migrationInfo : scanOnly.info().all()) {
      MigrationVersion version = migrationInfo.getVersion();
      if (version != null && version.compareTo(highest) > 0) {
        highest = version;
      }
    }
    return highest;
  }

  private static boolean upgrade(String jdbcUrl, Properties databaseProperties) {
    // Process the versions
    Flyway flyway = buildUpgradeFlyway(jdbcUrl, databaseProperties);
    MigrateResult result = flyway.migrate();
    if (!result.success) {
      LOG.error("Database migration error occurred: " + result.warnings.toString());
      return false;
    }
    return true;
  }

  /**
   * Builds the Flyway config used both to actually run upgrade migrations ({@link #upgrade}) and,
   * read-only via {@code info()}, to check whether they have already been fully applied ({@link
   * #isMigrationUpToDate}) -- kept as a single definition so the two can never disagree about what
   * "up to date" means.
   */
  private static Flyway buildUpgradeFlyway(String jdbcUrl, Properties databaseProperties) {
    return Flyway.configure()
        .table("flyway_history")
        .validateOnMigrate(false)
        .sqlMigrationPrefix("UPGRADE_")
        .repeatableSqlMigrationPrefix("REPEAT_")
        .dataSource(jdbcUrl, databaseProperties.getProperty("dataSource.user"), databaseProperties.getProperty("dataSource.password"))
        .locations(databaseUpgradeLocations())
        .placeholderReplacement(false)
        .outOfOrder(true)
        .cleanDisabled(true)
        .load();
  }

  private static boolean isInstalled() {
    return (DatabaseVersionRepository.count() > 0);
  }

  private static String[] databaseUpgradeLocations() {
    List<String> locations = new ArrayList<>();
    locations.add("com/simisinc/platform/infrastructure/database/upgrade");
    locations.add("classpath:database/upgrade");
    return locations.toArray(new String[0]);
  }
}
