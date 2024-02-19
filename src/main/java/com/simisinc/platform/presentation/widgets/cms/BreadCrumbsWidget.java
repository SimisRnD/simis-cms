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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.ReplaceBlogPostDynamicValuesCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Displays breadcrumbs
 *
 * @author matt rajkowski
 * @created 4/20/18 2:23 PM
 */
public class BreadCrumbsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  
  protected static Log LOG = LogFactory.getLog(BreadCrumbsWidget.class);

  static String JSP = "/cms/breadcrumbs.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine if the editor button is shown
    boolean showEditor = false;

    // Links are stored in the preferences
    String links = context.getPreferences().get("links");
    if (StringUtils.isBlank(links) && !showEditor) {
      return null;
    }

    // Determine if there are dynamic names for the breadcrumbs
    Blog blog = BlogPostWidget.retrieveValidatedBlogFromPreferences(context);
    BlogPost blogPost = BlogPostWidget.retrieveValidatedBlogPostFromUrl(context, blog);

    // Check the widget preferences for a list of breadcrumbs
    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("links");
    if (entriesList.isEmpty() && !showEditor) {
      LOG.debug("Links preference is empty");
      return null;
    }

    // Create a list of the breadcrumb entries to display
    LOG.debug("Entries found: " + entriesList.size());
    Map<String, String> linkList = new LinkedHashMap<>();
    for (Map<String, String> valueMap : entriesList) {
      try {
        // Determine the breadcrumb title and corresponding link
        String label = valueMap.get("name");
        String value = valueMap.get("link");
        if (value == null) {
          value = valueMap.get("value");
        }
        if (value == null) {
          value = "";
        }
        int index = value.indexOf("{param:");
        if (index > -1) {
          String parameterName = value.substring(index + 7, value.indexOf("}", index));
          String requestParam = context.getParameter(parameterName);
          value = StringUtils.replace(value, "{param:" + parameterName + "}", requestParam);
        }
        // Update dynamic values
        if (blogPost != null) {
          label = ReplaceBlogPostDynamicValuesCommand.replaceValues(blogPost, label);
        }
        linkList.put(label, value);
      } catch (Exception e) {
        LOG.error("Could not get property: " + e.getMessage());
      }
    }
    context.getRequest().setAttribute("linkList", linkList);

    // Show the html
    context.setJsp(JSP);
    return context;
  }
}
