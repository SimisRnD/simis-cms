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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Arrays;
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
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Verifies {@link ItemRepository#countByCategory} and {@link ItemRepository#countByDateRange}
 * (issue #421's facet counts, generalized to multi-select category by issue #636) against a real
 * PostgreSQL instance, since the EXISTS subquery against item_categories and the collections LEFT
 * JOIN cannot be exercised meaningfully with a mock. The schema here is a minimal subset covering
 * only what these two methods' shared WHERE clause (ItemRepository.createSearchWhereStatement)
 * touches -- items, collections, item_categories -- not the full production items table, since
 * these methods use DB.selectFunction (a scalar COUNT) rather than ItemRepository.buildRecord.
 *
 * <p>Integration test: starts a throwaway PostgreSQL container (Testcontainers) and is skipped
 * automatically when Docker is not available.</p>
 *
 * @author SimIS Inc.
 */
class ItemRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping ItemRepository integration test");

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
  void countByCategoryCountsOnlyItemsInThatCategory() {
    long collectionId = addCollection();
    long categoryA = 10;
    long categoryB = 20;
    addItem(collectionId, null, Timestamp.valueOf("2026-01-01 00:00:00"));
    long itemInA = addItem(collectionId, null, Timestamp.valueOf("2026-01-02 00:00:00"));
    linkCategory(itemInA, categoryA, collectionId);
    long itemInB = addItem(collectionId, null, Timestamp.valueOf("2026-01-03 00:00:00"));
    linkCategory(itemInB, categoryB, collectionId);

    ItemSpecification specification = new ItemSpecification();
    assertEquals(1, ItemRepository.countByCategory(specification, categoryA));
    assertEquals(1, ItemRepository.countByCategory(specification, categoryB));
    assertEquals(0, ItemRepository.countByCategory(specification, 999));
  }

  @Test
  void countByCategoryRespectsApprovedOnly() {
    long collectionId = addCollection();
    long categoryA = 10;
    long approvedItem = addItem(collectionId, Timestamp.valueOf("2026-01-01 00:00:00"), Timestamp.valueOf("2026-01-01 00:00:00"));
    linkCategory(approvedItem, categoryA, collectionId);
    long unapprovedItem = addItem(collectionId, null, Timestamp.valueOf("2026-01-01 00:00:00"));
    linkCategory(unapprovedItem, categoryA, collectionId);

    ItemSpecification specification = new ItemSpecification();
    specification.setApprovedOnly(true);
    assertEquals(1, ItemRepository.countByCategory(specification, categoryA));
  }

  @Test
  void countByCategoryUnionsTheSpecificationsCurrentSelectionWithTheCandidate() {
    // Issue #636 generalizes countByCategory's semantics from #421's single-select "ignore the
    // specification's own categoryId, count only the candidate" to "count the candidate PLUS
    // whatever the specification already has selected" (OR-within-dimension), so an unchecked
    // facet's displayed count reflects "what if this were ALSO selected" rather than "as if this
    // were the only one selected". Prove the union with a case where the specification's own
    // preselected category genuinely has matching items -- under the old semantics this would be
    // 1 (categoryA alone); the union is 2 (categoryA plus the already-selected categoryB).
    long collectionId = addCollection();
    long categoryA = 10;
    long categoryB = 20;
    long itemInA = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"));
    linkCategory(itemInA, categoryA, collectionId);
    long itemInB = addItem(collectionId, null, Timestamp.valueOf("2026-06-16 00:00:00"));
    linkCategory(itemInB, categoryB, collectionId);

    ItemSpecification specification = new ItemSpecification();
    specification.setCategoryId(categoryB); // already selected, via the legacy single-value field

    assertEquals(2, ItemRepository.countByCategory(specification, categoryA),
        "the candidate (categoryA) must be unioned with what's already selected (categoryB), not replace it");
  }

  @Test
  void countByCategoryStillAppliesTheDateRangeToTheUnion() {
    long collectionId = addCollection();
    long categoryA = 10;
    long inRange = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"));
    linkCategory(inRange, categoryA, collectionId);
    long outOfRange = addItem(collectionId, null, Timestamp.valueOf("2026-01-01 00:00:00"));
    linkCategory(outOfRange, categoryA, collectionId);

    ItemSpecification specification = new ItemSpecification();
    specification.setDateRangeStart(Timestamp.valueOf("2026-06-01 00:00:00"));

    assertEquals(1, ItemRepository.countByCategory(specification, categoryA),
        "the date range must still narrow the candidate's count");
  }

  @Test
  void countByCategoryWithMultipleSelectedCategoriesOrsWithinTheDimension() {
    // A specification with 2+ categoryIds selected must OR them together -- any item in category A
    // OR category B matches, category C (not selected) does not. Passing one of the already-
    // selected ids back in as the "candidate" is a no-op union (it's already in the set), so this
    // yields exactly the combined multi-selection's count -- the same trick
    // ItemsSearchResultsWidget uses to compute its "combined selected count" for the UI.
    long collectionId = addCollection();
    long categoryA = 10;
    long categoryB = 20;
    long categoryC = 30;
    long itemInA = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"));
    linkCategory(itemInA, categoryA, collectionId);
    long itemInB = addItem(collectionId, null, Timestamp.valueOf("2026-06-16 00:00:00"));
    linkCategory(itemInB, categoryB, collectionId);
    long itemInC = addItem(collectionId, null, Timestamp.valueOf("2026-06-17 00:00:00"));
    linkCategory(itemInC, categoryC, collectionId);

    ItemSpecification specification = new ItemSpecification();
    specification.setCategoryIds(Arrays.asList(categoryA, categoryB));

    assertEquals(2, ItemRepository.countByCategory(specification, categoryA),
        "categories A and B are both selected (OR-within-dimension) -- C must not be counted");
  }

  @Test
  void countByCategoryWithMultipleSelectedCategoriesStillAndsWithAnActiveDateRange() {
    // AND-across-dimensions: the category OR-selection and the date range must combine with AND,
    // not each independently override the other.
    long collectionId = addCollection();
    long categoryA = 10;
    long categoryB = 20;
    long inRangeA = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"));
    linkCategory(inRangeA, categoryA, collectionId);
    long outOfRangeB = addItem(collectionId, null, Timestamp.valueOf("2026-01-01 00:00:00"));
    linkCategory(outOfRangeB, categoryB, collectionId);

    ItemSpecification specification = new ItemSpecification();
    specification.setCategoryIds(Arrays.asList(categoryA, categoryB));
    specification.setDateRangeStart(Timestamp.valueOf("2026-06-01 00:00:00"));

    assertEquals(1, ItemRepository.countByCategory(specification, categoryA),
        "categoryB matches the OR-selection but its item is outside the date range, so AND-across-dimensions must exclude it");
  }

  @Test
  void countByDateRangeCountsItemsWithinTheBounds() {
    long collectionId = addCollection();
    addItem(collectionId, null, Timestamp.valueOf("2026-03-01 00:00:00"));
    addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"));
    addItem(collectionId, null, Timestamp.valueOf("2026-12-01 00:00:00"));

    ItemSpecification specification = new ItemSpecification();
    long count = ItemRepository.countByDateRange(specification,
        Timestamp.valueOf("2026-06-01 00:00:00"), Timestamp.valueOf("2026-07-01 00:00:00"));

    assertEquals(1, count);
  }

  @Test
  void countByDateRangeTreatsANullEndAsOpenEnded() {
    long collectionId = addCollection();
    addItem(collectionId, null, Timestamp.valueOf("2026-06-01 00:00:00"));
    addItem(collectionId, null, Timestamp.valueOf("2026-12-01 00:00:00"));

    ItemSpecification specification = new ItemSpecification();
    long count = ItemRepository.countByDateRange(specification, Timestamp.valueOf("2026-06-01 00:00:00"), null);

    assertEquals(2, count);
  }

  @Test
  void countByDateRangeAppliesTheSpecificationsCategoryButIgnoresItsOwnDateRange() {
    long collectionId = addCollection();
    long categoryA = 10;
    long matchingItem = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"));
    linkCategory(matchingItem, categoryA, collectionId);
    long wrongCategoryItem = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"));
    linkCategory(wrongCategoryItem, 999, collectionId);

    ItemSpecification specification = new ItemSpecification();
    specification.setCategoryId(categoryA);
    specification.setDateRangeStart(Timestamp.valueOf("2020-01-01 00:00:00"));
    specification.setDateRangeEnd(Timestamp.valueOf("2020-02-01 00:00:00")); // must be ignored

    long count = ItemRepository.countByDateRange(specification,
        Timestamp.valueOf("2026-01-01 00:00:00"), Timestamp.valueOf("2027-01-01 00:00:00"));

    assertEquals(1, count,
        "the specification's own categoryId should still narrow the count, but its own date range must not");
  }

  @Test
  void countByCategoryExcludesItemsWithoutALocationWhenASearchLocationIsActive() {
    long collectionId = addCollection();
    long categoryA = 10;
    long withLocation = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"), true);
    linkCategory(withLocation, categoryA, collectionId);
    long withoutLocation = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"), false);
    linkCategory(withoutLocation, categoryA, collectionId);

    ItemSpecification specification = new ItemSpecification();
    specification.setSearchLocation("Boston, MA");

    long count = ItemRepository.countByCategory(specification, categoryA);

    assertEquals(1, count,
        "a facet count taken while a location search is active must exclude items with no location, "
            + "the same way the real query does -- otherwise the facet badge overstates what selecting it would return");
  }

  @Test
  void countByDateRangeExcludesItemsWithoutALocationWhenASearchLocationIsActive() {
    long collectionId = addCollection();
    addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"), true);
    addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"), false);

    ItemSpecification specification = new ItemSpecification();
    specification.setSearchLocation("Boston, MA");

    long count = ItemRepository.countByDateRange(specification,
        Timestamp.valueOf("2026-01-01 00:00:00"), Timestamp.valueOf("2027-01-01 00:00:00"));

    assertEquals(1, count, "same as countByCategory -- the location restriction must carry into date-facet counts too");
  }

  @Test
  void countByCategoryReturnsZeroForAGuestWhenTheCollectionDoesNotAllowGuests() {
    long collectionId = addPrivateCollection();
    long categoryA = 10;
    long item = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"));
    linkCategory(item, categoryA, collectionId);

    ItemSpecification specification = new ItemSpecification();
    specification.setForUserId(UserSession.GUEST_ID);

    long count = ItemRepository.countByCategory(specification, categoryA);

    assertEquals(0, count,
        "a facet count must apply the same guest access-control restriction as the real query -- "
            + "this is what stops an unauthenticated user from learning a private category has items via its count");
  }

  @Test
  void countByCategoryIsNonZeroForAGuestWhenTheCollectionAllowsGuests() {
    long collectionId = addCollection(); // addCollection() already sets allows_guests = true
    long categoryA = 10;
    long item = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"));
    linkCategory(item, categoryA, collectionId);

    ItemSpecification specification = new ItemSpecification();
    specification.setForUserId(UserSession.GUEST_ID);

    long count = ItemRepository.countByCategory(specification, categoryA);

    assertEquals(1, count, "a guest-visible collection's category count must still come through for a guest requester");
  }

  @Test
  void countByCategoryExcludesArchivedItemsByDefault() {
    // Issue #814: PageServlet's deactivateCollectionItem sets Item.archived, but nothing ever
    // filtered query results on it, so a deactivated item never actually left the collection's
    // listing. createSearchWhereStatement (shared by query() and the facet-count methods below)
    // is where that filter now lives; verified here through countByCategory since it exercises
    // the exact same WHERE-building path as a real listing query without needing the full items
    // column set that ItemRepository.buildRecord expects.
    long collectionId = addCollection();
    long categoryA = 10;
    long activeItem = addItem(collectionId, null, Timestamp.valueOf("2026-01-01 00:00:00"));
    linkCategory(activeItem, categoryA, collectionId);
    long archivedItem = addItem(collectionId, null, Timestamp.valueOf("2026-01-02 00:00:00"));
    linkCategory(archivedItem, categoryA, collectionId);
    archiveItem(archivedItem);

    ItemSpecification specification = new ItemSpecification();

    assertEquals(1, ItemRepository.countByCategory(specification, categoryA),
        "a deactivated item must not be counted by a normal (non-includeArchived) query");
  }

  @Test
  void countByCategoryIncludesArchivedItemsWhenIncludeArchivedIsSet() {
    long collectionId = addCollection();
    long categoryA = 10;
    long activeItem = addItem(collectionId, null, Timestamp.valueOf("2026-01-01 00:00:00"));
    linkCategory(activeItem, categoryA, collectionId);
    long archivedItem = addItem(collectionId, null, Timestamp.valueOf("2026-01-02 00:00:00"));
    linkCategory(archivedItem, categoryA, collectionId);
    archiveItem(archivedItem);

    ItemSpecification specification = new ItemSpecification();
    specification.setIncludeArchived(true);

    assertEquals(2, ItemRepository.countByCategory(specification, categoryA),
        "a caller that explicitly opts in (e.g. dataset cleanup, single-item access checks) must still see archived items");
  }

  @Test
  void countByDateRangeExcludesArchivedItemsByDefault() {
    long collectionId = addCollection();
    addItem(collectionId, null, Timestamp.valueOf("2026-06-01 00:00:00"));
    long archivedItem = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"));
    archiveItem(archivedItem);

    ItemSpecification specification = new ItemSpecification();
    long count = ItemRepository.countByDateRange(specification,
        Timestamp.valueOf("2026-01-01 00:00:00"), Timestamp.valueOf("2027-01-01 00:00:00"));

    assertEquals(1, count, "the archived-exclusion filter must apply to date-facet counts too, not just the main listing");
  }

  @Test
  void countByDateRangeIncludesArchivedItemsWhenIncludeArchivedIsSet() {
    long collectionId = addCollection();
    addItem(collectionId, null, Timestamp.valueOf("2026-06-01 00:00:00"));
    long archivedItem = addItem(collectionId, null, Timestamp.valueOf("2026-06-15 00:00:00"));
    archiveItem(archivedItem);

    ItemSpecification specification = new ItemSpecification();
    specification.setIncludeArchived(true);
    long count = ItemRepository.countByDateRange(specification,
        Timestamp.valueOf("2026-01-01 00:00:00"), Timestamp.valueOf("2027-01-01 00:00:00"));

    assertEquals(2, count, "facet counts must not drift from what a real includeArchived(true) listing query would return");
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
          + "allows_guests BOOLEAN DEFAULT false, "
          + "has_allowed_groups BOOLEAN DEFAULT false)");
      statement.execute("CREATE TABLE items ("
          + "item_id BIGSERIAL PRIMARY KEY, "
          + "collection_id BIGINT, "
          + "approved TIMESTAMP, "
          + "created TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
          + "archived TIMESTAMP, "
          + "geom TEXT)"); // placeholder column: only IS NOT NULL is exercised here, real
                           // geometry semantics (PostGIS) aren't available in the plain
                           // postgres:15-alpine test image
      statement.execute("CREATE TABLE item_categories ("
          + "item_id BIGINT, "
          + "category_id BIGINT, "
          + "collection_id BIGINT)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the test schema", se);
    }
  }

  private static long addCollection() {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO collections (allows_guests) VALUES (true) RETURNING collection_id")) {
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a collection", se);
    }
  }

  private static long addPrivateCollection() {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO collections (allows_guests) VALUES (false) RETURNING collection_id")) {
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert a private collection", se);
    }
  }

  private static long addItem(long collectionId, Timestamp approved, Timestamp created) {
    return addItem(collectionId, approved, created, false);
  }

  private static long addItem(long collectionId, Timestamp approved, Timestamp created, boolean hasLocation) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO items (collection_id, approved, created, geom) VALUES (?, ?, ?, ?) RETURNING item_id")) {
      pst.setLong(1, collectionId);
      pst.setTimestamp(2, approved);
      pst.setTimestamp(3, created);
      pst.setString(4, hasLocation ? "has-a-location" : null);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert an item", se);
    }
  }

  private static void archiveItem(long itemId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE items SET archived = ? WHERE item_id = ?")) {
      pst.setTimestamp(1, Timestamp.valueOf("2026-01-01 00:00:00"));
      pst.setLong(2, itemId);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not archive an item", se);
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
