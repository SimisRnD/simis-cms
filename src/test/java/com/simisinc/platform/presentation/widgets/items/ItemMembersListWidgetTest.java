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

package com.simisinc.platform.presentation.widgets.items;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.Member;
import com.simisinc.platform.infrastructure.persistence.items.MemberRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * Removing a collaboration member must be gated on authorization and bound to the item in context.
 * Before the fix, delete() only checked that the caller could *view* the item, so any user who could
 * reach the widget could remove members by POSTing directly, and a member id from any other item
 * could be removed through it (IDOR). These verify the removal only happens for an admin acting on a
 * member that actually belongs to the item.
 *
 * @author Elizabeth Houser
 */
class ItemMembersListWidgetTest extends WidgetBase {

  private static Item itemWith(long id, long collectionId) {
    Item item = new Item();
    item.setId(id);
    item.setCollectionId(collectionId);
    return item;
  }

  private static Member memberOf(long id, long itemId) {
    Member member = new Member();
    member.setId(id);
    member.setItemId(itemId);
    member.setUserId(7L);
    return member;
  }

  @Test
  void nonAdminCannotRemoveAMember() {
    // Default login carries no admin role.
    addQueryParameter(widgetContext, "memberId", "5");
    widgetContext.getCoreData().put("itemUniqueId", "item-1");

    try (MockedStatic<MemberRepository> members = mockStatic(MemberRepository.class)) {
      new ItemMembersListWidget().delete(widgetContext);
      members.verify(() -> MemberRepository.remove(any()), never());
    }
  }

  @Test
  void adminCannotRemoveAMemberBelongingToAnotherItem() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "memberId", "5");
    widgetContext.getCoreData().put("itemUniqueId", "item-1");

    try (MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<MemberRepository> members = mockStatic(MemberRepository.class)) {
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(any(), anyLong())).thenReturn(itemWith(10L, 20L));
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(anyLong(), anyLong())).thenReturn(new Collection());
      members.when(() -> MemberRepository.findById(anyLong())).thenReturn(memberOf(5L, 99L)); // belongs to item 99, not 10

      new ItemMembersListWidget().delete(widgetContext);

      members.verify(() -> MemberRepository.remove(any()), never());
    }
  }

  @Test
  void adminRemovesAMemberOfThisItem() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "memberId", "5");
    widgetContext.getCoreData().put("itemUniqueId", "item-1");

    Member member = memberOf(5L, 10L); // belongs to the item in context
    try (MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class);
        MockedStatic<LoadCollectionCommand> loadCollection = mockStatic(LoadCollectionCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<MemberRepository> members = mockStatic(MemberRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueIdForAuthorizedUser(any(), anyLong())).thenReturn(itemWith(10L, 20L));
      loadCollection.when(() -> LoadCollectionCommand.loadCollectionByIdForAuthorizedUser(anyLong(), anyLong())).thenReturn(new Collection());
      loadUser.when(() -> LoadUserCommand.loadUser(anyLong())).thenReturn(null);
      members.when(() -> MemberRepository.findById(anyLong())).thenReturn(member);
      members.when(() -> MemberRepository.remove(member)).thenReturn(member); // non-null == removed

      new ItemMembersListWidget().delete(widgetContext);

      members.verify(() -> MemberRepository.remove(member), times(1));
    }
  }
}
