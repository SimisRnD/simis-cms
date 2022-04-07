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

package com.simisinc.platform.presentation.controller.admin.items;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.items.SaveCollectionRelationshipCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.CollectionRelationship;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRelationshipRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/26/18 1:27 PM
 */
public class CollectionRelationshipFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/collection-relationship-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean and parent collection
    long collectionId = -1;
    CollectionRelationship relationship = (CollectionRelationship) context.getRequestObject();
    if (relationship != null) {
      context.getRequest().setAttribute("relationship", relationship);
      collectionId = relationship.getCollectionId();
    } else {
      long relationshipId = context.getParameterAsLong("relationshipId");
      relationship = CollectionRelationshipRepository.findById(relationshipId);
      if (relationship != null) {
        context.getRequest().setAttribute("relationship", relationship);
        collectionId = relationship.getCollectionId();
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

    // Determine the collections this one can be related to
    List<Collection> collectionList = CollectionRepository.findAll();
    context.getRequest().setAttribute("collectionList", collectionList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    CollectionRelationship relationshipBean = new CollectionRelationship();
    BeanUtils.populate(relationshipBean, context.getParameterMap());
    relationshipBean.setCreatedBy(context.getUserId());
    relationshipBean.setModifiedBy(context.getUserId());

    boolean reciprocal = (context.getParameter("reciprocal") != null);

    // Save the collection
    CollectionRelationship relationship = null;
    try {
      relationship = SaveCollectionRelationshipCommand.saveRelationship(relationshipBean, reciprocal);
      if (relationship == null) {
        throw new DataException("The information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(relationshipBean);
      context.setRedirect("/admin/collection-relationships?collectionId=" + relationshipBean.getCollectionId());
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Relationship was saved");
    context.setRedirect("/admin/collection-relationships?collectionId=" + relationship.getCollectionId());
    return context;
  }
}
