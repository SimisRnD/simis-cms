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

package com.simisinc.platform.presentation.controller.cms;

import com.granule.CSSFastMin;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Stylesheet;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.StylesheetRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.simisinc.platform.presentation.controller.RequestConstants.ERROR_MESSAGE_TEXT;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/27/21 8:49 AM
 */
public class CssEditorWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  protected static Log LOG = LogFactory.getLog(CssEditorWidget.class);

  static String CSS_EDITOR_JSP = "/cms/css-editor.jsp";

  public WidgetContext execute(WidgetContext context) {

    // The default JSP
    context.setJsp(CSS_EDITOR_JSP);

    // Specify the return page
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    context.getRequest().setAttribute("returnPage", returnPage);

    Stylesheet stylesheet = null;

    // The POST was triggered
    if (context.getRequestObject() != null) {
      // Determine the reason...
      stylesheet = (Stylesheet) context.getRequestObject();
      context.getRequest().setAttribute("stylesheet", stylesheet);
      // There was a post error so let the user make changes
      return context;
    }

    // Determine the stylesheet based on web page

    // Allow either webPageId or just webPage
    long webPageId = context.getParameterAsLong("webPageId");
    if (webPageId > -1) {
      // Use the webPageId
      WebPage webPage = WebPageRepository.findById(webPageId);
      if (webPage != null) {
        webPageId = webPage.getId();
      }
    } else {
      // Use the page path
      String webPageLinkValue = context.getParameter("webPage");
      if (StringUtils.isNotEmpty(webPageLinkValue)) {
        WebPage webPage = WebPageRepository.findByLink(webPageLinkValue);
        if (webPage != null) {
          webPageId = webPage.getId();
        } else {
          context.getRequest().setAttribute(ERROR_MESSAGE_TEXT, "This page does not exist or it is a template page -- CSS will not be saved");
          return context;
        }
      }
    }
    context.getRequest().setAttribute("webPageId", webPageId);

    // Look for a related stylesheet
    stylesheet = StylesheetRepository.findByWebPageId(webPageId);
    if (stylesheet == null) {
      LOG.debug("Starting a new stylesheet...");
      stylesheet = new Stylesheet();
      stylesheet.setWebPageId(webPageId);
    }

    // Prepare for viewing
    context.getRequest().setAttribute("stylesheet", stylesheet);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Determine the stylesheet being edited
    long id = context.getParameterAsLong("id", -1);
    long webPageId = context.getParameterAsLong("webPageId");

    Stylesheet stylesheet = StylesheetRepository.findById(id);
    if (stylesheet == null) {
      LOG.info("Could not find an existing stylesheet, ready to create a new one: " + webPageId);
      stylesheet = new Stylesheet();
      stylesheet.setWebPageId(webPageId);
    }

    // Check for content
    String css = context.getParameter("css");
    if (StringUtils.isBlank(css)) {
      // Content is being removed
      stylesheet.setCss(null);
    } else {
      stylesheet.setCss(css);
    }

    if (!StringUtils.isEmpty(stylesheet.getCss())) {
      // Validate the CSS before saving and alert the user
      try {

        CSSFastMin cssMin = new CSSFastMin();
        cssMin.minimize(stylesheet.getCss());

        // validate the CSS...
        // matching {} counts, etc.
        // matching /* */

      } catch (Exception e) {
        LOG.error("User input: CSS did not validate: " + e.getMessage());
        context.setRequestObject(stylesheet);
        context.setErrorMessage("The CSS could not be validated. Error reported: " + e.getMessage());
        return context;
      }
    }

    // Save the stylesheet
    StylesheetRepository.save(stylesheet);

    // Determine the page to return to
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (!StringUtils.isEmpty(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/");
    }
    return context;
  }
}
