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

package com.simisinc.platform.infrastructure.persistence.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies wiki page search against a real PostgreSQL instance. wiki_pages.tsv has been populated
 * and GIN-indexed by a trigger since the initial code drop; this proves the query added to
 * {@link WikiPageRepository} for issue #477 actually finds what it should -- and only that.
 *
 * <p>
 * Minimal focused schema: a real {@code title_stem} text search configuration (copied verbatim
 * from {@code NEW_10024__new_items.sql}, since {@code wiki_pages}' own tsv trigger depends on it
 * existing) plus the real {@code wikis}/{@code wiki_pages} DDL from
 * {@code NEW_10050__new_wikis.sql}, not the full install script.
 * </p>
 *
 * @author SimIS
 * @created 7/28/2026
 */
class WikiPageSearchTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static long wikiId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping wiki search integration test");

    postgres = new GenericContainer<>(DockerImageName.parse(resolveImage()))
        .withEnv("POSTGRES_USER", DB_USER)
        .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
        .withEnv("POSTGRES_DB", DB_NAME)
        .withExposedPorts(POSTGRES_PORT)
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
            .withStartupTimeout(Duration.ofSeconds(120)));
    try {
      postgres.start();
    } catch (Throwable t) {
      Assumptions.abort("Unable to start PostgreSQL test container: " + t.getMessage());
    }

    String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT)
        + "/" + DB_NAME;
    Properties properties = new Properties();
    properties.setProperty("jdbcUrl", jdbcUrl);
    properties.setProperty("username", DB_USER);
    properties.setProperty("password", DB_PASSWORD);
    DataSource.init(properties);

    createSchemaAndSeedPages();
  }

  @AfterAll
  static void stopDatabase() {
    try {
      DataSource.shutdown();
    } catch (Exception e) {
      // Never initialized when Docker is unavailable
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @Test
  void findsAPageByATermInItsTitle() {
    WikiPageSpecification spec = new WikiPageSpecification();
    spec.setSearchTerm("troubleshooting");

    List<WikiPage> results = WikiPageRepository.findAll(spec, null);

    Set<String> titles = results.stream().map(WikiPage::getTitle).collect(Collectors.toSet());
    assertTrue(titles.contains("Troubleshooting Guide"), "expected to find the troubleshooting page: " + titles);
  }

  @Test
  void findsAPageByATermOnlyInItsBody() {
    WikiPageSpecification spec = new WikiPageSpecification();
    spec.setSearchTerm("kerberos");

    List<WikiPage> results = WikiPageRepository.findAll(spec, null);

    Set<String> titles = results.stream().map(WikiPage::getTitle).collect(Collectors.toSet());
    assertTrue(titles.contains("Authentication Setup"), "expected a body-only match: " + titles);
  }

  @Test
  void doesNotReturnPagesThatDoNotMatch() {
    WikiPageSpecification spec = new WikiPageSpecification();
    spec.setSearchTerm("kerberos");

    List<WikiPage> results = WikiPageRepository.findAll(spec, null);

    Set<String> titles = results.stream().map(WikiPage::getTitle).collect(Collectors.toSet());
    assertEquals(1, titles.size(), "expected only the one matching page: " + titles);
  }

  @Test
  void aBlankSearchTermReturnsEveryPageUnfiltered() {
    WikiPageSpecification spec = new WikiPageSpecification();
    spec.setWikiId(wikiId);

    List<WikiPage> results = WikiPageRepository.findAll(spec, null);

    assertEquals(3, results.size());
  }

  private static void createSchemaAndSeedPages() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE users (user_id BIGSERIAL PRIMARY KEY)");
      statement.execute("INSERT INTO users (user_id) VALUES (1)");

      statement.execute("CREATE TEXT SEARCH DICTIONARY title_stem (TEMPLATE = snowball, Language = english)");
      statement.execute("CREATE TEXT SEARCH CONFIGURATION title_stem (copy = english)");
      statement.execute("ALTER TEXT SEARCH CONFIGURATION title_stem "
          + "ALTER MAPPING FOR asciihword, asciiword, hword, hword_asciipart, hword_part, word WITH title_stem");

      statement.execute("CREATE TABLE wikis (wiki_id BIGSERIAL PRIMARY KEY, wiki_unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "name VARCHAR(255) NOT NULL, created_by BIGINT NOT NULL, modified_by BIGINT NOT NULL)");
      statement.execute("CREATE TABLE wiki_pages (wiki_page_id BIGSERIAL PRIMARY KEY, wiki_id BIGINT NOT NULL, "
          + "page_unique_id VARCHAR(255) NOT NULL, title VARCHAR(255) NOT NULL, body TEXT, summary TEXT, "
          + "created_by BIGINT NOT NULL, created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT NOT NULL, modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, tsv TSVECTOR)");
      statement.execute("CREATE INDEX wiki_pages_tsv_idx ON wiki_pages USING gin(tsv)");
      statement.execute("CREATE OR REPLACE FUNCTION wiki_pages_tsv_trigger() RETURNS trigger AS $$ "
          + "begin new.tsv := setweight(to_tsvector('title_stem', new.title), 'A') "
          + "|| setweight(to_tsvector('title_stem', coalesce(new.body,'')), 'B'); return new; end "
          + "$$ LANGUAGE plpgsql");
      statement.execute("CREATE TRIGGER tsvectorupdate BEFORE INSERT OR UPDATE ON wiki_pages "
          + "FOR EACH ROW EXECUTE PROCEDURE wiki_pages_tsv_trigger()");

      statement.execute("INSERT INTO wikis (wiki_unique_id, name, created_by, modified_by) "
          + "VALUES ('docs', 'Documentation', 1, 1)");
      try (var rs = statement.executeQuery("SELECT wiki_id FROM wikis WHERE wiki_unique_id = 'docs'")) {
        rs.next();
        wikiId = rs.getLong(1);
      }

      statement.execute("INSERT INTO wiki_pages (wiki_id, page_unique_id, title, body, created_by, modified_by) VALUES "
          + "(" + wikiId + ", 'troubleshooting-guide', 'Troubleshooting Guide', 'Common problems and fixes.', 1, 1)");
      statement.execute("INSERT INTO wiki_pages (wiki_id, page_unique_id, title, body, created_by, modified_by) VALUES "
          + "(" + wikiId + ", 'auth-setup', 'Authentication Setup', "
          + "'This page explains how to configure Kerberos for single sign-on.', 1, 1)");
      statement.execute("INSERT INTO wiki_pages (wiki_id, page_unique_id, title, body, created_by, modified_by) VALUES "
          + "(" + wikiId + ", 'faq', 'Frequently Asked Questions', 'General questions about the platform.', 1, 1)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the wiki search test schema", se);
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException | LinkageError e) {
      return false;
    }
  }

  private static String resolveImage() {
    String image = System.getenv("TEST_POSTGRES_IMAGE");
    return (image != null && !image.isBlank()) ? image : DEFAULT_IMAGE;
  }
}
