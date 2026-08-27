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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards this repo's install/upgrade parity convention across EVERY table, not just the handful
 * that happen to have a hand-written migration test.
 *
 * <p>
 * Fresh installs deliberately do not run UPGRADE_* migrations: {@link
 * com.simisinc.platform.application.admin.DatabaseCommand#installDatabase()} runs the NEW_*
 * scripts and then baselines the upgrade Flyway instance to a high version, on the premise that
 * "new installs start with the latest schema from NEW_* migrations". That premise only holds if
 * every table introduced by an upgrade migration is also defined in the install scripts -- when a
 * change adds the upgrade half and forgets the install half, the table simply does not exist on a
 * fresh install, and the feature that depends on it fails there and only there.
 * </p>
 *
 * <p>
 * That is not hypothetical: content_versions (#406) shipped with its UPGRADE migration and its
 * install-side site_properties row, but never got its CREATE TABLE added to NEW_10010 -- so on any
 * fresh install ContentRepository#publish's snapshot INSERT failed, and because that INSERT shares
 * the publish transaction, the publish itself silently rolled back. {@link WebVitalsMigrationTest}
 * asserts this same convention for web_vitals/web_vitals_aggregates by name; this test enforces it
 * for every table at once, so the next omission fails here instead of on someone's fresh install.
 * </p>
 *
 * @author Elizabeth Houser
 * @created 8/26/26
 */
class SchemaInstallUpgradeParityTest {

  private static final Path INSTALL_DIR = Path.of("src/main/resources/database/install");
  private static final Path UPGRADE_DIR = Path.of("src/main/resources/database/upgrade");

  /**
   * Tables an upgrade migration creates that are deliberately absent from the install scripts --
   * e.g. a table created by one migration and dropped by a later one, which a fresh install should
   * never materialize. Empty today; add an entry with a comment explaining WHY rather than
   * weakening the assertion below.
   */
  private static final Set<String> UPGRADE_ONLY_TABLES = Set.of();

  @Test
  void everyTableCreatedByAnUpgradeAlsoExistsInTheInstallScripts() throws IOException {
    Set<String> installTables = tablesCreatedIn(INSTALL_DIR);
    // Sanity check the parser itself: if the regex or the paths ever stop matching, this test must
    // fail loudly rather than pass vacuously on two empty sets.
    assertTrue(installTables.size() > 100,
        "expected the install scripts to define many tables, found " + installTables.size()
            + " -- the parser or the path is wrong, not the schema");

    List<String> missing = new ArrayList<>();
    for (TableRef ref : tableRefsIn(UPGRADE_DIR)) {
      if (!installTables.contains(ref.table) && !UPGRADE_ONLY_TABLES.contains(ref.table)) {
        missing.add(ref.table + " (created by " + ref.source + ")");
      }
    }

    assertTrue(missing.isEmpty(),
        "These tables are created by an upgrade migration but never by the install scripts, so they"
            + " will not exist on a fresh install (fresh installs skip UPGRADE_* migrations). Add the"
            + " same CREATE TABLE to the appropriate NEW_* script:\n  "
            + String.join("\n  ", missing));
  }

  /**
   * The gate above is only as good as its comment handling: a CREATE TABLE that is commented out
   * must never count as a definition, or a real omission passes unnoticed. Exercised on an inline
   * snippet rather than the live scripts so it keeps testing the parser even as they change.
   */
  @Test
  void commentedOutTablesAreNotCountedAsDefinitions() {
    String sql = ""
        + "CREATE TABLE real_one (id BIGSERIAL PRIMARY KEY);\n"
        + "-- CREATE TABLE line_commented (id BIGINT);\n"
        + "/* aspirational, not created yet\n"
        + "CREATE TABLE block_commented (id BIGINT);\n"
        + "*/\n";
    Set<String> found = new TreeSet<>();
    Matcher matcher = CREATE_TABLE.matcher(stripComments(sql));
    while (matcher.find()) {
      found.add(matcher.group(1).toLowerCase());
    }
    assertTrue(found.contains("real_one"), "the live CREATE TABLE should be found, got " + found);
    assertTrue(!found.contains("line_commented") && !found.contains("block_commented"),
        "commented-out tables must not count as definitions, got " + found);
  }

  /** Collects the table names created by every .sql file under the directory tree. */
  private static Set<String> tablesCreatedIn(Path dir) throws IOException {
    Set<String> tables = new TreeSet<>();
    for (TableRef ref : tableRefsIn(dir)) {
      tables.add(ref.table);
    }
    return tables;
  }

  private static List<TableRef> tableRefsIn(Path dir) throws IOException {
    assertTrue(Files.isDirectory(dir), "expected a directory at " + dir.toAbsolutePath()
        + " -- tests must run with the repository root as the working directory");
    List<TableRef> refs = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(dir)) {
      List<Path> sqlFiles = paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".sql"))
          .sorted()
          .toList();
      for (Path sqlFile : sqlFiles) {
        // Comments are stripped first so a commented-out CREATE TABLE never counts as a real
        // definition on either side. BOTH forms matter: the install scripts carry several `--`
        // future-work notes, and NEW_10010 has an entire CREATE TABLE email_templates sitting
        // inside a /* ... */ block. Counting that block as a definition would let a future upgrade
        // that really creates email_templates pass this gate while fresh installs lacked the table.
        String sql = stripComments(Files.readString(sqlFile));
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
          refs.add(new TableRef(matcher.group(1).toLowerCase(), dir.relativize(sqlFile).toString()));
        }
      }
    }
    return refs;
  }

  private static final Pattern CREATE_TABLE = Pattern.compile(
      "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?\"?(\\w+)\"?", Pattern.CASE_INSENSITIVE);

  private static String stripComments(String sql) {
    return sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("--[^\n]*", "");
  }

  /** A single CREATE TABLE occurrence, kept with its file so a failure names where to look. */
  private record TableRef(String table, String source) {
  }
}
