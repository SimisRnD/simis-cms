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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Exercises the check against a database deliberately put into the broken state, rather than only
 * against a correct one -- a check that has never been shown to fail is not evidence of anything.
 *
 * <p>The three states below are the ones observed in issue #1745: the column, its trigger and its
 * index were all absent on the pilot, and page-title search returned nothing site-wide with no error
 * anywhere.
 *
 * @author SimIS Inc.
 */
class SchemaIntegrityCommandTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_schema_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static String jdbcUrl;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping schema integrity test");
    postgres = new GenericContainer<>(DockerImageName.parse(resolveImage()))
        .withEnv("POSTGRES_USER", DB_USER)
        .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
        .withEnv("POSTGRES_DB", DB_NAME)
        .withExposedPorts(POSTGRES_PORT)
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
            .withStartupTimeout(Duration.ofSeconds(120)));
    postgres.start();
    jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT)
        + "/" + DB_NAME;
    // Created once, not per fixture: PostgreSQL has no IF NOT EXISTS for a text search
    // configuration, so a per-test CREATE fails on the second test in the class.
    try (Connection connection = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TEXT SEARCH CONFIGURATION title_stem (copy = english)");
    } catch (Exception e) {
      throw new IllegalStateException("could not prepare the text search configuration", e);
    }
  }

  @AfterAll
  static void stopDatabase() {
    if (postgres != null) {
      postgres.stop();
    }
  }

  private Connection connection() throws Exception {
    return DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
  }

  /** A healthy web_pages: tsvector column, maintenance trigger and GIN index all present. */
  private void createHealthyWebPages(Statement statement) throws Exception {
    statement.execute("DROP TABLE IF EXISTS web_pages CASCADE");
    statement.execute("CREATE TABLE web_pages (web_page_id BIGSERIAL PRIMARY KEY, page_title VARCHAR(255), tsv TSVECTOR)");
    statement.execute("CREATE OR REPLACE FUNCTION web_pages_tsv_trigger() RETURNS trigger AS $$ "
        + "begin new.tsv := to_tsvector('title_stem', COALESCE(new.page_title, '')); return new; end $$ LANGUAGE plpgsql");
    statement.execute("CREATE TRIGGER web_pages_tsv_trigger BEFORE INSERT OR UPDATE ON web_pages "
        + "FOR EACH ROW EXECUTE PROCEDURE web_pages_tsv_trigger()");
    statement.execute("CREATE INDEX web_pages_tsv_idx ON web_pages USING gin(tsv)");
  }

  private static List<String> findingsFor(List<String> findings, String table) {
    return findings.stream().filter(f -> f.startsWith(table + " ")).toList();
  }

  @Test
  void aHealthyTableProducesNoFinding() throws Exception {
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      createHealthyWebPages(statement);

      List<String> findings = SchemaIntegrityCommand.findMissingObjects(connection);

      assertTrue(findingsFor(findings, "web_pages").isEmpty(),
          "a complete table must not be reported: " + findings);
    }
  }

  @Test
  void aMissingTsvectorColumnIsReported() throws Exception {
    // the state issue #1745 was actually in -- neither migration track created the column
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      createHealthyWebPages(statement);
      statement.execute("ALTER TABLE web_pages DROP COLUMN tsv");

      List<String> found = findingsFor(SchemaIntegrityCommand.findMissingObjects(connection), "web_pages");

      assertEquals(1, found.size(), "expected exactly one finding, got: " + found);
      assertTrue(found.get(0).contains("no tsvector column"), found.get(0));
      assertTrue(found.get(0).contains("WebPageRepository.search"),
          "the finding must name what breaks, not just the object: " + found.get(0));
    }
  }

  @Test
  void aMissingTriggerIsReportedSeparatelyFromAMissingColumn() throws Exception {
    // the more insidious half: the column exists, so nothing errors -- it is simply never populated,
    // which is indistinguishable from "nothing matches"
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      createHealthyWebPages(statement);
      statement.execute("DROP TRIGGER web_pages_tsv_trigger ON web_pages");

      List<String> found = findingsFor(SchemaIntegrityCommand.findMissingObjects(connection), "web_pages");

      assertEquals(1, found.size(), "expected exactly one finding, got: " + found);
      assertTrue(found.get(0).contains("no maintenance trigger"), found.get(0));
      assertTrue(found.get(0).contains("silently returns nothing"), found.get(0));
    }
  }

  @Test
  void aMissingGinIndexIsReported() throws Exception {
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      createHealthyWebPages(statement);
      statement.execute("DROP INDEX web_pages_tsv_idx");

      List<String> found = findingsFor(SchemaIntegrityCommand.findMissingObjects(connection), "web_pages");

      assertEquals(1, found.size(), "expected exactly one finding, got: " + found);
      assertTrue(found.get(0).contains("no GIN index"), found.get(0));
    }
  }

  @Test
  void aMissingColumnDoesNotAlsoReportTheTriggerAndIndexHangingOffIt() throws Exception {
    // one cause, one finding -- three lines about the same missing column is noise that buries the
    // other tables in the report
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      createHealthyWebPages(statement);
      statement.execute("DROP TABLE web_pages CASCADE");
      statement.execute("CREATE TABLE web_pages (web_page_id BIGSERIAL PRIMARY KEY, page_title VARCHAR(255))");

      List<String> found = findingsFor(SchemaIntegrityCommand.findMissingObjects(connection), "web_pages");

      assertEquals(1, found.size(), "a missing column must produce one finding, not three: " + found);
      assertTrue(found.get(0).contains("no tsvector column"), found.get(0));
    }
  }

  @Test
  void aTableThatDoesNotExistAtAllIsNotReported() throws Exception {
    // an absent table is a louder, different problem; this check is not the place to raise it, and
    // reporting every unbuilt table would make the output useless on a partially built schema
    try (Connection connection = connection(); Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS web_pages CASCADE");

      List<String> found = findingsFor(SchemaIntegrityCommand.findMissingObjects(connection), "web_pages");

      assertTrue(found.isEmpty(), "an absent table must not be reported here: " + found);
    }
  }

  @Test
  void everyExpectationNamesTheCodeThatDependsOnIt() {
    // the report is only actionable if it says what breaks; an entry added without that is a line
    // someone reads and cannot act on
    assertFalse(SchemaIntegrityCommand.FULL_TEXT_EXPECTATIONS.isEmpty());
    for (SchemaIntegrityCommand.FullTextExpectation expectation : SchemaIntegrityCommand.FULL_TEXT_EXPECTATIONS) {
      assertTrue(expectation.usedBy != null && !expectation.usedBy.isBlank(),
          expectation.tableName + " has no usedBy");
      assertTrue(expectation.searchConfiguration != null && !expectation.searchConfiguration.isBlank(),
          expectation.tableName + " has no search configuration");
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  private static String resolveImage() {
    String image = System.getenv("TEST_POSTGRES_IMAGE");
    return (image != null && !image.isBlank()) ? image : DEFAULT_IMAGE;
  }
}
