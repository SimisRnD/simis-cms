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

import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.application.cms.ContentHtmlCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Displays slider based on content from the content repository or from embedded HTML
 *
 * @author matt rajkowski
 * @created 4/6/18 9:26 PM
 */
public class ContentSliderWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/content-card-slider.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Common attributes
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine if the editor button is shown
    if (EditorPermissionCommand.canEditContent(context.getUserSession())) {
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

    // Preferences
    context.getRequest().setAttribute("carouselClass", context.getPreferences().get("carouselClass"));
    context.getRequest().setAttribute("showControls", context.getPreferences().getOrDefault("showControls", "true"));
    context.getRequest().setAttribute("showLeftControl",
        context.getPreferences().getOrDefault("showLeftControl", "true"));
    context.getRequest().setAttribute("showRightControl",
        context.getPreferences().getOrDefault("showRightControl", "true"));
    context.getRequest().setAttribute("showPagination",
        context.getPreferences().getOrDefault("showPagination", "true"));
    context.getRequest().setAttribute("loop", context.getPreferences().getOrDefault("loop", "true"));
    context.getRequest().setAttribute("autoplayDelay", context.getPreferences().getOrDefault("autoplayDelay", "-1"));

    // Split the content into arrays, based mostly on <hr>
    List<String> cardList = ContentHtmlCommand.extractCardsFromHtml(context, html, null);
    cardList = promoteFirstHeroImage(cardList, context.getPreferences().get("carouselClass"));
    context.getRequest().setAttribute("cardList", cardList);

    context.getRequest().setAttribute("cardClass", context.getPreferences().get("cardClass"));
    context.setJsp(JSP);
    return context;
  }

  /**
   * The first slide of a hero-banner is the page's LCP element, but ContentImageSrcsetCommand
   * (issue #411/#975) marks every content image {@code loading="lazy"} -- which defers that LCP
   * image and drags LCP/Speed Index down. For a hero-banner only, promote the first slide's image to
   * {@code loading="eager" fetchpriority="high"} and drop the {@code decoding="async"} so it is
   * fetched and decoded up front; every later slide stays lazy. Scoped to {@code hero-banner} so an
   * off-screen card slider does not eagerly fetch its first image. Mirrors ContentCarouselWidget's
   * position-aware loading, adapted to this widget's verbatim {@code ${card}} output (issue #1814).
   */
  static List<String> promoteFirstHeroImage(List<String> cardList, String carouselClass) {
    if (cardList == null || cardList.isEmpty()
        || carouselClass == null || !carouselClass.contains("hero-banner")) {
      return cardList;
    }
    String first = cardList.get(0);
    if (!first.contains("<img ")) {
      return cardList;
    }
    String promoted = first
        .replace(" loading=\"lazy\"", "")
        .replace(" decoding=\"async\"", "")
        .replaceFirst("<img ", "<img loading=\"eager\" fetchpriority=\"high\" ");
    List<String> updated = new ArrayList<>(cardList);
    updated.set(0, promoted);
    return updated;
  }

  public WidgetContext action(WidgetContext context) {
    // Publish or Delete content based on the browser action
    return ContentHtmlCommand.performWebAction(context);
  }
}
