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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Column-level half of this repo's install/upgrade parity convention: every COLUMN an upgrade
 * migration adds (and does not later drop) must also exist in the install schema.
 *
 * <p>
 * Fresh installs never run {@code UPGRADE_*} migrations, so a column that only an upgrade adds is
 * simply absent there. That is how {@code web_pages.tsv} (#404) went missing: the tsvector column,
 * its trigger and its GIN index shipped only in UPGRADE_20260726.1003, so on a fresh install
 * {@code WebPageRepository.search()} queried a column that did not exist, {@code DB.selectAllFrom}
 * swallowed the SQLException, and web page title search returned zero results for every query.
 * </p>
 *
 * <p>
 * The table-level counterpart lives in {@code SchemaInstallUpgradeParityTest}. This test needs no
 * Docker -- it reads the .sql files directly.
 * </p>
 *
 * @author Elizabeth Houser
 * @created 8/26/26
 */
class SchemaColumnParityTest {

  private static final Path INSTALL_DIR = Path.of("src/main/resources/database/install");
  private static final Path UPGRADE_DIR = Path.of("src/main/resources/database/upgrade");

  /**
   * Columns an upgrade adds that are deliberately absent from the install schema. Empty today; add
   * an entry with a comment explaining WHY rather than loosening the assertion.
   */
  private static final Set<String> UPGRADE_ONLY_COLUMNS = Set.of();

  private static final Pattern CREATE_TABLE = Pattern.compile(
      "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?\"?(\\w+)\"?\\s*\\(", Pattern.CASE_INSENSITIVE);
  private static final Pattern ALTER_TABLE = Pattern.compile(
      "ALTER\\s+TABLE\\s+(?:ONLY\\s+)?(?:IF\\s+EXISTS\\s+)?\"?(\\w+)\"?\\s+(.*?);",
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern ADD_COLUMN = Pattern.compile(
      "^ADD\\s+(?:COLUMN\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?\"?(\\w+)\"?", Pattern.CASE_INSENSITIVE);
  private static final Pattern DROP_COLUMN = Pattern.compile(
      "^DROP\\s+(?:COLUMN\\s+)?(?:IF\\s+EXISTS\\s+)?\"?(\\w+)\"?", Pattern.CASE_INSENSITIVE);
  private static final Pattern RENAME_COLUMN = Pattern.compile(
      "^RENAME\\s+(?:COLUMN\\s+)?\"?(\\w+)\"?\\s+TO\\s+\"?(\\w+)\"?", Pattern.CASE_INSENSITIVE);
  /** ALTER actions that are not column additions (constraints, types, defaults, ownership...). */
  private static final Pattern NOT_A_COLUMN = Pattern.compile(
      "^(ADD\\s+(CONSTRAINT|PRIMARY|FOREIGN|UNIQUE|CHECK|EXCLUDE)|DROP\\s+CONSTRAINT|ALTER|OWNER"
          + "|RENAME\\s+TO|SET|VALIDATE|ENABLE|DISABLE|CLUSTER|INHERIT|NO\\s+INHERIT|ATTACH|DETACH)",
      Pattern.CASE_INSENSITIVE);
  /**
   * Table-constraint keywords, compared against a definition's FIRST TOKEN exactly. Never use
   * startsWith here -- it silently swallows real columns whose names begin with one of these, e.g.
   * blog_posts.exclude_from_feed ("EXCLUDE") or a check_digit / unique_ref.
   */
  private static final Set<String> CONSTRAINT_KEYWORDS =
      Set.of("CONSTRAINT", "UNIQUE", "CHECK", "PRIMARY", "FOREIGN", "EXCLUDE", "LIKE");

  @Test
  void everyColumnAddedByAnUpgradeAlsoExistsInTheInstallSchema() throws IOException {
    Map<String, Set<String>> install = installSchema();
    Map<String, Set<String>> expected = columnsAddedByUpgrades();

    // Guard against a vacuous pass: if the parsers stop matching, fail loudly instead of "clean".
    assertTrue(install.size() > 100,
        "expected the install scripts to define many tables, found " + install.size()
            + " -- the parser or the path is wrong, not the schema");
    assertTrue(expected.size() > 5,
        "expected upgrades to add columns to several tables, found " + expected.size()
            + " -- the ALTER TABLE parser is not matching");

    List<String> missing = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : expected.entrySet()) {
      String table = entry.getKey();
      Set<String> installColumns = install.get(table);
      if (installColumns == null) {
        // Table-level drift; SchemaInstallUpgradeParityTest owns that case.
        continue;
      }
      for (String column : entry.getValue()) {
        if (!installColumns.contains(column) && !UPGRADE_ONLY_COLUMNS.contains(table + "." + column)) {
          missing.add(table + "." + column);
        }
      }
    }

    assertTrue(missing.isEmpty(),
        "These columns are added by an upgrade migration but are missing from the install schema, so"
            + " they will not exist on a fresh install (fresh installs skip UPGRADE_* migrations)."
            + " Add them to the appropriate NEW_* script:\n  " + String.join("\n  ", missing));
  }

  /** The parser must not be fooled by commented-out DDL or by constraint-keyword prefixes. */
  @Test
  void parserHandlesCommentsAndConstraintKeywordPrefixes() {
    String sql = ""
        + "CREATE TABLE sample (\n"
        + "  sample_id BIGSERIAL PRIMARY KEY,\n"
        + "  exclude_from_feed BOOLEAN NOT NULL DEFAULT false,\n"
        + "  price NUMERIC(10, 2),\n"
        + "  label VARCHAR(20) DEFAULT 'a,b',\n"
        + "  CONSTRAINT sample_unique UNIQUE (sample_id),\n"
        + "  PRIMARY KEY (sample_id)\n"
        + ");\n"
        + "/* CREATE TABLE ghost (ghost_id BIGINT); */\n";
    Map<String, Set<String>> schema = parseCreateTables(stripComments(sql));

    assertEquals(Set.of("sample"), schema.keySet(), "block-commented tables must not be parsed");
    assertEquals(new TreeSet<>(Set.of("sample_id", "exclude_from_feed", "price", "label")),
        new TreeSet<>(schema.get("sample")),
        "columns whose names start with a constraint keyword must survive, and constraint clauses"
            + " (and commas inside types or string defaults) must not become columns");
  }

  /** Install schema: CREATE TABLE bodies, then any ALTERs the install scripts themselves apply. */
  private static Map<String, Set<String>> installSchema() throws IOException {
    Map<String, Set<String>> schema = new TreeMap<>();
    for (Path file : sqlFiles(INSTALL_DIR)) {
      String sql = stripComments(Files.readString(file));
      parseCreateTables(sql).forEach((table, columns) ->
          schema.computeIfAbsent(table, unused -> new TreeSet<>()).addAll(columns));
      applyAlters(sql, schema, false);
    }
    return schema;
  }

  /** Net columns added by upgrade migrations, replayed in version (filename) order. */
  private static Map<String, Set<String>> columnsAddedByUpgrades() throws IOException {
    Map<String, Set<String>> added = new LinkedHashMap<>();
    for (Path file : sqlFiles(UPGRADE_DIR)) {
      applyAlters(stripComments(Files.readString(file)), added, true);
    }
    added.values().removeIf(Set::isEmpty);
    return added;
  }

  /**
   * Applies every column-affecting ALTER TABLE action to {@code schema}. When {@code createMissing}
   * is set, a table not yet present is created on demand (the upgrade side tracks only deltas).
   */
  private static void applyAlters(String sql, Map<String, Set<String>> schema, boolean createMissing) {
    Matcher matcher = ALTER_TABLE.matcher(sql);
    while (matcher.find()) {
      String table = matcher.group(1).toLowerCase();
      for (String rawAction : splitTopLevel(matcher.group(2))) {
        String action = rawAction.trim();
        if (action.isEmpty() || NOT_A_COLUMN.matcher(action).find()) {
          continue;
        }
        Matcher rename = RENAME_COLUMN.matcher(action);
        Matcher drop = DROP_COLUMN.matcher(action);
        Matcher add = ADD_COLUMN.matcher(action);
        if (rename.find()) {
          Set<String> columns = columnsFor(schema, table, createMissing);
          if (columns != null) {
            columns.remove(rename.group(1).toLowerCase());
            columns.add(rename.group(2).toLowerCase());
          }
        } else if (drop.find()) {
          Set<String> columns = schema.get(table);
          if (columns != null) {
            columns.remove(drop.group(1).toLowerCase());
          }
        } else if (add.find()) {
          Set<String> columns = columnsFor(schema, table, createMissing);
          if (columns != null) {
            columns.add(add.group(1).toLowerCase());
          }
        }
      }
    }
  }

  private static Set<String> columnsFor(Map<String, Set<String>> schema, String table, boolean create) {
    if (create) {
      return schema.computeIfAbsent(table, unused -> new TreeSet<>());
    }
    return schema.get(table);
  }

  private static Map<String, Set<String>> parseCreateTables(String sql) {
    Map<String, Set<String>> schema = new TreeMap<>();
    Matcher matcher = CREATE_TABLE.matcher(sql);
    while (matcher.find()) {
      String body = parenBody(sql, matcher.end() - 1);
      schema.computeIfAbsent(matcher.group(1).toLowerCase(), unused -> new TreeSet<>())
          .addAll(columnNames(body));
    }
    return schema;
  }

  private static Set<String> columnNames(String body) {
    Set<String> columns = new TreeSet<>();
    for (String raw : splitTopLevel(body)) {
      String definition = raw.trim();
      if (definition.isEmpty()) {
        continue;
      }
      String first = definition.split("\\s+")[0].replace("\"", "");
      if (CONSTRAINT_KEYWORDS.contains(first.toUpperCase())) {
        continue;
      }
      columns.add(first.toLowerCase());
    }
    return columns;
  }

  /** Splits on commas at paren-depth zero and outside single-quoted literals. */
  private static List<String> splitTopLevel(String text) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int start = 0;
    boolean inString = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inString) {
        if (c == '\'') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
            i++;
          } else {
            inString = false;
          }
        }
      } else if (c == '\'') {
        inString = true;
      } else if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        parts.add(text.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(text.substring(start));
    return parts;
  }

  /** Returns the text between the paren at {@code openIndex} and its match. */
  private static String parenBody(String sql, int openIndex) {
    int depth = 0;
    boolean inString = false;
    for (int i = openIndex; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (inString) {
        if (c == '\'') {
          if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
            i++;
          } else {
            inString = false;
          }
        }
      } else if (c == '\'') {
        inString = true;
      } else if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          return sql.substring(openIndex + 1, i);
        }
      }
    }
    throw new IllegalStateException("unbalanced parentheses in CREATE TABLE body");
  }

  private static String stripComments(String sql) {
    return sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("--[^\n]*", "");
  }

  private static List<Path> sqlFiles(Path dir) throws IOException {
    assertTrue(Files.isDirectory(dir), "expected a directory at " + dir.toAbsolutePath()
        + " -- tests must run with the repository root as the working directory");
    try (Stream<Path> paths = Files.walk(dir)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".sql"))
          .sorted()
          .toList();
    }
  }
}
