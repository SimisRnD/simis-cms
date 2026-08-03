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
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link TagRepository} against a real PostgreSQL instance (issue #632), since the
 * unique-per-collection lookup and item_tags cascade-on-remove behavior aren't meaningful to
 * exercise with a mock.
 *
 * <p>Integration test: starts a throwaway PostgreSQL container (Testcontainers) and is skipped
 * automatically when Docker is not available.</p>
 *
 * @author SimIS Inc.
 */
class TagRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping TagRepository integration test");

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
      statement.execute("TRUNCATE TABLE item_tags, tags, collections RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset tables", se);
    }
  }

  @Test
  void saveInsertsANewTagAndFindByIdReturnsIt() {
    long collectionId = addCollection();
    Tag tagBean = new Tag();
    tagBean.setCollectionId(collectionId);
    tagBean.setName("Fiction");
    tagBean.setCreatedBy(1);

    Tag saved = TagRepository.save(tagBean);
    assertTrue(saved.getId() > -1);

    Tag found = TagRepository.findById(saved.getId());
    assertEquals("Fiction", found.getName());
    assertEquals(collectionId, found.getCollectionId());
    assertEquals(0, found.getItemCount());
  }

  @Test
  void findByNameWithinCollectionIsCaseInsensitiveAndScopedToTheCollection() {
    long collectionA = addCollection();
    long collectionB = addCollection();
    addTag(collectionA, "Fiction");

    assertEquals("Fiction", TagRepository.findByNameWithinCollection("fiction", collectionA).getName());
    assertEquals("Fiction", TagRepository.findByNameWithinCollection("FICTION", collectionA).getName());
    assertNull(TagRepository.findByNameWithinCollection("Fiction", collectionB));
    assertNull(TagRepository.findByNameWithinCollection("Nonfiction", collectionA));
  }

  @Test
  void findAllByCollectionIdReturnsOnlyThatCollectionsTagsSortedByName() {
    long collectionA = addCollection();
    long collectionB = addCollection();
    addTag(collectionA, "Zebra");
    addTag(collectionA, "Apple");
    addTag(collectionB, "Other");

    List<Tag> tagList = TagRepository.findAllByCollectionId(collectionA);
    assertEquals(2, tagList.size());
    assertEquals("Apple", tagList.get(0).getName());
    assertEquals("Zebra", tagList.get(1).getName());
  }

  @Test
  void findAllReturnsEveryTagAcrossCollectionsSortedByName() {
    // Issue #632: mirrors CategoryRepository.findAll() exactly -- ItemsSearchResultsWidget needs
    // this to enumerate every tag facet candidate, the same way it enumerates category candidates
    // via CategoryRepository.findAll(), regardless of which collection a tag belongs to.
    long collectionA = addCollection();
    long collectionB = addCollection();
    addTag(collectionA, "Zebra");
    addTag(collectionB, "Apple");

    List<Tag> tagList = TagRepository.findAll();
    assertEquals(2, tagList.size());
    assertEquals("Apple", tagList.get(0).getName());
    assertEquals("Zebra", tagList.get(1).getName());
  }

  @Test
  void findAllReturnsNullWhenNoTagsExist() {
    assertNull(TagRepository.findAll(), "mirrors CategoryRepository.findAll()'s null-when-empty shape");
  }

  @Test
  void saveUpdatesAnExistingTagsName() {
    long collectionId = addCollection();
    long tagId = addTag(collectionId, "Fiction");

    Tag tagBean = TagRepository.findById(tagId);
    tagBean.setName("Non-Fiction");
    Tag updated = TagRepository.save(tagBean);
    assertEquals("Non-Fiction", updated.getName());

    Tag reloaded = TagRepository.findById(tagId);
    assertEquals("Non-Fiction", reloaded.getName());
  }

  @Test
  void updateItemCountIncrementsAndDecrements() throws SQLException {
    long collectionId = addCollection();
    long tagId = addTag(collectionId, "Fiction");

    try (Connection connection = DB.getConnection()) {
      TagRepository.updateItemCount(connection, tagId, 1);
      TagRepository.updateItemCount(connection, tagId, 1);
      assertEquals(2, TagRepository.findById(tagId).getItemCount());

      TagRepository.updateItemCount(connection, tagId, -1);
      assertEquals(1, TagRepository.findById(tagId).getItemCount());
    }
  }

  @Test
  void removeDeletesTheTagAndItsItemTagReferences() throws SQLException {
    long collectionId = addCollection();
    long tagId = addTag(collectionId, "Fiction");
    linkItemTag(1, tagId, collectionId);

    Tag tagBean = TagRepository.findById(tagId);
    assertTrue(TagRepository.remove(tagBean));

    assertNull(TagRepository.findById(tagId));
    assertEquals(0, countItemTagsForTag(tagId));
  }

  @Test
  void removeAllByCollectionDeletesAllOfThatCollectionsTags() throws SQLException {
    long collectionId = addCollection();
    addTag(collectionId, "Fiction");
    addTag(collectionId, "History");

    Collection collectionBean = new Collection();
    collectionBean.setId(collectionId);
    try (Connection connection = DB.getConnection()) {
      TagRepository.removeAll(connection, collectionBean);
    }

    assertEquals(0, TagRepository.findAllByCollectionId(collectionId).size());
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
      statement.execute("DROP TABLE IF EXISTS tags CASCADE");
      statement.execute("DROP TABLE IF EXISTS collections CASCADE");
      statement.execute("CREATE TABLE collections ("
          + "collection_id BIGSERIAL PRIMARY KEY)");
      statement.execute("CREATE TABLE tags ("
          + "tag_id BIGSERIAL PRIMARY KEY, "
          + "collection_id BIGINT NOT NULL, "
          + "name VARCHAR(255) NOT NULL, "
          + "created_by BIGINT, "
          + "created TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
          + "item_count BIGINT NOT NULL DEFAULT 0)");
      statement.execute("CREATE TABLE item_tags ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "item_id BIGINT NOT NULL, "
          + "tag_id BIGINT NOT NULL, "
          + "collection_id BIGINT NOT NULL)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the test schema", se);
    }
  }

  private static long addCollection() {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO collections DEFAULT VALUES RETURNING collection_id")) {
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a collection", se);
    }
  }

  private static long addTag(long collectionId, String name) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO tags (collection_id, name, created_by) VALUES (?, ?, 1) RETURNING tag_id")) {
      pst.setLong(1, collectionId);
      pst.setString(2, name);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a tag", se);
    }
  }

  private static void linkItemTag(long itemId, long tagId, long collectionId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO item_tags (item_id, tag_id, collection_id) VALUES (?, ?, ?)")) {
      pst.setLong(1, itemId);
      pst.setLong(2, tagId);
      pst.setLong(3, collectionId);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not link an item tag", se);
    }
  }

  private static long countItemTagsForTag(long tagId) throws SQLException {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "SELECT COUNT(*) FROM item_tags WHERE tag_id = ?")) {
      pst.setLong(1, tagId);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }
}
