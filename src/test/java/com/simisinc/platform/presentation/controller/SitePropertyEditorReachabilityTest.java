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

package com.simisinc.platform.presentation.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Structural gate: a {@code site_properties} row an administrator is meant to configure only has a
 * field somewhere if its name is covered by a {@code sitePropertiesEditor} {@code <prefix>} on some
 * admin page.
 *
 * <p>{@code SitePropertyRepository.findAllByPrefix} matches {@code property_name LIKE prefix ||
 * '.%'}, and {@code SitePropertiesEditorWidget} renders -- and saves -- only the rows that query
 * returns. It has no insert path either, so an unregistered prefix cannot be reached by any page:
 * the row sits in the database with no field anywhere, and changing it takes a direct database
 * update. Nothing fails, nothing logs; the setting is simply not there.
 *
 * <p>That is how {@code password.maxAgeDays} shipped (#492): seeded correctly on both the install
 * and upgrade paths, read by {@code UserDetailsWidget} and {@code UsersListWidget}, and editable
 * nowhere, because no page registers a {@code password} prefix. It has since been renamed to
 * {@code security.password.maxAgeDays} so the existing {@code security} page picks it up. This test
 * exists so the next one fails here rather than in production, and is deliberately a different
 * shape of gate from the missing-seed class: there the row is absent, here the row is present and
 * unreachable.
 *
 * <p>Note {@code SitePropertySettingsPageCommand} is not the source of truth for this -- it maps
 * only the integration prefixes the integrations hub links to, so a prefix missing from it is
 * expected rather than a defect. {@code admin-layout.xml} and its sibling layouts are.
 *
 * @author elizabeth houser
 */
class SitePropertyEditorReachabilityTest {

  private static final Path INSTALL_DIR = Path.of("src/main/resources/database/install");
  private static final Path UPGRADE_DIR = Path.of("src/main/resources/database/upgrade");
  private static final File LAYOUT_DIR = new File("src/main/webapp/WEB-INF/web-layouts/page");

  /**
   * Root prefixes whose seeded properties knowingly have no admin editor today. This is a ratchet,
   * not an endorsement: every entry below is a pre-existing gap, and the point of the list is that
   * a NEW unreachable prefix fails this test instead of joining them silently. Fixing one means
   * deleting its line here, not adding to it -- and a new entry needs a reason that says why the
   * property must not be web-editable, not merely that nobody has built the page yet.
   */
  private static final Set<String> PREFIXES_WITHOUT_AN_ADMIN_EDITOR = Set.of(
      // Deployment-level settings read at startup (ContextListener, WebRequestFilter). Mostly host
      // filesystem paths -- system.filepath, system.configpath, system.customizations.filepath --
      // which must not be web-editable: a form that rewrote them from a browser would be a
      // privilege-escalation surface. system.upload.maxBytes is the odd one out and would be a
      // reasonable thing to expose; it is here only because it shares the prefix.
      "system",
      // Retention windows with no editor -- the state /admin/analytics-retention.jsp already
      // documents in its own page copy ("none of them currently has an admin editor -- changing any
      // of them today requires a direct database update"). Tracked separately from this test.
      "audit",
      "formData",
      "funnel",
      // Governed publish workflow (#407) and version-history caps (#957, #406). Read by
      // SaveContentCommand / PageServlet / the review widgets, seeded on both paths, editable
      // nowhere.
      "blogPost",
      "content",
      "webPage",
      // DatasetDownloadRemoteFileCommand.resolveMaxRows's bound on a paged remote download.
      "dataset");

  @Test
  void everySeededSitePropertyIsReachableFromSomeAdminEditorPage() throws Exception {
    Set<String> registeredPrefixes = registeredEditorPrefixes();
    assertTrue(registeredPrefixes.size() > 10,
        "parsed suspiciously few sitePropertiesEditor <prefix> registrations (" + registeredPrefixes.size()
            + ") -- the parser or the layout path is wrong, not the layouts");

    Set<String> seeded = liveSeededPropertyNames();
    assertTrue(seeded.size() > 100,
        "expected the seed scripts to define many site properties, found " + seeded.size()
            + " -- the parser or the path is wrong, not the schema");

    // Grouped by root prefix, because that is the unit an admin page registers and therefore the
    // unit a fix is applied in -- a failure should name the page that needs to exist, not repeat
    // every property under it.
    TreeMap<String, TreeSet<String>> unreachable = new TreeMap<>();
    for (String propertyName : seeded) {
      if (isCoveredByAPrefix(propertyName, registeredPrefixes)) {
        continue;
      }
      String rootPrefix = rootPrefixOf(propertyName);
      if (PREFIXES_WITHOUT_AN_ADMIN_EDITOR.contains(rootPrefix)) {
        continue;
      }
      unreachable.computeIfAbsent(rootPrefix, key -> new TreeSet<>()).add(propertyName);
    }

    assertTrue(unreachable.isEmpty(),
        "These seeded site properties are not matched by any sitePropertiesEditor <prefix> in "
            + LAYOUT_DIR.getPath() + ", so SitePropertyRepository.findAllByPrefix never returns them"
            + " and no admin page can render or save them -- the rows exist with no field anywhere."
            + " Either register the prefix on a page, rename the property under a prefix that is"
            + " already registered, or (only if it must never be web-editable) add the root prefix to"
            + " PREFIXES_WITHOUT_AN_ADMIN_EDITOR with the reason:\n  "
            + unreachable);
  }

  /**
   * The specific row this gate was written for (#492), asserted by name so the regression is
   * legible even if the sweep above is ever relaxed. Its siblings are checked alongside it: all
   * three password-policy settings belong to the same page.
   */
  @Test
  void thePasswordPolicySettingsAreReachableFromTheSecuritySettingsPage() throws Exception {
    Set<String> registeredPrefixes = registeredEditorPrefixes();
    assertTrue(registeredPrefixes.contains("security"),
        "no page registers a \"security\" prefix -- found " + registeredPrefixes);

    Set<String> seeded = liveSeededPropertyNames();
    for (String propertyName : List.of("security.password.maxAgeDays", "security.password.minLength",
        "security.password.requireComplexity")) {
      assertTrue(seeded.contains(propertyName), propertyName + " is not seeded by any install or upgrade script");
      assertTrue(isCoveredByAPrefix(propertyName, registeredPrefixes),
          propertyName + " is seeded but no registered <prefix> matches it, so it has no field on any admin page");
    }

    // The pre-rename name must be gone from the seeds, or a fresh install would create both rows
    // and the upgrade's rename would be skipped.
    assertFalse(seeded.contains("password.maxAgeDays"),
        "password.maxAgeDays is still seeded -- UPGRADE_20260831.1000 renames it to"
            + " security.password.maxAgeDays, and a seed of the old name would resurrect it");
  }

  /**
   * The sweep is only as good as its SQL parsing: a commented-out INSERT must not count as a seed,
   * a DELETE must retire the name, and a label containing quotes, commas or parentheses must not
   * shift the column the name is read from. Exercised on an inline snippet so it keeps testing the
   * parser even as the real scripts change.
   */
  @Test
  void theSeedParserHandlesCommentsDeletesAndAwkwardLabels() {
    String sql = ""
        + "INSERT INTO site_properties (property_order, property_label, property_name, property_value)"
        + " VALUES (1, 'Retention (days, terminal-state only)', 'live.one', '90');\n"
        + "INSERT INTO site_properties (property_label, property_name, property_value)"
        + " VALUES ('Tiles URL ({z}/{x}/{y} template)', 'live.two', '');\n"
        + "INSERT INTO site_properties (property_order, property_label, property_name, property_value)"
        + " VALUES (2, 'Bob''s threshold, revised', 'live.three', '1')\n"
        + "ON CONFLICT (property_name) DO NOTHING;\n"
        + "-- INSERT INTO site_properties (property_label, property_name, property_value)"
        + " VALUES ('Nope', 'commented.one', '');\n"
        + "/* not yet\n"
        + "INSERT INTO site_properties (property_label, property_name, property_value)"
        + " VALUES ('Nope', 'commented.two', '');\n"
        + "*/\n"
        + "INSERT INTO site_properties (property_label, property_name, property_value)"
        + " VALUES ('Gone soon', 'retired.one', '');\n"
        + "DELETE FROM site_properties WHERE property_name = 'retired.one';\n"
        + "INSERT INTO site_properties (property_label, property_name, property_value)"
        + " VALUES ('Gone soon too', 'retired.two', '');\n"
        + "DELETE FROM site_properties WHERE property_name IN (\n"
        + "  'retired.two',\n"
        + "  'retired.three'\n"
        + ");\n";

    Set<String> inserted = new TreeSet<>();
    collectInsertedPropertyNames(stripComments(sql), inserted);
    assertTrue(inserted.containsAll(Set.of("live.one", "live.two", "live.three")),
        "every live INSERT should be parsed, got " + inserted);
    assertTrue(inserted.contains("retired.one") && inserted.contains("retired.two"),
        "a later-deleted property is still an INSERT at parse time, got " + inserted);
    assertFalse(inserted.contains("commented.one") || inserted.contains("commented.two"),
        "commented-out INSERTs must not count as seeds, got " + inserted);

    Set<String> deleted = new TreeSet<>();
    collectDeletedPropertyNames(stripComments(sql), deleted);
    assertTrue(deleted.containsAll(Set.of("retired.one", "retired.two", "retired.three")),
        "both the single-name and IN (...) DELETE forms should be parsed, got " + deleted);
  }

  /** @return the seeded property names that some later migration has not deleted */
  private static Set<String> liveSeededPropertyNames() throws IOException {
    Set<String> inserted = new TreeSet<>();
    Set<String> deleted = new TreeSet<>();
    for (Path sqlFile : sqlFilesUnder(INSTALL_DIR, UPGRADE_DIR)) {
      String sql = stripComments(Files.readString(sqlFile));
      collectInsertedPropertyNames(sql, inserted);
      collectDeletedPropertyNames(sql, deleted);
    }
    inserted.removeAll(deleted);
    return inserted;
  }

  /**
   * True when some registered prefix would match the name the way findAllByPrefix does. Checked as
   * a prefix match rather than a root-prefix equality so a multi-segment registration such as
   * {@code site.header} is honored exactly as the SQL LIKE honors it.
   */
  private static boolean isCoveredByAPrefix(String propertyName, Set<String> registeredPrefixes) {
    for (String prefix : registeredPrefixes) {
      if (propertyName.startsWith(prefix + ".")) {
        return true;
      }
    }
    return false;
  }

  /** @return the segment before the first dot, matching SitePropertyRepository.saveAll's convention */
  private static String rootPrefixOf(String propertyName) {
    int dot = propertyName.indexOf('.');
    return dot > 0 ? propertyName.substring(0, dot) : propertyName;
  }

  /**
   * Every {@code <prefix>} declared by a page layout, with comma-separated lists split out -- one
   * widget may register several ({@code site.header,theme.utilitybar}).
   */
  private static Set<String> registeredEditorPrefixes() throws Exception {
    assertTrue(LAYOUT_DIR.isDirectory(),
        "layout directory not found (run from the project root): " + LAYOUT_DIR.getAbsolutePath());
    File[] layouts = LAYOUT_DIR.listFiles((dir, name) -> name.endsWith(".xml"));
    assertTrue(layouts != null && layouts.length > 0, "no page layout files found in " + LAYOUT_DIR.getAbsolutePath());

    Set<String> prefixes = new TreeSet<>();
    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    for (File layout : layouts) {
      Document document = builder.parse(layout);
      NodeList nodes = document.getElementsByTagName("prefix");
      for (int i = 0; i < nodes.getLength(); i++) {
        for (String prefix : nodes.item(i).getTextContent().split(",")) {
          if (!prefix.isBlank()) {
            prefixes.add(prefix.trim());
          }
        }
      }
    }
    return prefixes;
  }

  private static final Pattern INSERT_SITE_PROPERTY = Pattern.compile(
      "INSERT\\s+INTO\\s+site_properties\\s*\\(([^)]*)\\)\\s*VALUES", Pattern.CASE_INSENSITIVE);

  private static final Pattern DELETE_SITE_PROPERTY = Pattern.compile(
      "DELETE\\s+FROM\\s+site_properties\\s+WHERE\\s+property_name\\s*(?:=\\s*'([^']*)'|IN\\s*\\(([^)]*)\\))",
      Pattern.CASE_INSENSITIVE);

  /** Reads the property_name column out of every INSERT, by position in that statement's column list. */
  private static void collectInsertedPropertyNames(String sql, Set<String> into) {
    Matcher matcher = INSERT_SITE_PROPERTY.matcher(sql);
    while (matcher.find()) {
      int nameColumn = -1;
      String[] columns = matcher.group(1).split(",");
      for (int i = 0; i < columns.length; i++) {
        if ("property_name".equalsIgnoreCase(columns[i].trim())) {
          nameColumn = i;
        }
      }
      if (nameColumn < 0) {
        continue;
      }
      List<String> values = firstValuesTuple(sql, matcher.end());
      if (nameColumn < values.size()) {
        into.add(values.get(nameColumn));
      }
    }
  }

  /**
   * Splits the tuple that follows VALUES into its column values, tracking quotes so a label
   * containing a comma or an unbalanced parenthesis does not shift the columns. Every INSERT into
   * this table is single-row, so only the first tuple is read.
   */
  private static List<String> firstValuesTuple(String sql, int from) {
    List<String> values = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean started = false;
    boolean inQuote = false;
    int depth = 0;
    for (int i = from; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (inQuote) {
        if (c == '\'') {
          // '' is an escaped quote inside the literal, not the end of it
          if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
            current.append('\'');
            i++;
          } else {
            inQuote = false;
          }
        } else {
          current.append(c);
        }
        continue;
      }
      if (c == '\'') {
        inQuote = true;
        continue;
      }
      if (c == '(') {
        depth++;
        if (depth == 1) {
          started = true;
          continue;
        }
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          values.add(current.toString().trim());
          break;
        }
      } else if (c == ',' && depth == 1) {
        values.add(current.toString().trim());
        current.setLength(0);
        continue;
      } else if (!started && !Character.isWhitespace(c)) {
        // Anything other than whitespace before the opening paren means this was not a VALUES tuple
        break;
      }
      if (started) {
        current.append(c);
      }
    }
    return values;
  }

  /** Collects the names retired by a DELETE, in both the {@code = '...'} and {@code IN (...)} forms. */
  private static void collectDeletedPropertyNames(String sql, Set<String> into) {
    Matcher matcher = DELETE_SITE_PROPERTY.matcher(sql);
    while (matcher.find()) {
      if (matcher.group(1) != null) {
        into.add(matcher.group(1));
        continue;
      }
      Matcher names = Pattern.compile("'([^']*)'").matcher(matcher.group(2));
      while (names.find()) {
        into.add(names.group(1));
      }
    }
  }

  private static List<Path> sqlFilesUnder(Path... dirs) throws IOException {
    List<Path> files = new ArrayList<>();
    for (Path dir : dirs) {
      assertTrue(Files.isDirectory(dir), "expected a directory at " + dir.toAbsolutePath()
          + " -- tests must run with the repository root as the working directory");
      try (Stream<Path> paths = Files.walk(dir)) {
        paths.filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().endsWith(".sql"))
            .sorted()
            .forEach(files::add);
      }
    }
    return files;
  }

  private static String stripComments(String sql) {
    return sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("--[^\n]*", "");
  }
}
