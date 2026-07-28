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

/**
 * Installs and upgrades the database
 *
 * @author matt rajkowski
 * @created 6/21/18 2:36 PM
 */
public class DatabaseCommand {

  private static Log LOG = LogFactory.getLog(DatabaseCommand.class);

  public static boolean initialize(Properties databaseProperties) {

    // V (One-Time Version files)
    // R (Repeatable every upgrade)
    // Java-based: public class V1_2__Another_user implements JdbcMigration

    String jdbcUrl =
        "jdbc:postgresql://" +
            databaseProperties.getProperty("dataSource.serverName") + ":" +
            databaseProperties.getProperty("dataSource.portNumber") + "/" +
            databaseProperties.getProperty("dataSource.databaseName");

    // Multi-instance deployment: web-only nodes (CMS_NODE_TYPE=web) skip migrations
    // and wait for the primary node to complete. Primary nodes acquire a distributed
    // lock to serialize Flyway execution and prevent concurrent migrations.
    boolean isWebNode = InstanceManager.isWebNodeOnly();
    String lockUuid = null;
    boolean acquired = false;

    if (!isWebNode) {
      // Primary node: acquire distributed lock before migrations
      lockUuid = LockManager.lock("flyway_migration", Duration.ofMinutes(5));
      if (lockUuid == null) {
        LOG.warn("Could not acquire migration lock; another node is migrating. Waiting for completion...");
        // Poll for lock release (another node completed)
        for (int attempt = 0; attempt < 30; attempt++) {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
          }
        }
        LOG.info("Migration lock released by another node; proceeding without lock");
      } else {
        LOG.info("Acquired migration lock: " + lockUuid);
        acquired = true;
      }
    } else {
      LOG.info("Web-only node detected (CMS_NODE_TYPE=web); skipping migration lock acquisition");
    }

    try {
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
        if (LockManager.unlock("flyway_migration", lockUuid)) {
          LOG.info("Released migration lock: " + lockUuid);
        } else {
          LOG.warn("Failed to release migration lock: " + lockUuid);
        }
      }
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
    Flyway flyway = Flyway.configure()
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
    MigrateResult result = flyway.migrate();
    if (!result.success) {
      LOG.error("Database migration error occurred: " + result.warnings.toString());
      return false;
    }
    return true;
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
