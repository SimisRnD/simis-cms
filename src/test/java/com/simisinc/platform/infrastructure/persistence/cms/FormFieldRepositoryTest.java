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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Verifies {@link FormFieldRepository} against a real PostgreSQL instance (issue #409), including
 * that drag-and-drop reordering persists and that the select/checkbox options round-trip through
 * their delimited-string storage.
 *
 * @author SimIS Inc.
 */
class FormFieldRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;
  private static long formDefinitionId;
  private static long otherFormDefinitionId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping FormFieldRepository integration test");

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
    formDefinitionId = insertFormDefinition("contact-us");
    otherFormDefinitionId = insertFormDefinition("volunteer-signup");
  }

  @Test
  void saveInsertsANewField() {
    FormField field = newField(formDefinitionId, "full_name", "Full Name", "text");

    FormField saved = FormFieldRepository.save(field);

    assertNotNull(saved);
    assertTrue(saved.getId() > -1);
    assertEquals("full_name", saved.getName());
  }

  @Test
  void aFieldSavedWithoutAnExplicitOrderGetsTheColumnDefaultSoItSortsToTheEnd() {
    // FormField's fieldOrder defaults to -1 (issue #409's domain-model addition); the repository
    // must omit the column on insert rather than binding that sentinel as NULL, so Postgres'
    // "field_order INTEGER DEFAULT 100" actually applies.
    FormField saved = FormFieldRepository.save(newField(formDefinitionId, "full_name", "Full Name", "text"));

    FormField reloaded = FormFieldRepository.findById(saved.getId());

    assertEquals(100, reloaded.getFieldOrder());
  }

  @Test
  void findAllByFormDefinitionIdOrdersByFieldOrder() {
    FormField third = FormFieldRepository.save(newField(formDefinitionId, "c", "C", "text"));
    FormField first = FormFieldRepository.save(newField(formDefinitionId, "a", "A", "text"));
    FormField second = FormFieldRepository.save(newField(formDefinitionId, "b", "B", "text"));
    reorder(first, 10);
    reorder(second, 20);
    reorder(third, 30);

    List<FormField> fields = FormFieldRepository.findAllByFormDefinitionId(formDefinitionId);

    assertEquals(List.of("a", "b", "c"), fields.stream().map(FormField::getName).toList());
  }

  @Test
  void findAllByFormDefinitionIdScopesToThatFormOnly() {
    FormFieldRepository.save(newField(formDefinitionId, "email", "Email", "email"));
    FormFieldRepository.save(newField(otherFormDefinitionId, "email", "Email", "email"));

    List<FormField> fields = FormFieldRepository.findAllByFormDefinitionId(formDefinitionId);

    assertEquals(1, fields.size());
    assertTrue(fields.stream().allMatch(f -> f.getFormDefinitionId() == formDefinitionId));
  }

  @Test
  void reorderFieldsPersistsTheNewOrder() {
    FormField a = FormFieldRepository.save(newField(formDefinitionId, "a", "A", "text"));
    FormField b = FormFieldRepository.save(newField(formDefinitionId, "b", "B", "text"));
    FormField c = FormFieldRepository.save(newField(formDefinitionId, "c", "C", "text"));
    // Insert order is a, b, c (all default field_order 100) -- reorder to c, a, b as a drag-and-drop
    // action would submit (issue #409's hidden "fieldOrder" comma-joined id list).
    boolean reordered = FormFieldRepository.reorderFields(formDefinitionId,
        List.of(c.getId(), a.getId(), b.getId()));

    assertTrue(reordered);
    List<FormField> fields = FormFieldRepository.findAllByFormDefinitionId(formDefinitionId);
    assertEquals(List.of("c", "a", "b"), fields.stream().map(FormField::getName).toList(),
        "field_order must persist and be reflected by a fresh findAllByFormDefinitionId query");
  }

  @Test
  void reorderFieldsIsStableAcrossASecondReorder() {
    // Reordering twice, verifying the second reorder's result rather than assuming the first one
    // "stuck" -- guards against a reorder that appears to work once but leaves stale field_order
    // values that only happen to look right after a single pass.
    FormField a = FormFieldRepository.save(newField(formDefinitionId, "a", "A", "text"));
    FormField b = FormFieldRepository.save(newField(formDefinitionId, "b", "B", "text"));
    FormFieldRepository.reorderFields(formDefinitionId, List.of(a.getId(), b.getId()));

    FormFieldRepository.reorderFields(formDefinitionId, List.of(b.getId(), a.getId()));

    List<FormField> fields = FormFieldRepository.findAllByFormDefinitionId(formDefinitionId);
    assertEquals(List.of("b", "a"), fields.stream().map(FormField::getName).toList());
  }

  @Test
  void reorderFieldsSkipsAnIdBelongingToAnotherForm() {
    // A malicious or stale request could submit a field id from a different form; reorderFields
    // must not let that id escape its own form's scope by writing a field_order for it under this
    // formDefinitionId's reorder call.
    FormField ownField = FormFieldRepository.save(newField(formDefinitionId, "email", "Email", "email"));
    FormField otherFormsField = FormFieldRepository.save(newField(otherFormDefinitionId, "email", "Email", "email"));

    boolean result = FormFieldRepository.reorderFields(formDefinitionId,
        List.of(otherFormsField.getId(), ownField.getId()));

    assertTrue(result);
    FormField reloadedOtherField = FormFieldRepository.findById(otherFormsField.getId());
    assertEquals(otherFormDefinitionId, reloadedOtherField.getFormDefinitionId(),
        "the other form's field must not be reassigned or reordered by this call");
    assertEquals(1, FormFieldRepository.findAllByFormDefinitionId(formDefinitionId).size());
  }

  @Test
  void reorderFieldsWithNoMatchingIdsReturnsTrueWithoutChangingOrder() {
    FormField a = FormFieldRepository.save(newField(otherFormDefinitionId, "a", "A", "text"));

    // Every id in the list belongs to a different form than formDefinitionId, so nothing should move.
    boolean result = FormFieldRepository.reorderFields(formDefinitionId, List.of(a.getId()));

    assertTrue(result);
    FormField reloaded = FormFieldRepository.findById(a.getId());
    assertEquals(100, reloaded.getFieldOrder(), "an id outside the target form must be left untouched");
  }

  @Test
  void reorderFieldsWithAnEmptyListReturnsFalse() {
    assertFalse(FormFieldRepository.reorderFields(formDefinitionId, List.of()));
  }

  @Test
  void getNextFieldOrderStartsAtOneForAFormWithNoFields() {
    assertEquals(1, FormFieldRepository.getNextFieldOrder(formDefinitionId));
  }

  @Test
  void getNextFieldOrderReturnsOneMoreThanTheCurrentMax() {
    FormField field = FormFieldRepository.save(newField(formDefinitionId, "a", "A", "text"));
    reorder(field, 30);

    assertEquals(31, FormFieldRepository.getNextFieldOrder(formDefinitionId));
  }

  @Test
  void getNextFieldOrderIsScopedToItsOwnForm() {
    FormField field = FormFieldRepository.save(newField(otherFormDefinitionId, "a", "A", "text"));
    reorder(field, 500);

    // A high field_order on a different form must not influence this form's next value
    assertEquals(1, FormFieldRepository.getNextFieldOrder(formDefinitionId));
  }

  @Test
  void twoFieldsAddedWithoutAnInterveningReorderGetDistinctFieldOrders() {
    // Reproduces issue #409's field_order duplicate-value gap: SaveFormFieldCommand calls
    // getNextFieldOrder() and sets it on every newly-created field, so two consecutive adds -- with
    // no drag-and-drop reorder between them -- must not both land on the column's DEFAULT 100.
    FormField first = newField(formDefinitionId, "a", "A", "text");
    first.setFieldOrder(FormFieldRepository.getNextFieldOrder(formDefinitionId));
    FormField savedFirst = FormFieldRepository.save(first);

    FormField second = newField(formDefinitionId, "b", "B", "text");
    second.setFieldOrder(FormFieldRepository.getNextFieldOrder(formDefinitionId));
    FormField savedSecond = FormFieldRepository.save(second);

    assertTrue(savedSecond.getFieldOrder() > savedFirst.getFieldOrder(),
        "a second field added without an intervening reorder must not collide with the first field's order");
    assertEquals(List.of("a", "b"),
        FormFieldRepository.findAllByFormDefinitionId(formDefinitionId).stream().map(FormField::getName).toList());
  }

  @Test
  void optionsRoundTripThroughTheDelimitedStringColumn() {
    Map<String, String> options = new LinkedHashMap<>();
    options.put("red", "Red");
    options.put("blue", "Blue");
    FormField field = newField(formDefinitionId, "favorite_color", "Favorite Color", "select");
    field.setListOfOptions(options);

    FormField saved = FormFieldRepository.save(field);
    FormField reloaded = FormFieldRepository.findById(saved.getId());

    assertEquals(options, reloaded.getListOfOptions());
  }

  @Test
  void aFieldWithNoOptionsReloadsWithANullOptionsMap() {
    FormField saved = FormFieldRepository.save(newField(formDefinitionId, "full_name", "Full Name", "text"));

    FormField reloaded = FormFieldRepository.findById(saved.getId());

    assertNull(reloaded.getListOfOptions());
  }

  @Test
  void updateChangesTheStoredValuesInPlace() {
    FormField saved = FormFieldRepository.save(newField(formDefinitionId, "full_name", "Full Name", "text"));

    saved.setLabel("Legal Name");
    saved.setRequired(true);
    saved.setPlaceholder("e.g. Jane Doe");
    FormFieldRepository.save(saved);

    assertEquals(1, DB.selectCountFrom("form_fields"), "an update must not insert a second row");
    FormField reloaded = FormFieldRepository.findById(saved.getId());
    assertEquals("Legal Name", reloaded.getLabel());
    assertTrue(reloaded.isRequired());
    assertEquals("e.g. Jane Doe", reloaded.getPlaceholder());
  }

  @Test
  void removeDeletesOnlyThatField() {
    FormField keep = FormFieldRepository.save(newField(formDefinitionId, "email", "Email", "email"));
    FormField remove = FormFieldRepository.save(newField(formDefinitionId, "phone", "Phone", "text"));

    boolean removed = FormFieldRepository.remove(remove);

    assertTrue(removed);
    assertNull(FormFieldRepository.findById(remove.getId()));
    assertNotNull(FormFieldRepository.findById(keep.getId()));
  }

  private static void reorder(FormField field, int order) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "UPDATE form_fields SET field_order = ? WHERE form_field_id = ?")) {
      pst.setInt(1, order);
      pst.setLong(2, field.getId());
      pst.executeUpdate();
    } catch (SQLException se) {
      throw new IllegalStateException("Could not set up field_order for test", se);
    }
  }

  private static FormField newField(long formDefinitionId, String name, String label, String type) {
    FormField field = new FormField();
    field.setFormDefinitionId(formDefinitionId);
    field.setName(name);
    field.setLabel(label);
    field.setType(type);
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
          + "created_by BIGINT, "
          + "modified_by BIGINT, "
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
      throw new IllegalStateException("Could not create the form_definitions/form_fields schema", se);
    }
  }

  private static long insertFormDefinition(String uniqueId) {
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(
            "INSERT INTO form_definitions (unique_id, name) VALUES (?, ?) RETURNING form_definition_id")) {
      pst.setString(1, uniqueId);
      pst.setString(2, uniqueId);
      try (ResultSet rs = pst.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException se) {
      throw new IllegalStateException("Could not insert test form definition", se);
    }
  }
}
