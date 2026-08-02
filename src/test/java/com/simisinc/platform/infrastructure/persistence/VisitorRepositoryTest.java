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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
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

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Real-Postgres integration coverage for {@link VisitorRepository#findReturnVisitorRatePercent}
 * (issue #568's "return-visitor-rate" engagement report).
 *
 * @author elizabeth houser
 */
class VisitorRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping VisitorRepository integration test");

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
      statement.execute("TRUNCATE TABLE visitors RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset the visitors table", se);
    }
  }

  @Test
  void findReturnVisitorRatePercentCountsTokensWithMoreThanOneRowAllTime() {
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    Timestamp ninetyDaysAgo = minusDays(now, 90);
    Timestamp oneDayAgo = minusDays(now, 1);

    // token-1: 1 row in the window -- a first-ever visit, not "returning"
    seedVisitor("token-1", now);
    // token-2: 1 row in the window, but a 2nd row from long before the window -- returning
    seedVisitor("token-2", ninetyDaysAgo);
    seedVisitor("token-2", now);
    // token-3: 2 rows, both inside the window -- returning
    seedVisitor("token-3", oneDayAgo);
    seedVisitor("token-3", now);
    // token-4: 1 row, but outside the window entirely -- not part of the active denominator at all
    seedVisitor("token-4", ninetyDaysAgo);

    // Active in the last 30 days: token-1, token-2, token-3 (token-4 is not active in range)
    // Returning (>= 2 total rows, all-time): token-2, token-3
    // 2 of 3 active tokens are returning = 66.7%
    double percent = VisitorRepository.findReturnVisitorRatePercent(30);

    assertEquals(66.7, percent, 0.05);
  }

  @Test
  void findReturnVisitorRatePercentReturnsZeroWhenNoVisitorsAreInRange() {
    double percent = VisitorRepository.findReturnVisitorRatePercent(30);

    // Must not throw a divide-by-zero error, and must not misreport as some positive rate
    assertEquals(0.0, percent, 0.0001);
  }

  private static Timestamp minusDays(Timestamp base, int days) {
    return new Timestamp(base.getTime() - Duration.ofDays(days).toMillis());
  }

  private static void seedVisitor(String token, Timestamp created) {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO visitors (token, created) VALUES ('" + token + "', '" + created + "')");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not seed visitor", se);
    }
  }

  private static void createSchema() {
    // A focused subset of the real schema (NEW_10000__new_database.sql) -- just the visitors table,
    // enough for findReturnVisitorRatePercent's self-join.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS visitors CASCADE");
      statement.execute("CREATE TABLE visitors ("
          + "visitor_id BIGSERIAL PRIMARY KEY, "
          + "token VARCHAR(255), "
          + "session_id VARCHAR(255), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the schema", se);
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
