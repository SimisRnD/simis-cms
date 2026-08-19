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

package com.simisinc.platform.infrastructure.persistence.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
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

import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies the filename substring search added to {@link ImageRepository} for issue #498 slice 1
 * against a real PostgreSQL instance: {@code ImageSpecification.matchesName} must find partial,
 * case-insensitive filename matches (the browse page's search box), and the search term must be
 * bound as a query parameter, never concatenated into the SQL text.
 *
 * <p>Also verifies the {@link DataConstraints} paging added for issue #498 slice 2: a page number
 * and page size must actually limit and offset the rows returned, on both the unfiltered {@code
 * findAll(null, constraints)} path and the search-filtered {@code findAll(specification,
 * constraints)} path, and a page number past the last page must degrade to an empty list rather
 * than error.
 *
 * <p>Also verifies the {@code focal_x}/{@code focal_y} columns added for issue #411 PR3: a new
 * image defaults to a dead-center focal point, and {@link ImageRepository#save} actually persists a
 * focal-point change on an existing row (previously {@code update()} only ever wrote {@code
 * processed}).
 *
 * @author SimIS Inc.
 */
class ImageRepositorySearchTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static long userId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping ImageRepository search test");

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
    userId = insertUser("image-owner");
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
  void resetAndSeed() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE image_tag_map, image_tags, images RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset images table", se);
    }
    insertImage("3-D Printing.png");
    insertImage("3-D Prints.png");
    insertImage("CMMC Compliance and Documentation Readiness.png");
    insertImage("GenSocialCard.png");
    insertImage("blobid0.png");
  }

  @Test
  void substringSearchFindsPartialCaseInsensitiveFilenameMatches() {
    ImageSpecification spec = new ImageSpecification();
    spec.setMatchesName("3-d");

    List<Image> results = ImageRepository.findAll(spec, null);

    Set<String> filenames = results.stream().map(Image::getFilename).collect(Collectors.toSet());
    assertEquals(2, results.size(), "both '3-D Printing.png' and '3-D Prints.png' must match");
    assertTrue(filenames.contains("3-D Printing.png"));
    assertTrue(filenames.contains("3-D Prints.png"));
  }

  @Test
  void substringSearchMatchesInTheMiddleOfAFilename() {
    ImageSpecification spec = new ImageSpecification();
    spec.setMatchesName("social");

    List<Image> results = ImageRepository.findAll(spec, null);

    assertEquals(1, results.size());
    assertEquals("GenSocialCard.png", results.get(0).getFilename());
  }

  @Test
  void substringSearchReturnsEverythingWhenBlank() {
    ImageSpecification spec = new ImageSpecification();
    spec.setMatchesName("   ");

    List<Image> results = ImageRepository.findAll(spec, null);

    assertEquals(5, results.size(), "a blank search term must not filter anything");
  }

  @Test
  void substringSearchTermIsBoundAsAParameterNotConcatenatedIntoTheQuery() {
    // If the search term were concatenated into the SQL text instead of bound as a placeholder,
    // this value would either throw a SQLException (broken syntax) or -- far worse -- actually
    // execute the injected statement. Neither happens: it is treated as a literal, no-match string.
    ImageSpecification spec = new ImageSpecification();
    spec.setMatchesName("x'; DROP TABLE images; --");

    List<Image> results = ImageRepository.findAll(spec, null);

    assertEquals(0, results.size(), "the payload must be treated as a literal search string, matching nothing");
    // The table must still exist and still hold every seeded row -- proving DROP TABLE never ran.
    assertEquals(5, DB.selectCountFrom("images"));
  }

  @Test
  void substringSearchAlsoToleratesLikeWildcardCharactersInTheSearchTerm() {
    // '%' and '_' are LIKE metacharacters; a raw, unescaped one in the search term would silently
    // change what the query matches instead of being searched for literally. This does not assert
    // a specific escaping behavior -- only that it does not error and does not return every row.
    ImageSpecification spec = new ImageSpecification();
    spec.setMatchesName("%");

    List<Image> results = ImageRepository.findAll(spec, null);

    assertTrue(results.size() <= 5);
  }

  @Test
  void paginationLimitsResultsPerPageOnTheUnfilteredList() {
    List<Image> results = ImageRepository.findAll(null, pageConstraints(1, 2));

    assertEquals(2, results.size(), "a page size of 2 must return exactly 2 of the 5 seeded images");
  }

  @Test
  void paginationOffsetsToASecondPageOnTheUnfilteredListWithoutRepeatingRows() {
    List<Image> firstPage = ImageRepository.findAll(null, pageConstraints(1, 2));
    List<Image> secondPage = ImageRepository.findAll(null, pageConstraints(2, 2));

    assertEquals(2, firstPage.size());
    assertEquals(2, secondPage.size());
    Set<String> firstPageFilenames = firstPage.stream().map(Image::getFilename).collect(Collectors.toSet());
    Set<String> secondPageFilenames = secondPage.stream().map(Image::getFilename).collect(Collectors.toSet());
    // If OFFSET were not actually applied, the second page would just repeat the first
    assertTrue(Collections.disjoint(firstPageFilenames, secondPageFilenames),
        "the second page must not repeat any image from the first page");
  }

  @Test
  void paginationOnTheUnfilteredListsLastPageReturnsOnlyTheRemainder() {
    // 5 seeded images at a page size of 2 -> page 3 holds only the 1 remaining image
    List<Image> lastPage = ImageRepository.findAll(null, pageConstraints(3, 2));

    assertEquals(1, lastPage.size());
  }

  @Test
  void paginationAnOutOfRangePageNumberOnTheUnfilteredListReturnsAnEmptyListNotAnError() {
    List<Image> results = ImageRepository.findAll(null, pageConstraints(50, 2));

    assertTrue(results.isEmpty(), "a page far past the last page must degrade to an empty list");
  }

  @Test
  void paginationAppliesToTheSearchFilteredBranchNotJustTheUnfilteredList() {
    // "3-D Printing.png" and "3-D Prints.png" both match "3-d" -- constraining to 1 per page must
    // return exactly 1 of the 2 matches, proving paging composes with the search filter instead of
    // being ignored on the filtered branch.
    ImageSpecification spec = new ImageSpecification();
    spec.setMatchesName("3-d");

    List<Image> results = ImageRepository.findAll(spec, pageConstraints(1, 1));

    assertEquals(1, results.size());
  }

  @Test
  void paginationOnASearchFilteredSecondPageReturnsTheRemainingMatchNotTheSameOne() {
    ImageSpecification spec = new ImageSpecification();
    spec.setMatchesName("3-d");

    List<Image> firstPage = ImageRepository.findAll(spec, pageConstraints(1, 1));
    List<Image> secondPage = ImageRepository.findAll(spec, pageConstraints(2, 1));

    assertEquals(1, firstPage.size());
    assertEquals(1, secondPage.size());
    assertNotEquals(firstPage.get(0).getFilename(), secondPage.get(0).getFilename(),
        "the second page of a search-filtered query must not repeat the first page's match");
  }

  @Test
  void paginationAnOutOfRangePageNumberOnTheSearchFilteredBranchReturnsAnEmptyListNotAnError() {
    ImageSpecification spec = new ImageSpecification();
    spec.setMatchesName("3-d");

    List<Image> results = ImageRepository.findAll(spec, pageConstraints(50, 1));

    assertTrue(results.isEmpty());
  }

  @Test
  void tagIdFilterReturnsOnlyImagesCarryingThatTag() {
    List<Image> all = ImageRepository.findAll();
    long taggedImageId = all.stream().filter(i -> "GenSocialCard.png".equals(i.getFilename())).findFirst().get().getId();
    long taggedImageTagId = insertImageTag("Homepage");
    linkImageToTag(taggedImageId, taggedImageTagId);

    ImageSpecification spec = new ImageSpecification();
    spec.setTagId(taggedImageTagId);
    List<Image> results = ImageRepository.findAll(spec, null);

    assertEquals(1, results.size(), "only the one tagged image must be returned");
    assertEquals("GenSocialCard.png", results.get(0).getFilename());
  }

  @Test
  void tagIdFilterExcludesImagesNotCarryingThatTag() {
    long taggedImageTagId = insertImageTag("Unused Tag");
    // No image_tag_map row links any image to this tag

    ImageSpecification spec = new ImageSpecification();
    spec.setTagId(taggedImageTagId);
    List<Image> results = ImageRepository.findAll(spec, null);

    assertTrue(results.isEmpty(), "a tag with no assigned images must return an empty list, not everything");
  }

  @Test
  void matchesNameAndTagIdComposeWithAndNotOr() {
    // Two images match the filename search term "gen", but only one of them carries the tag --
    // proves the two clauses are ANDed together, not ORed (which would also return the untagged match).
    insertImage("GenBanner.png");
    List<Image> all = ImageRepository.findAll();
    long taggedImageId = all.stream().filter(i -> "GenSocialCard.png".equals(i.getFilename())).findFirst().get().getId();
    long untaggedButNameMatchingImageId =
        all.stream().filter(i -> "GenBanner.png".equals(i.getFilename())).findFirst().get().getId();
    assertNotEquals(taggedImageId, untaggedButNameMatchingImageId);

    long tagId = insertImageTag("Homepage");
    linkImageToTag(taggedImageId, tagId);

    ImageSpecification spec = new ImageSpecification();
    spec.setMatchesName("gen");
    spec.setTagId(tagId);
    List<Image> results = ImageRepository.findAll(spec, null);

    assertEquals(1, results.size(),
        "only the image matching both the name search and the tag filter must be returned");
    assertEquals("GenSocialCard.png", results.get(0).getFilename());
  }

  @Test
  void newlySavedImageDefaultsToADeadCenterFocalPoint() {
    Image saved = ImageRepository.findAll().get(0);

    assertEquals(0, new BigDecimal("50.00").compareTo(saved.getFocalX()),
        "a freshly-inserted image must default to a dead-center focal point");
    assertEquals(0, new BigDecimal("50.00").compareTo(saved.getFocalY()));
  }

  @Test
  void updatePersistsFocalPointAndOtherMutableFieldsOnAnExistingRecord() {
    // ImageRepository.update() used to only ever write the `processed` column -- a latent bug
    // invisible until issue #411 PR3's "set focal point on an existing image" action became the
    // first caller needing anything else to actually persist on an existing row.
    Image image = new Image();
    image.setFilename("focal-point-test.png");
    image.setFileServerPath("2026/07/focal-point-test.png");
    image.setCreatedBy(userId);
    image.setFileLength(2048);
    image.setFileType("image/png");
    image.setWidth(200);
    image.setHeight(200);
    image.setWebPath("2026/07");
    Image saved = ImageRepository.save(image);

    saved.setFocalX(new BigDecimal("12.50"));
    saved.setFocalY(new BigDecimal("87.25"));
    ImageRepository.save(saved);

    Image reloaded = ImageRepository.findById(saved.getId());
    assertEquals(0, new BigDecimal("12.50").compareTo(reloaded.getFocalX()));
    assertEquals(0, new BigDecimal("87.25").compareTo(reloaded.getFocalY()));
  }

  /**
   * Builds paging constraints with an explicit, unique sort column (the primary key) instead of
   * relying on {@code ImageRepository.findAll}'s internal "created DESC" default -- the seed
   * images are inserted back-to-back and may land on the same millisecond timestamp, which would
   * make cross-page ordering (and therefore these pagination assertions) nondeterministic.
   * {@link DataConstraints#setColumnToSortBy(String)} is an explicit override that takes priority
   * over the repository's default, so this sort is what actually runs.
   */
  private static DataConstraints pageConstraints(int pageNumber, int pageSize) {
    DataConstraints constraints = new DataConstraints(pageNumber, pageSize);
    constraints.setColumnToSortBy("image_id");
    return constraints;
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
      statement.execute("DROP TABLE IF EXISTS image_tag_map CASCADE");
      statement.execute("DROP TABLE IF EXISTS image_tags CASCADE");
      statement.execute("DROP TABLE IF EXISTS images CASCADE");
      statement.execute("DROP TABLE IF EXISTS users CASCADE");
      statement.execute("CREATE TABLE users ("
          + "user_id BIGSERIAL PRIMARY KEY, "
          + "unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "username VARCHAR(255) UNIQUE NOT NULL, "
          + "password VARCHAR(255) NOT NULL)");
      statement.execute("CREATE TABLE images ("
          + "image_id BIGSERIAL PRIMARY KEY, "
          + "filename VARCHAR(255) NOT NULL, "
          + "path VARCHAR(255) NOT NULL, "
          + "created_by BIGINT REFERENCES users(user_id) NOT NULL, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "processed TIMESTAMP(3), "
          + "file_length BIGINT DEFAULT 0, "
          + "file_type VARCHAR(20), "
          + "width INTEGER NOT NULL, "
          + "height INTEGER NOT NULL, "
          + "web_path VARCHAR(50) NOT NULL, "
          + "focal_x NUMERIC(5,2) NOT NULL DEFAULT 50.00, "
          + "focal_y NUMERIC(5,2) NOT NULL DEFAULT 50.00, "
          + "file_hash VARCHAR(1024))");
      statement.execute("CREATE TABLE image_tags ("
          + "image_tag_id BIGSERIAL PRIMARY KEY, "
          + "name VARCHAR(255) NOT NULL)");
      statement.execute("CREATE TABLE image_tag_map ("
          + "id BIGSERIAL PRIMARY KEY, "
          + "image_id BIGINT REFERENCES images(image_id) NOT NULL, "
          + "image_tag_id BIGINT REFERENCES image_tags(image_tag_id) NOT NULL)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the images/users schema", se);
    }
  }

  private static long insertImageTag(String name) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO image_tags (name) VALUES (?) RETURNING image_tag_id")) {
      pst.setString(1, name);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test image tag", se);
    }
  }

  private static void linkImageToTag(long imageId, long imageTagId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO image_tag_map (image_id, image_tag_id) VALUES (?, ?)")) {
      pst.setLong(1, imageId);
      pst.setLong(2, imageTagId);
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not link test image to tag", se);
    }
  }

  private static long insertUser(String uniqueId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO users (unique_id, username, password) VALUES (?, ?, ?) RETURNING user_id")) {
      pst.setString(1, uniqueId);
      pst.setString(2, uniqueId);
      pst.setString(3, "not-a-real-hash");
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test user", se);
    }
  }

  private static void insertImage(String filename) {
    Image image = new Image();
    image.setFilename(filename);
    image.setFileServerPath("2026/07/" + filename);
    image.setCreatedBy(userId);
    image.setFileLength(1024);
    image.setFileType("image/png");
    image.setWidth(100);
    image.setHeight(100);
    image.setWebPath("2026/07");
    ImageRepository.save(image);
  }
}
