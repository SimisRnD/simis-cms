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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.postgresql.ds.PGSimpleDataSource;

import com.simisinc.platform.infrastructure.instance.InstanceManager;

/**
 * An optional second database login, used only to change the schema (issue #1129).
 *
 * <p>By default one login does everything: {@code DB_USER} runs the migrations at startup and then
 * serves every request, so it has to be able to create and drop tables, and a SQL injection anywhere
 * in the application runs with that power. Setting {@code DB_MIGRATION_USER} and
 * {@code DB_MIGRATION_PASSWORD} splits the two jobs:
 *
 * <ul>
 * <li>The <b>migration login</b> owns the schema. Flyway and JobRunr's own table setup connect as it,
 * for a few seconds at startup.</li>
 * <li>The <b>pool's login</b>, still {@code DB_USER}, needs only to read and write rows. It is what
 * every request and background job runs as.</li>
 * </ul>
 *
 * <p>The pool's login gets its access from here rather than from a script someone has to remember to
 * re-run. {@link #grantRuntimeAccess()} runs as the migration login before and after every migration
 * run, and does two things. It sets default privileges, so a table a migration creates is usable by
 * the pool's login the moment it exists -- the Java install migrations write through the pool in the
 * same Flyway run that created their tables. And it re-grants on everything already there, so a
 * grant that was missed or removed by hand is repaired at the next start instead of surfacing as a
 * page that fails.
 *
 * <p>What the pool's login is given is deliberately narrow: SELECT, INSERT, UPDATE and DELETE on the
 * tables, and the right to take the next value from the sequences. No TRUNCATE, no ownership, no
 * right to create anything. Only the sequences in {@link #RESETTABLE_SEQUENCES} can be set back to
 * a chosen value. The migration history tables, Flyway's and JobRunr's, are read-only to it, so
 * history cannot be rewritten from a request. And only what the migration login itself owns is
 * handed out: a table in the same schema that belongs to another login is left alone.
 *
 * <p>A web-only node never changes the schema, so it never connects as the migration login, even
 * when the two variables are set there.
 *
 * <p>With neither variable set nothing here does anything, and the application behaves exactly as it
 * did before this class existed.
 *
 * @author SimIS Inc.
 */
public final class MigrationLogin {

  private static Log LOG = LogFactory.getLog(MigrationLogin.class);

  public static final String USER_VARIABLE = "DB_MIGRATION_USER";
  public static final String PASSWORD_VARIABLE = "DB_MIGRATION_PASSWORD";

  /**
   * Migration bookkeeping: Flyway's two history tables and JobRunr's. The pool's login may read
   * these (a web-only node reads Flyway's to learn whether migrations are done) but never write them.
   */
  static final List<String> HISTORY_TABLES = List.of("flyway_install", "flyway_history", "jobrunr_migrations");

  /**
   * The sequences the application sets back to a chosen value, which takes UPDATE on the sequence.
   * Every other sequence is only ever advanced. {@link DB#resetSequence} refuses a name that is not
   * listed here, so a new caller finds this list on its first run rather than on the first site
   * that runs with a data-only login.
   */
  public static final List<String> RESETTABLE_SEQUENCES = List.of("order_daily_seq");

  private static final String TABLE_PRIVILEGES = "SELECT, INSERT, UPDATE, DELETE";
  /** nextval and currval. Not setval: see {@link #RESETTABLE_SEQUENCES}. */
  private static final String SEQUENCE_PRIVILEGES = "USAGE, SELECT";
  /** Other relations to name in the warning before it says "and N more" */
  private static final int NOT_OWNED_NAMES_TO_LOG = 10;

  /** The configured split, or null when one login does everything */
  private static volatile Split split = null;

  private record Split(String user, String password, String runtimeUser, String jdbcUrl) {
  }

  private MigrationLogin() {
  }

  /**
   * Reads {@code DB_MIGRATION_USER} and {@code DB_MIGRATION_PASSWORD}. Called once at startup, after
   * the other database variables have been applied to {@code databaseProperties}.
   *
   * @throws IllegalStateException when only one of the two is set: starting with half a
   *     configuration would run the migrations as a login nobody chose
   */
  public static void configure(Properties databaseProperties, Map<String, String> env) {
    String user = StringUtils.trimToNull(env.get(USER_VARIABLE));
    String password = env.get(PASSWORD_VARIABLE);
    if (user == null && StringUtils.isBlank(password)) {
      split = null;
      return;
    }
    if (InstanceManager.isWebNodeOnly(env)) {
      // It waits for the primary node's migrations and reads their history as the pool's login, so
      // it has no use for the owner's password and does not keep it
      LOG.info(USER_VARIABLE + " and " + PASSWORD_VARIABLE + " are ignored on a web-only node, which never changes the schema");
      split = null;
      return;
    }
    if (user == null || StringUtils.isBlank(password)) {
      throw new IllegalStateException(USER_VARIABLE + " and " + PASSWORD_VARIABLE + " must be set together, or not at all");
    }
    String runtimeUser = databaseProperties.getProperty("dataSource.user");
    if (user.equals(runtimeUser)) {
      LOG.info(USER_VARIABLE + " names the same login as DB_USER, so migrations and the connection pool share it");
      split = null;
      return;
    }
    LOG.info("Found variable " + USER_VARIABLE + ": migrations run as a separate login from the connection pool");
    split = new Split(user, password, runtimeUser, jdbcUrl(databaseProperties));
  }

  /** Whether migrations run as a different login from the connection pool */
  public static boolean isSeparate() {
    return split != null;
  }

  /** The login Flyway connects as: the migration login when one is configured, otherwise the pool's */
  public static String user(Properties databaseProperties) {
    Split current = split;
    return current != null ? current.user() : databaseProperties.getProperty("dataSource.user");
  }

  public static String password(Properties databaseProperties) {
    Split current = split;
    return current != null ? current.password() : databaseProperties.getProperty("dataSource.password");
  }

  /** The URL Flyway has always connected with, built from the same three properties */
  public static String jdbcUrl(Properties databaseProperties) {
    return "jdbc:postgresql://"
        + databaseProperties.getProperty("dataSource.serverName") + ":"
        + databaseProperties.getProperty("dataSource.portNumber") + "/"
        + databaseProperties.getProperty("dataSource.databaseName");
  }

  /**
   * A data source that connects as the migration login, for the one other thing that changes the
   * schema at startup: JobRunr creating and upgrading its own tables.
   *
   * @return null when no separate migration login is configured
   */
  public static javax.sql.DataSource dataSource() {
    Split current = split;
    if (current == null) {
      return null;
    }
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(current.jdbcUrl());
    dataSource.setUser(current.user());
    dataSource.setPassword(current.password());
    return dataSource;
  }

  /**
   * Gives the pool's login its access, as the migration login. Safe to run any number of times.
   *
   * @return true when there is nothing to do or the grants are in place; false when a separate
   *     migration login is configured and the grants could not be applied, in which case the
   *     application must not start: the pool's login would fail on some page later instead
   */
  public static boolean grantRuntimeAccess() {
    Split current = split;
    if (current == null) {
      return true;
    }
    String runtime = quoteIdentifier(current.runtimeUser());
    try (Connection connection = DriverManager.getConnection(current.jdbcUrl(), current.user(), current.password())) {
      if (!roleExists(connection, current.runtimeUser())) {
        LOG.error("The login in DB_USER does not exist in the database, so it cannot be granted access. Create it, "
            + "then start again.");
        return false;
      }
      String schemaName = currentSchema(connection);
      if (schemaName == null) {
        LOG.error("The migration login has no schema to work in (its search_path names none that exists)");
        return false;
      }
      String schema = quoteIdentifier(schemaName);
      try (Statement statement = connection.createStatement()) {
        if (!hasSchemaUsage(connection, current.runtimeUser(), schemaName)) {
          statement.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + runtime);
        }
        // What the migration login creates from now on is granted as it is created
        statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA " + schema
            + " GRANT " + TABLE_PRIVILEGES + " ON TABLES TO " + runtime);
        statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA " + schema
            + " GRANT " + SEQUENCE_PRIVILEGES + " ON SEQUENCES TO " + runtime);

        // And what is already there. Only relations the migration login itself owns, and nothing an
        // extension installed: PostGIS's own tables are not this application's to hand out, and
        // neither is a table some other login keeps in the same schema. The test is ownership by
        // name, not pg_has_role(): a superuser, or a member of the other login's role, passes
        // pg_has_role() for everything, which would hand out every table in the schema.
        List<String> tables = new ArrayList<>();
        List<String> readOnly = new ArrayList<>();
        List<String> sequences = new ArrayList<>();
        List<String> resettable = new ArrayList<>();
        List<String> notOwned = new ArrayList<>();
        try (PreparedStatement pst = connection.prepareStatement(
            "SELECT c.relname, c.relkind, pg_get_userbyid(c.relowner) = current_user AS owned FROM pg_class c "
                + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = ? AND c.relkind IN ('r', 'p', 'v', 'm', 'S') "
                + "AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.classid = 'pg_class'::regclass "
                + "AND d.objid = c.oid AND d.deptype = 'e') "
                + "ORDER BY c.relname")) {
          pst.setString(1, schemaName);
          try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
              String name = rs.getString(1);
              String kind = rs.getString(2);
              if (!rs.getBoolean(3)) {
                notOwned.add(name);
                continue;
              }
              String qualified = schema + "." + quoteIdentifier(name);
              if ("S".equals(kind)) {
                sequences.add(qualified);
                if (RESETTABLE_SEQUENCES.contains(name)) {
                  resettable.add(qualified);
                }
              } else if (HISTORY_TABLES.contains(name) || "v".equals(kind) || "m".equals(kind)) {
                readOnly.add(qualified);
              } else {
                tables.add(qualified);
              }
            }
          }
        }
        if (!tables.isEmpty()) {
          statement.execute("GRANT " + TABLE_PRIVILEGES + " ON TABLE " + String.join(", ", tables) + " TO " + runtime);
        }
        if (!sequences.isEmpty()) {
          statement.execute("GRANT " + SEQUENCE_PRIVILEGES + " ON SEQUENCE " + String.join(", ", sequences) + " TO " + runtime);
        }
        if (!resettable.isEmpty()) {
          statement.execute("GRANT UPDATE ON SEQUENCE " + String.join(", ", resettable) + " TO " + runtime);
        }
        if (!readOnly.isEmpty()) {
          // The default privileges above granted writes on these as they were created
          String list = String.join(", ", readOnly);
          statement.execute("REVOKE INSERT, UPDATE, DELETE ON TABLE " + list + " FROM " + runtime);
          statement.execute("GRANT SELECT ON TABLE " + list + " TO " + runtime);
        }
        LOG.info("Granted the connection pool's login access to " + tables.size() + " table(s) and "
            + sequences.size() + " sequence(s), read-only on "
            + readOnly.stream().map(name -> name.substring(name.indexOf('.') + 1)).collect(Collectors.joining(", ", "[", "]")));
        if (!notOwned.isEmpty()) {
          int more = notOwned.size() - NOT_OWNED_NAMES_TO_LOG;
          LOG.warn(notOwned.size() + " relation(s) in schema " + schemaName + " belong to another login and were left alone: "
              + notOwned.stream().limit(NOT_OWNED_NAMES_TO_LOG).collect(Collectors.joining(", "))
              + (more > 0 ? " and " + more + " more" : "")
              + ". If they are part of this application, make the migration login their owner.");
        }
      }
      return true;
    } catch (SQLException e) {
      LOG.error("Could not grant the connection pool's login its access as the migration login: " + e.getMessage());
      return false;
    }
  }

  private static boolean roleExists(Connection connection, String role) throws SQLException {
    try (PreparedStatement pst = connection.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = ?")) {
      pst.setString(1, role);
      try (ResultSet rs = pst.executeQuery()) {
        return rs.next();
      }
    }
  }

  private static String currentSchema(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT current_schema()")) {
      return rs.next() ? rs.getString(1) : null;
    }
  }

  private static boolean hasSchemaUsage(Connection connection, String role, String schema) throws SQLException {
    try (PreparedStatement pst = connection.prepareStatement("SELECT has_schema_privilege(?, ?, 'USAGE')")) {
      pst.setString(1, role);
      pst.setString(2, schema);
      try (ResultSet rs = pst.executeQuery()) {
        return rs.next() && rs.getBoolean(1);
      }
    }
  }

  /** Quotes a name for use where SQL takes an identifier and a bind parameter is not allowed */
  static String quoteIdentifier(String name) {
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  /** Forgets the configured split. For tests, which share this class's static state. */
  static void reset() {
    split = null;
  }
}
