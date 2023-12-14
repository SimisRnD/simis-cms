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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.cms.ContentHtmlCommand;
import com.simisinc.platform.domain.model.cms.AccordionSection;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Displays a multi-level accordion based on content from the content repository or from embedded HTML
 *
 * @author matt rajkowski
 * @created 4/6/18 9:26 PM
 */
public class ContentAccordionWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String ACCORDION_JSP = "/cms/content-accordion.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Common attributes
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

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

    // Split the content into arrays, based mostly on <hr>
    List<String> cardList = ContentHtmlCommand.extractCardsFromHtml(context, html, null);
    context.getRequest().setAttribute("cardList", cardList);

    // Preferences
    context.getRequest().setAttribute("accordionClass", context.getPreferences().get("class"));
    context.getRequest().setAttribute("innerAccordionClass", context.getPreferences().get("innerClass"));
    context.getRequest().setAttribute("expandTopLevel",
        context.getPreferences().getOrDefault("expandTopLevel", "false"));

    // Use the content itself to find the Accordion Label and Content; If the content begins with an <H1> then create a nested accordion
    List<AccordionSection> sectionList = new ArrayList<>();
    AccordionSection currentSection = null;

    for (String content : cardList) {
      // Check for a section
      if (LOG.isDebugEnabled()) {
        LOG.debug("Original Content: " + content);
      }
      if (content.trim().startsWith("<h1")) {
        int sectionStartIdx = content.indexOf(">", content.indexOf("<h1"));
        int sectionEndIdx = content.indexOf("</h1>", sectionStartIdx);
        if (sectionEndIdx > sectionStartIdx) {
          // Start a new section
          String sectionName = content.substring(sectionStartIdx + 1, sectionEndIdx);
          currentSection = new AccordionSection(sectionName);
          sectionList.add(currentSection);
        }
      }
      // Must have a section before adding data
      if (currentSection == null) {
        currentSection = new AccordionSection();
        sectionList.add(currentSection);
      }
      // Determine the label and content area
      // <p><span style="font-weight: 400;">&gt; The label</span></p>
      int labelStartIdx = content.indexOf(">&gt;") + 5;
      int labelEndIdx = content.indexOf("</", labelStartIdx);
      int tagEndIdx = content.indexOf("</p>", labelEndIdx) + 4;
      // Split the label and content into a new card
      String label = content.substring(labelStartIdx, labelEndIdx).trim();
      String card = content.substring(tagEndIdx);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Found label: " + label);
        LOG.debug("Found card: " + card);
      }
      currentSection.getLabelsList().add(label);
      currentSection.getContentList().add(card);
    }
    context.getRequest().setAttribute("sectionList", sectionList);

    // Show the accordion
    context.setJsp(ACCORDION_JSP);

    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Publish or Delete content based on the browser action
    return ContentHtmlCommand.performWebAction(context);
  }
}
