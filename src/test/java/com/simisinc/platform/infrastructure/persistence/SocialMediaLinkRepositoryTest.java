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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.simisinc.platform.domain.model.SocialMediaLink;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies SocialMediaLinkRepository (issue #516) against a real PostgreSQL instance.
 *
 * @author SimIS Inc.
 */
class SocialMediaLinkRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping SocialMediaLinkRepository integration test");

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
      statement.execute("TRUNCATE TABLE social_media_links RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset social_media_links table", se);
    }
  }

  @Test
  void addSavesAndAssignsAnId() {
    SocialMediaLink link = new SocialMediaLink();
    link.setPlatformName("Instagram");
    link.setUrl("https://instagram.com/SimISInc");
    link.setLinkOrder(25);

    SocialMediaLink saved = SocialMediaLinkRepository.add(link);

    assertNotNull(saved);
    assertTrue(saved.getId() > 0);
    assertEquals(1, DB.selectCountFrom("social_media_links"));
  }

  @Test
  void findAllOrdersByLinkOrderThenPlatformName() {
    addLink("YouTube", "https://youtube.com/@SimISInc", 45);
    addLink("Facebook", "https://facebook.com/SimISInc", 20);
    addLink("Discord", "https://discord.gg/simis", 20);

    List<SocialMediaLink> all = SocialMediaLinkRepository.findAll();

    assertEquals(3, all.size());
    // Same link_order (20) breaks the tie alphabetically by platform name
    assertEquals("Discord", all.get(0).getPlatformName());
    assertEquals("Facebook", all.get(1).getPlatformName());
    assertEquals("YouTube", all.get(2).getPlatformName());
  }

  @Test
  void updateChangesTheExistingRowNotACopy() {
    SocialMediaLink saved = addLink("Mastodon", "https://old-instance.social/@simis", 100);

    saved.setUrl("https://new-instance.social/@simis");
    SocialMediaLink updated = SocialMediaLinkRepository.update(saved);

    assertNotNull(updated);
    assertEquals(1, DB.selectCountFrom("social_media_links"));
    SocialMediaLink reloaded = SocialMediaLinkRepository.findById(saved.getId());
    assertEquals("https://new-instance.social/@simis", reloaded.getUrl());
  }

  @Test
  void removeDeletesTheRow() {
    SocialMediaLink saved = addLink("TikTok", "https://tiktok.com/@simis", 100);

    boolean removed = SocialMediaLinkRepository.remove(saved);

    assertTrue(removed);
    assertNull(SocialMediaLinkRepository.findById(saved.getId()));
    assertEquals(0, DB.selectCountFrom("social_media_links"));
  }

  @Test
  void findByIdReturnsNullForAMissingRecord() {
    assertNull(SocialMediaLinkRepository.findById(999));
    assertNull(SocialMediaLinkRepository.findById(-1));
  }

  @Test
  void findByPlatformNameIsCaseInsensitive() {
    addLink("Instagram", "https://instagram.com/SimISInc", 25);

    assertNotNull(SocialMediaLinkRepository.findByPlatformName("instagram"));
    assertNotNull(SocialMediaLinkRepository.findByPlatformName("INSTAGRAM"));
    assertNotNull(SocialMediaLinkRepository.findByPlatformName("  Instagram  "));
    assertNull(SocialMediaLinkRepository.findByPlatformName("Mastodon"));
    assertNull(SocialMediaLinkRepository.findByPlatformName(""));
  }

  private static SocialMediaLink addLink(String platformName, String url, int linkOrder) {
    SocialMediaLink link = new SocialMediaLink();
    link.setPlatformName(platformName);
    link.setUrl(url);
    link.setLinkOrder(linkOrder);
    return SocialMediaLinkRepository.add(link);
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
      statement.execute("DROP TABLE IF EXISTS social_media_links CASCADE");
      statement.execute("CREATE TABLE social_media_links ("
          + "social_media_link_id BIGSERIAL PRIMARY KEY, "
          + "platform_name VARCHAR(100) NOT NULL, "
          + "url VARCHAR(512) NOT NULL, "
          + "link_order INTEGER DEFAULT 100, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the social_media_links schema", se);
    }
  }
}
