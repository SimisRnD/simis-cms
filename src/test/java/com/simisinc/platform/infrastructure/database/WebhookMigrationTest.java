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
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Install/upgrade parity check for issue #418's schema, no Docker required (same technique as
 * {@code WebVitalsMigrationTest#installSchemaMatchesUpgradeSchemaColumns}). Both
 * {@code webhook_subscription} and {@code webhook_delivery} are meant to be defined identically
 * in {@code NEW_10130__new_webhooks.sql} (fresh installs) and
 * {@code UPGRADE_20260801.1001__create_webhook_tables.sql} (existing databases) -- this fails
 * loudly if a future edit touches one file and not the other, the exact bug shape issue #431
 * already hit once in this codebase.
 */
class WebhookMigrationTest {

  private static final String INSTALL_PATH = "src/main/resources/database/install/NEW_10130__new_webhooks.sql";
  private static final String UPGRADE_PATH =
      "src/main/resources/database/upgrade/2026/UPGRADE_20260801.1001__create_webhook_tables.sql";

  @Test
  void installAndUpgradeDefineTheSameTablesWithTheSameColumns() throws IOException {
    String installSql = Files.readString(Path.of(INSTALL_PATH));
    String upgradeSql = Files.readString(Path.of(UPGRADE_PATH));

    for (String table : new String[] { "webhook_subscription", "webhook_delivery" }) {
      Set<String> installColumns = createTableColumns(installSql, table);
      Set<String> upgradeColumns = createTableColumns(upgradeSql, table);
      assertEquals(installColumns, upgradeColumns,
          table + " columns differ between the install and upgrade paths");
      assertTrue(installColumns.size() > 3, "sanity check: " + table + " should have several columns");
    }
  }

  @Test
  void upgradeMigrationUsesIfNotExistsSoItIsSafeToRunAgainstAnExistingDatabase() throws IOException {
    // Distinguishing feature from the install/ copy: an existing database may already be at or
    // past this version in some deployment path, and Flyway upgrade migrations in this codebase
    // consistently guard with IF NOT EXISTS for that reason (see UPGRADE_20260729.1003's allow_list
    // precedent).
    String upgradeSql = Files.readString(Path.of(UPGRADE_PATH));
    assertTrue(upgradeSql.contains("CREATE TABLE IF NOT EXISTS webhook_subscription"));
    assertTrue(upgradeSql.contains("CREATE TABLE IF NOT EXISTS webhook_delivery"));
  }

  /** Extracts column names from a {@code CREATE TABLE <name> ( ... )} block. */
  private static Set<String> createTableColumns(String sql, String tableName) {
    Pattern createTable = Pattern.compile(
        "CREATE TABLE (?:IF NOT EXISTS )?" + Pattern.quote(tableName) + "\\s*\\((.*)",
        Pattern.DOTALL);
    Matcher matcher = createTable.matcher(stripLineComments(sql));
    assertTrue(matcher.find(), "no CREATE TABLE " + tableName + " found");
    return parseColumnNames(closingParenBody(matcher.group(1)));
  }

  private static String stripLineComments(String sql) {
    return sql.replaceAll("--[^\n]*", "");
  }

  /** Given text starting just after a CREATE TABLE's opening paren, returns up to its matching close. */
  private static String closingParenBody(String afterOpenParen) {
    int depth = 1;
    for (int i = 0; i < afterOpenParen.length(); i++) {
      char c = afterOpenParen.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          return afterOpenParen.substring(0, i);
        }
      }
    }
    throw new IllegalArgumentException("Unbalanced parentheses in CREATE TABLE body");
  }

  private static Set<String> parseColumnNames(String body) {
    Set<String> columns = new HashSet<>();
    for (String rawLine : body.split(",\\n|,\\r\\n")) {
      String line = rawLine.trim();
      if (line.isEmpty()) {
        continue;
      }
      String upper = line.toUpperCase();
      // Skip table-level constraints, which don't start with a column name.
      if (upper.startsWith("PRIMARY KEY") || upper.startsWith("UNIQUE") || upper.startsWith("CONSTRAINT")
          || upper.startsWith("FOREIGN KEY") || upper.startsWith("CHECK")) {
        continue;
      }
      String[] parts = line.split("\\s+");
      if (parts.length > 0) {
        columns.add(parts[0].toLowerCase());
      }
    }
    return columns;
  }
}
