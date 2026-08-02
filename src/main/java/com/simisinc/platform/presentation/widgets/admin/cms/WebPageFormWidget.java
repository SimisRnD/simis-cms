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

package com.simisinc.platform.presentation.widgets.admin.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveWebPageCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.SitemapChangeFrequencyOptions;
import com.simisinc.platform.domain.model.cms.SolutionTypeOptions;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;

/**
 * Widget for displaying a system administration form to add/update web pages
 *
 * @author matt rajkowski
 * @created 5/4/18 6:12 PM
 */
public class WebPageFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/web-page-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // This page can return to different places
    context.getRequest().setAttribute("returnPage", UrlCommand.getValidReturnPage(context.getParameter("returnPage")));

    // Form bean
    if (context.getRequestObject() != null) {
      context.getRequest().setAttribute("webPage", context.getRequestObject());
    } else {
      // Allow either webPageId or just webPage
      long webPageId = context.getParameterAsLong("webPageId");
      if (webPageId > -1) {
        WebPage webPage = WebPageRepository.findById(webPageId);
        context.getRequest().setAttribute("webPage", webPage);
      } else {
        // Determine the page being edited
        String webPageLinkValue = context.getParameter("webPage");
        if (StringUtils.isNotEmpty(webPageLinkValue)) {
          WebPage webPage = WebPageRepository.findByLink(webPageLinkValue);
          if (webPage == null) {
            webPage = new WebPage();
            webPage.setLink(webPageLinkValue);
          }
          context.getRequest().setAttribute("webPage", webPage);
        }
      }
    }

    context.getRequest().setAttribute("sitemapChangeFrequencyMap", SitemapChangeFrequencyOptions.map);
    context.getRequest().setAttribute("solutionTypeMap", SolutionTypeOptions.map);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // deletePage is submitted via a real POST (issue #358 moved state-changing admin actions
    // off GET query strings), so it arrives here rather than in action() below. Dispatch
    // through the same table action() uses for a GET caller, before the page save logic
    // below, which doesn't check the action parameter and would otherwise treat a plain
    // deletePage request as a (validation-rejected) page save.
    if ("deletePage".equals(context.getParameter("action"))) {
      return action(context);
    }

    // Load the record to get all the fields
    long webPageId = context.getParameterAsLong("id");
    WebPage webPageBean = WebPageRepository.findById(webPageId);
    if (webPageBean == null) {
      webPageBean = new WebPage();
    }

    // Populate the form fields
    BeanUtils.populate(webPageBean, context.getParameterMap());

    // Handle publish/draft choice
    String publish = context.getParameter("publish");
    if (StringUtils.isBlank(publish)) {
      webPageBean.setDraft(true);
    } else {
      webPageBean.setDraft(false);
    }

    // Handle when value is not sent in request
    String searchable = context.getParameter("searchable");
    if (StringUtils.isBlank(searchable)) {
      webPageBean.setSearchable(false);
    }

    // Handle when value is not sent in request
    String showInSitemap = context.getParameter("showInSitemap");
    if (StringUtils.isBlank(showInSitemap)) {
      webPageBean.setShowInSitemap(false);
    }

    // Parse optional scheduling timestamps (BeanUtils cannot convert String → Timestamp)
    String publishAtStr = context.getParameter("publishAt");
    if (StringUtils.isBlank(publishAtStr)) {
      webPageBean.setPublishAt(null);
    } else {
      try {
        webPageBean.setPublishAt(Timestamp.valueOf(publishAtStr.replace("T", " ") + ":00"));
      } catch (IllegalArgumentException e) {
        context.setErrorMessage("Go live date format is not valid");
        context.setRequestObject(webPageBean);
        return context;
      }
    }
    String expiresAtStr = context.getParameter("expiresAt");
    if (StringUtils.isBlank(expiresAtStr)) {
      webPageBean.setExpiresAt(null);
    } else {
      try {
        webPageBean.setExpiresAt(Timestamp.valueOf(expiresAtStr.replace("T", " ") + ":00"));
      } catch (IllegalArgumentException e) {
        context.setErrorMessage("Expire date format is not valid");
        context.setRequestObject(webPageBean);
        return context;
      }
    }

    // Set the server values
    webPageBean.setCreatedBy(context.getUserId());
    webPageBean.setModifiedBy(context.getUserId());

    // Publishing makes the page live; saving as draft takes it out of live view
    String eventType = webPageBean.getDraft() ? "content.unpublish" : "content.publish";

    // Save the record
    WebPage webPage = null;
    try {
      webPage = SaveWebPageCommand.saveWebPage(webPageBean);
      if (webPage == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, eventType, AuditEventCommand.FAILURE,
          "web_page", String.valueOf(webPageBean.getId()), webPageBean.getTitle(), e.getMessage());
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(webPageBean);
      return context;
    }

    // Record the publish/unpublish
    AuditEventCommand.record(context, AuditEventCommand.CONTENT, eventType, AuditEventCommand.SUCCESS,
        "web_page", String.valueOf(webPage.getId()), webPage.getTitle(), null);

    // Determine the page to return to
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (StringUtils.isEmpty(returnPage)) {
      returnPage = webPage.getLink();
    }
    context.setRedirect(returnPage);
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Permission is required
    if (!context.hasRole("admin")) {
      return context;
    }
    // Find the record
    long webPageId = context.getParameterAsLong("webPageId");
    WebPage webPage = WebPageRepository.findById(webPageId);
    if (webPage == null) {
      context.setErrorMessage("The record was not found");
      return context;
    }
    // Execute the action
    context.setRedirect(webPage.getLink());
    String action = context.getParameter("action");
    if ("deletePage".equals(action)) {
      String targetId = String.valueOf(webPage.getId());
      String targetLabel = webPage.getTitle();
      try {
        WebPageRepository.remove(webPage);
        PublishEventCachePurgeHandler.onPageDeleted(webPage.getLink());
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete", AuditEventCommand.SUCCESS,
            "web_page", targetId, targetLabel, null);
        context.setSuccessMessage("Page was deleted");
      } catch (Exception e) {
        AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete", AuditEventCommand.FAILURE,
            "web_page", targetId, targetLabel, e.getMessage());
        context.setErrorMessage("The page could not be deleted: " + e.getMessage());
      }
    }
    return context;
  }
}
