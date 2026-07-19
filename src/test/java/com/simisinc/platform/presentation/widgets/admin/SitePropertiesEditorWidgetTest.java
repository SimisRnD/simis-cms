/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.admin.SecretSitePropertiesCommand;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;

/**
 * Tests the site properties editor, including that masked secret fields do not wipe stored values
 *
 * @author elizabeth houser
 */
class SitePropertiesEditorWidgetTest extends WidgetBase {

  private SiteProperty property(String name, String value, String type) {
    SiteProperty siteProperty = new SiteProperty();
    siteProperty.setName(name);
    siteProperty.setValue(value);
    siteProperty.setType(type);
    return siteProperty;
  }

  @Test
  void secretListMatchesKnownProperties() {
    assertTrue(SecretSitePropertiesCommand.isSecret("mail.password"));
    assertTrue(SecretSitePropertiesCommand.isSecret("ecommerce.stripe.production.secret"));
    // Browser-bound publishable values must never be masked
    assertFalse(SecretSitePropertiesCommand.isSecret("ecommerce.stripe.production.key"));
    assertFalse(SecretSitePropertiesCommand.isSecret("captcha.google.sitekey"));
    assertFalse(SecretSitePropertiesCommand.isSecret("maps.mapbox.accesstoken"));
    assertFalse(SecretSitePropertiesCommand.isSecret(null));
  }

  @Test
  void blankSecretSubmissionKeepsTheStoredValue() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mail.host.name", "smtp.example.com", null));
    stored.add(property("mail.password", "existing-smtp-password", null));

    // The admin edits the host but leaves the masked password field blank
    addQueryParameter(widgetContext, "mail.host.name", "smtp2.example.com");
    addQueryParameter(widgetContext, "mail.password", "");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored))).thenReturn(true);

      SitePropertiesEditorWidget widget = new SitePropertiesEditorWidget();
      widget.post(widgetContext);

      assertEquals("smtp2.example.com", stored.get(0).getValue());
      // The blank masked field must not wipe the stored secret
      assertEquals("existing-smtp-password", stored.get(1).getValue());
    }
  }

  @Test
  void aNewSecretValueIsSaved() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>mail</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("mail.password", "old-password", null));

    addQueryParameter(widgetContext, "mail.password", "new-password");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("mail"), eq(stored))).thenReturn(true);

      SitePropertiesEditorWidget widget = new SitePropertiesEditorWidget();
      widget.post(widgetContext);

      assertEquals("new-password", stored.get(0).getValue());
    }
  }

  @Test
  void blankNonSecretSubmissionStillClearsTheValue() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"sitePropertiesEditor\">\n" +
        "  <prefix>site</prefix>\n" +
        "</widget>");

    List<SiteProperty> stored = new ArrayList<>();
    stored.add(property("site.header.line1", "A header", null));

    addQueryParameter(widgetContext, "site.header.line1", "");

    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SitePropertyRepository.findAllByPrefix(anyString())).thenReturn(stored);
      repository.when(() -> SitePropertyRepository.saveAll(eq("site"), eq(stored))).thenReturn(true);

      SitePropertiesEditorWidget widget = new SitePropertiesEditorWidget();
      widget.post(widgetContext);

      // Pre-existing behavior for normal fields is unchanged
      assertEquals("", stored.get(0).getValue());
    }
  }
}
