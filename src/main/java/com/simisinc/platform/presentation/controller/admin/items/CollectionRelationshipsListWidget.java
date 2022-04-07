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

import java.util.List;

import com.simisinc.platform.application.items.LoadCollectionRelationshipListCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.CollectionRelationship;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRelationshipRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/26/18 12:03 PM
 */
public class CollectionRelationshipsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/collection-relationships-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the parent collection
    long collectionId = context.getParameterAsLong("collectionId");
    Collection collection = CollectionRepository.findById(collectionId);
    if (collection == null) {
      context.setErrorMessage("Error. Collection was not found.");
      return context;
    }
    context.getRequest().setAttribute("collection", collection);

    // Find all the available relationships
    List<CollectionRelationship> relationshipList = LoadCollectionRelationshipListCommand.findAllByCollectionId(collectionId);
    context.getRequest().setAttribute("relationshipList", relationshipList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    // Determine what's being deleted
    long relationshipId = context.getParameterAsLong("relationshipId");
    if (relationshipId > -1) {
      CollectionRelationship relationship = CollectionRelationshipRepository.findById(relationshipId);
      try {
        CollectionRelationshipRepository.remove(relationship);
        context.setSuccessMessage("Relationship deleted");
        context.setRedirect("/admin/collection-details?collectionId=" + relationship.getCollectionId());
        return context;
      } catch (Exception e) {
        context.setErrorMessage("Error. Category could not be deleted.");
        return context;
      }
    }
    return context;
  }
}
