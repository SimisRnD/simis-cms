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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link WebRedirectRepository} CRUD and lookups (issue #408) against a real PostgreSQL
 * instance. Minimal schema replicated from NEW_10180__new_web_redirects.sql, without the
 * users(user_id) FK on created_by/modified_by -- same simplification
 * WebhookSubscriptionRepositoryTest makes.
 */
class WebRedirectRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping web redirect integration test");

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
  void clearTable() throws SQLException {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping web redirect integration test");
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE web_redirects RESTART IDENTITY CASCADE");
    }
  }

  @Test
  void addThenFindByIdRoundTripsAllFields() {
    WebRedirect redirect = new WebRedirect();
    redirect.setFromPath("/old-page");
    redirect.setToUrl("/new-page");
    redirect.setStatusCode(301);
    redirect.setEnabled(true);
    redirect.setCreatedBy(1L);
    redirect.setModifiedBy(1L);

    WebRedirect saved = WebRedirectRepository.add(redirect);
    assertNotNull(saved);
    assertTrue(saved.getId() > -1);

    WebRedirect found = WebRedirectRepository.findById(saved.getId());
    assertEquals("/old-page", found.getFromPath());
    assertEquals("/new-page", found.getToUrl());
    assertEquals(301, found.getStatusCode());
    assertTrue(found.getEnabled());
    assertNotNull(found.getCreated());
    assertEquals(1L, found.getCreatedBy());
  }

  @Test
  void findByIdReturnsNullWhenNoRowMatches() {
    assertNull(WebRedirectRepository.findById(999999L));
  }

  @Test
  void findByIdReturnsNullForTheSentinelIdOfMinusOne() {
    assertNull(WebRedirectRepository.findById(-1L));
  }

  @Test
  void updateChangesToUrlAndStatusCodeAndEnabled() {
    WebRedirect redirect = seed("/a", "/b", 301, true);

    redirect.setToUrl("/c");
    redirect.setStatusCode(302);
    redirect.setEnabled(false);
    WebRedirectRepository.update(redirect);

    WebRedirect found = WebRedirectRepository.findById(redirect.getId());
    assertEquals("/c", found.getToUrl());
    assertEquals(302, found.getStatusCode());
    assertFalse(found.getEnabled());
  }

  @Test
  void updateCanRenameTheFromPath() {
    WebRedirect redirect = seed("/old-path", "/target", 301, true);

    redirect.setFromPath("/renamed-path");
    WebRedirectRepository.update(redirect);

    assertNull(WebRedirectRepository.findByFromPath("/old-path"));
    WebRedirect found = WebRedirectRepository.findByFromPath("/renamed-path");
    assertNotNull(found);
    assertEquals(redirect.getId(), found.getId());
  }

  @Test
  void saveAddsANewRecordWhenIdIsUnset() {
    WebRedirect redirect = new WebRedirect();
    redirect.setFromPath("/save-new");
    redirect.setToUrl("/target");
    redirect.setStatusCode(301);
    redirect.setEnabled(true);
    redirect.setCreatedBy(1L);
    redirect.setModifiedBy(1L);

    WebRedirect saved = WebRedirectRepository.save(redirect);

    assertNotNull(saved);
    assertTrue(saved.getId() > -1);
    assertEquals("/save-new", WebRedirectRepository.findById(saved.getId()).getFromPath());
  }

  @Test
  void saveUpdatesAnExistingRecordWhenIdIsSet() {
    WebRedirect redirect = seed("/save-existing", "/target", 301, true);

    redirect.setToUrl("/updated-target");
    WebRedirectRepository.save(redirect);

    assertEquals("/updated-target", WebRedirectRepository.findById(redirect.getId()).getToUrl());
  }

  @Test
  void removeDeletesTheRow() {
    WebRedirect redirect = seed("/a", "/b", 301, true);
    assertTrue(WebRedirectRepository.remove(redirect));
    assertNull(WebRedirectRepository.findById(redirect.getId()));
  }

  @Test
  void findAllReturnsEveryRedirectSortedByFromPath() {
    seed("/zzz-last", "/z", 301, true);
    seed("/aaa-first", "/a", 301, true);
    seed("/mmm-middle", "/m", 301, true);

    List<WebRedirect> all = WebRedirectRepository.findAll();

    assertEquals(3, all.size());
    assertEquals("/aaa-first", all.get(0).getFromPath());
    assertEquals("/mmm-middle", all.get(1).getFromPath());
    assertEquals("/zzz-last", all.get(2).getFromPath());
  }

  @Test
  void findAllReturnsAnEmptyListRatherThanNullWhenThereAreNoRedirects() {
    List<WebRedirect> all = WebRedirectRepository.findAll();
    assertNotNull(all);
    assertTrue(all.isEmpty());
  }

  @Test
  void findByFromPathReturnsAnExactMatchRegardlessOfEnabledState() {
    seed("/disabled-path", "/target", 301, false);

    WebRedirect found = WebRedirectRepository.findByFromPath("/disabled-path");

    assertNotNull(found, "findByFromPath is used for the admin form's duplicate-path check, "
        + "so a disabled row must still be found");
    assertFalse(found.getEnabled());
  }

  @Test
  void findByFromPathReturnsNullWhenThereIsNoMatch() {
    assertNull(WebRedirectRepository.findByFromPath("/does-not-exist"));
  }

  @Test
  void findEnabledByFromPathReturnsTheRedirectWhenEnabled() {
    seed("/enabled-path", "/target", 301, true);

    WebRedirect found = WebRedirectRepository.findEnabledByFromPath("/enabled-path");

    assertNotNull(found);
    assertEquals("/target", found.getToUrl());
  }

  @Test
  void findEnabledByFromPathReturnsNullWhenTheRedirectIsDisabled() {
    // This is the loader function for the request-time cache (LoadWebRedirectCommand /
    // CacheManager.WEB_REDIRECT_CACHE) -- a disabled redirect must never be served, so the loader
    // itself must return null rather than a disabled record the caller has to remember to check.
    seed("/disabled-path", "/target", 301, false);

    assertNull(WebRedirectRepository.findEnabledByFromPath("/disabled-path"));
  }

  @Test
  void findEnabledByFromPathReturnsNullWhenThereIsNoMatch() {
    assertNull(WebRedirectRepository.findEnabledByFromPath("/does-not-exist"));
  }

  @Test
  void statusCodeDefaultsTo301WhenNotExplicitlySet() {
    WebRedirect redirect = new WebRedirect();
    redirect.setFromPath("/default-status");
    redirect.setToUrl("/target");
    redirect.setCreatedBy(1L);
    redirect.setModifiedBy(1L);

    WebRedirect saved = WebRedirectRepository.add(redirect);

    assertEquals(301, WebRedirectRepository.findById(saved.getId()).getStatusCode());
  }

  @Test
  void fromPathMustBeUniqueAtTheDatabaseLevel() {
    WebRedirect first = seed("/duplicate-path", "/a", 301, true);

    WebRedirect second = new WebRedirect();
    second.setFromPath("/duplicate-path");
    second.setToUrl("/b");
    second.setStatusCode(301);
    second.setEnabled(true);
    second.setCreatedBy(1L);
    second.setModifiedBy(1L);

    // WebRedirectRepository.add() itself does not pre-check for a duplicate (that is
    // SaveWebRedirectCommand's job, a later stage of issue #408) -- this asserts the unique index
    // from NEW_10180/UPGRADE_20260804.1004 is the backstop that makes the constraint real even if
    // that application-level check is ever bypassed. DB.insertInto() catches the resulting
    // SQLException and logs it rather than propagating it, so the observable effect here is
    // add() returning null and the original row being left untouched.
    WebRedirect saved = WebRedirectRepository.add(second);

    assertNull(saved, "a duplicate from_path should fail the unique index and leave add() returning null");
    WebRedirect stillThere = WebRedirectRepository.findByFromPath("/duplicate-path");
    assertNotNull(stillThere);
    assertEquals(first.getId(), stillThere.getId());
    assertEquals("/a", stillThere.getToUrl(), "the original row must be unaffected by the failed duplicate insert");
  }

  @Test
  void onlyAStatusCodeOf301Or302IsAccepted() throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      SQLException thrown = null;
      try {
        statement.execute(
            "INSERT INTO web_redirects (from_path, to_url, status_code) VALUES ('/bad-status', '/target', 404)");
      } catch (SQLException se) {
        thrown = se;
      }
      assertNotNull(thrown, "the web_redirects_status_code_check CHECK constraint should reject a 404 status code");
    }
  }

  private WebRedirect seed(String fromPath, String toUrl, int statusCode, boolean enabled) {
    WebRedirect redirect = new WebRedirect();
    redirect.setFromPath(fromPath);
    redirect.setToUrl(toUrl);
    redirect.setStatusCode(statusCode);
    redirect.setEnabled(enabled);
    redirect.setCreatedBy(1L);
    redirect.setModifiedBy(1L);
    return WebRedirectRepository.add(redirect);
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE web_redirects ("
          + "web_redirect_id BIGSERIAL PRIMARY KEY, "
          + "from_path VARCHAR(500) NOT NULL, "
          + "to_url VARCHAR(2000) NOT NULL, "
          + "status_code INTEGER NOT NULL DEFAULT 301, "
          + "enabled BOOLEAN DEFAULT true, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "created_by BIGINT, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT, "
          + "CONSTRAINT web_redirects_status_code_check CHECK (status_code IN (301, 302)))");
      statement.execute("CREATE UNIQUE INDEX web_redirects_from_path_idx ON web_redirects(from_path)");
      statement.execute("CREATE INDEX web_redirects_enabled_idx ON web_redirects(enabled)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the web_redirects test schema", se);
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException | LinkageError e) {
      return false;
    }
  }

  private static String resolveImage() {
    String image = System.getenv("TEST_POSTGRES_IMAGE");
    return (image != null && !image.isBlank()) ? image : DEFAULT_IMAGE;
  }
}
