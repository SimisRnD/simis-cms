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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.application.items.CollectionException;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.SaveCollectionCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.CollectionGroup;
import com.simisinc.platform.domain.model.items.PrivacyType;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/18/18 10:25 PM
 */
public class CollectionFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/collection-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // This page can return to different places
    String returnPage = context.getSharedRequestValue("returnPage");
    if (returnPage == null) {
      returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    }
    context.getRequest().setAttribute("returnPage", returnPage);

    // Form bean
    Collection collection = null;
    if (context.getRequestObject() != null) {
      collection = (Collection) context.getRequestObject();
      context.getRequest().setAttribute("collection", collection);
    } else {
      long collectionId = context.getParameterAsLong("collectionId");
      if (collectionId > -1) {
        collection = LoadCollectionCommand.loadCollectionById(collectionId);
        context.getRequest().setAttribute("collection", collection);
      }
    }

    // Set some form values
    List<Group> groupList = GroupRepository.findAll();
    context.getRequest().setAttribute("groupList", groupList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    // Populate the fields
    Collection collectionBean = new Collection();
    BeanUtils.populate(collectionBean, context.getParameterMap());
    collectionBean.setCreatedBy(context.getUserId());

    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));

    // Check for groups
    List<CollectionGroup> collectionGroupList = new ArrayList<>();
    List<Group> groupList = GroupRepository.findAll();
    if (groupList != null) {
      for (Group group : groupList) {
        String groupPrivacyTypeValue = context.getParameter("groupId" + group.getId() + "privacyType");
        if (StringUtils.isNotBlank(groupPrivacyTypeValue)) {
          CollectionGroup collectionGroup = new CollectionGroup();
          collectionGroup.setGroupId(group.getId());
          collectionGroup.setPrivacyType(PrivacyType.getTypeIdFromString(groupPrivacyTypeValue));
          String groupAddPermission = context.getParameter("groupId" + group.getId() + "add");
          collectionGroup.setAddPermission(groupAddPermission != null);
          String groupEditPermission = context.getParameter("groupId" + group.getId() + "edit");
          collectionGroup.setEditPermission(groupEditPermission != null);
          String groupDeletePermission = context.getParameter("groupId" + group.getId() + "delete");
          collectionGroup.setDeletePermission(groupDeletePermission != null);
          collectionGroupList.add(collectionGroup);
        }
      }
      if (!collectionGroupList.isEmpty()) {
        collectionBean.setCollectionGroupList(collectionGroupList);
      }
    }

    // Save the collection
    Collection collection = null;
    try {
      collection = SaveCollectionCommand.saveCollection(collectionBean);
      if (collection == null) {
        throw new CollectionException("Your information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException | CollectionException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(collectionBean);
      context.addSharedRequestValue("returnPage", returnPage);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Collection was saved");
    if (StringUtils.isNotBlank(returnPage)) {
      context.setRedirect(returnPage);
    } else {
      context.setRedirect("/admin/collections");
    }
    return context;
  }
}
