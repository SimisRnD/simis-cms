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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

class RotateWebhookSecretCommandTest {

  @Test
  void rotatingReplacesTheSecretWithANewGeneratedOneAndSaves() {
    WebhookSubscription existing = new WebhookSubscription();
    existing.setId(3L);
    existing.setUrl("https://example.com/hooks");
    existing.setSecret("old-secret");
    existing.setEnabled(true);

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findById(3L)).thenReturn(existing);
      repository.when(() -> WebhookSubscriptionRepository.update(any())).thenAnswer(i -> i.getArgument(0));

      WebhookSubscription result = RotateWebhookSecretCommand.rotate(3L, 99L);

      assertNotNull(result);
      assertNotEquals("old-secret", result.getSecret());
      assertEquals(64, result.getSecret().length());
      assertEquals(99L, result.getModifiedBy());
      repository.verify(() -> WebhookSubscriptionRepository.update(argThat(s -> s.getId() == 3L)));
    }
  }

  @Test
  void rotatingAMissingSubscriptionReturnsNullWithoutSaving() {
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findById(404L)).thenReturn(null);

      WebhookSubscription result = RotateWebhookSecretCommand.rotate(404L, 99L);

      assertNull(result);
      repository.verify(() -> WebhookSubscriptionRepository.update(any()), org.mockito.Mockito.never());
    }
  }
}
