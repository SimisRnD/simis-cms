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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.items.CategoryException;
import com.simisinc.platform.application.items.SaveCategoryCommand;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.items.CategoryRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/19/18 2:11 PM
 */
public class CollectionCategoryFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/category-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean and parent collection
    long collectionId = -1;
    Category category = (Category) context.getRequestObject();
    if (category != null) {
      context.getRequest().setAttribute("category", category);
      collectionId = category.getCollectionId();
    } else {
      long categoryId = context.getParameterAsLong("categoryId");
      category = CategoryRepository.findById(categoryId);
      if (category != null) {
        context.getRequest().setAttribute("category", category);
        collectionId = category.getCollectionId();
      }
    }

    // Determine the collection
    if (collectionId == -1) {
      collectionId = context.getParameterAsLong("collectionId");
    }
    Collection collection = CollectionRepository.findById(collectionId);
    if (collection == null) {
      context.setErrorMessage("Error. Collection was not found.");
      return context;
    }
    context.getRequest().setAttribute("collection", collection);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    Category categoryBean = new Category();
    BeanUtils.populate(categoryBean, context.getParameterMap());
    categoryBean.setCreatedBy(context.getUserId());

    // Save the collection
    Category category = null;
    try {
      category = SaveCategoryCommand.saveCategory(categoryBean);
      if (category == null) {
        throw new CategoryException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | CategoryException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(categoryBean);
      if (categoryBean.getId() > -1) {
        context.setWarningMessage("This name appears to be a duplicate. Please try again.");
        context.setRedirect("/admin/category?categoryId=" + categoryBean.getId());
      } else {
        context.setRedirect("/admin/collection-categories?collectionId=" + categoryBean.getCollectionId());
      }
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Category was saved");
    context.setRedirect("/admin/collection-categories?collectionId=" + category.getCollectionId());
    return context;
  }
}
