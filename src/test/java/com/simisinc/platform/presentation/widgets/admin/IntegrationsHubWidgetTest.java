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

package com.simisinc.platform.presentation.widgets.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.SecretSitePropertiesCommand;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.presentation.widgets.admin.IntegrationsHubWidget.SecretStatus;

class IntegrationsHubWidgetTest extends WidgetBase {

  private static SiteProperty property(String name, String label, String value, String type) {
    SiteProperty siteProperty = new SiteProperty();
    siteProperty.setName(name);
    siteProperty.setLabel(label);
    siteProperty.setValue(value);
    siteProperty.setType(type);
    return siteProperty;
  }

  @SuppressWarnings("unchecked")
  private List<SecretStatus> execute() {
    new IntegrationsHubWidget().execute(widgetContext);
    return (List<SecretStatus>) widgetContext.getRequest().getAttribute("secretStatusList");
  }

  private SecretStatus find(List<SecretStatus> list, String name) {
    return list.stream().filter(s -> s.getName().equals(name)).findFirst()
        .orElseThrow(() -> new AssertionError(name + " was not in the list"));
  }

  @Test
  void listsEveryRegisteredSecretPropertyName() {
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(anyString())).thenReturn(null);

      List<SecretStatus> list = execute();

      assertEquals(SecretSitePropertiesCommand.getSecretPropertyNames().size(), list.size());
      for (String name : SecretSitePropertiesCommand.getSecretPropertyNames()) {
        find(list, name);
      }
    }
  }

  @Test
  void anUnsetPropertyIsMarkedNotSetWithNoPageStillReturningNullFromTheRepository() {
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(anyString())).thenReturn(null);

      SecretStatus status = find(execute(), "mail.password");

      assertFalse(status.isSet());
      assertFalse(status.isDisabled());
    }
  }

  @Test
  void aPropertyWithAValueIsMarkedSetAndLinksToItsOwningSettingsPage() {
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(anyString())).thenReturn(null);
      repository.when(() -> SitePropertyRepository.findByName("mail.password"))
          .thenReturn(property("mail.password", "SMTP Password", "some-value", null));

      SecretStatus status = find(execute(), "mail.password");

      assertTrue(status.isSet());
      assertEquals("/admin/mail-properties", status.getPageUrl());
    }
  }

  @Test
  void aDisabledPropertyIsSurfacedAsUnmanagedWithNoLink() {
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(anyString())).thenReturn(null);
      repository.when(() -> SitePropertyRepository.findByName("ecommerce.stripe.production.secret"))
          .thenReturn(property("ecommerce.stripe.production.secret", "Stripe Secret", "some-value", "disabled"));

      SecretStatus status = find(execute(), "ecommerce.stripe.production.secret");

      assertTrue(status.isDisabled());
      assertNull(status.getPageUrl(), "a disabled (database-managed) secret must not link to an editor");
    }
  }

  @Test
  void aPrefixWithNoAdminEditorHasNoPageLinkEither() {
    // oauth.clientSecret has no sitePropertiesEditor page registered for the "oauth" prefix at all
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(anyString())).thenReturn(null);

      SecretStatus status = find(execute(), "oauth.clientSecret");

      assertFalse(status.isDisabled());
      assertNull(status.getPageUrl());
    }
  }

  @Test
  void resolvesTheModifiedByUserDisplayName() {
    User rotatedBy = new User();
    rotatedBy.setId(5L);
    rotatedBy.setFirstName("Jamie");
    rotatedBy.setLastName("Rivera");

    SiteProperty rotated = property("bi.superset.secret", "Superset Secret", "some-value", null);
    rotated.setModifiedBy(5L);
    rotated.setModified(Timestamp.from(Instant.now()));

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<UserRepository> userRepository = mockStatic(UserRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(anyString())).thenReturn(null);
      repository.when(() -> SitePropertyRepository.findByName("bi.superset.secret")).thenReturn(rotated);
      userRepository.when(() -> UserRepository.findByUserId(5L)).thenReturn(rotatedBy);

      SecretStatus status = find(execute(), "bi.superset.secret");

      assertEquals(rotatedBy.getFullName(), status.getModifiedByName());
      userRepository.verify(() -> UserRepository.findByUserId(5L));
    }
  }

  @Test
  void neverLooksUpAUserWhenNothingHasEverBeenRotated() {
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<UserRepository> userRepository = mockStatic(UserRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(anyString())).thenReturn(null);

      execute();

      userRepository.verifyNoInteractions();
    }
  }

  @Test
  void computesExpiryStatusesForExpiredExpiringSoonAndOk() {
    SiteProperty expired = property("elearning.moodle.token", "Moodle Token", "v", null);
    expired.setExpiresAt(Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)));

    SiteProperty expiringSoon = property("elearning.lrs.key", "LRS Key", "v", null);
    expiringSoon.setExpiresAt(Timestamp.from(Instant.now().plus(5, ChronoUnit.DAYS)));

    SiteProperty ok = property("elearning.lrs.secret", "LRS Secret", "v", null);
    ok.setExpiresAt(Timestamp.from(Instant.now().plus(200, ChronoUnit.DAYS)));

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(anyString())).thenReturn(null);
      repository.when(() -> SitePropertyRepository.findByName("elearning.moodle.token")).thenReturn(expired);
      repository.when(() -> SitePropertyRepository.findByName("elearning.lrs.key")).thenReturn(expiringSoon);
      repository.when(() -> SitePropertyRepository.findByName("elearning.lrs.secret")).thenReturn(ok);

      List<SecretStatus> list = execute();

      assertEquals("expired", find(list, "elearning.moodle.token").getExpiryStatus());
      assertEquals("expiring-soon", find(list, "elearning.lrs.key").getExpiryStatus());
      assertEquals("ok", find(list, "elearning.lrs.secret").getExpiryStatus());
      assertEquals("none", find(list, "mail.password").getExpiryStatus(), "no expiresAt set at all");
    }
  }
}
