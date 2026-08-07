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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.application.items.SaveMemberCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.CollectionRole;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.items.Member;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRoleRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Granting collection membership is an administrative action -- item-layout.xml only ever renders
 * this widget's form with role="admin" -- but before this fix, post() enforced nothing itself, so
 * any logged-in user who could reach the action directly (bypassing whatever the UI chooses to
 * render) could grant themselves or anyone else membership on any item's collection.
 *
 * @author Elizabeth Houser
 */
class ItemMemberFormWidgetTest extends WidgetBase {

  private static Item itemWith(long id, long collectionId) {
    Item item = new Item();
    item.setId(id);
    item.setCollectionId(collectionId);
    return item;
  }

  private static CollectionRole role(long id) {
    CollectionRole role = new CollectionRole();
    role.setId(id);
    return role;
  }

  @Test
  void nonAdminCannotAddAMember() throws Exception {
    // Default login carries no admin role.
    addQueryParameter(widgetContext, "itemUniqueId", "item-1");
    addQueryParameter(widgetContext, "selectedEntry", "7");
    addQueryParameter(widgetContext, "roleId", "1");

    try (MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class);
        MockedStatic<SaveMemberCommand> saveMember = mockStatic(SaveMemberCommand.class)) {
      WidgetContext result = new ItemMemberFormWidget().post(widgetContext);

      loadItem.verifyNoInteractions();
      saveMember.verify(() -> SaveMemberCommand.saveMember(any()), never());
      assertEquals(null, result);
    }
  }

  @Test
  void adminAddsAMember() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "itemUniqueId", "item-1");
    addQueryParameter(widgetContext, "selectedEntry", "7");
    addQueryParameter(widgetContext, "roleId", "1");

    Item item = itemWith(10L, 20L);
    User user = new User();
    user.setId(7L);
    user.setFirstName("Jane");
    user.setLastName("Doe");
    List<CollectionRole> roles = new ArrayList<>();
    roles.add(role(1L));
    Member saved = new Member();
    saved.setId(99L);

    try (MockedStatic<LoadItemCommand> loadItem = mockStatic(LoadItemCommand.class);
        MockedStatic<LoadUserCommand> loadUser = mockStatic(LoadUserCommand.class);
        MockedStatic<CollectionRoleRepository> collectionRoles = mockStatic(CollectionRoleRepository.class);
        MockedStatic<SaveMemberCommand> saveMember = mockStatic(SaveMemberCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadItem.when(() -> LoadItemCommand.loadItemByUniqueId("item-1")).thenReturn(item);
      loadUser.when(() -> LoadUserCommand.loadUser(7L)).thenReturn(user);
      collectionRoles.when(() -> CollectionRoleRepository.findAllAvailableForCollectionId(20L)).thenReturn(roles);
      saveMember.when(() -> SaveMemberCommand.saveMember(any())).thenReturn(saved);

      WidgetContext result = new ItemMemberFormWidget().post(widgetContext);

      saveMember.verify(() -> SaveMemberCommand.saveMember(any()));
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("collection.member.add"),
          eq(AuditEventCommand.SUCCESS), eq("user"), eq("7"), any(), any()));
      assertEquals("Member was added", result.getSuccessMessage());
    }
  }
}
