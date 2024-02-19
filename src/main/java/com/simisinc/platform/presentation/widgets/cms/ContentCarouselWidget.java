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
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Displays a rotating carousel based on content from the content repository or from embedded HTML
 *
 * @author matt rajkowski
 * @created 4/6/18 9:26 PM
 */
public class ContentCarouselWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String CAROUSEL_JSP = "/cms/content-carousel.jsp";
  static String CAROUSEL_TEMPLATE = "/cms/content-carousel.html";

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

    // Split the content into arrays, based mostly on <hr>
    List<String> cardList = ContentHtmlCommand.extractCardsFromHtml(context, html, null);
    context.getRequest().setAttribute("cardList", cardList);

    // Check the preferences
    context.getRequest().setAttribute("carouselSize", context.getPreferences().getOrDefault("carouselSize", "small"));
    context.getRequest().setAttribute("carouselClass", context.getPreferences().get("carouselClass"));
    context.getRequest().setAttribute("carouselTitle", context.getPreferences().get("carouselTitle"));
    context.getRequest().setAttribute("showControls", context.getPreferences().getOrDefault("showControls", "true"));
    context.getRequest().setAttribute("showLeftControl",
        context.getPreferences().getOrDefault("showLeftControl", "true"));
    context.getRequest().setAttribute("showRightControl",
        context.getPreferences().getOrDefault("showRightControl", "true"));
    context.getRequest().setAttribute("showBullets", context.getPreferences().getOrDefault("showBullets", "true"));

    // Determine any carousel data options
    StringBuilder dataOptions = new StringBuilder();
    String timerDelayValue = context.getPreferences().getOrDefault("timerDelay", "-1");
    int timerDelay = Integer.parseInt(timerDelayValue);
    if (timerDelay > 0) {
      dataOptions.append("data-timer-delay=\"").append(timerDelayValue).append("\"");
    }
    String pauseOnHoverValue = context.getPreferences().getOrDefault("pauseOnHover", "true");
    if ("false".equals(pauseOnHoverValue)) {
      if (dataOptions.length() > 0) {
        dataOptions.append(" ");
      }
      dataOptions.append("data-pause-on-hover=\"false\"");
    }
    if (dataOptions.length() > 0) {
      context.getRequest().setAttribute("dataOptions", dataOptions.toString());
    }

    // Determine how the content will be displayed (typically as a complete text block)
    String display = context.getPreferences().getOrDefault("display", "text");
    context.getRequest().setAttribute("display", display);
    if ("images".equals(display)) {
      // Use the content itself to extract image tag attributes
      List<String> updatedCardList = new ArrayList<>();
      for (String originalCard : cardList) {
        // Determine the image
        // <p><img src="/assets/img/20190826142844-128/Small%20Business.jpg" alt="" /></p>
        int imgAttributesStartIdx = originalCard.indexOf("<img ") + 5;
        int imgAttributesEndIdx = originalCard.indexOf(">", imgAttributesStartIdx);
        String attributes = originalCard.substring(imgAttributesStartIdx, imgAttributesEndIdx);
        if (attributes.endsWith("/")) {
          attributes = attributes.substring(0, attributes.length() - 1);
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Found image attributes: " + attributes);
        }
        updatedCardList.add(attributes);
      }
      context.getRequest().setAttribute("cardList", updatedCardList);
    }

    context.setJsp(CAROUSEL_JSP);
    context.setTemplate(CAROUSEL_TEMPLATE);
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Publish or Delete content based on the browser action
    return ContentHtmlCommand.performWebAction(context);
  }
}
