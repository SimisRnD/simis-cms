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

package com.simisinc.platform.infrastructure.persistence.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemTag;
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link ItemTagRepository} against a real PostgreSQL instance (issue #632).
 *
 * <p>Integration test: starts a throwaway PostgreSQL container (Testcontainers) and is skipped
 * automatically when Docker is not available.</p>
 *
 * @author SimIS Inc.
 */
class ItemTagRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping ItemTagRepository integration test");

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
      statement.execute("TRUNCATE TABLE item_tags RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset tables", se);
    }
  }

  @Test
  void insertItemTagListInsertsARowForEveryTagOnTheItem() throws SQLException {
    Item item = new Item();
    item.setId(100L);
    item.setCollectionId(5);
    item.setTagIdList(new Long[] { 10L, 20L, 30L });

    try (Connection connection = DB.getConnection()) {
      ItemTagRepository.insertItemTagList(connection, item);
    }

    List<ItemTag> itemTagList = ItemTagRepository.findAllByItemId(100);
    assertEquals(3, itemTagList.size());
  }

  @Test
  void insertItemTagListDoesNothingWhenTheListIsNull() throws SQLException {
    Item item = new Item();
    item.setId(100L);
    item.setCollectionId(5);
    item.setTagIdList(null);

    try (Connection connection = DB.getConnection()) {
      ItemTagRepository.insertItemTagList(connection, item);
    }

    assertEquals(0, ItemTagRepository.findAllByItemId(100).size());
  }

  @Test
  void removeItemTagIdRemovesOnlyThatSpecificTag() throws SQLException {
    Item item = new Item();
    item.setId(100L);
    item.setCollectionId(5);

    try (Connection connection = DB.getConnection()) {
      ItemTagRepository.insertItemTagId(connection, item, 10);
      ItemTagRepository.insertItemTagId(connection, item, 20);
      ItemTagRepository.removeItemTagId(connection, item, 10);
    }

    List<ItemTag> remaining = ItemTagRepository.findAllByItemId(100);
    assertEquals(1, remaining.size());
    assertEquals(20, remaining.get(0).getTagId());
  }

  @Test
  void removeAllByItemRemovesEveryTagOnThatItem() throws SQLException {
    Item item = new Item();
    item.setId(100L);
    item.setCollectionId(5);

    try (Connection connection = DB.getConnection()) {
      ItemTagRepository.insertItemTagId(connection, item, 10);
      ItemTagRepository.insertItemTagId(connection, item, 20);
      ItemTagRepository.removeAll(connection, item);
    }

    assertTrue(ItemTagRepository.findAllByItemId(100).isEmpty());
  }

  @Test
  void removeAllByTagRemovesThatTagFromEveryItem() throws SQLException {
    Item itemA = new Item();
    itemA.setId(100L);
    itemA.setCollectionId(5);
    Item itemB = new Item();
    itemB.setId(200L);
    itemB.setCollectionId(5);

    try (Connection connection = DB.getConnection()) {
      ItemTagRepository.insertItemTagId(connection, itemA, 10);
      ItemTagRepository.insertItemTagId(connection, itemB, 10);

      Tag tag = new Tag();
      tag.setId(10L);
      ItemTagRepository.removeAll(connection, tag);
    }

    assertTrue(ItemTagRepository.findAllByItemId(100).isEmpty());
    assertTrue(ItemTagRepository.findAllByItemId(200).isEmpty());
  }

  @Test
  void removeAllByCollectionRemovesEveryItemTagInThatCollection() throws SQLException {
    Item itemA = new Item();
    itemA.setId(100L);
    itemA.setCollectionId(5);
    Item itemB = new Item();
    itemB.setId(200L);
    itemB.setCollectionId(6);

    try (Connection connection = DB.getConnection()) {
      ItemTagRepository.insertItemTagId(connection, itemA, 10);
      ItemTagRepository.insertItemTagId(connection, itemB, 20);

      Collection collection = new Collection();
      collection.setId(5L);
      ItemTagRepository.removeAll(connection, collection);
    }

    assertTrue(ItemTagRepository.findAllByItemId(100).isEmpty());
    assertEquals(1, ItemTagRepository.findAllByItemId(200).size());
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
      statement.execute("DROP TABLE IF EXISTS item_tags CASCADE");
      statement.execute("CREATE TABLE item_tags ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "item_id BIGINT NOT NULL, "
          + "tag_id BIGINT NOT NULL, "
          + "collection_id BIGINT NOT NULL)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the test schema", se);
    }
  }
}
