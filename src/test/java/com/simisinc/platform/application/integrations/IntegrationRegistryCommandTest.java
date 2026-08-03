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

package com.simisinc.platform.application.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.application.webhooks.WebhookEventTypeCommand;
import com.simisinc.platform.domain.model.integrations.IntegrationAuthType;
import com.simisinc.platform.domain.model.integrations.IntegrationDefinition;

class IntegrationRegistryCommandTest {

  @Test
  void everyEntryHasAUniqueNonBlankId() {
    Set<String> seen = new HashSet<>();
    for (IntegrationDefinition definition : IntegrationRegistryCommand.getAll()) {
      assertFalse(definition.getId() == null || definition.getId().isBlank(), "id must not be blank");
      assertTrue(seen.add(definition.getId()), "duplicate id: " + definition.getId());
    }
  }

  @Test
  void findByIdReturnsTheMatchingDefinition() {
    Optional<IntegrationDefinition> found = IntegrationRegistryCommand.findById("zerobounce");

    assertTrue(found.isPresent());
    assertEquals("ZeroBounce", found.get().getName());
  }

  @Test
  void findByIdReturnsEmptyForAnUnknownId() {
    assertTrue(IntegrationRegistryCommand.findById("does-not-exist").isEmpty());
  }

  @Test
  void anApiKeyIntegrationHasASitePropertyPrefixAndAtLeastOneCredentialField() {
    IntegrationDefinition zerobounce = IntegrationRegistryCommand.findById("zerobounce").orElseThrow();

    assertEquals(IntegrationAuthType.API_KEY, zerobounce.getAuthType());
    assertFalse(zerobounce.getCredentialFields().isEmpty());
    assertFalse(zerobounce.getSitePropertyPrefix() == null || zerobounce.getSitePropertyPrefix().isBlank());
  }

  @Test
  void aWebhookIntegrationHasExactlyOneCredentialFieldAndSupportedEventTypes() {
    IntegrationDefinition slack = IntegrationRegistryCommand.findById("slack").orElseThrow();

    assertEquals(IntegrationAuthType.WEBHOOK_URL, slack.getAuthType());
    assertEquals(1, slack.getCredentialFields().size());
    assertFalse(slack.getSupportedEventTypeIds().isEmpty());
  }

  @Test
  void everySlackDefaultEventTypeIsAlsoInItsSupportedList() {
    IntegrationDefinition slack = IntegrationRegistryCommand.findById("slack").orElseThrow();

    for (String defaultEventTypeId : slack.getDefaultEventTypeIds()) {
      assertTrue(slack.getSupportedEventTypeIds().contains(defaultEventTypeId),
          defaultEventTypeId + " is a default but not in the supported list");
    }
  }

  @Test
  void everySlackSupportedEventTypeIsARealWebhookEventType() {
    List<String> realEventTypeIds = WebhookEventTypeCommand.getAll().stream()
        .map(WebhookEventTypeCommand.WebhookEventType::getId).toList();
    IntegrationDefinition slack = IntegrationRegistryCommand.findById("slack").orElseThrow();

    for (String eventTypeId : slack.getSupportedEventTypeIds()) {
      assertTrue(realEventTypeIds.contains(eventTypeId), eventTypeId + " is not a real webhook event type");
    }
  }
}
