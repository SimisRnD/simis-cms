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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.presentation.controller.DeferredStartup.Outcome;

/**
 * The production failure, against a real PostgreSQL: the application starts while the database is
 * unreachable, and the database comes up afterwards. Azure's auto-heal recycled the site part-way
 * through both production database outages, which is exactly this start, and it used to leave the
 * site answering 404 on every path until a manual restart -- the pool failed fast, DataSource held
 * null, and startup died on a NullPointerException.
 *
 * <p>Nothing listens on the database port when the pool is created; PostgreSQL is then started on
 * that same port. The pool, the probe and the retry are the production ones, so this covers the
 * pool's configuration as well as the retry. Skipped automatically when Docker is not available.
 */
class DeferredStartupDatabaseRecoveryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private GenericContainer<?> postgres;
  private DeferredStartup deferredStartup;

  @AfterEach
  void tearDown() {
    if (deferredStartup != null) {
      deferredStartup.stop();
    }
    DataSource.shutdown();
    if (postgres != null) {
      postgres.stop();
    }
  }

  @Test
  void startupWaitsForADatabaseThatComesUpAfterThePoolWasCreated() throws Exception {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping database recovery test");

    int port = freePort();
    Properties properties = new Properties();
    properties.setProperty("jdbcUrl",
        "jdbc:postgresql://" + DockerClientFactory.instance().dockerHostIpAddress() + ":" + port + "/" + DB_NAME);
    properties.setProperty("username", DB_USER);
    properties.setProperty("password", DB_PASSWORD);
    // Keeps each failed attempt short; production waits the default 30 seconds
    properties.setProperty("connectionTimeout", "2000");

    // The database is down: the pool must still come up, rather than leave DataSource null
    assertDoesNotThrow(() -> DataSource.init(properties));
    assertNotNull(DataSource.getDataSource());

    AtomicInteger rowsRead = new AtomicInteger();
    DeferredStartup.Step startWithDatabase = () -> {
      try (Connection connection = DB.getConnection();
          Statement statement = connection.createStatement();
          ResultSet rs = statement.executeQuery("SELECT 1")) {
        while (rs.next()) {
          rowsRead.addAndGet(rs.getInt(1));
        }
      }
      return true;
    };
    assertEquals(Outcome.WAITING_FOR_DATABASE,
        DeferredStartup.attempt(DeferredStartup.POOLED_CONNECTION, startWithDatabase));

    deferredStartup = DeferredStartup.retry(DeferredStartup.POOLED_CONNECTION, startWithDatabase,
        Duration.ofMillis(200));

    // Bring the database up on the port the pool has been trying
    postgres = new GenericContainer<>(DockerImageName.parse(resolveImage()))
        .withEnv("POSTGRES_USER", DB_USER)
        .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
        .withEnv("POSTGRES_DB", DB_NAME)
        .withExposedPorts(POSTGRES_PORT)
        .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
            new PortBinding(Ports.Binding.bindPort(port), new ExposedPort(POSTGRES_PORT))))
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
            .withStartupTimeout(Duration.ofSeconds(120)));
    try {
      postgres.start();
    } catch (Throwable t) {
      Assumptions.abort("Unable to start PostgreSQL test container: " + t.getMessage());
    }

    assertEquals(Outcome.STARTED, deferredStartup.awaitOutcome(Duration.ofSeconds(60)),
        "startup should complete once the database is reachable");
    assertEquals(1, rowsRead.get());
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
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
