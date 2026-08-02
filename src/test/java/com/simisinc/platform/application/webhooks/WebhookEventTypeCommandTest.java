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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.application.webhooks.WebhookEventTypeCommand.WebhookEventType;

/**
 * Guards the hand-maintained event type list against silently drifting from {@code
 * BuildWebhookPayloadCommand}'s real {@code instanceof} chain, and against the dead
 * {@code item-file-uploaded} id (referenced only in item-workflows.yml, no matching Event class)
 * ever being reintroduced as a checkbox that can never actually fire.
 */
class WebhookEventTypeCommandTest {

  @Test
  void everyIdIsUniqueAndNonBlank() {
    Set<String> seen = new HashSet<>();
    for (WebhookEventType type : WebhookEventTypeCommand.getAll()) {
      assertTrue(type.getId() != null && !type.getId().isBlank(), "blank id");
      assertTrue(type.getLabel() != null && !type.getLabel().isBlank(), "blank label for " + type.getId());
      assertTrue(seen.add(type.getId()), "duplicate id: " + type.getId());
    }
  }

  @Test
  void matchesTheRealFourteenEventTypesBuildWebhookPayloadCommandHandles() {
    Set<String> expected = Set.of(
        "web-page-published", "web-page-updated", "blog-post-published",
        "calendar-event-scheduled", "calendar-event-rescheduled", "calendar-event-removed",
        "form-submitted", "order-submitted",
        "user-signed-up", "user-registered", "user-invited", "user-password-reset",
        "unsuspend-requested", "user-account-restored");

    Set<String> actual = new HashSet<>();
    for (WebhookEventType type : WebhookEventTypeCommand.getAll()) {
      actual.add(type.getId());
    }
    assertEquals(expected, actual);
  }

  @Test
  void doesNotIncludeTheDeadItemFileUploadedId() {
    for (WebhookEventType type : WebhookEventTypeCommand.getAll()) {
      assertFalse("item-file-uploaded".equals(type.getId()),
          "item-file-uploaded has no matching Event subclass and can never fire");
    }
  }

  @Test
  void getLabelFallsBackToTheIdForAnUnrecognizedType() {
    assertEquals("some-unknown-type", WebhookEventTypeCommand.getLabel("some-unknown-type"));
  }

  @Test
  void getLabelReturnsTheRealLabelForAKnownType() {
    assertEquals("Web page published", WebhookEventTypeCommand.getLabel("web-page-published"));
  }
}
