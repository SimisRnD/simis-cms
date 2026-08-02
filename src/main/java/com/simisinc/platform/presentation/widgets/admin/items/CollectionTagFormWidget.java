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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.items.SaveTagCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.infrastructure.persistence.items.TagRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;

/**
 * Add/edit form for a tag (issue #632)
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class CollectionTagFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908895L;

  static String JSP = "/admin/tag-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Form bean and parent collection
    long collectionId = -1;
    Tag tag = (Tag) context.getRequestObject();
    if (tag != null) {
      context.getRequest().setAttribute("tag", tag);
      collectionId = tag.getCollectionId();
    } else {
      long tagId = context.getParameterAsLong("tagId");
      tag = TagRepository.findById(tagId);
      if (tag != null) {
        context.getRequest().setAttribute("tag", tag);
        collectionId = tag.getCollectionId();
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
    Tag tagBean = new Tag();
    BeanUtils.populate(tagBean, context.getParameterMap());
    tagBean.setCreatedBy(context.getUserId());

    // Save the tag
    Tag tag = null;
    try {
      tag = SaveTagCommand.saveTag(tagBean);
      if (tag == null) {
        throw new DataException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(tagBean);
      if (tagBean.getId() > -1) {
        context.setWarningMessage("This name appears to be a duplicate. Please try again.");
        context.setRedirect("/admin/tag?tagId=" + tagBean.getId());
      } else {
        context.setRedirect("/admin/collection-tags?collectionId=" + tagBean.getCollectionId());
      }
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Tag was saved");
    context.setRedirect("/admin/collection-tags?collectionId=" + tag.getCollectionId());
    return context;
  }
}
