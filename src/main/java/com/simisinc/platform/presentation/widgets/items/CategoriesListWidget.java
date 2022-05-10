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

package com.simisinc.platform.presentation.widgets.items;

import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/25/18 5:45 PM
 */
public class CategoriesListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/items/categories-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the collection
    String collectionUniqueId = context.getPreferences().get("collectionUniqueId");
    Collection collection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(collectionUniqueId, context.getUserId());
    if (collection == null) {
      LOG.warn("Set a collection or collectionUniqueId preference");
      return null;
    }
    context.getRequest().setAttribute("collection", collection);

    // Load the categories
    boolean basedOnItems = "true".equals(context.getPreferences().getOrDefault("basedOnItems", "false"));
    List<Category> categoryList = CategoryRepository.findAllByCollectionId(collection.getId(), basedOnItems);
    if (categoryList == null || categoryList.isEmpty()) {
      return null;
    }
    if (categoryList.size() > 100) {
      return null;
    }
    context.getRequest().setAttribute("categoryList", categoryList);

    // Show the selected category
    long categoryId = context.getParameterAsLong("categoryId");
    if (categoryId > -1) {
      Category category = CategoryRepository.findById(categoryId);
      if (category != null && category.getCollectionId() == collection.getId()) {
        context.getRequest().setAttribute("category", category);
      }
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the directory url
    String listingsLink = context.getPreferences().getOrDefault("listingsLink", collection.getListingsLink());
    context.getRequest().setAttribute("listingsLink", listingsLink);

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }
}
