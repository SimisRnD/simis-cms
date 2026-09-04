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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Drives UPGRADE_20260904.1200__backfill_form_url_host.sql (issue #1835) against a real PostgreSQL
 * through {@link MigrationTestHarness}, following {@link ItemOrderMigrationTest}.
 *
 * <p>FormWidget.resolvePageUrl built the stored URL from the live request, and WidgetContext.getUrl()
 * composes that from request.getServerName() -- the Host header the app <em>receives</em>. Behind a
 * CDN or reverse proxy that is the origin's name rather than the visitor's, so submissions recorded
 * the App Service's own {@code *.azurewebsites.net} hostname. This migration repairs the rows
 * written before the companion code fix.
 *
 * <p>A backfill is exactly the shape that passes a green suite while doing nothing, so these assert
 * what it changed <em>and</em> what it deliberately left alone. The untouched cases carry most of
 * the value: a row already on the correct host is what makes the migration idempotent, and NULL and
 * non-absolute values are what keep it from corrupting rows it cannot parse.
 *
 * @author SimIS Inc.
 */
class FormUrlBackfillMigrationTest {

  /** The migration under test; the harness derives the baseline from it (issue #1755). */
  private static final String FORM_URL_BACKFILL_MIGRATION = "20260904.1200";

  private static MigrationTestHarness harness;
  private static MigrateResult migrateResult;

  @BeforeAll
  static void migrate() {
    harness = MigrationTestHarness.start("the form_data url backfill migration test");

    // Only what the migration reads: the site.url property and the two url-bearing tables.
    harness.execute(
        "CREATE TABLE site_properties (property_name VARCHAR(255), property_value VARCHAR(255))",
        "CREATE TABLE form_data (form_data_id BIGSERIAL PRIMARY KEY, url VARCHAR(512),"
            + " note VARCHAR(255) NOT NULL)",
        "CREATE TABLE form_submission_failures (id BIGSERIAL PRIMARY KEY, url VARCHAR(512),"
            + " note VARCHAR(255) NOT NULL)",
        "INSERT INTO site_properties VALUES ('site.url', 'https://www.example.com')",
        "INSERT INTO form_data (url, note) VALUES"
            // The reported shape: the origin's own hostname behind Front Door.
            + " ('https://app-pilot-abc123.azurewebsites.net/contact-us', 'reported'),"
            // The query string is part of the record and must survive untouched.
            + " ('https://app-pilot-abc123.azurewebsites.net/c?utm=a&x=1', 'query'),"
            // Already correct: proves the migration is idempotent rather than rewriting every row.
            + " ('https://www.example.com/already-right', 'correct'),"
            // An authority carrying a port must be replaced whole, not partially.
            + " ('http://origin:8080/deep/path/here', 'port'),"
            // No path at all -- the replacement must not invent one or produce a trailing slash.
            + " ('https://origin.internal', 'nopath'),"
            + " (NULL, 'null'),"
            // Not absolute: there is no authority to replace, so it must be left exactly as-is.
            + " ('not-a-url', 'relative')",
        "INSERT INTO form_submission_failures (url, note) VALUES"
            + " ('https://app-pilot-abc123.azurewebsites.net/rejected', 'reported'),"
            + " ('https://www.example.com/rejected-right', 'correct')");

    migrateResult = harness.applyOnly(FORM_URL_BACKFILL_MIGRATION);
  }

  @AfterAll
  static void stopDatabase() {
    try {
      DataSource.shutdown();
    } catch (Exception e) {
      // Never initialized when Docker is unavailable
    }
    if (harness != null) {
      harness.close();
    }
  }

  @Test
  void migrationAppliesSuccessfully() {
    assertTrue(migrateResult.success,
        "form url backfill migration did not apply cleanly: " + migrateResult.warnings);
  }

  @Test
  void repointsTheOriginHostnameAtTheSiteAddress() {
    assertEquals("https://www.example.com/contact-us", urlOf("form_data", "reported"));
  }

  @Test
  void keepsThePathAndQueryExactly() {
    // Only the scheme and authority are replaced; the part that says which page was submitted,
    // and the only part carrying information, has to survive byte for byte.
    assertEquals("https://www.example.com/c?utm=a&x=1", urlOf("form_data", "query"));
  }

  @Test
  void replacesAnAuthorityThatCarriesAPort() {
    assertEquals("https://www.example.com/deep/path/here", urlOf("form_data", "port"));
  }

  @Test
  void handlesAUrlWithNoPath() {
    assertEquals("https://www.example.com", urlOf("form_data", "nopath"));
  }

  @Test
  void leavesRowsAlreadyOnTheSiteAddressAlone() {
    // This is the idempotency guarantee: re-running matches nothing, and rows written by the fixed
    // code are never rewritten.
    assertEquals("https://www.example.com/already-right", urlOf("form_data", "correct"));
  }

  @Test
  void leavesNullAndNonAbsoluteUrlsAlone() {
    assertNull(urlOf("form_data", "null"));
    assertEquals("not-a-url", urlOf("form_data", "relative"));
  }

  @Test
  void repairsRejectedSubmissionsToo() {
    // form_submission_failures.url comes from the same resolvePageUrl, so it carries the same wrong
    // host and would otherwise be left behind by a fix that only considered form_data.
    assertEquals("https://www.example.com/rejected", urlOf("form_submission_failures", "reported"));
    assertEquals("https://www.example.com/rejected-right", urlOf("form_submission_failures", "correct"));
  }

  private static String urlOf(String table, String note) {
    // The table name is a literal from this test, never input, so interpolating it is safe here.
    try (Connection connection = harness.connection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT url FROM " + table + " WHERE note = ?")) {
      statement.setString(1, note);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next(), "no " + table + " row tagged '" + note + "'");
        return rs.getString("url");
      }
    } catch (SQLException e) {
      throw new IllegalStateException("could not read " + table + "." + note, e);
    }
  }
}
