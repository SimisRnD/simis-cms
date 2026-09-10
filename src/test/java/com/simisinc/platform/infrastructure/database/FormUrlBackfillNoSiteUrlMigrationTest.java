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
 * The unset-site.url guard for UPGRADE_20260904.1200__backfill_form_url_host.sql (issue #1835).
 *
 * <p>A second class rather than another test in {@link FormUrlBackfillMigrationTest}: the migration
 * reads site.url once, so this scenario needs its own database, and
 * {@link MigrationTestHarness#connection()} hands out connections from the global {@code DB} pool.
 * Starting a second harness inside one class re-points that pool at the second container and
 * stopping it breaks the first class's remaining assertions -- one harness per class is why
 * ItemOrderMigrationTest and WebVitalsMigrationTest are separate classes too.
 *
 * <p>site.url is seeded empty on a fresh install, and a value with no scheme (someone typing
 * "www.example.com") yields no authority to graft on. Guessing one would be worse than leaving the
 * rows alone, so both cases must be a no-op rather than a rewrite.
 *
 * @author SimIS Inc.
 */
class FormUrlBackfillNoSiteUrlMigrationTest {

  private static final String FORM_URL_BACKFILL_MIGRATION = "20260904.1200";

  private static MigrationTestHarness harness;
  private static MigrateResult migrateResult;

  @BeforeAll
  static void migrate() {
    harness = MigrationTestHarness.start("the form_data url backfill no-site-url guard test");

    harness.execute(
        "CREATE TABLE site_properties (property_name VARCHAR(255), property_value VARCHAR(255))",
        "CREATE TABLE form_data (form_data_id BIGSERIAL PRIMARY KEY, url VARCHAR(512),"
            + " note VARCHAR(255) NOT NULL)",
        "CREATE TABLE form_submission_failures (id BIGSERIAL PRIMARY KEY, url VARCHAR(512),"
            + " note VARCHAR(255) NOT NULL)",
        // The value a fresh install seeds.
        "INSERT INTO site_properties VALUES ('site.url', '')",
        "INSERT INTO form_data (url, note) VALUES"
            + " ('https://app-pilot-abc123.azurewebsites.net/contact-us', 'reported')",
        "INSERT INTO form_submission_failures (url, note) VALUES"
            + " ('https://app-pilot-abc123.azurewebsites.net/rejected', 'reported')");

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
  void migrationStillAppliesCleanlyWithNoSiteUrl() {
    // It must succeed, not error -- an upgrade that throws here would block the whole deployment
    // for every install that has not filled in site.url.
    assertTrue(migrateResult.success,
        "migration failed with site.url unset: " + migrateResult.warnings);
  }

  @Test
  void leavesEveryRowAloneWhenSiteUrlIsNotConfigured() {
    assertEquals("https://app-pilot-abc123.azurewebsites.net/contact-us", urlOf("form_data"));
    assertEquals("https://app-pilot-abc123.azurewebsites.net/rejected",
        urlOf("form_submission_failures"));
  }

  private static String urlOf(String table) {
    // The table name is a literal from this test, never input, so interpolating it is safe here.
    try (Connection connection = harness.connection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT url FROM " + table + " WHERE note = ?")) {
      statement.setString(1, "reported");
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next(), "no " + table + " row tagged 'reported'");
        return rs.getString("url");
      }
    } catch (SQLException e) {
      throw new IllegalStateException("could not read " + table, e);
    }
  }
}
