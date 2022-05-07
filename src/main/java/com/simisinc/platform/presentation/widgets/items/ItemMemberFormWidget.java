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
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.application.items.SaveMemberCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.CollectionRole;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.Member;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRoleRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/24/18 11:49 AM
 */
public class ItemMemberFormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/items/item-member-form.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the authorized item
    Item item = LoadItemCommand.loadItemByUniqueId(context.getCoreData().get("itemUniqueId"));
    if (item == null) {
      LOG.warn("No item found");
      return null;
    }
    context.getRequest().setAttribute("item", item);

    // Show all the available roles
    List<CollectionRole> collectionRoleList = CollectionRoleRepository.findAllAvailableForCollectionId(item.getCollectionId());
    if (collectionRoleList.isEmpty()) {
      LOG.warn("No collection roles found");
      return null;
    }
    context.getRequest().setAttribute("collectionRoleList", collectionRoleList);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    Item item = LoadItemCommand.loadItemByUniqueId(context.getParameter("itemUniqueId"));
    if (item == null) {
      return null;
    }

    // Determine the selected user
    String selectedEntry = context.getParameter("selectedEntry");
    if (StringUtils.isBlank(selectedEntry)) {
      context.setErrorMessage("A matching user was not found");
      return context;
    }

    // Load the user
    User user = null;
    if (selectedEntry.contains("@")) {
      user = LoadUserCommand.loadUserByEmailAddress(selectedEntry);
    } else {
      user = LoadUserCommand.loadUser(Long.parseLong(selectedEntry));
    }
    if (user == null) {
      context.setErrorMessage("A matching user was not found");
      return context;
    }

    // Determine the role(s)
    List<CollectionRole> collectionRoleList = CollectionRoleRepository.findAllAvailableForCollectionId(item.getCollectionId());
    List<CollectionRole> memberRoleList = new ArrayList<>();
    if (collectionRoleList != null) {
      long roleId = context.getParameterAsLong("roleId");
      for (CollectionRole role : collectionRoleList) {
        // Check for a single drop-down value
        if (roleId > -1 && roleId == role.getId()) {
          memberRoleList.add(role);
          continue;
        }
        // Check for a checkbox value
        String roleValue = context.getParameter("roleId" + role.getId());
        if (roleValue != null && roleValue.equals(String.valueOf(role.getId()))) {
          memberRoleList.add(role);
        }
      }
    }
    if (memberRoleList.isEmpty()) {
      context.setErrorMessage("A role was not specified");
      return context;
    }

    // Add the member
    Member memberBean = new Member();
    memberBean.setCreatedBy(context.getUserId());
    memberBean.setModifiedBy(context.getUserId());
    memberBean.setItemId(item.getId());
    memberBean.setCollectionId(item.getCollectionId());
    memberBean.setUserId(user.getId());
    memberBean.setRoleList(memberRoleList);
    memberBean.setApprovedBy(context.getUserId());

    Member member = null;
    try {
      member = SaveMemberCommand.saveMember(memberBean);
      if (member == null) {
        throw new DataException("The information could not be saved due to a system error. Please try again.");
      }
    } catch (DataException e) {
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(memberBean);
      return context;
    }

    // Determine the page to return to
    context.setSuccessMessage("Member was added");
    return context;
  }
}
