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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Covers the client -> {@link WebVitalsApiController} -> {@link
 * com.simisinc.platform.infrastructure.cache.WebVitalsCollector} -> {@code web_vitals} storage
 * path, focused on INP (issue: INP dashboard column read "--"/empty because the client-side
 * collector read a nonexistent {@code processingDuration} property off "first-input" entries,
 * which JSON.stringify silently turned into {@code null}, which the server's {@code
 * asDouble(-1)}/{@code value < 0} guard silently dropped).
 *
 * <p>
 * The bug was entirely client-side (see {@code web-vitals-collector.js}); these tests exist to
 * confirm the server side was already correct for a valid numeric value, and to guard the
 * specific "missing/null value is silently skipped" mechanism that let the client bug hide with
 * no error anywhere, for INP specifically and not just in isolation from the other metrics in the
 * same payload.
 * </p>
 *
 * <p>
 * This is an integration test: it starts a throwaway PostgreSQL container (Testcontainers) and
 * exercises the real doPost -> WebVitalsCollector -> DB.insertInto path, mirroring {@code
 * WebVitalsAggregateRepositoryTest}. It is skipped automatically when Docker is not available.
 * </p>
 */
class WebVitalsApiControllerTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping WebVitalsApiController integration test");

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
      // Never initialized when Docker is unavailable
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
      statement.execute("TRUNCATE TABLE web_vitals RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset web_vitals table", se);
    }
  }

  /** Captures the JSON body written to the response, along with whatever status was set. */
  private static class Recorded {
    String body;
    int status = 200; // HttpServletResponse's own default when setStatus is never called
  }

  private static HttpServletRequest requestWithBody(String jsonBody) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(jsonBody)));
    when(request.getSession(false)).thenReturn(null);
    when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (test-agent)");
    return request;
  }

  private static Recorded runDoPost(HttpServletRequest request) throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    Recorded recorded = new Recorded();
    doAnswer(inv -> {
      recorded.status = inv.getArgument(0);
      return null;
    }).when(response).setStatus(anyInt());

    new WebVitalsApiController().doPost(request, response);
    recorded.body = body.toString();
    return recorded;
  }

  private static List<Map<String, Object>> selectAllRows() throws SQLException {
    List<Map<String, Object>> rows = new ArrayList<>();
    try (Connection connection = DB.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "SELECT url, metric_type, value, rating FROM web_vitals ORDER BY metric_type");
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        Map<String, Object> row = new HashMap<>();
        row.put("url", rs.getString("url"));
        row.put("metric_type", rs.getString("metric_type"));
        row.put("value", rs.getBigDecimal("value"));
        row.put("rating", rs.getString("rating"));
        rows.add(row);
      }
    }
    return rows;
  }

  @Test
  void postValidInpMetricRoundTripsThroughStorageAndIsReadableFromTheRealDatabase() throws Exception {
    // Shape a real fixed client would now send: INP as a genuine numeric millisecond value
    // (the worst tracked "event" entry duration), not the undefined/NaN/null the
    // processingDuration bug used to produce.
    String jsonBody = "{"
        + "\"url\":\"/checkout\","
        + "\"metrics\":{"
        + "  \"LCP\":{\"value\":2200,\"rating\":\"good\"},"
        + "  \"INP\":{\"value\":180,\"rating\":\"good\"}"
        + "},"
        + "\"viewportWidth\":1024,"
        + "\"connectionType\":\"4g\""
        + "}";

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class)) {
      pages.when(() -> WebPageRepository.findByLink("/checkout")).thenReturn(null);

      HttpServletRequest request = requestWithBody(jsonBody);
      Recorded result = runDoPost(request);

      assertEquals(204, result.status, "a valid payload must be accepted with no content");
    }

    List<Map<String, Object>> rows = selectAllRows();
    assertEquals(2, rows.size(), "both LCP and INP should have been stored: " + rows);

    Map<String, Object> inpRow = rows.stream()
        .filter(r -> "INP".equals(r.get("metric_type")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no INP row was stored: " + rows));

    assertEquals("/checkout", inpRow.get("url"));
    assertEquals(0, BigDecimal.valueOf(180.00).compareTo((BigDecimal) inpRow.get("value")),
        "the INP value must round-trip as the real numeric millisecond figure, not be dropped");
    assertEquals("good", inpRow.get("rating"));
  }

  @Test
  void postMetricsWithAMissingOrNullInpValueIsSilentlySkippedWhileSiblingMetricsStillStore() throws Exception {
    // Reproduces the server side of the original bug shape directly: a client sending
    // JSON.stringify(NaN) for INP serializes to a JSON null for "value" (this is exactly what the
    // old processingDuration-typo client used to produce). The server's asDouble(-1)/"value < 0"
    // guard is *supposed* to skip only that single bad metric, not silently corrupt storage and
    // not take down the rest of the payload -- this pins that contract down explicitly for INP so
    // a future regression of the same shape fails a test instead of just going quiet again.
    String jsonBody = "{"
        + "\"url\":\"/checkout\","
        + "\"metrics\":{"
        + "  \"LCP\":{\"value\":2200,\"rating\":\"good\"},"
        + "  \"INP\":{\"value\":null,\"rating\":\"good\"}"
        + "}"
        + "}";

    try (MockedStatic<WebPageRepository> pages = mockStatic(WebPageRepository.class)) {
      pages.when(() -> WebPageRepository.findByLink("/checkout")).thenReturn(null);

      HttpServletRequest request = requestWithBody(jsonBody);
      Recorded result = runDoPost(request);

      assertEquals(204, result.status, "the request as a whole must still succeed");
    }

    List<Map<String, Object>> rows = selectAllRows();
    assertEquals(1, rows.size(), "only LCP should have been stored: " + rows);
    assertEquals("LCP", rows.get(0).get("metric_type"));
    assertFalse(rows.stream().anyMatch(r -> "INP".equals(r.get("metric_type"))),
        "a null INP value must never be stored as a fabricated -1 or 0");
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
    // A focused subset of the real web_vitals table (matches UPGRADE_20260726.2000 +
    // UPGRADE_20260727.1001) -- enough for the doPost -> WebVitalsCollector -> storeMetric path
    // under test, without needing a real web_pages table for the optional FK.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS web_vitals CASCADE");
      statement.execute("CREATE TABLE web_vitals ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "url VARCHAR(2048) NOT NULL, "
          + "metric_type VARCHAR(50) NOT NULL, "
          + "value NUMERIC(10, 2) NOT NULL, "
          + "rating VARCHAR(20), "
          + "session_id VARCHAR(64), "
          + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, "
          + "web_page_id BIGINT, "
          + "user_agent_hash VARCHAR(64), "
          + "viewport_width SMALLINT, "
          + "connection_type VARCHAR(16), "
          + "CONSTRAINT metric_type_check CHECK (metric_type IN ('LCP', 'CLS', 'INP', 'FCP', 'TTFB')))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the web_vitals schema", se);
    }
  }
}
