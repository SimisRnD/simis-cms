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

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.beanutils.BeanUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveWebRedirectCommand;
import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.persistence.cms.WebRedirectRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Creates and edits a {@code web_redirects} row (issue #408): from path, to URL, status code
 * (301/302), and enabled. Mirrors {@code WebhookSubscriptionFormWidget}'s execute()/post() shape,
 * without its secret-flash/rotate/test-send actions -- {@code WebRedirect} has no analog to a
 * webhook's signing secret.
 *
 * @author SimIS Inc.
 */
public class WebRedirectFormWidget extends GenericWidget {

  static final long serialVersionUID = 1L;

  static String JSP = "/admin/web-redirect-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    WebRedirect webRedirect;
    if (context.getRequestObject() != null) {
      webRedirect = (WebRedirect) context.getRequestObject();
    } else {
      long webRedirectId = context.getParameterAsLong("webRedirectId", -1);
      if (webRedirectId > -1) {
        webRedirect = WebRedirectRepository.findById(webRedirectId);
        if (webRedirect == null) {
          context.setErrorMessage("The web redirect was not found");
          webRedirect = new WebRedirect();
        }
      } else {
        webRedirect = new WebRedirect();
      }
    }
    context.getRequest().setAttribute("webRedirect", webRedirect);

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    WebRedirect bean = new WebRedirect();
    BeanUtils.populate(bean, context.getParameterMap());
    // Checkboxes are handled by hand -- an unchecked checkbox submits nothing at all, so
    // BeanUtils.populate would silently leave the field's true default in place rather than
    // clearing it (same convention as WebhookSubscriptionFormWidget's "enabled" handling).
    bean.setEnabled(context.getParameterAsBoolean("enabled"));
    bean.setCreatedBy(context.getUserId());
    bean.setModifiedBy(context.getUserId());

    boolean isNew = bean.getId() == null || bean.getId() <= -1;

    WebRedirect saved;
    try {
      // /admin/web-redirects accepts either "admin" or "content-manager" (see
      // WebRedirectListWidget.hasAccess()) -- pass which one this user actually has so a
      // content-manager can't set an external toUrl (see SaveWebRedirectCommand.save(bean, boolean)).
      saved = SaveWebRedirectCommand.save(bean, context.hasRole("admin"));
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION,
          isNew ? "web_redirect.add" : "web_redirect.update", AuditEventCommand.FAILURE,
          "web_redirect", bean.getId() != null ? String.valueOf(bean.getId()) : null, bean.getFromPath(),
          e.getMessage());
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(bean);
      context.setRedirect("/admin/web-redirect" + (isNew ? "" : "?webRedirectId=" + bean.getId()));
      return context;
    }

    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION,
        isNew ? "web_redirect.add" : "web_redirect.update", AuditEventCommand.SUCCESS,
        "web_redirect", String.valueOf(saved.getId()), saved.getFromPath(), null);

    context.setSuccessMessage("Web redirect saved");
    context.setRedirect("/admin/web-redirect?webRedirectId=" + saved.getId());
    return context;
  }
}
