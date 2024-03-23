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
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.LoadTableOfContentsCommand;
import com.simisinc.platform.domain.model.cms.TableOfContents;
import com.simisinc.platform.domain.model.cms.TableOfContentsLink;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Displays a table of contents dynamically from database
 *
 * @author matt rajkowski
 * @created 4/20/18 2:23 PM
 */
public class TableOfContentsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  protected static Log LOG = LogFactory.getLog(TableOfContentsWidget.class);

  static String JSP = "/cms/table-of-contents.jsp";
  static String TEMPLATE = "/cms/table-of-contents.html";

  private static TableOfContents createTableOfContentsFromPreferences(WidgetContext context) {
    // Use a widget preference fallback
    TableOfContents tableOfContents = new TableOfContents();
    List<TableOfContentsLink> linkList = new ArrayList<>();

    // Use the fields preference to determine the item properties to be shown
    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("links");
    if (!entriesList.isEmpty()) {
      for (Map<String, String> valueMap : entriesList) {
        try {
          String name = valueMap.get("name");
          String link = valueMap.get("value");
          linkList.add(new TableOfContentsLink(name, link));
        } catch (Exception e) {
          LOG.error("Could not get property: " + e.getMessage());
        }
      }
    }
    tableOfContents.setEntries(linkList);
    return tableOfContents;
  }

  public WidgetContext execute(WidgetContext context) {

    // Check for a uniqueId
    String uniqueId = context.getPreferences().get("uniqueId");
    if (context.hasRole("admin") || context.hasRole("content-manager")) {
      LOG.debug("Has content manager access!");
      context.getRequest().setAttribute("uniqueId", uniqueId);
      context.getRequest().setAttribute("showEditor", "true");
      context.getRequest().setAttribute("returnPage", context.getRequest().getRequestURI());
    }

    // Look for saved content
    TableOfContents tableOfContents = LoadTableOfContentsCommand.loadByUniqueId(uniqueId, true);
    if (tableOfContents == null) {
      tableOfContents = createTableOfContentsFromPreferences(context);
    }

    // Only show if there is content (or if an Admin wants to see it)
    if (tableOfContents.getEntries() == null || tableOfContents.getEntries().isEmpty()) {
      // Hide the empty TOC if no access
      if (!context.hasRole("admin") && !context.hasRole("content-manager")) {
        LOG.debug("No entries found");
        return null;
      }
    }

    // A few things require the list now...
    if (tableOfContents.getEntries() == null) {
      tableOfContents.setEntries(new ArrayList<>());
    }

    // Compare the entries with the current URI to show active link
    if (!tableOfContents.getEntries().isEmpty()) {
      String uri = context.getUri();
      for (TableOfContentsLink link : tableOfContents.getEntries()) {
        if (uri.equalsIgnoreCase(link.getLink())) {
          link.setActive(true);
          break;
        }
      }
    }

    // See if there is a title and link to insert at the beginning
    String linkValue = context.getPreferences().get("link");
    if (StringUtils.isNotBlank(linkValue) && linkValue.contains("=")) {
      int idx = linkValue.indexOf("=");
      String name = linkValue.substring(0, idx);
      String href = linkValue.substring(idx + 1);
      TableOfContentsLink link = new TableOfContentsLink(name, href);
      //      link.setActive(true);
      tableOfContents.getEntries().add(0, link);
    }

    // Show the JSP
    context.getRequest().setAttribute("tableOfContents", tableOfContents);
    context.setJsp(JSP);
    context.setTemplate(TEMPLATE);
    return context;
  }
}
