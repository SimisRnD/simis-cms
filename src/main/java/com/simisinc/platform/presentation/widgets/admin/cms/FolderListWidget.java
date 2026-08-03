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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 12/12/18 3:26 PM
 */
public class FolderListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/folder-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the folders
    List<Folder> folderList;
    if (context.hasRole("admin")) {
      folderList = FolderRepository.findAll();
    } else {
      folderList = LoadFolderCommand.findAllAuthorizedForUser(context.getUserId());
    }

    // Optionally filter by name -- the list is admin-curated and small (dozens, not
    // pages, of folders), so an in-memory filter here avoids touching the two
    // different data-access paths above (each with its own authorization logic)
    String query = context.getParameter("query");
    context.getRequest().setAttribute(RequestConstants.RECORD_QUERY, query);
    if (StringUtils.isNotBlank(query)) {
      String needle = query.trim().toLowerCase(Locale.ROOT);
      List<Folder> matchingFolderList = new ArrayList<>();
      for (Folder folder : folderList) {
        if (folder.getName() != null && folder.getName().toLowerCase(Locale.ROOT).contains(needle)) {
          matchingFolderList.add(folder);
        }
      }
      folderList = matchingFolderList;
    }

    // Sort by name or # of files (issue #502) -- date/size sort isn't meaningful at the folder
    // level (a folder has no single date or size of its own), so only these two are offered.
    // Applied in-memory for the same reason the search filter above is: the list is small and
    // admin-curated, and re-sorting here avoids touching either findAll() data-access path.
    String sort = context.getParameter("sort", "name");
    Comparator<Folder> comparator;
    switch (sort) {
      case "name_desc":
        comparator = Comparator.comparing(Folder::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).reversed();
        break;
      case "files_desc":
        comparator = Comparator.comparingInt(Folder::getFileCount).reversed();
        break;
      case "files_asc":
        comparator = Comparator.comparingInt(Folder::getFileCount);
        break;
      case "name":
      default:
        sort = "name";
        comparator = Comparator.comparing(Folder::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        break;
    }
    List<Folder> sortedFolderList = new ArrayList<>(folderList);
    sortedFolderList.sort(comparator);
    context.getRequest().setAttribute("sort", sort);
    context.getRequest().setAttribute("folderList", sortedFolderList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }
}
