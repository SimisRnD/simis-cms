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

package com.simisinc.platform.application.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.domain.events.cms.FormSubmittedEvent;
import com.simisinc.platform.domain.events.cms.WebPagePublishedEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;

/**
 * Verifies the {@code {event, occurredOn, deliveryId, data}} payload shape (issue #418) for two
 * representative event types, as suggested by the issue's own examples.
 */
class BuildWebhookPayloadCommandTest {

  @Test
  void webPagePublishedEventProducesTheExpectedShapeAndData() throws Exception {
    WebPage webPage = new WebPage();
    webPage.setId(101L);
    webPage.setLink("/about-us");
    webPage.setTitle("About Us");
    webPage.setModifiedBy(7L);

    User user = new User();
    user.setId(7L);
    user.setUsername("editor7");
    user.setEmail("editor7@example.com");
    user.setFirstName("Ed");
    user.setLastName("Itor");

    WebPagePublishedEvent event = new WebPagePublishedEvent(webPage);

    String json;
    try (MockedStatic<UserRepository> userRepository = mockStatic(UserRepository.class)) {
      userRepository.when(() -> UserRepository.findByUserId(7L)).thenReturn(user);
      json = BuildWebhookPayloadCommand.build(event, "11111111-1111-1111-1111-111111111111");
    }

    assertNotNull(json);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(json);

    assertEquals("web-page-published", root.get("event").asText());
    assertEquals(event.getOccurred(), root.get("occurredOn").asLong());
    assertEquals("11111111-1111-1111-1111-111111111111", root.get("deliveryId").asText());

    JsonNode data = root.get("data");
    assertNotNull(data);
    assertEquals(101L, data.get("webPageId").asLong());
    assertEquals("/about-us", data.get("link").asText());
    assertEquals("About Us", data.get("title").asText());
    assertEquals("editor7@example.com", data.get("user").get("email").asText());
    assertEquals("editor7", data.get("user").get("username").asText());

    // The full User object (password, mfaSecret, accountToken, ...) must never appear -- only
    // the hand-picked summary fields.
    assertFalse(data.get("user").has("password"), "password must never be in a webhook payload");
    assertFalse(data.get("user").has("mfaSecret"), "mfaSecret must never be in a webhook payload");
    assertFalse(data.get("user").has("accountToken"), "accountToken must never be in a webhook payload");
  }

  @Test
  void formSubmittedEventProducesTheExpectedShapeAndData() {
    FormData formData = new FormData();
    formData.setId(55L);
    formData.setFormUniqueId("contact-us");
    FormField nameField = new FormField();
    nameField.setLabel("Name");
    nameField.setName("name");
    nameField.setUserValue("Jane Doe");
    formData.setFormFieldList(List.of(nameField));

    FormSubmittedEvent event = new FormSubmittedEvent(formData, null);

    Map<String, Object> data;
    try (MockedStatic<FormDataRepository> formDataRepository = mockStatic(FormDataRepository.class)) {
      formDataRepository.when(() -> FormDataRepository.findById(anyLong())).thenReturn(formData);
      data = BuildWebhookPayloadCommand.buildData(event);
    }

    assertEquals(55L, data.get("formId"));
    assertEquals("contact-us", data.get("formUniqueId"));
    assertNotNull(data.get("generatedId"));

    @SuppressWarnings("unchecked")
    List<Map<String, String>> fields = (List<Map<String, String>>) data.get("fields");
    assertEquals(1, fields.size());
    assertEquals("Name", fields.get(0).get("label"));
    assertEquals("Jane Doe", fields.get(0).get("value"));
  }

  @Test
  void unmappedEventTypeStillProducesAPayloadWithAnEmptyDataObjectInsteadOfFailing() {
    com.simisinc.platform.domain.events.Event unmapped = new com.simisinc.platform.domain.events.Event() {
      @Override
      public String getDomainEventType() {
        return "not-a-real-event-type";
      }
    };

    String json = BuildWebhookPayloadCommand.build(unmapped, "deadbeef-0000-0000-0000-000000000000");
    assertNotNull(json, "an unmapped event type must not fail the whole dispatch");
    assertTrue(json.contains("\"data\":{}"));
  }
}
