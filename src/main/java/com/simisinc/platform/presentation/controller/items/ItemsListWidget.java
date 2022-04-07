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

package com.simisinc.platform.presentation.controller.items;

import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/20/18 2:23 PM
 */
public class ItemsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/items/items-list.jsp";
  static String TABLE_VIEW_JSP = "/items/items-table.jsp";
  static String JOBS_LIST_JSP = "/items/items-jobs-list.jsp";
  static String SEARCH_RESULTS_JSP = "/items/items-search-results-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the view
    String jsp = JSP;
    String view = context.getPreferences().get("view");
    if ("table".equals(view)) {
      jsp = TABLE_VIEW_JSP;
    } else if ("jobs".equals(view)) {
      jsp = JOBS_LIST_JSP;
    }

    // Determine the collection properties
    Collection collection = null;
    String collectionName = context.getPreferences().get("collection");
    if (StringUtils.isNotBlank(collectionName)) {
      collection = CollectionRepository.findByName(collectionName);
      if (collection == null) {
        LOG.warn("Specified collection was not found: " + collectionName);
        return null;
      }
    } else {
      String collectionUniqueId = context.getPreferences().get("collectionUniqueId");
      if (StringUtils.isNotBlank(collectionUniqueId)) {
        collection = LoadCollectionCommand.loadCollectionByUniqueId(collectionUniqueId);
        if (collection == null) {
          LOG.warn("Specified collectionUniqueId was not found: " + collectionUniqueId);
          return null;
        }
      }
    }
    if (collection == null) {
      LOG.warn("Set a collection or collectionUniqueId preference");
      return null;
    }

    // Validate access to the collection
    if (LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(collection.getId(), context.getUserId()) == null) {
      LOG.warn("User does not have access to this collection");
      return null;
    }
    context.getRequest().setAttribute("collection", collection);

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    String sortBy = context.getPreferences().get("sortBy");
    if ("new".equals(sortBy)) {
      constraints.setColumnToSortBy("created", "desc");
    }
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    // Determine criteria
    ItemSpecification specification = new ItemSpecification();
    specification.setCollectionId(collection.getId());
    specification.setForUserId(context.getUserId());
    if (!context.hasRole("admin") && !context.hasRole("data-manager")) {
      specification.setApprovedOnly(true);
    }

    long categoryId = context.getParameterAsLong("categoryId");
    if (categoryId > -1) {
      Category category = CategoryRepository.findById(categoryId);
      if (category != null && category.getCollectionId() == collection.getId()) {
        specification.setCategoryId(categoryId);
        context.getRequest().setAttribute("category", category);
      }
    }

    // Check shared request values for search criteria
    String searchName = context.getSharedRequestValue("searchName");
    if (searchName == null) {
      searchName = context.getParameter("searchName");
    }
    if (StringUtils.isNotBlank(searchName)) {
      specification.setSearchName(searchName);
    }
    String searchLocation = context.getSharedRequestValue("searchLocation");
    if (searchLocation == null) {
      searchLocation = context.getParameter("searchLocation");
    }
    if (StringUtils.isNotBlank(searchLocation)) {
      specification.setSearchLocation(searchLocation);
      specification.setWithinMeters(48281);
    }
    if (searchName != null || searchLocation != null) {
      jsp = SEARCH_RESULTS_JSP;
    }

    // Sort by nearby items
    String nearbyItemUniqueId = context.getPreferences().get("nearbyItemUniqueId");
    if (StringUtils.isNotBlank(nearbyItemUniqueId)) {
      // Find items nearby based on the specified item
      Item item = ItemRepository.findByUniqueId(nearbyItemUniqueId);
      if (item == null || !item.hasGeoPoint()) {
        return null;
      }
      specification.setExcludeId(item.getId());
      specification.setNearItemId(item.getId());
      specification.setWithinMeters(48281);
    }

    // Query the data
    List<Item> itemList = ItemRepository.findAll(specification, constraints);
    if (itemList == null || itemList.isEmpty()) {
      if (!"true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "false"))) {
        LOG.debug("Skipping, no items found for collection: " + collection.getUniqueId());
        return context;
      }
    }
    context.getRequest().setAttribute("itemList", itemList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("showPaging", context.getPreferences().getOrDefault("showPaging", "true"));
    context.getRequest().setAttribute("returnPage", context.getRequest().getRequestURI());

    // Show the JSP
    context.setJsp(jsp);
    return context;
  }
}
