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

package com.simisinc.platform.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
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

import com.simisinc.platform.domain.model.dashboard.BotIdentityStats;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Tests parsing of the geo-anomaly alert's configurable windows, plus (for
 * {@link #findTopCountriesByCount}) a real-Postgres integration test -- the geographic-anomaly
 * dashboard tile added for issue #569 slice 2. Also covers {@link SessionRepository#countByAppId},
 * which backs the admin Apps list's "Devices" column (previously a hardcoded 0, not bound to any
 * query).
 *
 * @author elizabeth houser
 */
class SessionRepositoryTest {

  @Test
  void resolveGeoAnomalyBaselineDaysFallsBackToDefaultWhenBlankOrUnparseable() {
    assertEquals(30, SessionRepository.resolveGeoAnomalyBaselineDays(null));
    assertEquals(30, SessionRepository.resolveGeoAnomalyBaselineDays(""));
    assertEquals(30, SessionRepository.resolveGeoAnomalyBaselineDays("not-a-number"));
    assertEquals(30, SessionRepository.resolveGeoAnomalyBaselineDays("30; DROP TABLE sessions"));
  }

  @Test
  void resolveGeoAnomalyBaselineDaysUsesConfiguredValueAndBounds() {
    assertEquals(90, SessionRepository.resolveGeoAnomalyBaselineDays("90"));
    assertEquals(1, SessionRepository.resolveGeoAnomalyBaselineDays("0"));
    assertEquals(1, SessionRepository.resolveGeoAnomalyBaselineDays("-5"));
    assertEquals(365, SessionRepository.resolveGeoAnomalyBaselineDays("999999"));
  }

  @Test
  void resolveGeoAnomalyRecentHoursFallsBackToDefaultWhenBlankOrUnparseable() {
    assertEquals(24, SessionRepository.resolveGeoAnomalyRecentHours(null));
    assertEquals(24, SessionRepository.resolveGeoAnomalyRecentHours(""));
    assertEquals(24, SessionRepository.resolveGeoAnomalyRecentHours("not-a-number"));
  }

  @Test
  void resolveGeoAnomalyRecentHoursUsesConfiguredValueAndBounds() {
    assertEquals(6, SessionRepository.resolveGeoAnomalyRecentHours("6"));
    assertEquals(1, SessionRepository.resolveGeoAnomalyRecentHours("0"));
    assertEquals(168, SessionRepository.resolveGeoAnomalyRecentHours("999999"));
  }

  // --- findTopCountriesByCount() integration coverage (issue #569 slice 2) ---

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping SessionRepository integration test");

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
      statement.execute("TRUNCATE TABLE sessions RESTART IDENTITY");
      statement.execute("TRUNCATE TABLE bot_list RESTART IDENTITY");
      statement.execute("TRUNCATE TABLE web_page_hits RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset sessions table", se);
    }
  }

  // --- findBotSessionsByIdentity() coverage -- the per-bot-identity breakdown table on the Site
  // Analytics page ---

  @Test
  void findBotSessionsByIdentityGroupsAndCountsByLabelWhenOneIsConfigured() {
    seedBotUserAgent("Googlebot/2.1", "Googlebot");
    seedBotSession("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", now());
    seedBotSession("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", now());

    List<StatisticsData> results = SessionRepository.findBotSessionsByIdentity(30);

    assertEquals(1, results.size());
    assertEquals("Googlebot", results.get(0).getLabel());
    assertEquals("2", results.get(0).getValue());
  }

  @Test
  void findBotSessionsByIdentityFallsBackToTheSignatureWhenNoLabelIsConfigured() {
    seedBotUserAgent("Bingbot/2.0", null);
    seedBotSession("Mozilla/5.0 (compatible; Bingbot/2.0; +http://www.bing.com/bingbot.htm)", now());

    List<StatisticsData> results = SessionRepository.findBotSessionsByIdentity(30);

    assertEquals(1, results.size());
    assertEquals("Bingbot/2.0", results.get(0).getLabel());
  }

  @Test
  void findBotSessionsByIdentityMatchesRegardlessOfUserAgentCasing() {
    // Real Bingbot UA is sent lowercase ("bingbot"), but the seeded bot_list signature is
    // capitalized ("Bingbot") -- issue #1145, same case-sensitivity bug as SessionCommand.checkForBot()
    // (fixed there in PR #1146), independently present here since classifyBotUserAgent() has its own
    // substring match rather than delegating to checkForBot().
    seedBotUserAgent("Bingbot", "Bingbot");
    seedBotSession("Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)", now());

    List<StatisticsData> results = SessionRepository.findBotSessionsByIdentity(30);

    assertEquals(1, results.size());
    assertEquals("Bingbot", results.get(0).getLabel());
  }

  @Test
  void findBotSessionsByIdentityBucketsUnmatchedUserAgentsAsUnclassified() {
    seedBotUserAgent("Googlebot/2.1", "Googlebot");
    seedBotSession("SomeOtherCrawler/1.0", now());

    List<StatisticsData> results = SessionRepository.findBotSessionsByIdentity(30);

    assertEquals(1, results.size());
    assertEquals("Unclassified", results.get(0).getLabel());
  }

  @Test
  void findBotSessionsByIdentityBucketsBlankUserAgentsSeparatelyFromUnmatched() {
    seedBotSession(null, now());
    seedBotSession("SomeOtherCrawler/1.0", now());

    List<StatisticsData> results = SessionRepository.findBotSessionsByIdentity(30);

    assertEquals(2, results.size());
    assertTrue(results.stream().anyMatch(d -> "Unclassified (blank User-Agent)".equals(d.getLabel())));
    assertTrue(results.stream().anyMatch(d -> "Unclassified".equals(d.getLabel())));
  }

  @Test
  void findBotSessionsByIdentityExcludesSessionsOutsideTheWindow() {
    seedBotUserAgent("Googlebot/2.1", "Googlebot");
    seedBotSession("Googlebot/2.1", Timestamp.from(Instant.now().minus(Duration.ofDays(45))));
    seedBotSession("Googlebot/2.1", now());

    List<StatisticsData> results = SessionRepository.findBotSessionsByIdentity(30);

    assertEquals(1, results.size());
    assertEquals("1", results.get(0).getValue());
  }

  @Test
  void findBotSessionsByIdentitySortsDescendingByCount() {
    seedBotUserAgent("Googlebot/2.1", "Googlebot");
    seedBotUserAgent("Bingbot/2.0", "Bingbot");
    seedBotSession("Bingbot/2.0", now());
    seedBotSession("Googlebot/2.1", now());
    seedBotSession("Googlebot/2.1", now());
    seedBotSession("Googlebot/2.1", now());

    List<StatisticsData> results = SessionRepository.findBotSessionsByIdentity(30);

    assertEquals(2, results.size());
    assertEquals("Googlebot", results.get(0).getLabel());
    assertEquals("3", results.get(0).getValue());
    assertEquals("Bingbot", results.get(1).getLabel());
  }

  @Test
  void findBotSessionsByIdentityReturnsEmptyListWhenThereIsNoData() {
    List<StatisticsData> results = SessionRepository.findBotSessionsByIdentity(30);

    assertTrue(results.isEmpty());
  }

  // --- findBotSessionStatsByIdentity() coverage -- first/last seen + top crawled page ---

  @Test
  void findBotSessionStatsByIdentityIncludesFirstAndLastSeen() {
    seedBotUserAgent("Googlebot/2.1", "Googlebot");
    seedBotSessionWithId("s1", "Googlebot/2.1", Timestamp.from(Instant.now().minus(Duration.ofDays(5))));
    seedBotSessionWithId("s2", "Googlebot/2.1", Timestamp.from(Instant.now().minus(Duration.ofHours(1))));

    List<BotIdentityStats> results = SessionRepository.findBotSessionStatsByIdentity(30);

    assertEquals(1, results.size());
    BotIdentityStats googlebot = results.get(0);
    assertEquals("Googlebot", googlebot.getIdentity());
    assertEquals(2, googlebot.getSessionCount());
    assertNotNull(googlebot.getFirstSeen());
    assertNotNull(googlebot.getLastSeen());
    assertNotEquals(googlebot.getFirstSeen(), googlebot.getLastSeen(),
        "the 5-days-ago and 1-hour-ago sessions must format to visibly different timestamps");
  }

  @Test
  void findBotSessionStatsByIdentityFindsTheMostCrawledPage() {
    seedBotUserAgent("Googlebot/2.1", "Googlebot");
    seedBotSessionWithId("s1", "Googlebot/2.1", now());
    seedBotSessionWithId("s2", "Googlebot/2.1", now());
    seedPageHit("s1", "/home");
    seedPageHit("s2", "/home");
    seedPageHit("s2", "/about");

    List<BotIdentityStats> results = SessionRepository.findBotSessionStatsByIdentity(30);

    assertEquals(1, results.size());
    assertEquals("/home", results.get(0).getTopPage());
    assertEquals(2, results.get(0).getTopPageHits());
  }

  @Test
  void findBotSessionStatsByIdentityLeavesTopPageNullWhenNoHitsAreRecorded() {
    seedBotUserAgent("Googlebot/2.1", "Googlebot");
    seedBotSessionWithId("s1", "Googlebot/2.1", now());

    List<BotIdentityStats> results = SessionRepository.findBotSessionStatsByIdentity(30);

    assertEquals(1, results.size());
    assertEquals("Googlebot", results.get(0).getIdentity());
    assertEquals(1, results.get(0).getSessionCount());
    assertNull(results.get(0).getTopPage());
    assertEquals(0, results.get(0).getTopPageHits());
  }

  @Test
  void findBotSessionStatsByIdentityOnlyCountsHitsFromSessionsWithinTheWindow() {
    seedBotUserAgent("Googlebot/2.1", "Googlebot");
    seedBotSessionWithId("s1", "Googlebot/2.1", Timestamp.from(Instant.now().minus(Duration.ofDays(45))));
    seedPageHit("s1", "/stale-page");

    List<BotIdentityStats> results = SessionRepository.findBotSessionStatsByIdentity(30);

    assertTrue(results.isEmpty(), "the session (and its hit) is outside the 30-day window: " + results);
  }

  @Test
  void findBotSessionStatsByIdentitySortsDescendingByCount() {
    seedBotUserAgent("Googlebot/2.1", "Googlebot");
    seedBotUserAgent("Bingbot/2.0", "Bingbot");
    seedBotSessionWithId("s1", "Bingbot/2.0", now());
    seedBotSessionWithId("s2", "Googlebot/2.1", now());
    seedBotSessionWithId("s3", "Googlebot/2.1", now());

    List<BotIdentityStats> results = SessionRepository.findBotSessionStatsByIdentity(30);

    assertEquals(2, results.size());
    assertEquals("Googlebot", results.get(0).getIdentity());
    assertEquals(2, results.get(0).getSessionCount());
    assertEquals("Bingbot", results.get(1).getIdentity());
  }

  @Test
  void findBotSessionStatsByIdentityReturnsEmptyListWhenThereIsNoData() {
    List<BotIdentityStats> results = SessionRepository.findBotSessionStatsByIdentity(30);

    assertTrue(results.isEmpty());
  }

  private static void seedBotUserAgent(String userAgent, String label) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      String labelValue = label == null ? "NULL" : "'" + label + "'";
      statement.execute("INSERT INTO bot_list (user_agent, label) VALUES ('" + userAgent + "', " + labelValue + ")");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed bot_list", se);
    }
  }

  private static void seedBotSession(String userAgent, Timestamp created) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      String userAgentValue = userAgent == null ? "NULL" : "'" + userAgent + "'";
      statement.execute("INSERT INTO sessions (is_bot, user_agent, created) VALUES (true, "
          + userAgentValue + ", '" + created + "')");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed bot session", se);
    }
  }

  private static void seedBotSessionWithId(String sessionId, String userAgent, Timestamp created) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO sessions (session_id, is_bot, user_agent, created) VALUES ('"
          + sessionId + "', true, '" + userAgent + "', '" + created + "')");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed bot session", se);
    }
  }

  private static void seedPageHit(String sessionId, String pagePath) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO web_page_hits (session_id, page_path) VALUES ('"
          + sessionId + "', '" + pagePath + "')");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed web_page_hits", se);
    }
  }

  @Test
  void findTopCountriesByCountOrdersByDescendingSessionCount() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession("Brazil", false, false, now);
    seedSession("Brazil", false, false, now);
    seedSession("Brazil", false, false, now);
    seedSession("Canada", false, false, now);
    seedSession("Canada", false, false, now);
    seedSession("Mexico", false, false, now);

    // Use a comfortably-future upper bound, not a freshly-computed now() -- otherwise this is flaky:
    // findTopCountriesByCount's upper bound is exclusive (created < endDate), so if a second
    // System.currentTimeMillis() call happens to land in the same millisecond as the seeded rows'
    // "now" (or earlier, under scheduling jitter), they'd be excluded by their own query's boundary.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 5);

    assertEquals(3, results.size());
    assertEquals("Brazil", results.get(0).getLabel());
    assertEquals("3", results.get(0).getValue());
    assertEquals("Canada", results.get(1).getLabel());
    assertEquals("Mexico", results.get(2).getLabel());
  }

  @Test
  void findTopCountriesByCountExcludesBotSessions() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession("Brazil", true, false, now);
    seedSession("Brazil", true, false, now);
    seedSession("Brazil", true, false, now);
    seedSession("Canada", false, false, now);

    // Use a comfortably-future upper bound -- see findTopCountriesByCountOrdersByDescendingSessionCount for why.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 5);

    assertEquals(1, results.size(), "Brazil's bot sessions must not count at all: " + results);
    assertEquals("Canada", results.get(0).getLabel());
  }

  @Test
  void findTopCountriesByCountExcludesSessionsWithNoResolvedCountry() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession(null, false, false, now);
    seedSession(null, false, false, now);
    seedSession("Canada", false, false, now);

    // Use a comfortably-future upper bound -- see findTopCountriesByCountOrdersByDescendingSessionCount for why.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 5);

    assertEquals(1, results.size(), "sessions with no resolved country can't be attributed to any country: " + results);
    assertEquals("Canada", results.get(0).getLabel());
  }

  @Test
  void findTopCountriesByCountIncludesAnonymousSessions() {
    // GeoIPCommand/SaveSessionCommand populate country for every session, anonymous or not --
    // only city/postal/lat/long are anonymous-restricted. Filtering on is_anonymous here would
    // silently undercount and could hide a real anomaly made up mostly of anonymous traffic.
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession("Brazil", false, true, now);

    // Use a comfortably-future upper bound -- see findTopCountriesByCountOrdersByDescendingSessionCount for why.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 5);

    assertEquals(1, results.size(), "an anonymous session's country must still count: " + results);
    assertEquals("Brazil", results.get(0).getLabel());
  }

  @Test
  void findTopCountriesByCountExcludesSessionsOutsideTheWindow() {
    Timestamp twoHoursAgo = Timestamp.from(Instant.now().minus(Duration.ofHours(2)));
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession("Brazil", false, false, twoHoursAgo);
    seedSession("Canada", false, false, now);

    // Use a comfortably-future upper bound -- see findTopCountriesByCountOrdersByDescendingSessionCount for why.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 5);

    assertEquals(1, results.size(), "the session outside the 1-hour window must not count: " + results);
    assertEquals("Canada", results.get(0).getLabel());
  }

  @Test
  void findTopCountriesByCountReturnsEmptyListWhenThereIsNoData() {
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), now(), 5);

    assertTrue(results.isEmpty());
  }

  @Test
  void findTopCountriesByCountRespectsTheRecordLimit() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    seedSession("Brazil", false, false, now);
    seedSession("Canada", false, false, now);
    seedSession("Mexico", false, false, now);

    // Use a comfortably-future upper bound -- see findTopCountriesByCountOrdersByDescendingSessionCount for why.
    List<StatisticsData> results = SessionRepository.findTopCountriesByCount(hoursAgo(1), future(), 2);

    assertEquals(2, results.size());
  }

  // --- countByAppId() coverage -- backs the admin Apps list's "Devices" column, previously a
  // hardcoded 0 not bound to any query at all ---

  @Test
  void countByAppIdCountsOnlySessionsForThatApp() {
    seedSessionWithAppId(1L);
    seedSessionWithAppId(1L);
    seedSessionWithAppId(2L);

    assertEquals(2, SessionRepository.countByAppId(1L));
    assertEquals(1, SessionRepository.countByAppId(2L));
  }

  @Test
  void countByAppIdReturnsZeroWhenNoSessionsAreAttributedToThatApp() {
    seedSessionWithAppId(1L);

    assertEquals(0, SessionRepository.countByAppId(2L));
  }

  @Test
  void countByAppIdIgnoresSessionsWithNoAppId() {
    // A session established outside of an API-key context (a normal browser visitor) has no app_id
    seedSession("Canada", false, false, new Timestamp(System.currentTimeMillis()));
    seedSessionWithAppId(1L);

    assertEquals(1, SessionRepository.countByAppId(1L));
  }

  @Test
  void countByAppIdReturnsZeroForANonPositiveAppIdWithoutQueryingTheDatabase() {
    // -1 is App's "unsaved"/unset id -- must short-circuit rather than run a WHERE app_id = -1 scan
    assertEquals(0, SessionRepository.countByAppId(-1L));
    assertEquals(0, SessionRepository.countByAppId(0L));
  }

  private static void seedSessionWithAppId(long appId) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO sessions (app_id) VALUES (" + appId + ")");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed session with app_id", se);
    }
  }

  private static Timestamp hoursAgo(int hours) {
    return Timestamp.from(Instant.now().minus(Duration.ofHours(hours)));
  }

  private static Timestamp now() {
    return new Timestamp(System.currentTimeMillis());
  }

  /**
   * A query upper bound guaranteed to be strictly after anything seeded with {@code now()} in this
   * test class, without relying on two separate {@code System.currentTimeMillis()} calls landing in
   * different milliseconds. findTopCountriesByCount's upper bound is exclusive, so an upper bound
   * that isn't comfortably ahead of the seeded data is a real source of flakiness, not just a
   * theoretical one.
   */
  private static Timestamp future() {
    return Timestamp.from(Instant.now().plusSeconds(60));
  }

  private static void seedSession(String country, boolean isBot, boolean isAnonymous, Timestamp created) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      String countryValue = country == null ? "NULL" : "'" + country + "'";
      statement.execute("INSERT INTO sessions (country, is_bot, is_anonymous, created) VALUES ("
          + countryValue + ", " + isBot + ", " + isAnonymous + ", '" + created + "')");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed session", se);
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
    // A focused subset of the real sessions schema -- enough for findTopCountriesByCount's
    // window/bot/country filtering, plus app_id for countByAppId(). FK to apps(app_id) is omitted
    // (no apps table in this focused schema), matching this class's existing simplification of
    // leaving out FKs to unrelated tables.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS sessions CASCADE");
      statement.execute("CREATE TABLE sessions ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "session_id VARCHAR(255), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "country VARCHAR(100), "
          + "user_agent VARCHAR(255), "
          + "referer VARCHAR(255), "
          + "host VARCHAR(255), "
          + "is_bot BOOLEAN DEFAULT false, "
          + "is_anonymous BOOLEAN NOT NULL DEFAULT false, "
          + "app_id BIGINT)");
      statement.execute("DROP TABLE IF EXISTS bot_list CASCADE");
      statement.execute("CREATE TABLE bot_list ("
          + "bot_list_id BIGSERIAL PRIMARY KEY, "
          + "user_agent VARCHAR(255) NOT NULL, "
          + "label VARCHAR(255), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
      statement.execute("DROP TABLE IF EXISTS web_page_hits CASCADE");
      statement.execute("CREATE TABLE web_page_hits ("
          + "hit_id BIGSERIAL PRIMARY KEY, "
          + "page_path VARCHAR(255), "
          + "session_id VARCHAR(255), "
          + "hit_date TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the sessions schema", se);
    }
  }

  @Test
  void aSessionRecordsTheHostTheRequestArrivedOn() {
    Session session = new Session();
    session.setReferer("https://fde-example.z03.azurefd.net/about-us");
    session.setHost("fde-example.z03.azurefd.net");
    assertEquals("fde-example.z03.azurefd.net", session.getHost());
  }

  @Test
  void aSessionWithNoKnownHostLeavesItNull() {
    // Rows written before the column existed, and non-web sources, stay NULL and keep the
    // site.url comparison rather than silently changing meaning (issue #1893)
    assertNull(new Session().getHost());
  }

  @Test
  void topReferralsExcludesAReferrerFromTheHostTheRequestArrivedOn() throws SQLException {
    Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    try (Connection connection = DB.getConnection(); Statement st = connection.createStatement()) {
      st.execute("TRUNCATE TABLE sessions RESTART IDENTITY CASCADE");
      // self-referral arriving on a hostname site.url does not name
      st.execute("INSERT INTO sessions (session_id, referer, host, is_bot) VALUES "
          + "('a','https://fde-example.z03.azurefd.net/sams','fde-example.z03.azurefd.net',false)");
      // a genuine external referral on the same host
      st.execute("INSERT INTO sessions (session_id, referer, host, is_bot) VALUES "
          + "('b','https://www.google.com','fde-example.z03.azurefd.net',false)");
      // a legacy row with no host recorded -- must survive, filtered only by site.url
      st.execute("INSERT INTO sessions (session_id, referer, host, is_bot) VALUES "
          + "('c','https://news.example.org',NULL,false)");
    }
    List<StatisticsData> top = SessionRepository.findTopReferrals(30, 'd', 10);
    List<String> labels = top.stream().map(StatisticsData::getLabel).collect(Collectors.toList());
    assertFalse(labels.stream().anyMatch(l -> l.contains("azurefd.net")),
        "A referrer from the host the request arrived on is a self-referral, whatever that host is");
    assertTrue(labels.contains("https://www.google.com"), "Real external referrals stay");
    assertTrue(labels.contains("https://news.example.org"),
        "A row with no recorded host must still be reported");
  }

}
