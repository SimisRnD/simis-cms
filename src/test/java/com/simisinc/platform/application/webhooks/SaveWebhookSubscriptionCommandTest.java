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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

/**
 * Verifies {@link SaveWebhookSubscriptionCommand}'s validation and its secret handling: a new
 * subscription is issued a fresh, generated secret; an update never touches -- and can never be
 * made to overwrite -- the persisted secret, no matter what the incoming form bean carries
 * (issue #453, alongside the {@code WebhookSubscriptionRepository} at-rest encryption fix).
 */
class SaveWebhookSubscriptionCommandTest {

  private static WebhookSubscription bean(long id, String url, List<String> eventTypes, boolean enabled) {
    WebhookSubscription bean = new WebhookSubscription();
    bean.setId(id);
    bean.setUrl(url);
    bean.setEventTypeList(eventTypes);
    bean.setEnabled(enabled);
    bean.setCreatedBy(42L);
    bean.setModifiedBy(42L);
    return bean;
  }

  @Test
  void aNewSubscriptionIsIssuedAFreshGeneratedSecret() throws DataException {
    WebhookSubscription bean = bean(-1L, "https://example.com/hooks", List.of("web-page-published"), true);

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebhookSubscription saved = SaveWebhookSubscriptionCommand.save(bean);

      assertNotNull(saved.getSecret());
      assertEquals(64, saved.getSecret().length(), "expected a generated hex secret");
      repository.verify(() -> WebhookSubscriptionRepository.save(argThat(s -> s.getCreatedBy() == 42L)));
    }
  }

  @Test
  void twoNewSubscriptionsGetDifferentSecrets() throws DataException {
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebhookSubscription first = SaveWebhookSubscriptionCommand
          .save(bean(-1L, "https://example.com/a", List.of("web-page-published"), true));
      WebhookSubscription second = SaveWebhookSubscriptionCommand
          .save(bean(-1L, "https://example.com/b", List.of("web-page-published"), true));

      assertNotEquals(first.getSecret(), second.getSecret());
    }
  }

  @Test
  void updatingAnExistingSubscriptionNeverChangesItsPersistedSecret() throws DataException {
    WebhookSubscription existing = new WebhookSubscription();
    existing.setId(5L);
    existing.setUrl("https://example.com/old");
    existing.setEventTypeList(List.of("web-page-published"));
    existing.setSecret("original-secret-value");
    existing.setEnabled(true);

    // The incoming form bean has no secret field at all -- BeanUtils.populate never sets one,
    // matching WebhookSubscriptionFormWidget's real behavior (the form has no secret input).
    WebhookSubscription editBean = bean(5L, "https://example.com/new", List.of("order-submitted"), false);

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findById(5L)).thenReturn(existing);
      repository.when(() -> WebhookSubscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebhookSubscription saved = SaveWebhookSubscriptionCommand.save(editBean);

      assertEquals("original-secret-value", saved.getSecret());
      assertEquals("https://example.com/new", saved.getUrl());
      assertEquals(List.of("order-submitted"), saved.getEventTypeList());
      assertEquals(false, saved.getEnabled());
    }
  }

  @Test
  void aBlankUrlIsRejected() {
    WebhookSubscription bean = bean(-1L, "", List.of("web-page-published"), true);
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      assertThrows(DataException.class, () -> SaveWebhookSubscriptionCommand.save(bean));
      repository.verify(() -> WebhookSubscriptionRepository.save(any()), never());
    }
  }

  @Test
  void aMalformedUrlIsRejected() {
    WebhookSubscription bean = bean(-1L, "not-a-url", List.of("web-page-published"), true);
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      assertThrows(DataException.class, () -> SaveWebhookSubscriptionCommand.save(bean));
      repository.verify(() -> WebhookSubscriptionRepository.save(any()), never());
    }
  }

  @Test
  void atLeastOneEventTypeIsRequired() {
    WebhookSubscription bean = bean(-1L, "https://example.com/hooks", List.of(), true);
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      DataException e = assertThrows(DataException.class, () -> SaveWebhookSubscriptionCommand.save(bean));
      assertTrue(e.getMessage().toLowerCase().contains("event type"));
      repository.verify(() -> WebhookSubscriptionRepository.save(any()), never());
    }
  }

  @Test
  void aNewSubscriptionCarriesTheBeansIntegrationId() throws DataException {
    WebhookSubscription bean = bean(-1L, "https://hooks.slack.com/services/T00/B00/xyz", List.of("form-submitted"), true);
    bean.setIntegrationId("slack");

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebhookSubscription saved = SaveWebhookSubscriptionCommand.save(bean);

      assertEquals("slack", saved.getIntegrationId());
    }
  }

  @Test
  void editingAnExistingRegistryInstalledSubscriptionThroughTheGenericFormDoesNotUntagIt() throws DataException {
    // Issue #455: the standalone webhook-subscription admin form has no integrationId field, so a
    // form-submitted bean always carries integrationId=null. An admin editing a Slack-installed
    // subscription's url/events through that generic form must not silently strip its tag --
    // otherwise uninstalling Slack later would no longer find this row.
    WebhookSubscription existing = new WebhookSubscription();
    existing.setId(5L);
    existing.setUrl("https://hooks.slack.com/services/T00/B00/xyz");
    existing.setEventTypeList(List.of("form-submitted"));
    existing.setSecret("original-secret-value");
    existing.setEnabled(true);
    existing.setIntegrationId("slack");

    WebhookSubscription editBean = bean(5L, "https://hooks.slack.com/services/T00/B00/new", List.of("order-submitted"), true);
    // editBean.getIntegrationId() is null here, exactly as a real form-populated bean would be.

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findById(5L)).thenReturn(existing);
      repository.when(() -> WebhookSubscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      WebhookSubscription saved = SaveWebhookSubscriptionCommand.save(editBean);

      assertEquals("slack", saved.getIntegrationId());
    }
  }

  @Test
  void editingAMissingSubscriptionThrows() {
    WebhookSubscription bean = bean(999L, "https://example.com/hooks", List.of("web-page-published"), true);
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findById(999L)).thenReturn(null);

      assertThrows(DataException.class, () -> SaveWebhookSubscriptionCommand.save(bean));
      repository.verify(() -> WebhookSubscriptionRepository.save(any()), never());
    }
  }
}
