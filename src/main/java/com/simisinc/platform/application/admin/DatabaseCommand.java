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
      // Run upgrade migrations for fresh installs (any new features added since baseline)
      // Baseline to 71130 (the last NEW_* migration version) so UPGRADE migrations from 20260700+ run
      Flyway flyway = Flyway.configure()
          .table("flyway_history")
          .sqlMigrationPrefix("UPGRADE_")
          .repeatableSqlMigrationPrefix("REPEAT_")
          .dataSource(jdbcUrl, databaseProperties.getProperty("dataSource.user"), databaseProperties.getProperty("dataSource.password"))
          .locations(databaseUpgradeLocations())
          .placeholderReplacement(false)
          .cleanDisabled(true)
          .outOfOrder(true)
          .baselineVersion("71130")
          .load();
      flyway.baseline();
      LOG.info("Database baseline completed");

      // Now run any upgrade migrations after the baseline
      flyway = Flyway.configure()
          .table("flyway_history")
          .validateOnMigrate(false)
          .sqlMigrationPrefix("UPGRADE_")
          .repeatableSqlMigrationPrefix("REPEAT_")
          .dataSource(jdbcUrl, databaseProperties.getProperty("dataSource.user"), databaseProperties.getProperty("dataSource.password"))
          .locations(databaseUpgradeLocations())
          .placeholderReplacement(false)
          .cleanDisabled(true)
          .load();
      MigrateResult result = flyway.migrate();
      if (!result.success) {
        LOG.error("Database upgrade migration error occurred: " + result.warnings.toString());
        return false;
      }
      LOG.info("Database upgrade migrations completed");
    }
    return true;
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
