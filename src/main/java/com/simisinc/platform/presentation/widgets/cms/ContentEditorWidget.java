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

package com.simisinc.platform.presentation.widgets.cms;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.DateCommand;
import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.cms.SaveContentCommand;
import com.simisinc.platform.application.cms.TinyMceCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.events.cms.WebPageUpdatedEvent;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/17/18 8:09 PM
 */
public class ContentEditorWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/content-editor.jsp";
  static String CODE_EDITOR_JSP = "/cms/content-code-editor.jsp";

  public WidgetContext execute(WidgetContext context) {

    // This is a demo capability
    String codeContent = context.getPreferences().get("codeContent");
    if (codeContent != null) {
      context.getRequest().setAttribute("codeContent", codeContent);

      String returnPage = context.getPreferences().get("returnPage");
      context.getRequest().setAttribute("returnPage", returnPage);

      context.setJsp(CODE_EDITOR_JSP);
      return context;
    }

    // Determine the page being edited
    String uniqueId = context.getParameter("uniqueId");
    if (StringUtils.isEmpty(uniqueId)) {
      return context;
    }
    Content content = ContentRepository.findByUniqueId(uniqueId);
    if (content == null) {
      content = new Content();
      content.setUniqueId(uniqueId);
    }
    context.getRequest().setAttribute("content", content);

    // Determine the HTML
    String contentHtml = content.getContent();
    if (content.getDraftContent() != null) {
      context.getRequest().setAttribute("isDraft", "true");
      contentHtml = content.getDraftContent();
    }

    // Set Icons to Span for TinyMCE editor
    if (contentHtml != null) {
      // Handle conventions used in TinyMCE for editing
      contentHtml = TinyMceCommand.prepareContentForEditor(contentHtml);
    }
    context.getRequest().setAttribute("contentHtml", contentHtml);

    // Determine the return page
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    context.getRequest().setAttribute("returnPage", returnPage);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Determine the content's uniqueId
    String uniqueId = context.getParameter("uniqueId");
    if (StringUtils.isEmpty(uniqueId)) {
      context.setErrorMessage("Content id must be specified");
      return context;
    }

    // Check for editor content
    String contentHtml = context.getParameter("content");
    if (contentHtml == null) {
      LOG.error("DEVELOPER: Content parameter was not found");
      context.setErrorMessage("A system error occurred");
      return context;
    }

    // Determine the page to return to
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (StringUtils.isEmpty(returnPage)) {
      returnPage = "/";
    }
    context.setRedirect(returnPage);

    // Determine if this is a draft being removed
    String saveAction = context.getParameter("save");
    if ("Remove this Draft".equalsIgnoreCase(saveAction)) {
      removeDraft(uniqueId);
      return context;
    }

    // Determine if the content is immediately published or saved as draft
    boolean publish = true;
    if ("Save as Draft".equalsIgnoreCase(saveAction)) {
      publish = false;
      LOG.debug("Saving as draft...");
    }
    try {
      Content content = SaveContentCommand.saveSafeContent(uniqueId, contentHtml, context.getUserId(), publish);
      if (content == null) {
        LOG.warn("Content record was not saved!");
        context.setErrorMessage("An error occurred");
      } else {
        if (publish) {
          // The web page has content which was just updated
          WebPage webPage = LoadWebPageCommand.loadByLink(returnPage);
          if (webPage != null) {
            // Check for events
            boolean justUpdatedInTheLastDay = DateCommand.isHoursOld(webPage.getModified(), 24);

            // Update the related page
            WebPageRepository.markAsModified(webPage, context.getUserId());

            // Trigger events
            if (justUpdatedInTheLastDay) {
              WorkflowManager.triggerWorkflowForEvent(new WebPageUpdatedEvent(webPage));
            }
          }
        }
      }
    } catch (DataException e) {
      LOG.error("DEVELOPER: Content parameter was not found");
      context.setErrorMessage("A system error occurred");
    }
    return context;
  }

  private void removeDraft(String uniqueId) {
    Content content = LoadContentCommand.loadContentByUniqueId(uniqueId);
    ContentRepository.removeDraft(content);
  }
}
