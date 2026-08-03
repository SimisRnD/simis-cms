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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.SitePropertySettingsPageCommand;
import com.simisinc.platform.application.integrations.InstallIntegrationCommand;
import com.simisinc.platform.application.integrations.IntegrationRegistryCommand;
import com.simisinc.platform.application.integrations.IntegrationStatusCommand;
import com.simisinc.platform.application.integrations.UninstallIntegrationCommand;
import com.simisinc.platform.domain.model.integrations.CredentialField;
import com.simisinc.platform.domain.model.integrations.IntegrationDefinition;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The Settings &gt; Integrations gallery (issue #455): a card per {@link IntegrationRegistryCommand}
 * entry, with an install/uninstall action per card. This is the primary widget on {@code
 * /admin/integrations} -- the read-only secret/rotation audit table issue #454 built lives at
 * {@code /admin/integrations/secrets} now, linked from here, since "Integrations" more naturally
 * means "what can I add" than "what's already configured."
 */
public class IntegrationRegistryWidget extends GenericWidget {

  static final long serialVersionUID = 1L;

  static String JSP = "/admin/integration-registry.jsp";

  private static final String CREDENTIAL_FIELD_PREFIX = "cred_";

  public static class IntegrationCard {
    private final IntegrationDefinition definition;
    private final boolean installed;
    private final String manageUrl;

    IntegrationCard(IntegrationDefinition definition, boolean installed, String manageUrl) {
      this.definition = definition;
      this.installed = installed;
      this.manageUrl = manageUrl;
    }

    public IntegrationDefinition getDefinition() {
      return definition;
    }

    public boolean isInstalled() {
      return installed;
    }

    /** @return the settings page this credential is also editable from, or null when not applicable */
    public String getManageUrl() {
      return manageUrl;
    }
  }

  public WidgetContext execute(WidgetContext context) {
    List<IntegrationCard> integrationCardList = new ArrayList<>();
    for (IntegrationDefinition definition : IntegrationRegistryCommand.getAll()) {
      boolean installed = IntegrationStatusCommand.isInstalled(definition);
      String manageUrl = definition.getSitePropertyPrefix() != null
          ? SitePropertySettingsPageCommand.findPageUrl(
              SitePropertySettingsPageCommand.rootPrefixOf(definition.getSitePropertyPrefix()))
          : null;
      integrationCardList.add(new IntegrationCard(definition, installed, manageUrl));
    }

    context.getRequest().setAttribute("integrationCardList", integrationCardList);
    context.getRequest().setAttribute("installingId", context.getParameter("installing"));
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    if ("uninstall".equals(context.getParameter("action"))) {
      return uninstall(context);
    }
    return install(context);
  }

  private WidgetContext install(WidgetContext context) {
    context.setRedirect("/admin/integrations");
    Optional<IntegrationDefinition> definitionOpt =
        IntegrationRegistryCommand.findById(context.getParameter("integrationId"));
    if (definitionOpt.isEmpty()) {
      context.setErrorMessage("Unknown integration");
      return context;
    }
    IntegrationDefinition definition = definitionOpt.get();

    Map<String, String> credentialValues = new HashMap<>();
    for (CredentialField field : definition.getCredentialFields()) {
      credentialValues.put(field.getName(), context.getParameter(CREDENTIAL_FIELD_PREFIX + field.getName()));
    }
    String[] eventTypeParams = context.getParameterMap().get("eventType");
    List<String> eventTypeIds = eventTypeParams == null ? List.of() : Arrays.asList(eventTypeParams);

    try {
      InstallIntegrationCommand.install(definition, credentialValues, eventTypeIds, context.getUserId());
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "integration.install",
          AuditEventCommand.FAILURE, "integration", definition.getId(), definition.getName(), e.getMessage());
      context.setErrorMessage(e.getMessage());
      context.setRedirect("/admin/integrations?installing=" + definition.getId());
      return context;
    }

    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "integration.install",
        AuditEventCommand.SUCCESS, "integration", definition.getId(), definition.getName(), null);
    context.setSuccessMessage(definition.getName() + " was installed");
    return context;
  }

  private WidgetContext uninstall(WidgetContext context) {
    context.setRedirect("/admin/integrations");
    Optional<IntegrationDefinition> definitionOpt =
        IntegrationRegistryCommand.findById(context.getParameter("integrationId"));
    if (definitionOpt.isEmpty()) {
      context.setErrorMessage("Unknown integration");
      return context;
    }
    IntegrationDefinition definition = definitionOpt.get();

    boolean success = UninstallIntegrationCommand.uninstall(definition, context.getUserId());

    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "integration.uninstall",
        success ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE, "integration", definition.getId(),
        definition.getName(), success ? null : "The uninstall did not fully complete");
    if (!success) {
      context.setErrorMessage(
          "There was a problem uninstalling " + definition.getName() + ". Please check and try again.");
      return context;
    }
    context.setSuccessMessage(definition.getName() + " was uninstalled");
    return context;
  }
}
