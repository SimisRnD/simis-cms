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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * @author SimIS
 * @created 8/10/2026
 */
class DatabasePropertiesTest {

  @Test
  void applyEnvironmentOverridesLeavesBasePropertiesUntouchedWhenNoVarsAreSet() {
    Properties databaseProperties = new Properties();
    databaseProperties.setProperty("dataSource.serverName", "localhost");

    DatabaseProperties.applyEnvironmentOverrides(databaseProperties, Map.of());

    assertEquals("localhost", databaseProperties.getProperty("dataSource.serverName"));
    assertNull(databaseProperties.getProperty("jdbcUrl"));
  }

  @Test
  void applyEnvironmentOverridesAppliesTheFiveExistingDbVars() {
    Properties databaseProperties = new Properties();
    Map<String, String> env = Map.of(
        "DB_SERVER_NAME", "db.example.com",
        "DB_USER", "app_user",
        "DB_PASSWORD", "secret",
        "DB_NAME", "simis_cms",
        "DB_SSL", "true");

    DatabaseProperties.applyEnvironmentOverrides(databaseProperties, env);

    assertEquals("db.example.com", databaseProperties.getProperty("dataSource.serverName"));
    assertEquals("app_user", databaseProperties.getProperty("dataSource.user"));
    assertEquals("secret", databaseProperties.getProperty("dataSource.password"));
    assertEquals("simis_cms", databaseProperties.getProperty("dataSource.databaseName"));
    assertEquals("true", databaseProperties.getProperty("dataSource.ssl"));
  }

  @Test
  void applyEnvironmentOverridesIgnoresDbSslWhenNotLiterallyTrue() {
    Properties databaseProperties = new Properties();
    DatabaseProperties.applyEnvironmentOverrides(databaseProperties, Map.of("DB_SSL", "yes"));

    assertNull(databaseProperties.getProperty("dataSource.ssl"));
  }

  @Test
  void applyEnvironmentOverridesIgnoresDbAuthMethodWhenNotAzureSqlSpn() {
    Properties databaseProperties = new Properties();
    databaseProperties.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");

    DatabaseProperties.applyEnvironmentOverrides(databaseProperties, Map.of("DB_AUTH_METHOD", "something-else"));

    assertEquals("org.postgresql.ds.PGSimpleDataSource", databaseProperties.getProperty("dataSourceClassName"));
    assertNull(databaseProperties.getProperty("jdbcUrl"));
  }

  private static Map<String, String> fullSpnEnv() {
    return Map.of(
        "DB_AUTH_METHOD", "azure-sql-spn",
        "DB_SERVER_NAME", "my-server.postgres.database.azure.com",
        "DB_NAME", "simis_cms",
        "DB_USER", "app-sp@my-server",
        "DB_TENANT_ID", "tenant-123",
        "DB_CLIENT_ID", "client-456",
        "DB_SECRET", "shhh");
  }

  @Test
  void applyEnvironmentOverridesSwitchesToAzureSpnJdbcUrlWhenFullyConfigured() {
    Properties databaseProperties = new Properties();
    databaseProperties.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
    databaseProperties.setProperty("dataSource.user", "postgres");
    databaseProperties.setProperty("dataSource.password", "unused");

    DatabaseProperties.applyEnvironmentOverrides(databaseProperties, fullSpnEnv());

    assertFalse(databaseProperties.containsKey("dataSourceClassName"), "dataSourceClassName must be removed so Hikari uses jdbcUrl instead");
    assertEquals("org.postgresql.Driver", databaseProperties.getProperty("driverClassName"));
    assertTrue(databaseProperties.getProperty("jdbcUrl")
        .startsWith("jdbc:postgresql://my-server.postgres.database.azure.com:5432/simis_cms?sslmode=require"));
    assertTrue(databaseProperties.getProperty("jdbcUrl").contains(
        "authenticationPluginClassName=com.azure.identity.extensions.jdbc.postgresql.AzurePostgresqlAuthenticationPlugin"));
    assertEquals("app-sp@my-server", databaseProperties.getProperty("dataSource.user"),
        "the SPN branch must set the AAD principal's role name, not leave the pre-existing/default dataSource.user in place");
    assertEquals("tenant-123", databaseProperties.getProperty("dataSource.azure.tenantId"));
    assertEquals("client-456", databaseProperties.getProperty("dataSource.azure.clientId"));
    assertEquals("shhh", databaseProperties.getProperty("dataSource.azure.clientSecret"));
  }

  @Test
  void applyEnvironmentOverridesFailsFastWhenAzureSpnVarsAreIncomplete() {
    Properties databaseProperties = new Properties();
    Map<String, String> env = Map.of(
        "DB_AUTH_METHOD", "azure-sql-spn",
        "DB_SERVER_NAME", "my-server.postgres.database.azure.com",
        "DB_NAME", "simis_cms");
    // Missing DB_USER, DB_TENANT_ID, DB_CLIENT_ID, DB_SECRET

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> DatabaseProperties.applyEnvironmentOverrides(databaseProperties, env));

    assertTrue(exception.getMessage().contains("DB_AUTH_METHOD=azure-sql-spn"));
    assertNull(databaseProperties.getProperty("jdbcUrl"));
  }

  @Test
  void applyEnvironmentOverridesFailsFastWhenAzureSpnDbUserIsMissing() {
    // The AAD auth plugin only supplies the password (a token); DB_USER must independently name
    // the service principal's Postgres role, or the connection silently falls back to whatever
    // dataSource.user already happened to be set to.
    Properties databaseProperties = new Properties();
    Map<String, String> env = new HashMap<>(fullSpnEnv());
    env.remove("DB_USER");

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> DatabaseProperties.applyEnvironmentOverrides(databaseProperties, env));

    assertTrue(exception.getMessage().contains("DB_USER"));
    assertNull(databaseProperties.getProperty("jdbcUrl"));
  }

  @Test
  void applyEnvironmentOverridesRejectsUnsafeCharactersInAzureSpnServerOrDatabaseName() {
    Properties databaseProperties = new Properties();
    Map<String, String> env = new HashMap<>(fullSpnEnv());
    env.put("DB_NAME", "simis_cms&sslmode=disable");

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> DatabaseProperties.applyEnvironmentOverrides(databaseProperties, env));

    assertTrue(exception.getMessage().contains("DB_NAME"));
    assertNull(databaseProperties.getProperty("jdbcUrl"));
  }

  @Test
  void applyEnvironmentOverridesLeavesLegacyAuthInPlaceWhenDbAuthMethodIsUnrecognized() {
    Properties databaseProperties = new Properties();
    databaseProperties.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");

    DatabaseProperties.applyEnvironmentOverrides(databaseProperties, Map.of("DB_AUTH_METHOD", "azure-sql-spn "));

    // Trailing whitespace means this does NOT match AZURE_SQL_SPN -- confirms the strict-equals
    // comparison doesn't accidentally activate on a near-miss value.
    assertEquals("org.postgresql.ds.PGSimpleDataSource", databaseProperties.getProperty("dataSourceClassName"));
    assertNull(databaseProperties.getProperty("jdbcUrl"));
  }
}
