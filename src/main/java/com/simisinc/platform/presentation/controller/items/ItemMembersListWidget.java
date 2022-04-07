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

package com.simisinc.platform.presentation.controller.items;

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.Member;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRoleRepository;
import com.simisinc.platform.infrastructure.persistence.items.MemberRepository;
import com.simisinc.platform.presentation.controller.cms.GenericWidget;
import com.simisinc.platform.presentation.controller.cms.WidgetContext;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/27/18 11:20 AM
 */
public class ItemMembersListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/items/item-members.jsp";

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

    // Load the members
    List<Member> memberList = MemberRepository.findAllForItemId(item.getId());
    if (memberList == null || memberList.isEmpty()) {
      if (!"true".equals(context.getPreferences().getOrDefault("showWhenEmpty", "false"))) {
        // Skip if set
        LOG.debug("Skipping, no members found");
        return context;
      }
    } else {
      // Load the related data
      for (Member member : memberList) {
        member.setRoleList(CollectionRoleRepository.findAllByMember(member));
      }
      context.getRequest().setAttribute("memberList", memberList);
    }

    // Determine permissions for UI
    if (context.hasRole("admin")) {
      context.getRequest().setAttribute("canDelete", "true");
    }

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  /**
   * A member is being removed
   *
   * @param context
   * @return
   */
  public WidgetContext delete(WidgetContext context) {

    // Verify access to the item
    String itemUniqueId = context.getPreferences().getOrDefault("uniqueId", context.getCoreData().get("itemUniqueId"));
    Item item = LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(itemUniqueId, context.getUserId());
    if (item == null) {
      return null;
    }
    Collection collection = LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(item.getCollectionId(), context.getUserId());
    if (collection == null) {
      return null;
    }

    // Check for member to be removed
    long memberId = context.getParameterAsLong("memberId", -1);
    Member record = MemberRepository.findById(memberId);
    if (record == null) {
      LOG.warn("Member record does not exist or no access: " + memberId);
      return null;
    }

    // Remove the member
    try {
      User user = LoadUserCommand.loadUser(record.getUserId());
      MemberRepository.remove(record);
      context.setSuccessMessage(user.getFullName() + " was removed");
      return context;
    } catch (Exception e) {
      context.setErrorMessage("Error. Member could not be removed.");
    }
    return context;
  }
}
