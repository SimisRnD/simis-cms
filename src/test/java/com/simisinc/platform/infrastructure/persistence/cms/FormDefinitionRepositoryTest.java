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

import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link FormDefinitionRepository} against a real PostgreSQL instance (issue #409),
 * including the app-level cascade delete of a form's fields.
 *
 * @author SimIS Inc.
 */
class FormDefinitionRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static long userId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping FormDefinitionRepository integration test");

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
    userId = insertUser("form-editor");
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
  void resetTables() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE form_fields, form_definitions RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset form_definitions/form_fields tables", se);
    }
  }

  @Test
  void saveInsertsANewFormDefinition() {
    FormDefinition definition = newDefinition("contact-us", "Contact Us");

    FormDefinition saved = FormDefinitionRepository.save(definition);

    assertNotNull(saved);
    assertNotNull(saved.getId());
    assertTrue(saved.getId() > -1);
    assertEquals("contact-us", saved.getUniqueId());
  }

  @Test
  void saveOnAnExistingIdUpdatesRatherThanDuplicating() {
    FormDefinition saved = FormDefinitionRepository.save(newDefinition("contact-us", "Contact Us"));

    saved.setName("Contact Us (Updated)");
    saved.setEnabled(false);
    FormDefinitionRepository.save(saved);

    assertEquals(1, DB.selectCountFrom("form_definitions"), "an update must not insert a second row");
    FormDefinition reloaded = FormDefinitionRepository.findById(saved.getId());
    assertEquals("Contact Us (Updated)", reloaded.getName());
    assertFalse(reloaded.getEnabled());
  }

  @Test
  void findByUniqueIdReturnsNullWhenNoSuchFormExists() {
    assertNull(FormDefinitionRepository.findByUniqueId("does-not-exist"));
  }

  @Test
  void findByUniqueIdFindsAnEnabledOrDisabledForm() {
    FormDefinitionRepository.save(newDefinition("contact-us", "Contact Us"));

    FormDefinition found = FormDefinitionRepository.findByUniqueId("contact-us");

    assertNotNull(found);
    assertEquals("Contact Us", found.getName());
  }

  @Test
  void findAllReturnsEveryDefinition() {
    FormDefinitionRepository.save(newDefinition("contact-us", "Contact Us"));
    FormDefinitionRepository.save(newDefinition("volunteer-signup", "Volunteer Signup"));

    List<FormDefinition> all = FormDefinitionRepository.findAll();

    assertEquals(2, all.size());
  }

  @Test
  void removeDeletesTheFormDefinitionRow() {
    FormDefinition saved = FormDefinitionRepository.save(newDefinition("contact-us", "Contact Us"));

    boolean removed = FormDefinitionRepository.remove(saved);

    assertTrue(removed);
    assertNull(FormDefinitionRepository.findById(saved.getId()));
  }

  @Test
  void removeCascadesToTheFormsFields() {
    // Deleting a form definition is a deliberate app-level cascade (see
    // FormDefinitionRepository#remove's javadoc): form_fields.form_definition_id has no DB-level ON
    // DELETE CASCADE, so this verifies the repository performs the cascade itself, in a transaction,
    // rather than leaving orphaned rows or failing on the FK.
    FormDefinition saved = FormDefinitionRepository.save(newDefinition("contact-us", "Contact Us"));
    FormFieldRepository.save(newField(saved.getId(), "full_name", "Full Name"));
    FormFieldRepository.save(newField(saved.getId(), "email", "Email"));
    assertEquals(2, DB.selectCountFrom("form_fields"), "test setup: both fields must save");

    boolean removed = FormDefinitionRepository.remove(saved);

    assertTrue(removed);
    assertTrue(FormFieldRepository.findAllByFormDefinitionId(saved.getId()).isEmpty(),
        "deleting the form definition must also remove its fields");
    assertEquals(0, DB.selectCountFrom("form_fields"));
  }

  @Test
  void removeOfOneFormDoesNotAffectAnotherFormsFields() {
    FormDefinition contactUs = FormDefinitionRepository.save(newDefinition("contact-us", "Contact Us"));
    FormDefinition volunteer = FormDefinitionRepository.save(newDefinition("volunteer-signup", "Volunteer Signup"));
    FormFieldRepository.save(newField(contactUs.getId(), "email", "Email"));
    FormFieldRepository.save(newField(volunteer.getId(), "email", "Email"));

    FormDefinitionRepository.remove(contactUs);

    assertEquals(1, FormFieldRepository.findAllByFormDefinitionId(volunteer.getId()).size(),
        "the other form's fields must survive");
  }

  private static FormDefinition newDefinition(String uniqueId, String name) {
    FormDefinition definition = new FormDefinition();
    definition.setUniqueId(uniqueId);
    definition.setName(name);
    definition.setTitle(name);
    definition.setCreatedBy(userId);
    definition.setModifiedBy(userId);
    return definition;
  }

  private static FormField newField(long formDefinitionId, String name, String label) {
    FormField field = new FormField();
    field.setFormDefinitionId(formDefinitionId);
    field.setName(name);
    field.setLabel(label);
    field.setType("text");
    return field;
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
    // Mirrors NEW_10010__new_cms.sql's `form_definitions`/`form_fields` tables.
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS form_fields CASCADE");
      statement.execute("DROP TABLE IF EXISTS form_definitions CASCADE");
      statement.execute("DROP TABLE IF EXISTS users CASCADE");
      statement.execute("CREATE TABLE users ("
          + "user_id BIGSERIAL PRIMARY KEY, "
          + "unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "username VARCHAR(255) UNIQUE NOT NULL, "
          + "password VARCHAR(255) NOT NULL)");
      statement.execute("CREATE TABLE form_definitions ("
          + "form_definition_id BIGSERIAL PRIMARY KEY, "
          + "unique_id VARCHAR(255) UNIQUE NOT NULL, "
          + "name VARCHAR(255) NOT NULL, "
          + "title VARCHAR(255), "
          + "subtitle VARCHAR(255), "
          + "button_name VARCHAR(100), "
          + "success_title VARCHAR(255), "
          + "success_message TEXT, "
          + "email_to VARCHAR(512), "
          + "use_captcha BOOLEAN DEFAULT FALSE, "
          + "check_for_spam BOOLEAN DEFAULT TRUE, "
          + "enabled BOOLEAN DEFAULT TRUE, "
          + "show_privacy_notice BOOLEAN DEFAULT FALSE, "
          + "created_by BIGINT REFERENCES users(user_id), "
          + "modified_by BIGINT REFERENCES users(user_id), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
      statement.execute("CREATE TABLE form_fields ("
          + "form_field_id BIGSERIAL PRIMARY KEY, "
          + "form_definition_id BIGINT NOT NULL REFERENCES form_definitions(form_definition_id), "
          + "field_order INTEGER DEFAULT 100, "
          + "name VARCHAR(255) NOT NULL, "
          + "label VARCHAR(255) NOT NULL, "
          + "field_type VARCHAR(30) DEFAULT 'text', "
          + "required BOOLEAN DEFAULT FALSE, "
          + "placeholder VARCHAR(255), "
          + "default_value VARCHAR(255), "
          + "options TEXT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the form_definitions/form_fields/users schema", se);
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
}
