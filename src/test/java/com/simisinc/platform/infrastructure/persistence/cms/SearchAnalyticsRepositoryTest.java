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
import java.sql.PreparedStatement;
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

import com.simisinc.platform.domain.model.cms.SearchAnalytics;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies the zero-result and week-over-week trending queries against a real PostgreSQL instance,
 * since interval windows and grouping cannot be exercised meaningfully with a mock. Skipped
 * automatically when Docker is not available -- see WebPageRepositoryTest for the origin of this
 * pattern. deleteOld() is not covered here: it reads the search.retentionDays site property
 * through LoadSitePropertyCommand/CacheManager, a subsystem this focused schema does not stand up,
 * matching WebPageHitRepository's own precedent of leaving its equivalent deleteOldWebHits()
 * untested at the integration level.
 *
 * @author SimIS
 * @created 7/29/2026
 */
class SearchAnalyticsRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping SearchAnalyticsRepository integration test");

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
  void resetTable() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE search_analytics RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset search_analytics table", se);
    }
  }

  @Test
  void saveInsertsARetrievableRow() {
    SearchAnalytics saved = addEvent("widgets", "pages", 3);

    assertTrue(saved.getId() > 0);
  }

  @Test
  void findZeroResultTermsReturnsOnlyQueriesThatFoundNothing() {
    addEvent("widgets", "pages", 3);
    addEvent("xylophone", "pages", 0);

    List<StatisticsData> results = SearchAnalyticsRepository.findZeroResultTerms(30, 10);

    assertEquals(1, results.size());
    assertEquals("xylophone", results.get(0).getLabel());
  }

  @Test
  void findZeroResultTermsOrdersByFrequencyDescendingAcrossSearchTypes() {
    // Same term, three different widgets -- zero-result terms are a content-gap signal per term,
    // not per widget, so these should roll up into one count of 3
    addEvent("foo", "pages", 0);
    addEvent("foo", "content", 0);
    addEvent("foo", "wiki", 0);
    addEvent("bar", "pages", 0);

    List<StatisticsData> results = SearchAnalyticsRepository.findZeroResultTerms(30, 10);

    assertEquals(2, results.size());
    assertEquals("foo", results.get(0).getLabel());
    assertEquals("3", results.get(0).getValue());
    assertEquals("bar", results.get(1).getLabel());
    assertEquals("1", results.get(1).getValue());
  }

  @Test
  void findZeroResultTermsExcludesEventsOutsideTheWindow() {
    SearchAnalytics old = addEvent("ancient", "pages", 0);
    backdate(old.getId(), 40);
    addEvent("recent", "pages", 0);

    List<StatisticsData> results = SearchAnalyticsRepository.findZeroResultTerms(30, 10);

    assertEquals(1, results.size());
    assertEquals("recent", results.get(0).getLabel());
  }

  @Test
  void findTrendingTermsRanksTheBiggestWeekOverWeekGrowthFirst() {
    // "growing": no prior-week baseline, 5 hits this week
    for (int i = 0; i < 5; i++) {
      addEvent("growing", "pages", 2);
    }
    // "steady": 2 hits last week, 2 hits this week -- no growth
    backdate(addEvent("steady", "pages", 2).getId(), 8);
    backdate(addEvent("steady", "pages", 2).getId(), 8);
    addEvent("steady", "pages", 2);
    addEvent("steady", "pages", 2);

    List<StatisticsData> results = SearchAnalyticsRepository.findTrendingTerms(10);

    assertEquals(2, results.size());
    assertEquals("growing", results.get(0).getLabel(),
        "a term with no prior-week baseline should rank by its full this-week count");
    assertEquals("5", results.get(0).getValue());
    assertEquals("steady", results.get(1).getLabel());
  }

  @Test
  void findTrendingTermsExcludesEventsOlderThanTwoWeeks() {
    backdate(addEvent("forgotten", "pages", 1).getId(), 20);

    List<StatisticsData> results = SearchAnalyticsRepository.findTrendingTerms(10);

    assertTrue(results.isEmpty());
  }

  @Test
  void countZeroResultSearchesCountsOnlyZeroResultEventsInWindow() {
    addEvent("widgets", "pages", 3);
    addEvent("xylophone", "pages", 0);
    addEvent("zither", "pages", 0);
    backdate(addEvent("ancient", "pages", 0).getId(), 40);

    long count = SearchAnalyticsRepository.countZeroResultSearches(30);

    assertEquals(2, count);
  }

  @Test
  void countZeroResultSearchesReturnsZeroWhenNoneInWindow() {
    addEvent("widgets", "pages", 3);

    long count = SearchAnalyticsRepository.countZeroResultSearches(30);

    assertEquals(0, count);
  }

  @Test
  void countSearchesCountsEveryEventInTheWindowRegardlessOfFacet() {
    addEvent("widgets", "pages", 3);
    addEvent("gadgets", "items", 5, "categoryId");
    backdate(addEvent("ancient", "pages", 1).getId(), 40);

    long count = SearchAnalyticsRepository.countSearches(30);

    assertEquals(2, count);
  }

  @Test
  void countSearchesWithFacetAppliedCountsOnlyEventsThatCarryAFacetKey() {
    addEvent("widgets", "pages", 3);
    addEvent("gadgets", "items", 5, "categoryId");
    addEvent("gizmos", "items", 2, "dateFacet");
    backdate(addEvent("ancient", "items", 1, "categoryId").getId(), 40);

    long count = SearchAnalyticsRepository.countSearchesWithFacetApplied(30);

    assertEquals(2, count);
  }

  @Test
  void findFacetUsageBreakdownGroupsByFacetKeyAndExcludesUnfaceted() {
    addEvent("widgets", "pages", 3);
    addEvent("a", "items", 5, "categoryId");
    addEvent("b", "items", 5, "categoryId");
    addEvent("c", "items", 5, "categoryId,dateFacet");

    List<StatisticsData> results = SearchAnalyticsRepository.findFacetUsageBreakdown(30, 10);

    assertEquals(2, results.size());
    assertEquals("categoryId", results.get(0).getLabel());
    assertEquals("2", results.get(0).getValue());
    assertEquals("categoryId,dateFacet", results.get(1).getLabel());
    assertEquals("1", results.get(1).getValue());
  }

  @Test
  void findFacetUsageBreakdownExcludesEventsOutsideTheWindow() {
    backdate(addEvent("old", "items", 1, "categoryId").getId(), 40);
    addEvent("recent", "items", 1, "dateFacet");

    List<StatisticsData> results = SearchAnalyticsRepository.findFacetUsageBreakdown(30, 10);

    assertEquals(1, results.size());
    assertEquals("dateFacet", results.get(0).getLabel());
  }

  @Test
  void resolveZeroResultAlertThresholdFallsBackToDefaultWhenBlankOrUnparseable() {
    assertEquals(20, SearchAnalyticsRepository.resolveZeroResultAlertThreshold(null));
    assertEquals(20, SearchAnalyticsRepository.resolveZeroResultAlertThreshold(""));
    assertEquals(20, SearchAnalyticsRepository.resolveZeroResultAlertThreshold("not-a-number"));
  }

  @Test
  void resolveZeroResultAlertThresholdUsesConfiguredValueAndFloorsAtZero() {
    assertEquals(50, SearchAnalyticsRepository.resolveZeroResultAlertThreshold("50"));
    assertEquals(0, SearchAnalyticsRepository.resolveZeroResultAlertThreshold("-5"));
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
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS search_analytics CASCADE");
      statement.execute("CREATE TABLE search_analytics ("
          + "search_analytics_id BIGSERIAL PRIMARY KEY, "
          + "query VARCHAR(255) NOT NULL, "
          + "search_type VARCHAR(50) NOT NULL, "
          + "result_count INTEGER NOT NULL DEFAULT 0, "
          + "page_path VARCHAR(255), "
          + "facet_key VARCHAR(100), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the search_analytics schema", se);
    }
  }

  private static SearchAnalytics addEvent(String query, String searchType, int resultCount) {
    return addEvent(query, searchType, resultCount, null);
  }

  private static SearchAnalytics addEvent(String query, String searchType, int resultCount, String facetKey) {
    SearchAnalytics searchAnalytics = new SearchAnalytics();
    searchAnalytics.setQuery(query);
    searchAnalytics.setSearchType(searchType);
    searchAnalytics.setResultCount(resultCount);
    searchAnalytics.setFacetKey(facetKey);
    return SearchAnalyticsRepository.save(searchAnalytics);
  }

  private static void backdate(long id, int daysAgo) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE search_analytics SET created = NOW() - INTERVAL '" + daysAgo + " days' WHERE search_analytics_id = ?")) {
      pst.setLong(1, id);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not backdate row " + id, se);
    }
  }
}
