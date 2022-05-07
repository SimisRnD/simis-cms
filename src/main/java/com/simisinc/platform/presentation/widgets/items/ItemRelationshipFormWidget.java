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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.items.LoadCollectionRelationshipListCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.application.items.SaveItemRelationshipCommand;
import com.simisinc.platform.domain.model.items.CollectionRelationship;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.ItemRelationship;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/26/18 1:27 PM
 */
public class ItemRelationshipFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/items/item-relationship-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the authorized item
    Item item = LoadItemCommand.loadItemByUniqueId(context.getCoreData().get("itemUniqueId"));
    if (item == null) {
      return null;
    }
    context.getRequest().setAttribute("item", item);

    // Show all the available relationship types
    List<CollectionRelationship> collectionRelationshipList = LoadCollectionRelationshipListCommand.findAllByCollectionId(item.getCollectionId());
    if (collectionRelationshipList.isEmpty()) {
      return null;
    }
    context.getRequest().setAttribute("collectionRelationshipList", collectionRelationshipList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    Item item = LoadItemCommand.loadItemByUniqueId(context.getParameter("itemUniqueId"));
    if (item == null) {
      return null;
    }

    Item relatedItem = LoadItemCommand.loadItemByUniqueId(context.getParameter("relatedItemUniqueId"));
    if (relatedItem == null) {
      return null;
    }

    // Populate the fields
    ItemRelationship relationshipBean = new ItemRelationship();
    BeanUtils.populate(relationshipBean, context.getParameterMap());
    relationshipBean.setCreatedBy(context.getUserId());
    relationshipBean.setModifiedBy(context.getUserId());
    relationshipBean.setItemId(item.getId());
    relationshipBean.setCollectionId(item.getCollectionId());
    relationshipBean.setRelatedItemId(relatedItem.getId());
    relationshipBean.setRelatedCollectionId(relatedItem.getCollectionId());

    // Save the relationship
    ItemRelationship relationship = null;
    try {
      relationship = SaveItemRelationshipCommand.saveRelationship(relationshipBean);
      if (relationship == null) {
        throw new DataException("The information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(relationshipBean);
      context.setRedirect("/show/" + item.getUniqueId());
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Relationship was saved");
    context.setRedirect("/show/" + item.getUniqueId());
    return context;
  }
}
