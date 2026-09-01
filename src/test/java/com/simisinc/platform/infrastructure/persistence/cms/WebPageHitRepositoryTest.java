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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
 * <p>
 * Also covers {@link #findMaxHitsFromSingleIp} and {@link #resolveIpRequestRateAlertThreshold},
 * the request-rate-per-IP spike alert added for issue #569 slice 1.
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

  @Test
  void resolveIpRequestRateAlertThresholdFallsBackToDefaultWhenBlankOrUnparseable() {
    assertEquals(300, WebPageHitRepository.resolveIpRequestRateAlertThreshold(null));
    assertEquals(300, WebPageHitRepository.resolveIpRequestRateAlertThreshold(""));
    assertEquals(300, WebPageHitRepository.resolveIpRequestRateAlertThreshold("not-a-number"));
    assertEquals(300, WebPageHitRepository.resolveIpRequestRateAlertThreshold("30; DROP TABLE web_page_hits"));
  }

  @Test
  void resolveIpRequestRateAlertThresholdUsesConfiguredValueAndFloorsAtZero() {
    assertEquals(500, WebPageHitRepository.resolveIpRequestRateAlertThreshold("500"));
    assertEquals(0, WebPageHitRepository.resolveIpRequestRateAlertThreshold("-5"));
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

  // --- findTopPaths() integration coverage ---

  @Test
  void findTopPathsCountsRealSessionHitsGroupedByPathAndOrdersByCountDescending() {
    seedSession("real-session", false);
    seedSession("bot-session", true);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedHit("/contact-us", "real-session", now);
    seedHit("/contact-us", "real-session", now);
    seedHit("/about-us", "real-session", now);
    seedHit("/contact-us", "bot-session", now);

    List<StatisticsData> results = WebPageHitRepository.findTopPaths(30, 'd', 10);

    assertEquals(2, results.size());
    assertEquals("/contact-us", results.get(0).getLabel(), "the higher (real-session-only) count sorts first");
    assertEquals("2", results.get(0).getValue(), "the bot-session hit must not count");
    assertEquals("/about-us", results.get(1).getLabel());
  }

  @Test
  void findTopPathsExcludesWebContentAssetPaths() {
    // /web-content/ is a distinct static-asset path (favicons, logos) from /assets/, hit on every
    // page load -- without its own exclusion it shows up in this traffic ranking as if it were a
    // real page view.
    seedSession("real-session", false);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedHit("/web-content/images/favicon.png", "real-session", now);
    seedHit("/contact-us", "real-session", now);

    List<StatisticsData> results = WebPageHitRepository.findTopPaths(30, 'd', 10);

    assertEquals(1, results.size());
    assertEquals("/contact-us", results.get(0).getLabel());
  }

  @Test
  void findTopPathsExcludesAdminLoginAndAssetPaths() {
    seedSession("real-session", false);
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedHit("/admin/web-pages", "real-session", now);
    seedHit("/assets/captcha", "real-session", now);
    seedHit("/json/some-endpoint", "real-session", now);
    seedHit("/login", "real-session", now);
    seedHit("/content-editor", "real-session", now);
    seedHit("/news/*", "real-session", now);
    seedHit("/contact-us", "real-session", now);

    List<StatisticsData> results = WebPageHitRepository.findTopPaths(30, 'd', 10);

    assertEquals(1, results.size());
    assertEquals("/contact-us", results.get(0).getLabel());
  }

  @Test
  void findTopPathsReturnsEmptyListWhenThereIsNoData() {
    List<StatisticsData> results = WebPageHitRepository.findTopPaths(30, 'd', 10);

    assertNotNull(results);
    assertEquals(0, results.size());
  }

  // --- countViewsByWebPageId() integration coverage (issue #497) ---

  @Test
  void countViewsByWebPageIdCountsRealSessionHitsPerRequestedPage() {
    seedSession("real-session", false);
    seedSession("bot-session", true);
    long pageA = seedPage("/a");
    long pageB = seedPage("/b");
    seedHit(pageA, "real-session");
    seedHit(pageA, "real-session");
    seedHit(pageA, "bot-session");
    seedHit(pageB, "real-session");

    Map<Long, Long> counts = WebPageHitRepository.countViewsByWebPageId(List.of(pageA, pageB), 30);

    assertEquals(2L, counts.get(pageA), "only the two real-session hits should count, not the bot's");
    assertEquals(1L, counts.get(pageB));
  }

  @Test
  void countViewsByWebPageIdOmitsPagesWithNoHitsRatherThanReturningZero() {
    long pageWithNoHits = seedPage("/never-viewed");

    Map<Long, Long> counts = WebPageHitRepository.countViewsByWebPageId(List.of(pageWithNoHits), 30);

    assertTrue(counts.isEmpty(), "a page with no hits in range must be absent from the map, not present with 0");
  }

  @Test
  void countViewsByWebPageIdOnlyCountsRequestedPagesNotEveryPageInTheDatabase() {
    seedSession("real-session", false);
    long requestedPage = seedPage("/requested");
    long otherPage = seedPage("/not-requested");
    seedHit(requestedPage, "real-session");
    seedHit(otherPage, "real-session");
    seedHit(otherPage, "real-session");

    Map<Long, Long> counts = WebPageHitRepository.countViewsByWebPageId(List.of(requestedPage), 30);

    assertEquals(1, counts.size());
    assertEquals(1L, counts.get(requestedPage));
  }

  @Test
  void countViewsByWebPageIdReturnsEmptyMapForNullOrEmptyInputWithoutQuerying() {
    assertTrue(WebPageHitRepository.countViewsByWebPageId(null, 30).isEmpty());
    assertTrue(WebPageHitRepository.countViewsByWebPageId(List.of(), 30).isEmpty());
  }

  @Test
  void countViewsByWebPageIdExcludesHitsOutsideTheWindow() {
    seedSession("real-session", false);
    long page = seedPage("/boundary");
    Timestamp thirtyOneDaysAgo = Timestamp.from(java.time.Instant.now().minus(Duration.ofDays(31)));
    seedHit(page, "real-session", thirtyOneDaysAgo);
    seedHit(page, "real-session", new Timestamp(System.currentTimeMillis()));

    Map<Long, Long> counts = WebPageHitRepository.countViewsByWebPageId(List.of(page), 30);

    assertEquals(1L, counts.get(page), "the hit older than the 30-day window must not count");
  }

  private static void seedHit(long webPageId, String sessionId, Timestamp hitDate) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO web_page_hits (web_page_id, session_id, hit_date) VALUES ("
          + webPageId + ", '" + sessionId + "', '" + hitDate + "')");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed web page hit", se);
    }
  }

  // --- findAvgPagesPerSession() integration coverage (issue #568) ---

  @Test
  void findAvgPagesPerSessionComputesAverageAcrossRealSessionsOnly() {
    seedSession("session-a", false);
    seedSession("session-b", false);
    seedSession("bot-session", true);
    long pageId = seedPage("/contact-us");
    // session-a: 3 hits, session-b: 1 hit -- real average is (3 + 1) / 2 = 2.0
    seedHit(pageId, "session-a");
    seedHit(pageId, "session-a");
    seedHit(pageId, "session-a");
    seedHit(pageId, "session-b");
    // The bot session's 5 hits must not be counted, in either the numerator or the denominator
    for (int i = 0; i < 5; i++) {
      seedHit(pageId, "bot-session");
    }

    double avg = WebPageHitRepository.findAvgPagesPerSession(30);

    assertEquals(2.0, avg, 0.0001);
  }

  @Test
  void findAvgPagesPerSessionReturnsZeroWhenNoSessionsAreInRange() {
    double avg = WebPageHitRepository.findAvgPagesPerSession(30);

    // Must not throw a divide-by-zero error, and must not misreport as some positive average
    assertEquals(0.0, avg, 0.0001);
  }

  // --- findAvgTimeOnPageByPath() integration coverage (issue #568) ---

  @Test
  void findAvgTimeOnPageByPathComputesTheGapToTheNextHitInEachSession() {
    seedSession("s1", false);
    seedSession("s2", false);
    seedSession("bot-session", true);

    Timestamp t0 = Timestamp.valueOf(LocalDateTime.now().minusHours(1));
    // s1: /a -> /b (10s later) -> /x (25s after that, last hit of s1 -- no next, contributes nothing)
    seedHit("/a", "s1", t0);
    seedHit("/b", "s1", plusSeconds(t0, 10));
    seedHit("/x", "s1", plusSeconds(t0, 35));
    // s2: /a -> /c (30s later, last hit of s2 -- no next, contributes nothing)
    seedHit("/a", "s2", t0);
    seedHit("/c", "s2", plusSeconds(t0, 30));
    // bot session's hits must not contribute a sample at all
    seedHit("/a", "bot-session", t0);
    seedHit("/a", "bot-session", plusSeconds(t0, 1));

    List<StatisticsData> results = WebPageHitRepository.findAvgTimeOnPageByPath(30, 10);

    assertNotNull(results);
    assertEquals(2, results.size(), "only /a and /b have a next hit to diff against: " + results);
    // /b: single sample of 25s -- ranks first (ORDER BY avg desc)
    assertEquals("/b", results.get(0).getLabel());
    assertEquals("25.0s", results.get(0).getValue());
    // /a: samples of 10s (s1) and 30s (s2) -- averages to 20s
    assertEquals("/a", results.get(1).getLabel());
    assertEquals("20.0s", results.get(1).getValue());
  }

  @Test
  void findAvgTimeOnPageByPathReturnsEmptyListWhenThereIsNoData() {
    List<StatisticsData> results = WebPageHitRepository.findAvgTimeOnPageByPath(30, 10);

    assertNotNull(results);
    assertEquals(0, results.size());
  }

  private static Timestamp plusSeconds(Timestamp base, int seconds) {
    return new Timestamp(base.getTime() + (seconds * 1000L));
  }

  // --- findHighTrafficLowEngagementPages() / findLowTrafficHighEngagementPages() coverage (issue #568) ---

  @Test
  void trafficEngagementRankingsOrderPagesOppositelyAndEnforceTheMinimumHitFloor() {
    Timestamp t0 = Timestamp.valueOf(LocalDateTime.now().minusHours(1));
    // /popular: 8 sessions, each bounces to /other after 2s -- high traffic, low engagement
    for (int i = 0; i < 8; i++) {
      String session = "popular-" + i;
      seedSession(session, false);
      seedHit("/popular", session, t0);
      seedHit("/other", session, plusSeconds(t0, 2));
    }
    // /deep-dive: 5 sessions (right at the floor), each stays 120s -- low traffic, high engagement
    for (int i = 0; i < 5; i++) {
      String session = "deep-dive-" + i;
      seedSession(session, false);
      seedHit("/deep-dive", session, t0);
      seedHit("/other2", session, plusSeconds(t0, 120));
    }
    // /rare: only 2 sessions -- below the floor, must be excluded from both rankings entirely
    for (int i = 0; i < 2; i++) {
      String session = "rare-" + i;
      seedSession(session, false);
      seedHit("/rare", session, t0);
      seedHit("/other3", session, plusSeconds(t0, 50));
    }

    List<StatisticsData> highTrafficLowEngagement = WebPageHitRepository.findHighTrafficLowEngagementPages(30, 10);
    assertNotNull(highTrafficLowEngagement);
    assertEquals(2, highTrafficLowEngagement.size(), "/rare must be excluded (below the min-hit floor): " + highTrafficLowEngagement);
    assertEquals("/popular", highTrafficLowEngagement.get(0).getLabel(), "highest hit count, lowest engagement ranks first");
    assertEquals("8 hits, 2.0s avg", highTrafficLowEngagement.get(0).getValue());
    assertEquals("/deep-dive", highTrafficLowEngagement.get(1).getLabel());

    List<StatisticsData> lowTrafficHighEngagement = WebPageHitRepository.findLowTrafficHighEngagementPages(30, 10);
    assertNotNull(lowTrafficHighEngagement);
    assertEquals(2, lowTrafficHighEngagement.size(), "/rare must be excluded (below the min-hit floor): " + lowTrafficHighEngagement);
    assertEquals("/deep-dive", lowTrafficHighEngagement.get(0).getLabel(), "lowest hit count, highest engagement ranks first");
    assertEquals("5 hits, 120.0s avg", lowTrafficHighEngagement.get(0).getValue());
    assertEquals("/popular", lowTrafficHighEngagement.get(1).getLabel());
  }

  @Test
  void trafficEngagementRankingsReturnEmptyListsWhenThereIsNoData() {
    assertEquals(0, WebPageHitRepository.findHighTrafficLowEngagementPages(30, 10).size());
    assertEquals(0, WebPageHitRepository.findLowTrafficHighEngagementPages(30, 10).size());
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

  // --- findMaxHitsFromSingleIp() integration coverage (issue #569 slice 1) ---

  @Test
  void findMaxHitsFromSingleIpReturnsTheHighestCountAcrossIps() {
    seedSession("session-a", false);
    seedSession("session-b", false);
    // 1.1.1.1: 5 hits, 2.2.2.2: 2 hits -- the max across IPs is 5, not the total hit count
    for (int i = 0; i < 5; i++) {
      seedHitWithIp("1.1.1.1", "session-a");
    }
    seedHitWithIp("2.2.2.2", "session-b");
    seedHitWithIp("2.2.2.2", "session-b");

    long max = WebPageHitRepository.findMaxHitsFromSingleIp(1);

    assertEquals(5, max);
  }

  @Test
  void findMaxHitsFromSingleIpExcludesBotSessionHits() {
    seedSession("bot-session", true);
    seedSession("real-session", false);
    // The bot IP has far more hits, but must not count -- the max should come from the real session
    for (int i = 0; i < 50; i++) {
      seedHitWithIp("9.9.9.9", "bot-session");
    }
    seedHitWithIp("8.8.8.8", "real-session");
    seedHitWithIp("8.8.8.8", "real-session");

    long max = WebPageHitRepository.findMaxHitsFromSingleIp(1);

    assertEquals(2, max, "the bot session's 50 hits from 9.9.9.9 must not count toward the spike");
  }

  @Test
  void findMaxHitsFromSingleIpExcludesHitsOutsideTheWindow() {
    seedSession("real-session", false);
    Timestamp twoHoursAgo = Timestamp.from(java.time.Instant.now().minus(Duration.ofHours(2)));
    // 10 old hits from the same IP, outside the 1-hour window, plus 1 recent hit from a different IP
    for (int i = 0; i < 10; i++) {
      seedHitWithIp("3.3.3.3", "real-session", twoHoursAgo);
    }
    seedHitWithIp("4.4.4.4", "real-session");

    long max = WebPageHitRepository.findMaxHitsFromSingleIp(1);

    assertEquals(1, max, "hits older than the window must not contribute to the spike count");
  }

  @Test
  void findMaxHitsFromSingleIpExcludesHitsWithNoIpAddress() {
    seedSession("real-session", false);
    // A hit with no ip_address can't be attributed to a single source and must not count
    seedHit("/about", "real-session", new Timestamp(System.currentTimeMillis()));

    long max = WebPageHitRepository.findMaxHitsFromSingleIp(1);

    assertEquals(0, max);
  }

  @Test
  void findMaxHitsFromSingleIpReturnsZeroWhenThereIsNoData() {
    long max = WebPageHitRepository.findMaxHitsFromSingleIp(1);

    assertEquals(0, max);
  }

  private static void seedHitWithIp(String ipAddress, String sessionId) {
    seedHitWithIp(ipAddress, sessionId, new Timestamp(System.currentTimeMillis()));
  }

  private static void seedHitWithIp(String ipAddress, String sessionId, Timestamp hitDate) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO web_page_hits (ip_address, session_id, hit_date) VALUES ('"
          + ipAddress + "', '" + sessionId + "', '" + hitDate + "')");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed web page hit", se);
    }
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

  // --- non-page paths must not appear in reports that RANK pages (issue #1725 follow-up) ---
  //
  // findTopPaths already excluded these and had tests for it; the engagement rankings and the
  // avg-time report did not, so /web-content/images/favicon.png was the top row of "High Traffic,
  // Low Engagement" with a 77-second average -- a meaningless number for an icon the browser
  // fetches by itself, on every page load.

  /** Interleaves hits so LEAD() produces a next-hit delta for both paths, and clears the
   *  MIN_HITS_FOR_ENGAGEMENT_RANKING floor of 5. */
  private static void seedInterleavedHits(String assetPath, String realPath, String sessionId) {
    long start = System.currentTimeMillis() - (60L * 60 * 1000);
    for (int i = 0; i < 8; i++) {
      seedHit(assetPath, sessionId, new Timestamp(start + (i * 120_000L)));
      seedHit(realPath, sessionId, new Timestamp(start + (i * 120_000L) + 60_000L));
    }
  }

  @Test
  void highTrafficLowEngagementExcludesAssetPaths() {
    seedSession("real-session", false);
    seedInterleavedHits("/web-content/images/favicon.png", "/contact-us", "real-session");

    List<StatisticsData> results = WebPageHitRepository.findHighTrafficLowEngagementPages(30, 10);

    assertTrue(results.stream().noneMatch(r -> r.getLabel().startsWith("/web-content/")),
        "a favicon is not a page and must not be ranked as one");
    assertTrue(results.stream().anyMatch(r -> "/contact-us".equals(r.getLabel())),
        "real pages must still be ranked");
  }

  @Test
  void lowTrafficHighEngagementExcludesAssetPaths() {
    // Shares findTrafficEngagementRanking with the report above, so it would have had the same
    // defect -- and the inverse ordering makes an asset even likelier to surface.
    seedSession("real-session", false);
    seedInterleavedHits("/web-content/images/apple-touch-icon.png", "/about-us", "real-session");

    List<StatisticsData> results = WebPageHitRepository.findLowTrafficHighEngagementPages(30, 10);

    assertTrue(results.stream().noneMatch(r -> r.getLabel().startsWith("/web-content/")));
    assertTrue(results.stream().anyMatch(r -> "/about-us".equals(r.getLabel())));
  }

  @Test
  void avgTimeOnPageExcludesAssetAndAdminPaths() {
    // This one ranks BY average time, so an asset with a long gap after it sorts straight to the
    // top -- the worst place for a value that means nothing.
    seedSession("real-session", false);
    seedInterleavedHits("/web-content/images/favicon.png", "/news", "real-session");
    seedHit("/admin/users", "real-session", new Timestamp(System.currentTimeMillis() - 30_000L));
    seedHit("/news", "real-session", new Timestamp(System.currentTimeMillis() - 20_000L));

    List<StatisticsData> results = WebPageHitRepository.findAvgTimeOnPageByPath(30, 10);

    assertTrue(results.stream().noneMatch(r -> r.getLabel().startsWith("/web-content/")));
    assertTrue(results.stream().noneMatch(r -> r.getLabel().startsWith("/admin")));
    assertTrue(results.stream().anyMatch(r -> "/news".equals(r.getLabel())));
  }

  private static void seedHit(String pagePath, String sessionId, Timestamp hitDate) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO web_page_hits (page_path, session_id, hit_date) VALUES ("
          + "'" + pagePath + "', '" + sessionId + "', '" + hitDate + "')");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed web page hit", se);
    }
  }

  private static void seedPageHitByPath(String pagePath, String sessionId) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO web_page_hits (page_path, session_id, hit_date) VALUES ('"
          + pagePath + "', '" + sessionId + "', CURRENT_TIMESTAMP)");
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
      statement.execute("DROP TABLE IF EXISTS web_page_hit_snapshots CASCADE");
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
          + "page_path VARCHAR(255), "
          + "ip_address VARCHAR(200), "
          + "session_id VARCHAR(255), "
          + "hit_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
      statement.execute("CREATE TABLE web_page_hit_snapshots ("
          + "snapshot_id BIGSERIAL PRIMARY KEY, "
          + "snapshot_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "date_value VARCHAR(10) UNIQUE NOT NULL, "
          + "unique_sessions BIGINT DEFAULT 0, "
          + "web_page_hits BIGINT DEFAULT 0)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the schema", se);
    }
  }
}
