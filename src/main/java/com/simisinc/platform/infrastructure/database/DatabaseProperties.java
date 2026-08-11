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

import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Applies DB_* environment variable overrides to the base database.properties loaded by
 * ContextListener, including an optional Azure Service Principal authentication mode for
 * Postgres (issue #1129) -- not used by the initial Azure deployment, which authenticates with
 * DB_USER/DB_PASSWORD; this is a dormant, opt-in alternative for later. Extracted out of
 * ContextListener so this logic (particularly the SPN branch's JDBC URL construction) is
 * unit-testable; ServletContextEvent-based startup code is not. The env map is threaded through
 * as a parameter (rather than each method reading System.getenv() directly) purely so tests can
 * substitute a fake map -- this codebase has no env-var mocking library.
 *
 * @author SimIS
 * @created 8/10/2026
 */
public class DatabaseProperties {

  private static Log LOG = LogFactory.getLog(DatabaseProperties.class);

  private static final String AZURE_SQL_SPN = "azure-sql-spn";

  private DatabaseProperties() {
  }

  public static void applyEnvironmentOverrides(Properties databaseProperties) {
    applyEnvironmentOverrides(databaseProperties, System.getenv());
  }

  static void applyEnvironmentOverrides(Properties databaseProperties, Map<String, String> env) {
    if (env.containsKey("DB_SERVER_NAME")) {
      LOG.info("Found variable DB_SERVER_NAME=" + env.get("DB_SERVER_NAME"));
      databaseProperties.setProperty("dataSource.serverName", env.get("DB_SERVER_NAME"));
    }
    if (env.containsKey("DB_USER")) {
      LOG.info("Found variable DB_USER");
      databaseProperties.setProperty("dataSource.user", env.get("DB_USER"));
    }
    if (env.containsKey("DB_PASSWORD")) {
      LOG.info("Found variable DB_PASSWORD");
      databaseProperties.setProperty("dataSource.password", env.get("DB_PASSWORD"));
    }
    if (env.containsKey("DB_NAME")) {
      LOG.info("Found variable DB_NAME=" + env.get("DB_NAME"));
      databaseProperties.setProperty("dataSource.databaseName", env.get("DB_NAME"));
    }
    if (env.containsKey("DB_SSL") && "true".equals(env.get("DB_SSL"))) {
      LOG.info("Found variable DB_SSL=" + env.get("DB_SSL"));
      databaseProperties.setProperty("dataSource.ssl", "true");
    }

    String authMethod = env.get("DB_AUTH_METHOD");
    if (AZURE_SQL_SPN.equals(authMethod)) {
      applyAzureSpnAuthentication(databaseProperties, env);
    } else if (StringUtils.isNotBlank(authMethod)) {
      LOG.warn("Found variable DB_AUTH_METHOD=" + authMethod + ", but the only recognized value is \""
          + AZURE_SQL_SPN + "\" -- ignoring it and using the default DB_USER/DB_PASSWORD authentication");
    }
  }

  /**
   * serverName/databaseName come from operator-set env vars, not end-user input, but they still
   * get concatenated straight into a JDBC URL below -- reject anything that could be
   * misinterpreted as a URL delimiter rather than silently building a malformed connection
   * string.
   */
  private static boolean isUrlSafe(String value) {
    return value.chars().noneMatch(
        c -> Character.isWhitespace(c) || c == '&' || c == '?' || c == '#' || c == '/' || c == '@');
  }

  /**
   * Switches Hikari from its default dataSourceClassName-based Postgres config to a jdbcUrl
   * carrying Azure's JDBC authentication plugin, so the app connects using an Azure Service
   * Principal instead of a stored password. Same pattern SimisRnD/cms-platform already ships
   * (DatabaseProperties.java there), backed by com.azure:azure-identity-extensions -- see
   * https://github.com/Azure/azure-sdk-for-java/blob/main/sdk/identity/azure-identity-extensions/Azure-Database-for-PostgreSQL-README.md
   *
   * <p>
   * Fails fast with a specific message rather than the NullPointerException a missing env var
   * would otherwise cause deep inside Properties/Hikari -- this runs once, at startup, so a clear
   * error here is worth the extra validation. DB_USER is required here (not just left to the
   * unconditional block above) because Azure's AAD authentication plugin only supplies the
   * password (an access token); the JDBC "user" property must independently be set to the
   * service principal's registered Postgres role name, or the connection silently attempts to
   * authenticate as whatever dataSource.user already happened to be (e.g. database.properties'
   * local-dev default "postgres").
   * </p>
   */
  private static void applyAzureSpnAuthentication(Properties databaseProperties, Map<String, String> env) {
    String serverName = env.get("DB_SERVER_NAME");
    String databaseName = env.get("DB_NAME");
    String user = env.get("DB_USER");
    String tenantId = env.get("DB_TENANT_ID");
    String clientId = env.get("DB_CLIENT_ID");
    String clientSecret = env.get("DB_SECRET");
    if (StringUtils.isBlank(serverName) || StringUtils.isBlank(databaseName) || StringUtils.isBlank(user)
        || StringUtils.isBlank(tenantId) || StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
      throw new IllegalStateException(
          "DB_AUTH_METHOD=azure-sql-spn requires DB_SERVER_NAME, DB_NAME, DB_USER (the AAD principal's Postgres "
              + "role name), DB_TENANT_ID, DB_CLIENT_ID, and DB_SECRET to all be set");
    }
    if (!isUrlSafe(serverName) || !isUrlSafe(databaseName)) {
      throw new IllegalStateException(
          "DB_AUTH_METHOD=azure-sql-spn: DB_SERVER_NAME and DB_NAME must not contain whitespace or any of & ? # / @");
    }

    LOG.info("Found variable DB_AUTH_METHOD=azure-sql-spn, configuring Azure SPN authentication");
    databaseProperties.remove("dataSourceClassName");
    databaseProperties.setProperty("driverClassName", "org.postgresql.Driver");
    databaseProperties.setProperty("jdbcUrl",
        "jdbc:postgresql://" + serverName + ":5432/" + databaseName
            + "?sslmode=require"
            + "&authenticationPluginClassName=com.azure.identity.extensions.jdbc.postgresql.AzurePostgresqlAuthenticationPlugin");
    databaseProperties.setProperty("dataSource.user", user);
    databaseProperties.setProperty("dataSource.azure.tenantId", tenantId);
    databaseProperties.setProperty("dataSource.azure.clientId", clientId);
    databaseProperties.setProperty("dataSource.azure.clientSecret", clientSecret);
  }
}
