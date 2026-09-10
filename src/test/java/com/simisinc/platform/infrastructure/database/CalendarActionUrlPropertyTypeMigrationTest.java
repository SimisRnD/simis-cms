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
 * Covers UPGRADE_20260905.2100, which clears the property_type on site.calendar.actionUrl.
 *
 * <p>The property shipped as type 'url', and SitePropertiesEditorWidget validates that type with
 * commons-validator UrlValidator over {http, https} -- so a site-relative path was rejected and the
 * whole Site Settings form refused to save. calendar-event-details.jsp branches on the value and has
 * always accepted a path, so the field was unable to store what it exists for.
 *
 * <p>The cases below are the populations the UPDATE has to distinguish. It targets one property by
 * name, so the risks are that it misses the row it means to fix, or reaches rows it does not own --
 * and, because the same statement replays onto a freshly installed database where the column is
 * already null, that it errors instead of being a no-op.
 */
class CalendarActionUrlPropertyTypeMigrationTest {

  /** The migration under test; the harness derives the baseline from it (issue #1755). */
  private static final String ACTION_URL_TYPE_MIGRATION = "20260905.2100";

  private static MigrationTestHarness harness;
  private static MigrateResult migrateResult;

  @BeforeAll
  static void migrate() {
    harness = MigrationTestHarness.start("the calendar action url property type migration test");

    // Only the columns this migration reads and writes.
    harness.execute(
        "CREATE TABLE site_properties (property_id BIGSERIAL PRIMARY KEY, property_order INTEGER,"
            + " property_label VARCHAR(255), property_name VARCHAR(255) NOT NULL,"
            + " property_value VARCHAR(255), property_type VARCHAR(100))",
        "INSERT INTO site_properties (property_order, property_label, property_name, property_value, property_type) VALUES"
            // The defect: typed 'url', so a page path could never be saved.
            + " (28, 'Event page button link', 'site.calendar.actionUrl', '/trade-shows', 'url'),"
            // Its untyped neighbour, already correct: must be left alone.
            + " (27, 'Event page button label', 'site.calendar.actionLabel', 'Trade Shows', NULL),"
            // A different property that legitimately IS a url: must keep its type.
            + " (8, 'Site URL', 'site.url', 'https://example.org', 'url'),"
            // A different property of another type entirely: must keep its type.
            + " (32, 'Header details page', 'site.header.page', '/about-us', 'web-page')");

    migrateResult = harness.applyOnly(ACTION_URL_TYPE_MIGRATION);
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
        "the calendar action url property type migration did not apply cleanly: " + migrateResult.warnings);
  }

  @Test
  void clearsTheTypeOnTheEventPageButtonLink() {
    // The whole point: untyped means SitePropertiesEditorWidget neither validates nor rewrites the
    // value, so a page path saves and an absolute URL survives intact.
    assertNull(typeOf("site.calendar.actionUrl"),
        "site.calendar.actionUrl still carries a property_type, so a page path is still rejected");
  }

  @Test
  void keepsWhateverValueTheSiteHadAlreadySaved() {
    // The migration changes the type, never the value. A site that worked around the validation by
    // saving an absolute URL keeps it, and the JSP still renders it -- as an external link.
    assertEquals("/trade-shows", valueOf("site.calendar.actionUrl"),
        "the migration rewrote the property value; it should only touch property_type");
  }

  @Test
  void leavesEveryOtherPropertyTypeAlone() {
    // The UPDATE is filtered by property_name. site.url is genuinely a url and must keep validating,
    // and site.header.page must keep the web-page behaviour that prefixes a slash.
    assertEquals("url", typeOf("site.url"), "site.url lost its type; it still needs url validation");
    assertEquals("web-page", typeOf("site.header.page"), "site.header.page lost its web-page type");
    assertNull(typeOf("site.calendar.actionLabel"), "the untyped label row should be unchanged");
  }

  @Test
  void isANoOpOnAFreshInstallWhereTheColumnIsAlreadyNull() {
    // NEW_10000 now creates the row with no property_type, so DatabaseMigrationTest replays this
    // migration onto a database where it matches a row whose column is already null. Setting null to
    // null has to be silent rather than an error -- that is what makes the replay exception
    // unnecessary. Re-running it here asserts the same thing directly.
    harness.execute("UPDATE site_properties SET property_type = NULL WHERE property_name = 'site.calendar.actionUrl'");
    assertNull(typeOf("site.calendar.actionUrl"));
    assertEquals("/trade-shows", valueOf("site.calendar.actionUrl"));
  }

  private static String typeOf(String propertyName) {
    return columnOf("property_type", propertyName);
  }

  private static String valueOf(String propertyName) {
    return columnOf("property_value", propertyName);
  }

  private static String columnOf(String column, String propertyName) {
    try (Connection connection = harness.connection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT " + column + " FROM site_properties WHERE property_name = ?")) {
      statement.setString(1, propertyName);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? resultSet.getString(1) : null;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("could not read " + column + " for " + propertyName, e);
    }
  }
}
