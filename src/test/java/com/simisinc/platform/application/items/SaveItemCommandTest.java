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

package com.simisinc.platform.application.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
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

import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;

/**
 * Regression test for a bug found live-verifying issue #815 in Docker: {@link
 * SaveItemCommand#saveItem} builds a brand-new {@code Item} for inserts and copies over a fixed
 * field list (uniqueId, categoryId, name, summary, ... "these values can be set on insert, but
 * not update") -- itemOrder was missing from that list, so {@code
 * PageServlet#saveCollectionItem}'s {@code newItem.setItemOrder(ItemRepository.getNextItemOrder(
 * collectionId))} call was silently discarded and every new item landed at the Item domain
 * model's static default (100) instead of appending after the collection's existing items.
 * Live-confirmed via a real POST to a running instance before this fix, and again after.
 *
 * <p>Integration test: starts a throwaway PostgreSQL container (Testcontainers) and is skipped
 * automatically when Docker is not available. The schema is fuller than {@link
 * com.simisinc.platform.infrastructure.persistence.items.ItemRepositoryTest}'s or {@link
 * com.simisinc.platform.infrastructure.persistence.items.ItemRepositoryReorderTest}'s: saveItem()
 * exercises the real {@code ItemRepository.add()} path end to end, including its
 * collection/category item-count side effects and cache invalidation, so both tables need enough
 * columns for {@code CollectionRepository.buildRecord}/{@code CategoryRepository.updateItemCount}
 * to succeed rather than silently failing (and, for the collections cache-invalidation lookup,
 * poisoning the enclosing transaction).</p>
 *
 * @author SimIS Inc.
 */
class SaveItemCommandTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping SaveItemCommand integration test");

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
      statement.execute("TRUNCATE TABLE item_categories, item_tags, items, categories, collections RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset tables", se);
    }
  }

  @Test
  void saveItemCarriesTheCallerSuppliedItemOrderThroughOnInsert() throws Exception {
    long collectionId = addCollection();
    // Reproduces PageServlet#saveCollectionItem's exact call sequence for an already-reordered
    // collection: an existing item sits at item_order 5, so a correctly-appended new item must
    // land at 6, not fall back to the Item domain model's static default of 100.
    insertItemDirectly(collectionId, "Existing", 5);

    Item newItem = new Item();
    newItem.setCollectionId(collectionId);
    newItem.setItemOrder(ItemRepository.getNextItemOrder(collectionId));
    newItem.setName("New");
    newItem.setCreatedBy(1L);
    newItem.setModifiedBy(1L);

    Item saved = SaveItemCommand.saveItem(newItem);

    assertNotNull(saved);
    assertEquals(6, ItemRepository.findById(saved.getId()).getItemOrder(),
        "a new item saved through SaveItemCommand must persist the order the caller computed "
            + "(getNextItemOrder), not silently fall back to the domain model's static default");
  }

  @Test
  void saveItemDoesNotResetAnExistingItemsOrderOnUpdate() throws Exception {
    // The insert-only copy this test's sibling verifies must NOT also apply to updates -- an
    // update's `item` starts from the already-loaded DB record (see saveItem()'s `item =
    // ItemRepository.findById(...)` branch), so its real item_order must survive untouched even
    // though the itemBean passed in here carries the domain model's unrelated static default.
    long collectionId = addCollection();
    long itemId = insertItemDirectly(collectionId, "Manually Ordered", 42);

    Item itemBean = new Item();
    itemBean.setId(itemId);
    itemBean.setCollectionId(collectionId);
    itemBean.setName("Manually Ordered (renamed)");
    itemBean.setCreatedBy(1L);
    itemBean.setModifiedBy(1L);
    // ItemRepository.update() requires a non-null categoryIdList (a pre-existing precondition,
    // unrelated to #815); a real item-edit form always supplies one, even if empty.
    itemBean.setCategoryIdList(new Long[0]);
    // itemBean.getItemOrder() is left at the domain model's static default (100) -- an ordinary
    // item-edit form has no order field and would never set this.

    Item saved = SaveItemCommand.saveItem(itemBean);

    assertNotNull(saved, "the update itself must have succeeded, or this test would trivially "
        + "pass by having changed nothing at all");
    Item reloaded = ItemRepository.findById(itemId);
    assertEquals("Manually Ordered (renamed)", reloaded.getName(),
        "sanity check that the update actually applied");
    assertEquals(42, reloaded.getItemOrder(),
        "editing an item's other fields must not reset its manually-set item_order to the "
            + "domain model's static default");
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
      statement.execute("DROP TABLE IF EXISTS item_tags CASCADE");
      statement.execute("DROP TABLE IF EXISTS items CASCADE");
      statement.execute("DROP TABLE IF EXISTS categories CASCADE");
      statement.execute("DROP TABLE IF EXISTS collections CASCADE");
      // Full column set CollectionRepository.buildRecord() reads -- CollectionRepository.findById
      // (called at the top of saveItem()) and the item-count cache-invalidation lookup inside
      // ItemRepository.add() both need this to succeed, not silently return null/poison the
      // transaction.
      statement.execute("CREATE TABLE collections ("
          + "collection_id BIGSERIAL PRIMARY KEY, "
          + "name VARCHAR(255) DEFAULT 'Test Collection', "
          + "unique_id VARCHAR(255) UNIQUE, "
          + "description TEXT, "
          + "created_by BIGINT DEFAULT 1, "
          + "created TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
          + "category_count BIGINT DEFAULT 0, "
          + "item_count BIGINT DEFAULT 0, "
          + "has_allowed_groups BOOLEAN DEFAULT false, "
          + "allows_guests BOOLEAN DEFAULT true, "
          + "guest_privacy_type INTEGER DEFAULT 0, "
          + "listings_link VARCHAR(255), "
          + "image_url VARCHAR(255), "
          + "header_xml TEXT, "
          + "icon VARCHAR(20), "
          + "show_listings_link BOOLEAN DEFAULT true, "
          + "show_search BOOLEAN DEFAULT true, "
          + "header_text_color VARCHAR(20), "
          + "header_bg_color VARCHAR(20), "
          + "menu_text_color VARCHAR(20), "
          + "menu_bg_color VARCHAR(20), "
          + "menu_border_color VARCHAR(20), "
          + "menu_active_text_color VARCHAR(20), "
          + "menu_active_bg_color VARCHAR(20), "
          + "menu_active_border_color VARCHAR(20), "
          + "menu_hover_text_color VARCHAR(20), "
          + "menu_hover_bg_color VARCHAR(20), "
          + "menu_hover_border_color VARCHAR(20), "
          + "field_values JSONB, "
          + "item_url_text VARCHAR(50))");
      statement.execute("CREATE TABLE categories (category_id BIGSERIAL PRIMARY KEY, item_count INTEGER DEFAULT 0)");
      // Full column set ItemRepository.buildRecord()/add() touch, minus geometry/tsv (nothing
      // under test exercises a geo or keyword-search path). generateUniqueId() (called inside
      // saveItem()) does a real ItemRepository.findByUniqueId() lookup, so this must be complete
      // enough for buildRecord to succeed, same as ItemRepositoryReorderTest's schema.
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
          // Placeholder, not real PostGIS geometry (this image has no PostGIS extension): an
          // update with no geo point generates "SET geom = NULL" (see SqlValue's GEOM_TYPE
          // branch), which only needs the column to exist, not real geometry semantics -- same
          // convention ItemRepositoryTest uses.
          + "geom TEXT)");
      statement.execute("CREATE TABLE item_categories ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "item_id BIGINT NOT NULL, "
          + "category_id BIGINT NOT NULL, "
          + "collection_id BIGINT NOT NULL, "
          + "dataset_id BIGINT)");
      // ItemRepository.update() unconditionally queries item_tags too (issue #632's tag
      // reconciliation, mirroring the item_categories reconciliation above).
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
            "INSERT INTO collections (unique_id) VALUES (?) RETURNING collection_id")) {
      pst.setString(1, "collection-" + System.nanoTime());
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a collection", se);
    }
  }

  private static long insertItemDirectly(long collectionId, String name, int itemOrder) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO items (collection_id, item_order, unique_id, name, created_by, modified_by) "
                + "VALUES (?, ?, ?, ?, 1, 1) RETURNING item_id")) {
      pst.setLong(1, collectionId);
      pst.setInt(2, itemOrder);
      pst.setString(3, "item-" + collectionId + "-" + System.nanoTime());
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
