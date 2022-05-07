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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.TableOfContents;
import com.simisinc.platform.domain.model.cms.TableOfContentsLink;
import com.simisinc.platform.infrastructure.persistence.cms.TableOfContentsRepository;

import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 12/10/18 9:13 AM
 */
public class TableOfContentsEditorWidget extends GenericWidget {

  public static final String allowedChars = "abcdefghijklmnopqrstuvwyxz1234567890";
  static final long serialVersionUID = -8484048371911908893L;
  static String JSP = "/cms/table-of-contents-editor.jsp";

  public static String generateLinkFromName(String name) {
    // Force lowercase
    name = name.toLowerCase();
    // Replace spaces and special characters
    StringBuilder sb = new StringBuilder();
    final int len = name.length();
    char lastChar = '-';
    for (int i = 0; i < len; i++) {
      char c = name.charAt(i);
      if (allowedChars.indexOf(name.charAt(i)) > -1) {
        sb.append(c);
        lastChar = c;
      } else if (c == '&') {
        sb.append("and");
        lastChar = '&';
      } else if (c == ' ' || c == '-' || c == '/') {
        if (lastChar != '-') {
          sb.append("-");
        }
        lastChar = '-';
      }
    }
    String value = sb.toString();
    while (value.endsWith("-")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  public WidgetContext execute(WidgetContext context) {

    // Determine the table of contents being edited
    String uniqueId = context.getPreferences().getOrDefault("uniqueId", context.getParameter("uniqueId"));
    if (StringUtils.isEmpty(uniqueId)) {
      LOG.warn("A unique id is required");
      return context;
    }
    TableOfContents tableOfContents = TableOfContentsRepository.findByUniqueId(uniqueId);
    if (tableOfContents == null) {
      tableOfContents = new TableOfContents();
      tableOfContents.setTocUniqueId(uniqueId);
    }
    context.getRequest().setAttribute("tableOfContents", tableOfContents);

    // Determine the entries
    if (tableOfContents.getEntries() == null) {
      List<TableOfContentsLink> linkList = new ArrayList<>();
      tableOfContents.setEntries(linkList);
    }

    // Determine the return page
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (returnPage == null) {
      returnPage = context.getUri();
    }
    context.getRequest().setAttribute("returnPage", returnPage);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Determine the content's uniqueId
    String uniqueId = context.getPreferences().getOrDefault("uniqueId", context.getParameter("uniqueId"));
    if (StringUtils.isEmpty(uniqueId)) {
      context.setErrorMessage("TOC uniqueId must be specified");
      return context;
    }

    // Populate the object
    TableOfContents tableOfContentsBean = TableOfContentsRepository.findByUniqueId(uniqueId);
    if (tableOfContentsBean == null) {
      tableOfContentsBean = new TableOfContents();
      tableOfContentsBean.setTocUniqueId(uniqueId);
    }
    tableOfContentsBean.setName(tableOfContentsBean.getTocUniqueId());
    tableOfContentsBean.setCreatedBy(context.getUserId());
    tableOfContentsBean.setModifiedBy(context.getUserId());

    // Determine the entries
    HashMap<Integer, TableOfContentsLink> map = new HashMap<>();
    int count = 1;
    while (context.getParameter("order" + count) != null) {
      int order = context.getParameterAsInt("order" + count);
      String name = context.getParameter("name" + count).trim();
      String link = context.getParameter("link" + count).trim();
      // Use the specified link or create one
      if (StringUtils.isBlank(link)) {
        link = generateLinkFromName(name);
      }
      // Validate the link
      if (!link.startsWith("/") && !link.startsWith("http://") && !link.startsWith("https://")) {
        link = "/" + link;
      }
      // Only add entries with names, otherwise remove/delete the entry
      if (StringUtils.isNotBlank(name)) {
        TableOfContentsLink tableOfContentsLink = new TableOfContentsLink(name, link);
        String numberValue = order + (count < 10 ? "0" + count : String.valueOf(count));
        map.put(Integer.parseInt(numberValue), tableOfContentsLink);
      }
      ++count;
    }

    // Sort the unique entries
    List<TableOfContentsLink> linkList = new ArrayList<>();
    ArrayList<Integer> sortedKeys = new ArrayList<>(map.keySet());
    Collections.sort(sortedKeys);
    for (Integer key : sortedKeys) {
      linkList.add(map.get(key));
    }
    tableOfContentsBean.setEntries(linkList);

    // Save it
    TableOfContents tableOfContents = TableOfContentsRepository.save(tableOfContentsBean);
    if (tableOfContents == null) {
      LOG.warn("TOC record was not saved!");
      context.setErrorMessage("An error occurred");
      return context;
    }

    // Determine the page to return to
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (StringUtils.isEmpty(returnPage)) {
      returnPage = "/";
    }
    context.setRedirect(returnPage);
    return context;
  }
}
