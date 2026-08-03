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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class SecretSitePropertiesCommandTest {

  @Test
  void isSecretRecognizesAKnownName() {
    assertTrue(SecretSitePropertiesCommand.isSecret("mail.password"));
  }

  @Test
  void isSecretRejectsAnUnknownName() {
    assertFalse(SecretSitePropertiesCommand.isSecret("site.timezone"));
  }

  @Test
  void isSecretRejectsNull() {
    assertFalse(SecretSitePropertiesCommand.isSecret(null));
  }

  /**
   * Guards {@code SECRET_PROPERTY_NAMES} against silent drift (issue #454 review): it is a
   * hand-maintained allowlist, so a new integration's key/token/password whose seed SQL a
   * developer forgets to also add here would be stored in plaintext at rest, with nothing else in
   * the codebase to catch the omission. This scans every real {@code INSERT INTO site_properties}
   * statement (install + upgrade migrations -- the actual source of every property name that will
   * ever reach the database) for names that look secret-like by suffix, and asserts each one is
   * registered.
   *
   * <p>{@code .key} is deliberately excluded from the heuristic, matching {@link
   * SecretSitePropertiesCommand}'s own documented caveat: it is ambiguous (a secret for some
   * services, a public browser-facing key for others such as Stripe/Square/captcha site keys), so
   * a blanket suffix match on it would false-positive on properties that must stay unencrypted and
   * visible to the browser.
   */
  @Test
  void everySecretLikeSeedPropertyNameIsRegistered() throws IOException {
    Set<String> suspicious = new HashSet<>();
    for (String propertyName : findAllSeededPropertyNames()) {
      if (looksSecretLike(propertyName) && !SecretSitePropertiesCommand.isSecret(propertyName)) {
        suspicious.add(propertyName);
      }
    }
    assertTrue(suspicious.isEmpty(),
        "these seeded site properties look like secrets by name but are not in "
            + "SecretSitePropertiesCommand.SECRET_PROPERTY_NAMES, so they are stored in plaintext: " + suspicious);
  }

  @Test
  void theScanItselfFindsAtLeastTheKnownSecretNames() throws IOException {
    // A sanity check on the scanner, not the allowlist: if this ever finds zero rows, the
    // regex/paths broke silently and the drift-guard above would pass for the wrong reason
    // (nothing to check) rather than a real one.
    Set<String> seeded = findAllSeededPropertyNames();
    assertTrue(seeded.contains("mail.password"), "expected to find mail.password in the seed SQL: " + seeded);
    assertTrue(seeded.size() > 50, "expected far more than 50 seeded site properties: " + seeded.size());
  }

  private static final Pattern INSERT_START_PATTERN = Pattern.compile(
      "INSERT\\s+INTO\\s+site_properties\\s*\\(", Pattern.CASE_INSENSITIVE);
  private static final Pattern VALUES_START_PATTERN = Pattern.compile("VALUES\\s*\\(", Pattern.CASE_INSENSITIVE);
  // Matches both forms seen in this codebase's migrations: "WHERE property_name = 'x'" and
  // "WHERE property_name IN ('x', 'y', ...)"
  private static final Pattern DELETE_PATTERN = Pattern.compile(
      "DELETE\\s+FROM\\s+site_properties\\s+WHERE\\s+property_name\\s*(?:=\\s*'([^']*)'|IN\\s*\\(([^)]*)\\))",
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern QUOTED_LITERAL = Pattern.compile("'([^']*)'");

  // Suffixes (the last dot-segment, lowercased) that indicate a genuine secret. ".key" is
  // intentionally absent -- see the javadoc above.
  private static final Set<String> SECRET_LIKE_SUFFIXES = Set.of(
      "secret", "secretkey", "apikey", "token", "password", "accesstoken", "clientsecret", "authheader");

  private static boolean looksSecretLike(String propertyName) {
    int dot = propertyName.lastIndexOf('.');
    String lastSegment = (dot >= 0 ? propertyName.substring(dot + 1) : propertyName).toLowerCase();
    return SECRET_LIKE_SUFFIXES.contains(lastSegment);
  }

  /**
   * Parses every {@code INSERT INTO site_properties (...) VALUES (...)} statement in every install
   * + upgrade migration, then subtracts every name a later migration's {@code DELETE FROM
   * site_properties WHERE property_name ...} removed again (e.g. {@code
   * UPGRADE_20260801.1003__remove_dead_webconferencing_properties.sql}, which deleted
   * {@code conferencing.bbb.secret} as dead-code cleanup for #525) -- a name that was seeded once
   * and later deleted never reaches a live schema (a fresh install never ran the old INSERT-only
   * migration in isolation, and an upgraded deployment ran the DELETE too), so it is not a live
   * plaintext-secret risk and would otherwise be a false positive here.
   *
   * <p>Column lists are single-line in practice, but the {@code VALUES} tuple is commonly on its
   * own line (see e.g. {@code UPGRADE_20260729.1000__robots_ai_crawler_properties.sql}), so this
   * can't be a per-line regex scan. It also can't be a naive "match up to the next )" regex
   * either: several property labels themselves contain parentheses (e.g. {@code 'Allow GPTBot
   * (OpenAI)'}), which would close the match early. {@link #findMatchingParen} walks the text
   * tracking quote state and paren depth together so a paren inside a quoted string is just a
   * character, not a delimiter.
   */
  private static Set<String> findAllSeededPropertyNames() throws IOException {
    Set<String> names = new HashSet<>();
    Set<String> deleted = new HashSet<>();
    for (Path sqlFile : findSqlFiles()) {
      String content = String.join("\n", Files.readAllLines(sqlFile).stream()
          .map(line -> line.trim().startsWith("--") ? "" : line)
          .collect(Collectors.toList()));

      Matcher insertMatcher = INSERT_START_PATTERN.matcher(content);
      while (insertMatcher.find()) {
        int columnsOpen = insertMatcher.end() - 1;
        int columnsClose = findMatchingParen(content, columnsOpen);
        if (columnsClose < 0) {
          continue;
        }
        List<String> columns = splitUnquoted(content.substring(columnsOpen + 1, columnsClose)).stream()
            .map(String::trim).collect(Collectors.toList());

        Matcher valuesMatcher = VALUES_START_PATTERN.matcher(content);
        if (!valuesMatcher.find(columnsClose)) {
          continue;
        }
        int valuesOpen = valuesMatcher.end() - 1;
        int valuesClose = findMatchingParen(content, valuesOpen);
        if (valuesClose < 0) {
          continue;
        }
        List<String> values = splitUnquoted(content.substring(valuesOpen + 1, valuesClose));

        int nameIndex = columns.indexOf("property_name");
        if (nameIndex < 0 || nameIndex >= values.size()) {
          continue;
        }
        names.add(unquote(values.get(nameIndex).trim()));
      }

      Matcher deleteMatcher = DELETE_PATTERN.matcher(content);
      while (deleteMatcher.find()) {
        String single = deleteMatcher.group(1);
        if (single != null) {
          deleted.add(single);
          continue;
        }
        Matcher literals = QUOTED_LITERAL.matcher(deleteMatcher.group(2));
        while (literals.find()) {
          deleted.add(literals.group(1));
        }
      }
    }
    names.removeAll(deleted);
    return names;
  }

  /** @return the index of the {@code )} matching the {@code (} at openParenIndex, or -1 if unbalanced */
  private static int findMatchingParen(String s, int openParenIndex) {
    boolean inQuote = false;
    int depth = 0;
    for (int i = openParenIndex; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\'') {
        inQuote = !inQuote;
      } else if (!inQuote && c == '(') {
        depth++;
      } else if (!inQuote && c == ')') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static List<Path> findSqlFiles() throws IOException {
    List<Path> files = new ArrayList<>();
    for (String dir : new String[]{"src/main/resources/database/install", "src/main/resources/database/upgrade"}) {
      Path root = Paths.get(dir);
      if (!Files.isDirectory(root)) {
        continue;
      }
      try (Stream<Path> walk = Files.walk(root)) {
        walk.filter(p -> p.toString().endsWith(".sql")).forEach(files::add);
      }
    }
    return files;
  }

  /** Splits a comma-separated list, ignoring commas inside single-quoted string literals. */
  private static List<String> splitUnquoted(String s) {
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuote = false;
    for (char c : s.toCharArray()) {
      if (c == '\'') {
        inQuote = !inQuote;
        current.append(c);
      } else if (c == ',' && !inQuote) {
        parts.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    parts.add(current.toString());
    return parts;
  }

  private static String unquote(String s) {
    if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }
}
