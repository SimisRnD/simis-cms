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

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.application.webhooks.WebhookEventTypeCommand.WebhookEventType;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

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

  /**
   * A genuine, automated version of the cross-check both this class's javadoc and {@code
   * WebhookEventTypeCommand}'s own javadoc describe (issue #452 review finding) -- scans every
   * real {@code Event} subclass under {@code com.simisinc.platform.domain.events.**} via
   * ClassGraph (already a project dependency) and reads each one's {@code ID} field via
   * reflection, rather than comparing two independently hand-maintained literal lists that could
   * silently drift together. {@code item-file-uploaded} is deliberately excluded from the
   * comparison since (by design) no Event subclass declares it.
   */
  @Test
  void matchesEveryRealEventTypeIdReflectedFromTheDomainEventsPackage() throws Exception {
    Set<String> realEventIds = new HashSet<>();
    try (ScanResult scanResult = new ClassGraph()
        .enableClassInfo()
        .acceptPackages("com.simisinc.platform.domain.events")
        .scan()) {
      for (ClassInfo classInfo : scanResult.getSubclasses("com.simisinc.platform.domain.events.Event")) {
        if (classInfo.isAbstract()) {
          continue;
        }
        Field idField = classInfo.loadClass().getField("ID");
        realEventIds.add((String) idField.get(null));
      }
    }
    assertFalse(realEventIds.isEmpty(), "the scan itself found nothing -- likely a broken package name, not zero events");

    Set<String> registeredIds = new HashSet<>();
    for (WebhookEventType type : WebhookEventTypeCommand.getAll()) {
      registeredIds.add(type.getId());
    }
    assertEquals(realEventIds, registeredIds,
        "WebhookEventTypeCommand.EVENT_TYPES must list exactly the real Event subclasses' ids, or a new "
            + "event type silently becomes payload-buildable but never selectable in the admin subscription UI");
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
