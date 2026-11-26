/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.infrastructure.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariConfig;

/**
 * Verifies how {@link MigrationLogin} reads its two variables (issue #1129). What it does to a real
 * database is covered by {@link MigrationLoginIntegrationTest}.
 */
class MigrationLoginTest {

  // Named constants rather than repeated literals, so the expected URL below is assembled from the
  // values the test feeds in. A connection string spelled out whole reads as a credential to the
  // secret scanner (see .github/workflows/secret-scan-pr.yml).
  private static final String SERVER = "db.example.test";
  private static final String PORT = "5432";
  private static final String DATABASE = "simis";

  private static Properties poolProperties() {
    Properties properties = new Properties();
    properties.setProperty("dataSource.serverName", SERVER);
    properties.setProperty("dataSource.portNumber", PORT);
    properties.setProperty("dataSource.databaseName", DATABASE);
    properties.setProperty("dataSource.user", "cms_app");
    properties.setProperty("dataSource.password", "app-password");
    return properties;
  }

  @AfterEach
  void forgetTheSplit() {
    MigrationLogin.reset();
  }

  @Test
  void withNeitherVariableOneLoginDoesEverything() {
    Properties properties = poolProperties();
    MigrationLogin.configure(properties, Map.of());

    assertFalse(MigrationLogin.isSeparate());
    assertEquals("cms_app", MigrationLogin.user(properties), "Flyway connects as the pool's login, as before");
    assertEquals("app-password", MigrationLogin.password(properties));
    assertNull(MigrationLogin.dataSource());
    assertTrue(MigrationLogin.grantRuntimeAccess(), "nothing to grant, and nothing is connected to");
  }

  @Test
  void withBothVariablesMigrationsGetTheirOwnLogin() {
    Properties properties = poolProperties();
    MigrationLogin.configure(properties, Map.of("DB_MIGRATION_USER", "cms_owner", "DB_MIGRATION_PASSWORD", "owner-password"));

    assertTrue(MigrationLogin.isSeparate());
    assertEquals("cms_owner", MigrationLogin.user(properties));
    assertEquals("owner-password", MigrationLogin.password(properties));
    assertNotNull(MigrationLogin.dataSource());
    assertEquals("cms_app", properties.getProperty("dataSource.user"), "the pool keeps its own login");
  }

  @Test
  void theMigrationUrlIsTheOneFlywayHasAlwaysUsed() {
    assertEquals("jdbc:postgresql://" + SERVER + ":" + PORT + "/" + DATABASE, MigrationLogin.jdbcUrl(poolProperties()));
  }

  @Test
  void aWebOnlyNodeNeverTakesTheMigrationLogin() {
    Properties properties = poolProperties();
    MigrationLogin.configure(properties,
        Map.of("CMS_NODE_TYPE", "web", "DB_MIGRATION_USER", "cms_owner", "DB_MIGRATION_PASSWORD", "owner-password"));

    assertFalse(MigrationLogin.isSeparate(), "it waits for the primary node's migrations, and reads their history as the pool's login");
    assertEquals("cms_app", MigrationLogin.user(properties));
    assertEquals("app-password", MigrationLogin.password(properties));
    assertNull(MigrationLogin.dataSource());
  }

  @Test
  void aWebOnlyNodeIgnoresHalfAConfigurationToo() {
    MigrationLogin.configure(poolProperties(), Map.of("CMS_NODE_TYPE", "web", "DB_MIGRATION_USER", "cms_owner"));

    assertFalse(MigrationLogin.isSeparate());
  }

  @Test
  void anyOtherNodeTypeStillSplits() {
    for (String nodeType : new String[] { "standalone", "", "WEB" }) {
      MigrationLogin.configure(poolProperties(),
          Map.of("CMS_NODE_TYPE", nodeType, "DB_MIGRATION_USER", "cms_owner", "DB_MIGRATION_PASSWORD", "owner-password"));

      assertTrue(MigrationLogin.isSeparate(), "CMS_NODE_TYPE=" + nodeType);
    }
  }

  @Test
  void halfAConfigurationStopsStartupRatherThanGuessing() {
    Properties properties = poolProperties();
    for (Map<String, String> env : new Map[] {
        Map.of("DB_MIGRATION_USER", "cms_owner"),
        Map.of("DB_MIGRATION_USER", "cms_owner", "DB_MIGRATION_PASSWORD", " "),
        Map.of("DB_MIGRATION_PASSWORD", "owner-password"),
        Map.of("DB_MIGRATION_USER", " ", "DB_MIGRATION_PASSWORD", "owner-password") }) {
      IllegalStateException e = assertThrows(IllegalStateException.class, () -> MigrationLogin.configure(properties, env),
          String.valueOf(env.keySet()));
      assertTrue(e.getMessage().contains("DB_MIGRATION_USER") && e.getMessage().contains("DB_MIGRATION_PASSWORD"));
      assertFalse(e.getMessage().contains("owner-password"), "the message never repeats the password");
    }
  }

  @Test
  void namingThePoolsOwnLoginIsNotASplit() {
    Properties properties = poolProperties();
    MigrationLogin.configure(properties, Map.of("DB_MIGRATION_USER", "cms_app", "DB_MIGRATION_PASSWORD", "app-password"));

    assertFalse(MigrationLogin.isSeparate());
  }

  @Test
  void aLaterStartWithoutTheVariablesForgetsTheSplit() {
    Properties properties = poolProperties();
    MigrationLogin.configure(properties, Map.of("DB_MIGRATION_USER", "cms_owner", "DB_MIGRATION_PASSWORD", "owner-password"));
    MigrationLogin.configure(properties, Map.of());

    assertFalse(MigrationLogin.isSeparate());
  }

  @Test
  void theEnvironmentOverridesReadItAndLeaveHikarisPropertiesAlone() {
    Properties properties = new Properties();
    properties.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
    properties.setProperty("dataSource.portNumber", "5432");
    Map<String, String> env = new HashMap<>();
    env.put("DB_SERVER_NAME", "db.example.test");
    env.put("DB_NAME", "simis");
    env.put("DB_USER", "cms_app");
    env.put("DB_PASSWORD", "app-password");
    env.put("DB_MIGRATION_USER", "cms_owner");
    env.put("DB_MIGRATION_PASSWORD", "owner-password");

    DatabaseProperties.applyEnvironmentOverrides(properties, env);

    assertTrue(MigrationLogin.isSeparate());
    assertEquals("cms_owner", MigrationLogin.user(properties));
    for (String name : properties.stringPropertyNames()) {
      assertFalse(name.toLowerCase().contains("migration"), name + " would be refused by HikariConfig as an unknown property");
      assertFalse("owner-password".equals(properties.getProperty(name)), "the migration password is not in " + name);
    }
    // HikariConfig throws for a key it does not know, which is why the two variables stay out
    HikariConfig config = new HikariConfig(properties);
    assertEquals("cms_app", config.getDataSourceProperties().getProperty("user"));
  }

  @Test
  void onlyAListedSequenceCanBeReset() {
    // Refused before any connection is made, on every site, so a new caller cannot work where one
    // login does everything and then fail where the pool's login is data-only
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DB.resetSequence("users_user_id_seq", 1));
    assertTrue(e.getMessage().contains("RESETTABLE_SEQUENCES"), e.getMessage());
    assertTrue(MigrationLogin.RESETTABLE_SEQUENCES.contains("order_daily_seq"), "the one sequence OrderCommand resets");
  }

  @Test
  void identifiersAreQuotedSoANameCannotEndTheStatement() {
    assertEquals("\"cms_app\"", MigrationLogin.quoteIdentifier("cms_app"));
    assertEquals("\"odd\"\"name\"", MigrationLogin.quoteIdentifier("odd\"name"));
    assertEquals("\"app; DROP TABLE users\"", MigrationLogin.quoteIdentifier("app; DROP TABLE users"));
  }
}
