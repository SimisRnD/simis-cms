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

import com.simisinc.platform.application.items.DeleteCategoryCommand;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/19/18 2:06 PM
 */
public class CollectionCategoriesListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/collection-categories-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the parent collection
    long collectionId = context.getParameterAsLong("collectionId");
    Collection collection = CollectionRepository.findById(collectionId);
    if (collection == null) {
      context.setErrorMessage("Error. Collection was not found.");
      return context;
    }
    context.getRequest().setAttribute("collection", collection);

    // Load the categories
    List<Category> categoryList = CategoryRepository.findAllByCollectionId(collection.getId());
    context.getRequest().setAttribute("categoryList", categoryList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Determine what's being deleted
    long categoryId = context.getParameterAsLong("categoryId");
    if (categoryId > -1) {
      Category category = CategoryRepository.findById(categoryId);
      try {
        DeleteCategoryCommand.deleteCategory(category);
        context.setSuccessMessage("Category deleted");
        context.setRedirect("/admin/collection-details?collectionId=" + category.getCollectionId());
        return context;
      } catch (Exception e) {
        context.setErrorMessage("Error. Category could not be deleted.");
//        context.setRedirect("/admin/collections");
        return context;
      }
    }

    return context;
  }
}
