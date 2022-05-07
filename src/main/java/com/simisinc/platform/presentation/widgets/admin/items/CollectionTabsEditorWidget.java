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

package com.simisinc.platform.presentation.widgets.admin.items;

import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.CollectionRole;
import com.simisinc.platform.domain.model.items.CollectionTab;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRoleRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionTabRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/13/21 12:00 PM
 */
public class CollectionTabsEditorWidget extends GenericWidget {

  public static final String allowedChars = "abcdefghijklmnopqrstuvwyxz1234567890";
  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/collection-tabs-editor.jsp";

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

    // Determine the parent collection
    long collectionId = context.getParameterAsLong("collectionId");
    Collection collection = CollectionRepository.findById(collectionId);
    if (collection == null) {
      context.setErrorMessage("Error. Collection was not found.");
      return context;
    }
    context.getRequest().setAttribute("collection", collection);

    // Show the current tabs
    List<CollectionTab> collectionTabList = CollectionTabRepository.findAllByCollectionId(collectionId);
    context.getRequest().setAttribute("collectionTabList", collectionTabList);

    // Show the available roles
    List<CollectionRole> collectionRoleList = CollectionRoleRepository.findAllAvailableForCollectionId(collectionId);
    context.getRequest().setAttribute("collectionRoleList", collectionRoleList);

    // Determine the return page
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (StringUtils.isBlank(returnPage)) {
      returnPage = "/admin/collection-tabs?collectionId=" + collection.getId();
    }
    context.getRequest().setAttribute("returnPage", returnPage);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Determine the parent collection
    long collectionId = context.getParameterAsLong("collectionId");
    Collection collection = CollectionRepository.findById(collectionId);
    if (collection == null) {
      context.setErrorMessage("Collection id must be specified.");
      return context;
    }

    // Start with the existing list
    List<CollectionTab> collectionTabList = CollectionTabRepository.findAllByCollectionId(collectionId);

    // Work the web page entries into it
    HashMap<Integer, CollectionTab> map = new HashMap<>();
    int count = 1;
    while (context.getParameter("order" + count) != null) {
      long tabId = context.getParameterAsLong("tab" + count);
      int tabOrder = context.getParameterAsInt("order" + count);
      String name = context.getParameter("name" + count).trim();
      String link = context.getParameter("link" + count).trim();
      boolean enabled = context.getParameterAsBoolean("enabled" + count);
      // Skip new blank entries
      if (tabId == -1 && StringUtils.isBlank(name)) {
        ++count;
        continue;
      }
      // Use the specified link or create one
      if (StringUtils.isBlank(link)) {
        link = generateLinkFromName(name);
      }
      // Validate the link
      if (!link.startsWith("/")) {
        link = "/" + link;
      }
      // Entries with names will be saved, otherwise deleted
      if (tabId == -1) {
        // This is a new tab
        CollectionTab tab = new CollectionTab(collection);
        tab.setTabOrder(tabOrder);
        tab.setName(name);
        tab.setLink(link);
        tab.setEnabled(enabled);

        // @todo NEXT!!
//        tab.setRoleIdList();

        String numberValue = tabOrder + (count < 10 ? "0" + count : String.valueOf(count));
        map.put(Integer.parseInt(numberValue), tab);
      } else {
        for (CollectionTab tab : collectionTabList) {
          if (tab.getId() == tabId) {
            // This is an existing tab
            tab.setTabOrder(tabOrder);
            tab.setName(name);
            tab.setLink(link);
            tab.setEnabled(enabled);
            String numberValue = tabOrder + (count < 10 ? "0" + count : String.valueOf(count));
            map.put(Integer.parseInt(numberValue), tab);
            break;
          }
        }
      }
      ++count;
    }

    // Sort the entries
    List<CollectionTab> linkList = new ArrayList<>();
    ArrayList<Integer> sortedKeys = new ArrayList<>(map.keySet());
    Collections.sort(sortedKeys);
    for (Integer key : sortedKeys) {
      linkList.add(map.get(key));
    }

    // Save the tabs
    if (!CollectionTabRepository.save(linkList)) {
      LOG.warn("Tabs were not saved!");
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
