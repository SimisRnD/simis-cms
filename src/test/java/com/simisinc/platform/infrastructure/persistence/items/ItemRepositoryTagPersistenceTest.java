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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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

import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemCategory;
import com.simisinc.platform.domain.model.items.ItemTag;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link ItemRepository#buildRecord} populates {@code tagIdList} the same way it already
 * populates {@code categoryIdList}, and that a save() round trip starting from a plain findById()
 * read does not silently strip an item's existing tags.
 *
 * <p>
 * Before this fix, {@code buildRecord} never populated {@code tagIdList}, so any caller that
 * loaded an item via {@code findById}/{@code findAll}/{@code query} and then called {@code
 * ItemRepository.save()} without itself rebuilding {@code tagIdList} (e.g. {@code
 * PageServlet#deactivateCollectionItem}, which only sets {@code archivedBy}/{@code archived})
 * would have {@code update()}'s {@code newTagList} default to empty and remove every tag row via
 * {@code ItemTagRepository.removeItemTagId}. {@code EditItemFormWidget.post()} was the only call
 * site that avoided this, because it explicitly rebuilds {@code tagIdList} from the submitted form
 * before saving.
 * </p>
 *
 * <p>Also verifies the N+1 fix for {@code query()}'s multi-row listing path (the Records tab of a
 * Collection at /admin/collection-records): {@link ItemCategoryRepository#findAllByItemIds} and
 * {@link ItemTagRepository#findAllByItemIds} each group their rows by item id in one batch query,
 * and {@link ItemRepository#findAll} attaches the same categoryIdList/tagIdList per item that the
 * old per-row {@code buildRecord()} lookups would have produced.</p>
 *
 * <p>Integration test: starts a throwaway PostgreSQL container (Testcontainers) and is skipped
 * automatically when Docker is not available.</p>
 *
 * @author SimIS Inc.
 */
class ItemRepositoryTagPersistenceTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping ItemRepository tag persistence integration test");

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
      statement.execute("TRUNCATE TABLE item_tags, item_categories, items, collections RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset tables", se);
    }
  }

  @Test
  void findByIdPopulatesTagIdListJustLikeCategoryIdList() {
    long collectionId = addCollection();
    long itemId = insertItem(collectionId, "Tagged Item");
    linkTag(itemId, 10, collectionId);
    linkTag(itemId, 20, collectionId);

    Item item = ItemRepository.findById(itemId);

    assertNotNull(item);
    assertNotNull(item.getTagIdList(), "buildRecord must populate tagIdList, mirroring categoryIdList");
    assertEquals(Set.of(10L, 20L), toSet(item.getTagIdList()));
  }

  @Test
  void archivingAnItemLoadedViaFindByIdDoesNotRemoveItsTags() {
    // Reproduces PageServlet#deactivateCollectionItem's exact call sequence: load via findById(),
    // set only archivedBy/archived, then save() -- never touching tagIdList.
    long collectionId = addCollection();
    long itemId = insertItem(collectionId, "Item To Archive");
    linkTag(itemId, 10, collectionId);
    linkTag(itemId, 20, collectionId);

    Item item = ItemRepository.findById(itemId);
    item.setArchivedBy(1L);
    item.setArchived(new Timestamp(System.currentTimeMillis()));
    Item saved = ItemRepository.save(item);

    assertNotNull(saved, "the archive save itself must succeed");
    Item reloaded = ItemRepository.findById(itemId);
    assertEquals(Set.of(10L, 20L), toSet(reloaded.getTagIdList()),
        "archiving an item must not silently strip its existing tags");
    assertEquals(2, ItemTagRepository.findAllByItemId(itemId).size(),
        "the underlying item_tags rows must survive the save, not just the in-memory record");
  }

  @Test
  void savingAnItemLoadedViaFindByIdPreservesTagsAcrossUnrelatedFieldChanges() {
    // A second, more ordinary caller shape than the archive path above -- any save() that starts
    // from findById() and only touches unrelated fields must be equally safe.
    long collectionId = addCollection();
    long itemId = insertItem(collectionId, "Item To Rename");
    linkTag(itemId, 10, collectionId);

    Item item = ItemRepository.findById(itemId);
    item.setName("Renamed Item");
    ItemRepository.save(item);

    Item reloaded = ItemRepository.findById(itemId);
    assertEquals("Renamed Item", reloaded.getName(), "sanity check that the update actually applied");
    assertTrue(toSet(reloaded.getTagIdList()).contains(10L), "an unrelated field update must not remove existing tags");
  }

  @Test
  void findAllByItemIdsGroupsCategoriesByItemIdIncludingOneWithNone() {
    long collectionId = addCollection();
    long itemWithOneCategory = insertItem(collectionId, "One Category");
    linkCategory(itemWithOneCategory, 10, collectionId);
    long itemWithTwoCategories = insertItem(collectionId, "Two Categories");
    linkCategory(itemWithTwoCategories, 20, collectionId);
    linkCategory(itemWithTwoCategories, 30, collectionId);
    long itemWithNoCategories = insertItem(collectionId, "No Categories");

    Map<Long, List<ItemCategory>> categoriesByItemId = ItemCategoryRepository.findAllByItemIds(
        List.of(itemWithOneCategory, itemWithTwoCategories, itemWithNoCategories));

    assertEquals(Set.of(10L), categoryIdsOf(categoriesByItemId.get(itemWithOneCategory)));
    assertEquals(Set.of(20L, 30L), categoryIdsOf(categoriesByItemId.get(itemWithTwoCategories)));
    assertFalse(categoriesByItemId.containsKey(itemWithNoCategories),
        "an item with no categories must be absent from the map, not present with an empty list");
  }

  @Test
  void findAllByItemIdsGroupsTagsByItemIdIncludingOneWithNone() {
    // Mirrors findAllByItemIdsGroupsCategoriesByItemIdIncludingOneWithNone above exactly, one
    // association type over.
    long collectionId = addCollection();
    long itemWithOneTag = insertItem(collectionId, "One Tag");
    linkTag(itemWithOneTag, 10, collectionId);
    long itemWithTwoTags = insertItem(collectionId, "Two Tags");
    linkTag(itemWithTwoTags, 20, collectionId);
    linkTag(itemWithTwoTags, 30, collectionId);
    long itemWithNoTags = insertItem(collectionId, "No Tags");

    Map<Long, List<ItemTag>> tagsByItemId = ItemTagRepository.findAllByItemIds(
        List.of(itemWithOneTag, itemWithTwoTags, itemWithNoTags));

    assertEquals(Set.of(10L), tagIdsOf(tagsByItemId.get(itemWithOneTag)));
    assertEquals(Set.of(20L, 30L), tagIdsOf(tagsByItemId.get(itemWithTwoTags)));
    assertFalse(tagsByItemId.containsKey(itemWithNoTags),
        "an item with no tags must be absent from the map, not present with an empty list");
  }

  @Test
  void findAllPopulatesCategoryAndTagIdListsPerItemViaTheBatchedPath() {
    // Proves the N+1 fix -- query()'s multi-row listing path now builds bare records and
    // batch-attaches categoryIdList/tagIdList for the whole page (via findAllByItemIds) instead of
    // buildRecord()'s two per-row queries -- doesn't change what ends up on each Item: same
    // categoryIdList/tagIdList per item as before, including an item with neither.
    long collectionId = addCollection();
    long itemA = insertItem(collectionId, "Item A");
    linkCategory(itemA, 10, collectionId);
    linkTag(itemA, 100, collectionId);
    long itemB = insertItem(collectionId, "Item B");
    linkCategory(itemB, 20, collectionId);
    linkCategory(itemB, 30, collectionId);
    long itemC = insertItem(collectionId, "Item C"); // no categories or tags

    ItemSpecification specification = new ItemSpecification();
    specification.setCollectionId(collectionId);

    // The regression this test exists to catch: query() silently reverting to the old per-row
    // buildRecord() path (one ItemCategoryRepository/ItemTagRepository query per item) instead of
    // the batched findAllByItemIds() call. Wrapping the real repositories with CALLS_REAL_METHODS
    // still executes every real query against the test database -- this only counts invocations.
    List<Item> items;
    try (MockedStatic<ItemCategoryRepository> categoryRepository = mockStatic(ItemCategoryRepository.class,
        CALLS_REAL_METHODS);
        MockedStatic<ItemTagRepository> tagRepository = mockStatic(ItemTagRepository.class, CALLS_REAL_METHODS)) {
      items = ItemRepository.findAll(specification, null);

      categoryRepository.verify(() -> ItemCategoryRepository.findAllByItemIds(anyList()), times(1));
      tagRepository.verify(() -> ItemTagRepository.findAllByItemIds(anyList()), times(1));
      categoryRepository.verify(() -> ItemCategoryRepository.findAllByItemId(anyLong()), never());
      tagRepository.verify(() -> ItemTagRepository.findAllByItemId(anyLong()), never());
    }

    Map<Long, Item> itemsById = items.stream().collect(Collectors.toMap(Item::getId, item -> item));
    assertEquals(Set.of(10L), toSet(itemsById.get(itemA).getCategoryIdList()));
    assertEquals(Set.of(100L), toSet(itemsById.get(itemA).getTagIdList()));
    assertEquals(Set.of(20L, 30L), toSet(itemsById.get(itemB).getCategoryIdList()));
    assertEquals(Set.of(), toSet(itemsById.get(itemB).getTagIdList()));
    assertEquals(Set.of(), toSet(itemsById.get(itemC).getCategoryIdList()),
        "an item with no categories must still get a (non-null, empty) categoryIdList, matching buildRecord()'s prior per-row behavior");
    assertEquals(Set.of(), toSet(itemsById.get(itemC).getTagIdList()),
        "an item with no tags must still get a (non-null, empty) tagIdList, matching buildRecord()'s prior per-row behavior");
  }

  private static Set<Long> categoryIdsOf(List<ItemCategory> categoryList) {
    return categoryList.stream().map(ItemCategory::getCategoryId).collect(Collectors.toSet());
  }

  private static Set<Long> tagIdsOf(List<ItemTag> tagList) {
    return tagList.stream().map(ItemTag::getTagId).collect(Collectors.toSet());
  }

  private static Set<Long> toSet(Long[] tagIdList) {
    return Set.copyOf(List.of(tagIdList));
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
      statement.execute("DROP TABLE IF EXISTS item_categories CASCADE");
      statement.execute("DROP TABLE IF EXISTS items CASCADE");
      statement.execute("DROP TABLE IF EXISTS collections CASCADE");
      statement.execute("CREATE TABLE collections (collection_id BIGSERIAL PRIMARY KEY)");
      // Full column set ItemRepository.buildRecord()/add()/update() touch, minus geometry/tsv
      // (nothing under test exercises a geo or keyword-search path) -- same convention as
      // ItemRepositoryReorderTest and SaveItemCommandTest.
      statement.execute("CREATE TABLE items ("
          + "item_id BIGSERIAL PRIMARY KEY, "
          + "collection_id BIGINT NOT NULL, "
          + "item_order INTEGER DEFAULT 100, "
          + "category_id BIGINT DEFAULT -1, "
          + "dataset_id BIGINT DEFAULT -1, "
          + "unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "name VARCHAR(255) NOT NULL, "
          + "summary TEXT, "
          + "description TEXT, "
          + "description_text TEXT, "
          + "created_by BIGINT NOT NULL, "
          + "created TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT NOT NULL, "
          + "modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
          + "archived_by BIGINT DEFAULT -1, "
          + "archived TIMESTAMP DEFAULT NULL, "
          + "location_name VARCHAR(255), "
          + "street VARCHAR(100), "
          + "address_line_2 VARCHAR(100), "
          + "address_line_3 VARCHAR(100), "
          + "city VARCHAR(100), "
          + "state VARCHAR(100), "
          + "country VARCHAR(100), "
          + "postal_code VARCHAR(100), "
          + "county VARCHAR(100), "
          + "phone_number VARCHAR(30), "
          + "email VARCHAR(255), "
          + "cost NUMERIC(15,6) DEFAULT 0, "
          + "expected_date TIMESTAMP DEFAULT NULL, "
          + "start_date TIMESTAMP DEFAULT NULL, "
          + "end_date TIMESTAMP DEFAULT NULL, "
          + "expiration_date TIMESTAMP DEFAULT NULL, "
          + "url VARCHAR(255), "
          + "url_text VARCHAR(50), "
          + "image_url VARCHAR(255), "
          + "barcode VARCHAR(1024), "
          + "keywords VARCHAR(255), "
          + "assigned_to BIGINT DEFAULT -1, "
          + "assigned TIMESTAMP DEFAULT NULL, "
          + "approved_by BIGINT DEFAULT -1, "
          + "approved TIMESTAMP DEFAULT NULL, "
          + "source VARCHAR(255), "
          + "sync_date TIMESTAMP, "
          + "dataset_key_value VARCHAR(255), "
          + "field_values JSONB, "
          + "latitude DOUBLE PRECISION DEFAULT 0, "
          + "longitude DOUBLE PRECISION DEFAULT 0, "
          + "geom TEXT)");
      statement.execute("CREATE TABLE item_categories ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "item_id BIGINT NOT NULL, "
          + "category_id BIGINT NOT NULL, "
          + "collection_id BIGINT NOT NULL, "
          + "dataset_id BIGINT)");
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

  private static long insertItem(long collectionId, String name) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO items (collection_id, unique_id, name, created_by, modified_by) "
                + "VALUES (?, ?, ?, 1, 1) RETURNING item_id")) {
      pst.setLong(1, collectionId);
      pst.setString(2, "item-" + collectionId + "-" + System.nanoTime());
      pst.setString(3, name);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert an item", se);
    }
  }

  private static void linkTag(long itemId, long tagId, long collectionId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO item_tags (item_id, tag_id, collection_id) VALUES (?, ?, ?)")) {
      pst.setLong(1, itemId);
      pst.setLong(2, tagId);
      pst.setLong(3, collectionId);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not link a tag", se);
    }
  }

  private static void linkCategory(long itemId, long categoryId, long collectionId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO item_categories (item_id, category_id, collection_id) VALUES (?, ?, ?)")) {
      pst.setLong(1, itemId);
      pst.setLong(2, categoryId);
      pst.setLong(3, collectionId);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not link a category", se);
    }
  }
}
