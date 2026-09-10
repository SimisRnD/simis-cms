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

package com.simisinc.platform.application.admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.infrastructure.database.DB;

/**
 * Reports schema objects the application depends on that are not actually in the database.
 *
 * <p>Issue #1753: a schema change can reach neither migration track and nothing says so. A database
 * installed between the day an upgrade migration was written and the day its install counterpart was
 * added gets the change from neither -- the upgrade sits at or below the install baseline and is
 * recorded as already applied, and the install track never runs again once a database is installed.
 * Both files exist, both are correct, and no build check, test or runtime error fires.
 *
 * <p>That is not hypothetical. It happened to web_pages' full-text index (issue #1745): the column,
 * its maintenance trigger and its GIN index were all absent, so page-title search returned nothing
 * for every query, site-wide, for weeks. The widget that runs that search hides itself when it has
 * no results, so the failure had no visible symptom at all -- it was eventually reported as one page
 * being unfindable.
 *
 * <p>Full-text search is checked because it is the case that has already failed and because it fails
 * <em>silently</em>: a missing column makes the query error where callers swallow it, and a missing
 * trigger leaves the column present but permanently empty, which looks identical to "nothing
 * matches". The expectations below are not a hand-maintained catalogue of the schema -- each entry
 * is a table some repository actually runs {@code tsv @@ PLAINTO_TSQUERY(...)} against, so the list
 * is exactly as long as the code's real dependency on it.
 *
 * <p>This reports; it does not repair. A missing object means a migration did not run, and the fix
 * belongs in a migration where it is recorded and repeatable -- not in a startup path that would
 * quietly paper over the same gap on every boot.
 *
 * @author SimIS Inc.
 */
public class SchemaIntegrityCommand {

  private static Log LOG = LogFactory.getLog(SchemaIntegrityCommand.class);

  /** A table whose rows the application full-text searches, and the search configuration it uses. */
  static class FullTextExpectation {
    final String tableName;
    final String searchConfiguration;
    final String usedBy;

    FullTextExpectation(String tableName, String searchConfiguration, String usedBy) {
      this.tableName = tableName;
      this.searchConfiguration = searchConfiguration;
      this.usedBy = usedBy;
    }
  }

  /**
   * One entry per table the code runs a tsvector query against. Adding a repository that
   * full-text searches a new table means adding it here; leaving it out only means that table is
   * not checked, never that the check misreports.
   */
  static final List<FullTextExpectation> FULL_TEXT_EXPECTATIONS = Arrays.asList(
      new FullTextExpectation("web_pages", "title_stem", "WebPageRepository.search"),
      new FullTextExpectation("wiki_pages", "title_stem", "WikiPageRepository"),
      new FullTextExpectation("calendar_events", "title_stem", "CalendarEventRepository"),
      new FullTextExpectation("items", "title_stem", "ItemRepository"),
      new FullTextExpectation("content", "content_stem", "ContentRepository"),
      new FullTextExpectation("blog_posts", "content_stem", "BlogPostRepository"),
      new FullTextExpectation("files", "file_stem", "FileItemRepository"),
      new FullTextExpectation("item_files", "item_file_stem", "ItemFileItemRepository"));

  private SchemaIntegrityCommand() {
    // static helper
  }

  /**
   * Every missing object, as a human-readable line naming the table, what is absent, and who needs
   * it. Empty when the schema is complete.
   */
  public static List<String> findMissingObjects() {
    try (Connection connection = DB.getConnection()) {
      if (connection == null) {
        LOG.warn("Schema integrity check skipped: no database connection");
        return new ArrayList<>();
      }
      return findMissingObjects(connection);
    } catch (Exception e) {
      // A check that cannot run must not stop the application from starting
      LOG.warn("Schema integrity check could not complete: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  /**
   * The check itself, against a caller-supplied connection so it can be exercised against a database
   * deliberately put into the broken state rather than only against a correct one.
   */
  static List<String> findMissingObjects(Connection connection) {
    List<String> findings = new ArrayList<>();
    try {
      for (FullTextExpectation expectation : FULL_TEXT_EXPECTATIONS) {
        if (!tableExists(connection, expectation.tableName)) {
          // Not a finding. A table absent entirely is a different and much louder problem than a
          // table missing one of its indexes, and it is not what this check is for.
          continue;
        }
        if (!hasTsvectorColumn(connection, expectation.tableName)) {
          findings.add(expectation.tableName + " has no tsvector column, so " + expectation.usedBy
              + " cannot match anything (see issue #1753)");
          // The trigger and index below hang off that column; reporting them too would be noise
          continue;
        }
        if (!hasTsvectorTrigger(connection, expectation.tableName)) {
          findings.add(expectation.tableName + " has a tsvector column but no maintenance trigger, so it"
              + " is never populated and " + expectation.usedBy + " silently returns nothing");
        }
        if (!hasTsvectorIndex(connection, expectation.tableName)) {
          findings.add(expectation.tableName + " has a tsvector column but no GIN index, so "
              + expectation.usedBy + " still works but scans the table");
        }
      }
    } catch (Exception e) {
      LOG.warn("Schema integrity check could not complete: " + e.getMessage());
    }
    return findings;
  }

  /**
   * Runs the check and logs anything missing. Called after migrations, on the path where an object
   * would have been created, so the log line sits next to the migration output that should have
   * produced it.
   */
  public static void logMissingObjects() {
    List<String> findings = findMissingObjects();
    if (findings.isEmpty()) {
      LOG.debug("Schema integrity check passed");
      return;
    }
    LOG.error("Schema integrity check found " + findings.size() + " missing object(s). A migration that"
        + " should have created these did not run -- see issue #1753 for how a change can reach"
        + " neither the install nor the upgrade track:");
    for (String finding : findings) {
      LOG.error("  " + finding);
    }
  }

  private static boolean tableExists(Connection connection, String tableName) throws Exception {
    return queryReturnsRow(connection,
        "SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?",
        tableName);
  }

  private static boolean hasTsvectorColumn(Connection connection, String tableName) throws Exception {
    return queryReturnsRow(connection,
        "SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()"
            + " AND table_name = ? AND udt_name = 'tsvector'",
        tableName);
  }

  /**
   * Any trigger on the table will do. Naming is not consistent enough across the migrations to match
   * on, and a table carrying a tsvector column has no other reason to have a row-level trigger.
   */
  private static boolean hasTsvectorTrigger(Connection connection, String tableName) throws Exception {
    return queryReturnsRow(connection,
        "SELECT 1 FROM information_schema.triggers WHERE trigger_schema = current_schema()"
            + " AND event_object_table = ?",
        tableName);
  }

  private static boolean hasTsvectorIndex(Connection connection, String tableName) throws Exception {
    return queryReturnsRow(connection,
        "SELECT 1 FROM pg_indexes WHERE schemaname = current_schema()"
            + " AND tablename = ? AND indexdef ILIKE '%USING gin%'",
        tableName);
  }

  private static boolean queryReturnsRow(Connection connection, String sql, String parameter)
      throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, parameter);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }
}
