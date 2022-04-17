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

package com.simisinc.platform.presentation.controller.admin.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveWebPageCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;

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

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Load the record to get all the fields
    long webPageId = context.getParameterAsLong("id");
    WebPage webPageBean = WebPageRepository.findById(webPageId);
    if (webPageBean == null) {
      webPageBean = new WebPage();
    }

    // Populate the form fields
    BeanUtils.populate(webPageBean, context.getParameterMap());

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

    // Set the server values
    webPageBean.setCreatedBy(context.getUserId());
    webPageBean.setModifiedBy(context.getUserId());

    // Save the record
    WebPage webPage = null;
    try {
      webPage = SaveWebPageCommand.saveWebPage(webPageBean);
      if (webPage == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(webPageBean);
      return context;
    }

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
      try {
        WebPageRepository.remove(webPage);
        context.setSuccessMessage("Page was deleted");
      } catch (Exception e) {
        context.setErrorMessage("The page could not be deleted: " + e.getMessage());
      }
    }
    return context;
  }
}
