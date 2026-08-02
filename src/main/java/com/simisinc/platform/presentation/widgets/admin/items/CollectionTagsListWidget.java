/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import com.simisinc.platform.application.items.DeleteTagCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.infrastructure.persistence.items.TagRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import java.util.List;

/**
 * Lists the tags for a collection, with delete support (issue #632)
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class CollectionTagsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908894L;

  static String JSP = "/admin/collection-tags-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the parent collection
    long collectionId = context.getParameterAsLong("collectionId");
    Collection collection = CollectionRepository.findById(collectionId);
    if (collection == null) {
      context.setErrorMessage("Error. Collection was not found.");
      return context;
    }
    context.getRequest().setAttribute("collection", collection);

    // Load the tags
    List<Tag> tagList = TagRepository.findAllByCollectionId(collection.getId());
    context.getRequest().setAttribute("tagList", tagList);

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Determine what's being deleted
    long tagId = context.getParameterAsLong("tagId");
    if (tagId > -1) {
      Tag tag = TagRepository.findById(tagId);
      try {
        DeleteTagCommand.deleteTag(tag);
        context.setSuccessMessage("Tag deleted");
        context.setRedirect("/admin/collection-details?collectionId=" + tag.getCollectionId());
        return context;
      } catch (Exception e) {
        context.setErrorMessage("Error. Tag could not be deleted.");
        return context;
      }
    }

    return context;
  }
}
