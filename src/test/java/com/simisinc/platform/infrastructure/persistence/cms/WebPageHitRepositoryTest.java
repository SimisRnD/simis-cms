/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Tests parsing of the configurable analytics retention window, plus (for {@link #findTopWebPages}
 * specifically) a real-Postgres integration test.
 *
 * <p>
 * {@code findTopWebPages()} joins {@code web_page_hits} with the alias {@code wph} but its
 * bot-exclusion subquery referred to the table by its pre-alias name ({@code web_page_hits.session_id}),
 * which Postgres rejects once an alias is assigned ("invalid reference to FROM-clause entry for table
 * \"web_page_hits\""). Every call threw, was caught, and returned {@code null} -- silently, since the
 * caller (SiteStatsWidget's "web-pages"/"Top Modules" report) treats a null result the same as a
 * legitimately empty one. See issue #804.
 * </p>
 *
 * @author elizabeth houser
 */
class WebPageHitRepositoryTest {

  @Test
  void resolveRetentionDaysParsesAndBounds() {
    assertEquals(30, WebPageHitRepository.resolveRetentionDays("30"));
    assertEquals(365, WebPageHitRepository.resolveRetentionDays("365"));
    assertEquals(90, WebPageHitRepository.resolveRetentionDays("  90  "));
    // Defaults when blank or non-numeric (the value comes from a site property, so it must not inject SQL)
    assertEquals(365, WebPageHitRepository.resolveRetentionDays(""));
    assertEquals(365, WebPageHitRepository.resolveRetentionDays(null));
    assertEquals(365, WebPageHitRepository.resolveRetentionDays("30; DROP TABLE web_page_hits"));
    assertEquals(365, WebPageHitRepository.resolveRetentionDays("abc"));
    // Bounded to a sane range
    assertEquals(1, WebPageHitRepository.resolveRetentionDays("0"));
    assertEquals(1, WebPageHitRepository.resolveRetentionDays("-5"));
    assertEquals(3650, WebPageHitRepository.resolveRetentionDays("999999"));
  }

  // --- findTopWebPages() integration coverage (issue #804) ---

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping WebPageHitRepository integration test");

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

    createSchema();
  }

  @AfterAll
  static void stopDatabase() {
    try {
      DataSource.shutdown();
    } catch (Exception e) {
      // The DataSource is never initialized when Docker is unavailable
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @BeforeEach
  void resetTables() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE web_page_hits, web_pages, sessions RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset tables", se);
    }
  }

  @Test
  void findTopWebPagesRunsWithoutThrowingAndCountsRealSessionHits() {
    seedSession("real-session", false);
    seedSession("bot-session", true);
    long pageId = seedPage("/contact-us");
    seedHit(pageId, "real-session");
    seedHit(pageId, "real-session");
    seedHit(pageId, "bot-session");

    List<StatisticsData> results = WebPageHitRepository.findTopWebPages(30, 10);

    // Before the fix (issue #804), the query threw on every call and this was always null.
    assertNotNull(results, "the query must run instead of throwing on the aliased table reference");
    assertEquals(1, results.size());
    assertEquals("/contact-us", results.get(0).getLabel());
    assertEquals("2", results.get(0).getValue(), "only the two real-session hits should count, not the bot's");
  }

  @Test
  void findTopWebPagesReturnsEmptyListWhenThereIsNoData() {
    List<StatisticsData> results = WebPageHitRepository.findTopWebPages(30, 10);

    assertNotNull(results);
    assertEquals(0, results.size());
  }

  // --- findTrafficBySolutionType() / findEngagementBySolutionType() integration coverage (issue #570) ---

  @Test
  void findTrafficBySolutionTypeGroupsRealSessionHitsByTag() {
    seedSession("real-session-1", false);
    seedSession("real-session-2", false);
    seedSession("bot-session", true);
    long govPage = seedPage("/solutions/cmmc", "government-solution");
    long careersPage = seedPage("/careers/engineering", "careers");
    long untaggedPage = seedPage("/about", null);
    seedHit(govPage, "real-session-1");
    seedHit(govPage, "real-session-2");
    seedHit(govPage, "bot-session");
    seedHit(careersPage, "real-session-1");
    seedHit(untaggedPage, "real-session-1");

    List<StatisticsData> results = WebPageHitRepository.findTrafficBySolutionType(30);

    assertNotNull(results);
    assertEquals(2, results.size(), "the untagged page must not appear as its own group");
    StatisticsData government = results.stream().filter(d -> "government-solution".equals(d.getLabel())).findFirst().orElseThrow();
    assertEquals("2", government.getValue(), "only the two real-session hits should count, not the bot's");
    StatisticsData careers = results.stream().filter(d -> "careers".equals(d.getLabel())).findFirst().orElseThrow();
    assertEquals("1", careers.getValue());
  }

  @Test
  void findTrafficBySolutionTypeReturnsEmptyListWhenNoPagesAreTagged() {
    seedSession("real-session", false);
    long page = seedPage("/about", null);
    seedHit(page, "real-session");

    List<StatisticsData> results = WebPageHitRepository.findTrafficBySolutionType(30);

    assertNotNull(results);
    assertEquals(0, results.size());
  }

  @Test
  void findEngagementBySolutionTypeComputesAverageViewsPerSession() {
    seedSession("session-a", false);
    seedSession("session-b", false);
    long govPage = seedPage("/solutions/cmmc", "government-solution");
    long govPage2 = seedPage("/solutions/cui", "government-solution");
    // session-a views 2 government-solution pages, session-b views 1 -- average is 1.5
    seedHit(govPage, "session-a");
    seedHit(govPage2, "session-a");
    seedHit(govPage, "session-b");

    List<StatisticsData> results = WebPageHitRepository.findEngagementBySolutionType(30);

    assertEquals(1, results.size());
    assertEquals("government-solution", results.get(0).getLabel());
    assertEquals("1.50", results.get(0).getValue());
  }

  @Test
  void findEngagementBySolutionTypeExcludesBotSessions() {
    seedSession("real-session", false);
    seedSession("bot-session", true);
    long govPage = seedPage("/solutions/cmmc", "government-solution");
    seedHit(govPage, "real-session");
    seedHit(govPage, "bot-session");
    seedHit(govPage, "bot-session");

    List<StatisticsData> results = WebPageHitRepository.findEngagementBySolutionType(30);

    assertEquals(1, results.size());
    assertEquals("1.00", results.get(0).getValue(), "the bot session's extra views must not count");
  }

  private static void seedSession(String sessionId, boolean isBot) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO sessions (session_id, is_bot) VALUES ('" + sessionId + "', " + isBot + ")");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed session", se);
    }
  }

  private static long seedPage(String link) {
    return seedPage(link, null);
  }

  private static long seedPage(String link, String solutionType) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      String solutionTypeValue = solutionType == null ? "NULL" : "'" + solutionType + "'";
      statement.execute("INSERT INTO web_pages (link, solution_type) VALUES ('" + link + "', " + solutionTypeValue + ")");
      var rs = statement.executeQuery("SELECT web_page_id FROM web_pages WHERE link = '" + link + "'");
      rs.next();
      return rs.getLong("web_page_id");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed web page", se);
    }
  }

  private static void seedHit(long webPageId, String sessionId) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO web_page_hits (web_page_id, session_id, hit_date) VALUES ("
          + webPageId + ", '" + sessionId + "', CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed web page hit", se);
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

  private static void createSchema() {
    // A focused subset of the real schema (issue #804's 3 tables) - enough for findTopWebPages'
    // join + bot-exclusion subquery. FK constraints to unrelated tables (users, etc.) are omitted.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS web_page_hits CASCADE");
      statement.execute("DROP TABLE IF EXISTS web_pages CASCADE");
      statement.execute("DROP TABLE IF EXISTS sessions CASCADE");
      statement.execute("CREATE TABLE web_pages ("
          + "web_page_id BIGSERIAL PRIMARY KEY, "
          + "link VARCHAR(255) UNIQUE NOT NULL, "
          + "solution_type VARCHAR(255))");
      statement.execute("CREATE TABLE sessions ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "session_id VARCHAR(255), "
          + "is_bot BOOLEAN DEFAULT false)");
      statement.execute("CREATE TABLE web_page_hits ("
          + "hit_id BIGSERIAL PRIMARY KEY, "
          + "web_page_id BIGINT, "
          + "session_id VARCHAR(255), "
          + "hit_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the schema", se);
    }
  }
}
