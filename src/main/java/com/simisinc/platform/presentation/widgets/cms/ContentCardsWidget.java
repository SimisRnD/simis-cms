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

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.cms.ContentHtmlCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Displays a list of cards using content from the content repository or from embedded HTML
 *
 * @author matt rajkowski
 * @created 4/6/18 9:26 PM
 */
public class ContentCardsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String CARD_JSP = "/cms/content-card.jsp";
  static String CARD_TEMPLATE = "/cms/content-card.html";

  public WidgetContext execute(WidgetContext context) {

    // Common attributes
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine if the editor button is shown
    if (context.hasRole("admin") || context.hasRole("content-manager")) {
      context.getRequest().setAttribute("showEditor", "true");
      context.getRequest().setAttribute("returnPage", context.getRequest().getRequestURI());
    }

    // Look for saved content using preferences:uniqueId, preferences:html
    // Set the html editor if the user has permission
    String html = ContentHtmlCommand.getHtmlFromPreferences(context);

    // A message is being shown to the content manager
    if (context.hasJsp()) {
      return context;
    }

    // No content is being shown because it's not set
    if (StringUtils.isBlank(html)) {
      return null;
    }

    // Extra content can be appended to the final HTML
    StringBuilder extraHTMLContent = new StringBuilder();

    // Split the content into arrays, based mostly on <hr>
    List<String> cardList = ContentHtmlCommand.extractCardsFromHtml(context, html, extraHTMLContent);
    context.getRequest().setAttribute("cardList", cardList);
    context.getRequest().setAttribute("extraHTMLContent", extraHTMLContent.toString());

    // Preferences
    context.getRequest().setAttribute("gridMargin", context.getPreferences().getOrDefault("gridMargin", "false"));
    context.getRequest().setAttribute("cardClass", context.getPreferences().get("cardClass"));
    context.setJsp(CARD_JSP);
    context.setTemplate(CARD_TEMPLATE);
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Publish or Delete content based on the browser action
    return ContentHtmlCommand.performWebAction(context);
  }
}
