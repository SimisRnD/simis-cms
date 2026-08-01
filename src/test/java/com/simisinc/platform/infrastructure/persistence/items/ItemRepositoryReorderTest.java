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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies issue #815's persistence layer against a real PostgreSQL instance:
 * {@link ItemRepository#reorderItem} actually persists a new order (not just returning success),
 * {@link ItemRepository#findAll} respects item_order (falling back to name for ties, so a
 * collection that has never been manually reordered is unaffected), and
 * {@link ItemRepository#getNextItemOrder} appends new items after existing ones instead of
 * landing at the domain model's static default.
 *
 * <p>
 * The schema here mirrors the real {@code items}/{@code collections}/{@code item_categories}
 * tables closely enough for {@link ItemRepository#buildRecord} and {@code query()} to run for
 * real (unlike {@link ItemRepositoryTest}'s narrower schema, which only needs to support
 * {@code DB.selectFunction} facet counts, not full record building) -- geometry/full-text-search
 * columns are omitted since nothing under test here exercises a geo or keyword-search code path.
 * </p>
 *
 * <p>Integration test: starts a throwaway PostgreSQL container (Testcontainers) and is skipped
 * automatically when Docker is not available.</p>
 *
 * @author SimIS Inc.
 */
class ItemRepositoryReorderTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping ItemRepository reorder integration test");

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
      statement.execute("TRUNCATE TABLE item_categories, items, collections RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset tables", se);
    }
  }

  @Test
  void findAllSortsByItemOrderNotJustName() {
    long collectionId = addCollection();
    long zebra = insertItem(collectionId, "Zebra", 1);
    long apple = insertItem(collectionId, "Apple", 2);
    long mango = insertItem(collectionId, "Mango", 3);

    List<Item> items = findAllForCollection(collectionId);

    assertEquals(List.of(zebra, apple, mango), idsOf(items),
        "findAll must sort by item_order, not alphabetically, once a collection has explicit "
            + "order values -- otherwise a manual reorder would never be visible");
  }

  @Test
  void findAllFallsBackToNameWhenOrderIsTied() {
    // Mirrors every item created before item_order existed (or before a collection's first
    // reorder): they all share the Item domain model's static default (100), so this is the
    // exact tie shape a freshly-migrated, never-reordered collection is in.
    long collectionId = addCollection();
    long zebra = insertItem(collectionId, "Zebra", 100);
    long apple = insertItem(collectionId, "Apple", 100);
    long mango = insertItem(collectionId, "Mango", 100);

    List<Item> items = findAllForCollection(collectionId);

    assertEquals(List.of(apple, mango, zebra), idsOf(items),
        "a collection that has never been manually reordered (tied item_order) must still render "
            + "alphabetically -- no regression to the pre-#815 default sort");
  }

  @Test
  void reorderItemActuallyPersists() {
    long collectionId = addCollection();
    long a = insertItem(collectionId, "A", 1);
    long b = insertItem(collectionId, "B", 2);
    long c = insertItem(collectionId, "C", 3);

    boolean result = ItemRepository.reorderItem(collectionId, c, 1);

    assertTrue(result, "reorderItem should report success for an item that belongs to the collection");
    // Re-read through the real query path (not the ids used to reorder) to prove this is a
    // genuine, separately-verifiable persisted write, not just a returned boolean.
    List<Item> items = findAllForCollection(collectionId);
    assertEquals(List.of(c, a, b), idsOf(items),
        "moving C to position 1 should push A and B down, preserving their relative order");
  }

  @Test
  void reorderItemPersistsAcrossASeparateFindAllCall() {
    long collectionId = addCollection();
    long a = insertItem(collectionId, "A", 1);
    long b = insertItem(collectionId, "B", 2);

    ItemRepository.reorderItem(collectionId, a, 2);

    // A second, independent call -- simulates a page reload -- must see the same result as the
    // first, proving the reorder was committed rather than only visible within the same
    // connection/transaction.
    assertEquals(List.of(b, a), idsOf(findAllForCollection(collectionId)));
    assertEquals(List.of(b, a), idsOf(findAllForCollection(collectionId)));
  }

  @Test
  void reorderItemRenumbersEveryItemSequentially() {
    long collectionId = addCollection();
    long a = insertItem(collectionId, "A", 5);
    long b = insertItem(collectionId, "B", 12);
    long c = insertItem(collectionId, "C", 47);

    ItemRepository.reorderItem(collectionId, b, 1);

    assertEquals(List.of(1, 2, 3), itemOrdersOf(List.of(b, a, c)),
        "item_order should be renumbered gap-free (1..N), not just have the moved item's old "
            + "value swapped in");
  }

  @Test
  void reorderItemClampsAPositionBeyondTheCollectionSize() {
    long collectionId = addCollection();
    long a = insertItem(collectionId, "A", 1);
    long b = insertItem(collectionId, "B", 2);

    boolean result = ItemRepository.reorderItem(collectionId, a, 999);

    assertTrue(result);
    assertEquals(List.of(b, a), idsOf(findAllForCollection(collectionId)),
        "an out-of-range target position should move the item to the end, not fail or corrupt order");
  }

  @Test
  void reorderItemClampsAZeroOrNegativePosition() {
    long collectionId = addCollection();
    long a = insertItem(collectionId, "A", 1);
    long b = insertItem(collectionId, "B", 2);

    boolean result = ItemRepository.reorderItem(collectionId, b, 0);

    assertTrue(result);
    assertEquals(List.of(b, a), idsOf(findAllForCollection(collectionId)),
        "a zero/negative target position should clamp to the front, not fail or corrupt order");
  }

  @Test
  void reorderItemReturnsFalseForAnItemNotInTheCollection() {
    long collectionId = addCollection();
    long otherCollectionId = addCollection();
    long a = insertItem(collectionId, "A", 1);
    long outsider = insertItem(otherCollectionId, "Outsider", 1);

    boolean result = ItemRepository.reorderItem(collectionId, outsider, 1);

    assertFalse(result, "an item that belongs to a different collection must not be reorderable into this one");
    // The real collection's own order must be untouched by the rejected attempt.
    assertEquals(List.of(a), idsOf(findAllForCollection(collectionId)));
  }

  @Test
  void reorderItemDoesNotAffectAnotherCollectionsOrder() {
    long collectionId = addCollection();
    long otherCollectionId = addCollection();
    long a = insertItem(collectionId, "A", 1);
    long b = insertItem(collectionId, "B", 2);
    long x = insertItem(otherCollectionId, "X", 1);
    long y = insertItem(otherCollectionId, "Y", 2);

    ItemRepository.reorderItem(collectionId, b, 1);

    assertEquals(List.of(x, y), idsOf(findAllForCollection(otherCollectionId)),
        "reordering one collection must not renumber a different collection's items");
  }

  @Test
  void getNextItemOrderIsOneForAnEmptyCollection() {
    long collectionId = addCollection();
    assertEquals(1, ItemRepository.getNextItemOrder(collectionId));
  }

  @Test
  void getNextItemOrderAppendsAfterTheCurrentMax() {
    long collectionId = addCollection();
    insertItem(collectionId, "A", 1);
    insertItem(collectionId, "B", 7);

    assertEquals(8, ItemRepository.getNextItemOrder(collectionId));
  }

  @Test
  void newlyAddedItemAppendsAfterManuallyOrderedItemsRatherThanUsingTheStaticDefault() {
    // Reproduces the real saveCollectionItem flow (PageServlet calls getNextItemOrder before
    // creating a new item, the same convention SaveMenuTabCommand already uses for MenuItem):
    // an existing, already-reordered collection (item_order values above the Item domain model's
    // static default of 100) gets a new item. Without explicitly calling getNextItemOrder, the
    // new item would silently sort ahead of these because 100 < 150.
    long collectionId = addCollection();
    long existing = insertItem(collectionId, "Existing", 150);

    int nextOrder = ItemRepository.getNextItemOrder(collectionId);
    long newItemId = insertItem(collectionId, "New", nextOrder);

    assertEquals(List.of(existing, newItemId), idsOf(findAllForCollection(collectionId)),
        "a freshly-added item must append after existing manually-ordered items");
  }

  @Test
  void findByIdReflectsAPersistedReorder() {
    // A different read path than findAll (which the other reorder tests already cover): confirms
    // reorderItem's write is visible to a plain single-record lookup too, and specifically that
    // ItemRepository.buildRecord() (which the #815 change added an item_order read to) reports
    // the new value, not the row's original one.
    long collectionId = addCollection();
    long a = insertItem(collectionId, "A", 1);
    long b = insertItem(collectionId, "B", 2);

    ItemRepository.reorderItem(collectionId, b, 1);

    assertEquals(1, ItemRepository.findById(b).getItemOrder());
    assertEquals(2, ItemRepository.findById(a).getItemOrder());
  }

  private static List<Item> findAllForCollection(long collectionId) {
    ItemSpecification specification = new ItemSpecification();
    specification.setCollectionId(collectionId);
    return ItemRepository.findAll(specification, null);
  }

  private static List<Long> idsOf(List<Item> items) {
    return items.stream().map(Item::getId).collect(Collectors.toList());
  }

  private static List<Integer> itemOrdersOf(List<Long> itemIdsInExpectedOrder) {
    return itemIdsInExpectedOrder.stream()
        .map(id -> ItemRepository.findById(id).getItemOrder())
        .collect(Collectors.toList());
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
      statement.execute("DROP TABLE IF EXISTS item_categories CASCADE");
      statement.execute("DROP TABLE IF EXISTS items CASCADE");
      statement.execute("DROP TABLE IF EXISTS collections CASCADE");
      statement.execute("CREATE TABLE collections ("
          + "collection_id BIGSERIAL PRIMARY KEY, "
          + "item_count INTEGER DEFAULT 0, "
          + "allows_guests BOOLEAN DEFAULT true, "
          + "has_allowed_groups BOOLEAN DEFAULT false)");
      // Full column set ItemRepository.buildRecord()/add()/update() touch, minus geometry/tsv
      // (nothing under test exercises a geo or keyword-search path).
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
          + "longitude DOUBLE PRECISION DEFAULT 0)");
      statement.execute("CREATE TABLE item_categories ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "item_id BIGINT NOT NULL, "
          + "category_id BIGINT NOT NULL, "
          + "collection_id BIGINT NOT NULL, "
          + "dataset_id BIGINT)");
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

  /** Inserts an item directly via SQL (bypassing ItemRepository) with a specific item_order. */
  private static long insertItem(long collectionId, String name, int itemOrder) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO items (collection_id, item_order, unique_id, name, created_by, modified_by) "
                + "VALUES (?, ?, ?, ?, 1, 1) RETURNING item_id")) {
      pst.setLong(1, collectionId);
      pst.setInt(2, itemOrder);
      pst.setString(3, "item-" + collectionId + "-" + name.toLowerCase().replace(" ", "-") + "-" + System.nanoTime());
      pst.setString(4, name);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert an item", se);
    }
  }
}
