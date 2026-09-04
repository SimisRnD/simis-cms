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

import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.application.cms.ContentHtmlCommand;
import com.simisinc.platform.application.cms.ContentVideoCommand;
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
    context.getRequest().setAttribute("videoBackgroundUrl", context.getPreferences().get("videoBackgroundUrl"));

    // Opt-in "Last updated" line. Off unless a page asks for it: it is wanted on the pages where
    // currency is the point -- a policy, a certification, a published standard -- and is noise on a
    // marketing panel or a call-to-action block, which is most of them.
    context.getRequest().setAttribute("showLastUpdated", context.getPreferences().get("showLastUpdated"));

    // Use the final html
    context.getRequest().setAttribute("contentHtml", html);

    // Report any self-hosted videos this block shows, so the page can describe them as VideoObject
    // (issue #1795). Read from the finished html rather than from the stored content, because that
    // is what the visitor and the crawler are actually given.
    context.setVideos(ContentVideoCommand.findVideos(html));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Publish or Delete content based on the browser action
    return ContentHtmlCommand.performWebAction(context);
  }

  public WidgetContext post(WidgetContext context) {
    // The approve form is POSTed so the step-up credential never travels over GET
    String action = context.getParameter("action");
    if ("approve".equals(action)) {
      execute(context);
      if (!context.hasJsp()) {
        return context;
      }
      return ContentHtmlCommand.performContentApproval(context);
    }
    // The inline editor's Save Draft button submits via a real POST (platform-editor.js
    // saveContentDraft()), so it arrives here rather than in action() below -- forward it through
    // the same dispatch action() uses for a GET caller (issue #812).
    if ("saveDraft".equals(action)) {
      return action(context);
    }
    return context;
  }
}
