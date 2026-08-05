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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.util.List;

import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.persistence.cms.WebRedirectRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WebRequestFilter;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Lists {@code web_redirects} records for the admin panel (issue #408): create/edit links, a
 * quick enable/disable toggle, and delete. Mirrors {@code WebhookSubscriptionListWidget}'s shape.
 *
 * @author SimIS Inc.
 */
public class WebRedirectListWidget extends GenericWidget {

  static final long serialVersionUID = 1L;

  static String JSP = "/admin/web-redirects-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the redirects
    List<WebRedirect> webRedirectList = WebRedirectRepository.findAll();
    context.getRequest().setAttribute("webRedirectList", webRedirectList);

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    if (!hasAccess(context)) {
      return context;
    }
    if ("toggleEnabled".equals(context.getParameter("action"))) {
      return toggleEnabled(context);
    }
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    long webRedirectId = context.getParameterAsLong("webRedirectId", -1);
    WebRedirect record = null;
    if (hasAccess(context)) {
      record = WebRedirectRepository.findById(webRedirectId);
    }
    if (record == null) {
      LOG.warn("Web redirect does not exist or no access: " + webRedirectId);
      context.setErrorMessage("Error. No access to remove this web redirect.");
      return context;
    }

    try {
      boolean removed = WebRedirectRepository.remove(record);
      if (removed) {
        // A deleted from_path may also be defined in the legacy redirects.csv fallback (issue #408);
        // without this, that fallback would keep serving it for the rest of this server's uptime,
        // since it is loaded once at startup and never otherwise refreshed. See
        // WebRequestFilter.purgeCsvFallback() for the full picture, including its limits.
        WebRequestFilter.purgeCsvFallback(record.getFromPath());
      }
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "web_redirect.remove",
          removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
          "web_redirect", String.valueOf(record.getId()), record.getFromPath(), null);
      if (removed) {
        context.setSuccessMessage("Web redirect deleted");
      } else {
        context.setErrorMessage("Error. Web redirect could not be deleted.");
      }
    } catch (Exception e) {
      LOG.error("Web redirect delete failed", e);
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "web_redirect.remove",
          AuditEventCommand.FAILURE, "web_redirect", String.valueOf(record.getId()), record.getFromPath(),
          e.getMessage());
      context.setErrorMessage("Error. Web redirect could not be deleted.");
    }
    return context;
  }

  private WidgetContext toggleEnabled(WidgetContext context) {
    long webRedirectId = context.getParameterAsLong("webRedirectId", -1);
    WebRedirect record = WebRedirectRepository.findById(webRedirectId);
    if (record == null) {
      context.setErrorMessage("Error. Web redirect was not found.");
      return context;
    }
    boolean newValue = !record.getEnabled();
    record.setEnabled(newValue);
    record.setModifiedBy(context.getUserId());
    WebRedirect saved = WebRedirectRepository.update(record);
    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION,
        newValue ? "web_redirect.enable" : "web_redirect.disable",
        saved != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "web_redirect", String.valueOf(record.getId()), record.getFromPath(), null);
    if (saved == null) {
      context.setErrorMessage("Error. Web redirect could not be updated.");
      return context;
    }
    context.setSuccessMessage(newValue ? "Web redirect enabled" : "Web redirect disabled");
    return context;
  }

  // The /admin/web-redirects page is reachable by admin or content-manager (see admin-layout.xml),
  // same pairing /admin/web-pages uses -- so these mutating actions must accept either role rather
  // than the admin-only gate WebhookSubscriptionListWidget uses for its admin-only page.
  private boolean hasAccess(WidgetContext context) {
    return context.hasRole("admin") || context.hasRole("content-manager");
  }
}
