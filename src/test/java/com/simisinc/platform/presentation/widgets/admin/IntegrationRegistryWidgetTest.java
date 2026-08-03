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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.integrations.InstallIntegrationCommand;
import com.simisinc.platform.application.integrations.IntegrationRegistryCommand;
import com.simisinc.platform.application.integrations.IntegrationStatusCommand;
import com.simisinc.platform.application.integrations.UninstallIntegrationCommand;
import com.simisinc.platform.domain.model.integrations.IntegrationDefinition;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.admin.IntegrationRegistryWidget.IntegrationCard;

class IntegrationRegistryWidgetTest extends WidgetBase {

  @SuppressWarnings("unchecked")
  private List<IntegrationCard> execute() {
    new IntegrationRegistryWidget().execute(widgetContext);
    return (List<IntegrationCard>) widgetContext.getRequest().getAttribute("integrationCardList");
  }

  private IntegrationCard find(List<IntegrationCard> cards, String id) {
    return cards.stream().filter(c -> c.getDefinition().getId().equals(id)).findFirst()
        .orElseThrow(() -> new AssertionError(id + " was not in the list"));
  }

  @Test
  void listsEveryRegisteredIntegrationWithItsComputedStatus() {
    try (MockedStatic<IntegrationStatusCommand> status = mockStatic(IntegrationStatusCommand.class)) {
      status.when(() -> IntegrationStatusCommand.isInstalled(any())).thenReturn(false);

      List<IntegrationCard> cards = execute();

      assertEquals(IntegrationRegistryCommand.getAll().size(), cards.size());
      for (IntegrationDefinition definition : IntegrationRegistryCommand.getAll()) {
        find(cards, definition.getId());
      }
    }
  }

  @Test
  void anInstalledIntegrationsCardReflectsThat() {
    try (MockedStatic<IntegrationStatusCommand> status = mockStatic(IntegrationStatusCommand.class)) {
      status.when(() -> IntegrationStatusCommand.isInstalled(argThat(d -> d.getId().equals("zerobounce"))))
          .thenReturn(true);
      status.when(() -> IntegrationStatusCommand.isInstalled(argThat(d -> !d.getId().equals("zerobounce"))))
          .thenReturn(false);

      List<IntegrationCard> cards = execute();

      assertTrue(find(cards, "zerobounce").isInstalled());
      assertFalse(find(cards, "slack").isInstalled());
    }
  }

  @Test
  void anApiKeyIntegrationsCardHasAManageUrlPointingAtItsSettingsPage() {
    try (MockedStatic<IntegrationStatusCommand> status = mockStatic(IntegrationStatusCommand.class)) {
      status.when(() -> IntegrationStatusCommand.isInstalled(any())).thenReturn(false);

      List<IntegrationCard> cards = execute();

      assertEquals("/admin/mailing-list-properties", find(cards, "zerobounce").getManageUrl());
    }
  }

  @Test
  void aWebhookIntegrationsCardHasNoManageUrl() {
    try (MockedStatic<IntegrationStatusCommand> status = mockStatic(IntegrationStatusCommand.class)) {
      status.when(() -> IntegrationStatusCommand.isInstalled(any())).thenReturn(false);

      List<IntegrationCard> cards = execute();

      assertEquals(null, find(cards, "slack").getManageUrl());
    }
  }

  @Test
  void anInstallingQueryParameterIsForwardedToTheJspAttribute() {
    addQueryParameter(widgetContext, "installing", "slack");
    try (MockedStatic<IntegrationStatusCommand> status = mockStatic(IntegrationStatusCommand.class)) {
      status.when(() -> IntegrationStatusCommand.isInstalled(any())).thenReturn(false);

      new IntegrationRegistryWidget().execute(widgetContext);

      assertEquals("slack", widgetContext.getRequest().getAttribute("installingId"));
    }
  }

  @Test
  void postingInstallForAnApiKeyIntegrationForwardsTheCredentialFieldAndRedirectsToTheGallery() throws DataException {
    addQueryParameter(widgetContext, "integrationId", "zerobounce");
    addQueryParameter(widgetContext, "cred_apiKey", "a-real-key");

    try (MockedStatic<InstallIntegrationCommand> install = mockStatic(InstallIntegrationCommand.class)) {
      WidgetContext result = new IntegrationRegistryWidget().post(widgetContext);

      install.verify(() -> InstallIntegrationCommand.install(
          argThat(d -> d.getId().equals("zerobounce")), eq(Map.of("apiKey", "a-real-key")), eq(List.of()),
          eq(widgetContext.getUserId())));
      assertEquals("/admin/integrations", result.getRedirect());
      assertNotNull(result.getSuccessMessage());
    }
  }

  @Test
  void postingInstallForAWebhookIntegrationForwardsTheSelectedEventTypes() throws DataException {
    addQueryParameter(widgetContext, "integrationId", "slack");
    addQueryParameter(widgetContext, "cred_webhookUrl", "https://hooks.slack.com/services/T00/B00/xyz");
    widgetContext.getParameterMap().put("eventType", new String[] { "form-submitted", "order-submitted" });

    try (MockedStatic<InstallIntegrationCommand> install = mockStatic(InstallIntegrationCommand.class)) {
      new IntegrationRegistryWidget().post(widgetContext);

      install.verify(() -> InstallIntegrationCommand.install(argThat(d -> d.getId().equals("slack")),
          eq(Map.of("webhookUrl", "https://hooks.slack.com/services/T00/B00/xyz")),
          eq(List.of("form-submitted", "order-submitted")), eq(widgetContext.getUserId())));
    }
  }

  @Test
  void postingInstallWithAnUnknownIntegrationIdSetsAnErrorAndCallsNothing() {
    addQueryParameter(widgetContext, "integrationId", "does-not-exist");

    try (MockedStatic<InstallIntegrationCommand> install = mockStatic(InstallIntegrationCommand.class)) {
      WidgetContext result = new IntegrationRegistryWidget().post(widgetContext);

      assertNotNull(result.getErrorMessage());
      install.verify(() -> InstallIntegrationCommand.install(any(), any(), any(), anyLong()), never());
    }
  }

  @Test
  void postingInstallWhenTheCommandThrowsRedirectsBackToTheExpandedCardWithTheErrorMessage() throws DataException {
    addQueryParameter(widgetContext, "integrationId", "zerobounce");
    addQueryParameter(widgetContext, "cred_apiKey", "");

    try (MockedStatic<InstallIntegrationCommand> install = mockStatic(InstallIntegrationCommand.class)) {
      install.when(() -> InstallIntegrationCommand.install(any(), any(), any(), anyLong()))
          .thenThrow(new DataException("API Key is required."));

      WidgetContext result = new IntegrationRegistryWidget().post(widgetContext);

      assertEquals("API Key is required.", result.getErrorMessage());
      assertEquals("/admin/integrations?installing=zerobounce", result.getRedirect());
    }
  }

  @Test
  void postingUninstallCallsTheUninstallCommandAndRedirectsToTheGalleryOnSuccess() {
    addQueryParameter(widgetContext, "action", "uninstall");
    addQueryParameter(widgetContext, "integrationId", "slack");

    try (MockedStatic<UninstallIntegrationCommand> uninstall = mockStatic(UninstallIntegrationCommand.class)) {
      uninstall.when(() -> UninstallIntegrationCommand.uninstall(any(), anyLong())).thenReturn(true);

      WidgetContext result = new IntegrationRegistryWidget().post(widgetContext);

      uninstall.verify(() -> UninstallIntegrationCommand
          .uninstall(argThat(d -> d.getId().equals("slack")), eq(widgetContext.getUserId())));
      assertEquals("/admin/integrations", result.getRedirect());
      assertNotNull(result.getSuccessMessage());
    }
  }

  @Test
  void postingUninstallShowsAnErrorRatherThanAFalseSuccessWhenTheCommandReportsFailure() {
    // Issue #455 review: an uninstall that didn't fully complete must not read as a success.
    addQueryParameter(widgetContext, "action", "uninstall");
    addQueryParameter(widgetContext, "integrationId", "slack");

    try (MockedStatic<UninstallIntegrationCommand> uninstall = mockStatic(UninstallIntegrationCommand.class)) {
      uninstall.when(() -> UninstallIntegrationCommand.uninstall(any(), anyLong())).thenReturn(false);

      WidgetContext result = new IntegrationRegistryWidget().post(widgetContext);

      assertNotNull(result.getErrorMessage());
      assertNull(result.getSuccessMessage());
    }
  }
}
