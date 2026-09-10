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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

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
import org.mockito.MockedStatic;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.FunnelEventCommand;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormFieldRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * End-to-end verification of {@link FormWidget}'s database-backed field configuration (issue
 * #409), against a real PostgreSQL instance, exercising {@link FormDefinitionRepository} and
 * {@link FormFieldRepository} exactly as the {@code formId} preference does, then the same
 * {@link FormDataRepository#save} path an XML-defined form already uses -- proving requirement #5
 * ("submissions land in the existing form_data table, in the same shape as an XML-defined form's
 * submission") with an actual round trip rather than a mocked assertion.
 *
 * <p>
 * {@link FormWidgetTest} covers the XML-preference path (unmodified by this feature) and the
 * formId-not-set backward-compatibility guarantee at the unit level with mocks; this class covers
 * the formId-set path against real data. It is skipped automatically when Docker is not available.
 * </p>
 *
 * @author SimIS Inc.
 */
class FormWidgetDatabaseFormIntegrationTest extends WidgetBase {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  private long formDefinitionId;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping FormWidget database-form integration test");

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
  void resetTablesAndSeedForm() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE form_data, form_fields, form_definitions RESTART IDENTITY CASCADE");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset form_definitions/form_fields/form_data tables", se);
    }

    // Seed a form definition with the same shape as FormWidgetTest#initCommonPreferences()'s XML
    // fields, via the real repositories (not raw SQL), so this test also exercises the same save
    // path an admin's /admin/forms editor would use.
    FormDefinition formDefinition = new FormDefinition();
    formDefinition.setUniqueId("db-contact");
    formDefinition.setName("Database Contact Form");
    formDefinition = FormDefinitionRepository.save(formDefinition);
    formDefinitionId = formDefinition.getId();

    saveField("name", "Your first and last name", "text", true, 10);
    saveField("organization", "Name of your organization", "text", false, 20);
    saveField("email", "Your email address", "email", true, 30);
    saveField("comments", "How Can We Help?", "textarea", true, 40);
  }

  @Test
  void executeRendersTheDatabaseDefinedFieldsInFieldOrder() {
    preferences.put("formId", String.valueOf(formDefinitionId));

    try (MockedStatic<com.simisinc.platform.application.RateLimitCommand> rateLimitCommand =
        mockStatic(com.simisinc.platform.application.RateLimitCommand.class)) {
      rateLimitCommand.when(() -> com.simisinc.platform.application.RateLimitCommand.isIpAllowedRightNow(any(), org.mockito.ArgumentMatchers.anyBoolean()))
          .thenReturn(true);

      FormWidget widget = new FormWidget();
      widget.execute(widgetContext);

      assertEquals(FormWidget.JSP, widgetContext.getJsp());

      @SuppressWarnings("unchecked")
      List<FormField> formFieldList = (List<FormField>) widgetContext.getRequest().getAttribute("formFieldList");
      assertEquals(4, formFieldList.size(), "all 4 database fields should be rendered");

      // field_order (10, 20, 30, 40) must drive rendering order, exactly like FormFieldRepository's
      // own findAllByFormDefinitionId ordering guarantee
      assertEquals("name", formFieldList.get(0).getName());
      assertEquals("Your first and last name", formFieldList.get(0).getLabel());
      assertTrue(formFieldList.get(0).isRequired());

      assertEquals("organization", formFieldList.get(1).getName());
      assertTrue(!formFieldList.get(1).isRequired());

      assertEquals("email", formFieldList.get(2).getName());
      assertEquals("email", formFieldList.get(2).getType());
      assertTrue(formFieldList.get(2).isRequired());

      assertEquals("comments", formFieldList.get(3).getName());
      assertEquals("textarea", formFieldList.get(3).getType());
      assertTrue(formFieldList.get(3).isRequired());
    }
  }

  @Test
  void postPersistsADatabaseDefinedSubmissionToFormDataInTheSameShapeAsAnXmlForm() {
    preferences.put("formId", String.valueOf(formDefinitionId));
    preferences.put("formUniqueId", "db-contact");
    preferences.put("checkForSpam", "false");

    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "name", "First Last");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "organization", "Acme Inc");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "email", "email@example.com");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "comments", "These are my comments.");

    try (MockedStatic<WorkflowManager> workflowManagerMockedStatic = mockStatic(WorkflowManager.class);
        MockedStatic<FunnelEventCommand> funnelEventCommand = mockStatic(FunnelEventCommand.class)) {

      FormWidget widget = new FormWidget();
      WidgetContext result = widget.post(widgetContext);

      assertNull(result, "a successful submission redirects (post() returns null)");
      assertNull(widgetContext.getWarningMessage());
      assertNull(widgetContext.getErrorMessage());
      workflowManagerMockedStatic.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()));
      funnelEventCommand.verify(() -> FunnelEventCommand.recordContactFormSubmitted(eq("db-contact"), any()));
    }

    // Re-read the row through FormDataRepository -- the exact same repository/table an XML-defined
    // form's submission is stored in (issue #409 requirement: no parallel storage mechanism).
    FormDataSpecification spec = new FormDataSpecification();
    spec.setFormUniqueId("db-contact");
    List<FormData> saved = FormDataRepository.findAll(spec, new DataConstraints());
    assertEquals(1, saved.size(), "exactly one form_data row should have been written");

    FormData formData = saved.get(0);
    assertEquals("db-contact", formData.getFormUniqueId());
    List<FormField> savedFields = formData.getFormFieldList();
    assertEquals(4, savedFields.size(), "field_values must round-trip all 4 submitted fields");

    FormField name = findByName(savedFields, "name");
    assertEquals("Your first and last name", name.getLabel());
    assertEquals("First Last", name.getUserValue());

    FormField email = findByName(savedFields, "email");
    assertEquals("email", email.getType());
    assertEquals("email@example.com", email.getUserValue());

    FormField comments = findByName(savedFields, "comments");
    assertEquals("textarea", comments.getType());
    assertEquals("These are my comments.", comments.getUserValue());
  }

  @Test
  void postRejectsAMissingRequiredDatabaseDefinedFieldJustLikeAnXmlField() {
    preferences.put("formId", String.valueOf(formDefinitionId));
    preferences.put("formUniqueId", "db-contact");
    preferences.put("checkForSpam", "false");

    // "comments" is required in the seeded form definition and is left blank here
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "name", "First Last");
    addQueryParameter(widgetContext, widgetContext.getUniqueId() + "email", "email@example.com");

    FormWidget widget = new FormWidget();
    WidgetContext result = widget.post(widgetContext);

    assertEquals(widgetContext, result, "a validation failure redisplays the form (post() returns the context)");
    assertEquals("How Can We Help? is required", widgetContext.getWarningMessage());

    assertEquals(0, FormDataRepository.countTotalSubmissions(), "an invalid submission must not be saved");
  }

  private static FormField findByName(List<FormField> fields, String name) {
    return fields.stream().filter(f -> name.equals(f.getName())).findFirst()
        .orElseThrow(() -> new AssertionError("No field named " + name + " in " + fields));
  }

  private void saveField(String name, String label, String type, boolean required, int fieldOrder) {
    FormField field = new FormField();
    field.setFormDefinitionId(formDefinitionId);
    field.setName(name);
    field.setLabel(label);
    field.setType(type);
    field.setRequired(required);
    field.setFieldOrder(fieldOrder);
    FormFieldRepository.save(field);
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
    // Mirrors NEW_10010__new_cms.sql's form_definitions/form_fields/form_data tables (a focused
    // subset of form_data -- see FormDataRepositoryTest for the same trimming rationale).
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS form_data CASCADE");
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
          + "show_privacy_notice BOOLEAN DEFAULT FALSE, "
          + "send_confirmation_to_submitter BOOLEAN DEFAULT FALSE, "
          + "notification_subject VARCHAR(255), "
          + "confirmation_subject VARCHAR(255), "
          + "confirmation_message TEXT, "
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
      statement.execute("CREATE TABLE form_data ("
          + "form_data_id BIGSERIAL PRIMARY KEY, "
          + "form_unique_id VARCHAR(255), "
          + "field_values JSONB, "
          + "ip_address VARCHAR(200), "
          + "created_by BIGINT, "
          + "modified_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "claimed TIMESTAMP(3) DEFAULT NULL, "
          + "claimed_by BIGINT, "
          + "dismissed TIMESTAMP(3) DEFAULT NULL, "
          + "url VARCHAR(512), "
          + "query_params VARCHAR(512), "
          + "flagged_as_spam BOOLEAN DEFAULT FALSE, "
          + "session_id VARCHAR(255), "
          + "dismissed_by BIGINT, "
          + "processed TIMESTAMP(3) DEFAULT NULL, "
          + "processed_by BIGINT, "
          + "processed_system VARCHAR(255))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the form_definitions/form_fields/form_data schema", se);
    }
  }
}
