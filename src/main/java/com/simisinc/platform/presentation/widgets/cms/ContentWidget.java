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

import com.simisinc.platform.application.cms.ContentHtmlCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Displays content from the content repository or from embedded HTML
 *
 * @author matt rajkowski
 * @created 4/6/18 9:26 PM
 */
public class ContentWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/content.jsp";
  static String TEMPLATE = "/cms/content.html";

  public WidgetContext execute(WidgetContext context) {

    String view = context.getPreferences().get("view");

    // These views have been extracted to new widgets
    if ("accordion".equals(view)) {
      context.setWidgetName("contentAccordion");
      return context;
    } else if ("cards".equals(view)) {
      context.setWidgetName("contentCards");
      return context;
    } else if ("cardSlider".equals(view)) {
      context.setWidgetName("contentSlider");
      return context;
    } else if ("carousel".equals(view)) {
      context.setWidgetName("contentCarousel");
      return context;
    } else if ("gallery".equals(view)) {
      context.setWidgetName("contentGallery");
      return context;
    } else if ("reveal".equals(view)) {
      context.setWidgetName("contentReveal");
      return context;
    }

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
      LOG.debug("Returning content for the Content manager");
      return context;
    }

    // No content is being shown because it's not set
    if (StringUtils.isBlank(html)) {
      LOG.debug("No content to display");
      return null;
    }

    // Preferences
    context.getRequest().setAttribute("videoBackgroundUrl", context.getPreferences().get("videoBackgroundUrl"));

    // Use the final html
    context.getRequest().setAttribute("contentHtml", html);

    // Handle scripts and iframes
    if (html.contains("<script")) {
      context.getResponse().setHeader("X-XSS-Protection", "0");
    } else if (html.contains("<iframe")) {
      // Allow iframes (can limit later to certain applications)
      context.getResponse().setHeader("X-XSS-Protection", "0");
      //        context.getResponse().setHeader("Content-Security-Policy", "script-src 'self' www.google-analytics.com ajax.googleapis.com;");
      /*
        if (html.contains("youtube.com")) {
          context.getResponse().setHeader("Content-Security-Policy", "child-src 'self' *.youtube.com ;");
        }
        if (html.contains("vimeo.com")) {
          context.getResponse().setHeader("Content-Security-Policy", "default-src *.vimeo.com ;");
          context.getResponse().setHeader("Content-Security-Policy", "script-src *.vimeo.com *.vimeocdn.com *.newrelic.com *.nr-data.net ;");
          context.getResponse().setHeader("Content-Security-Policy", "style-src *.vimeocdn.com ;");
          context.getResponse().setHeader("Content-Security-Policy", "child-src 'self' *.vimeo.com *.vimeocdn.com ;");
        }
      */
    }

    context.setJsp(JSP);
    context.setTemplate(TEMPLATE);
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Publish or Delete content based on the browser action
    return ContentHtmlCommand.performWebAction(context);
  }
}
