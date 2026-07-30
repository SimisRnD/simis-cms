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

package com.simisinc.platform.presentation.widgets.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.SaveRoleCapabilitiesCommand;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.infrastructure.persistence.CapabilityRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

class RoleCapabilitiesFormWidgetTest extends WidgetBase {

  private static Role contentManagerRole() {
    Role role = new Role();
    role.setId(2);
    role.setCode("content-manager");
    role.setTitle("Content Manager");
    return role;
  }

  private static Capability capability(String code, long id) {
    Capability capability = new Capability();
    capability.setId(id);
    capability.setCode(code);
    return capability;
  }

  @Test
  void executeIsRefusedWithoutAdminManagePermission() {
    // WidgetBase's default session has no capability list populated at all (null), matching a
    // pre-migration session or one without admin:manage.
    addQueryParameter(widgetContext, "roleId", "2");

    WidgetContext result = new RoleCapabilitiesFormWidget().execute(widgetContext);

    assertNull(result);
  }

  @Test
  void executeShowsTheRolesCurrentCapabilities() {
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));
    addQueryParameter(widgetContext, "roleId", "2");

    Capability contentManage = capability("content:manage", 1L);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class)) {
      roleRepo.when(() -> RoleRepository.findById(2)).thenReturn(contentManagerRole());
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(contentManage));
      capabilityRepo.when(() -> CapabilityRepository.findAllByRoleId(2)).thenReturn(List.of(contentManage));

      WidgetContext result = new RoleCapabilitiesFormWidget().execute(widgetContext);

      assertEquals(RoleCapabilitiesFormWidget.JSP, result.getJsp());
      assertEquals("content-manager", ((Role) result.getRequest().getAttribute("role")).getCode());
      assertTrue(((Set<?>) result.getRequest().getAttribute("grantedCodes")).contains("content:manage"));
    }
  }

  @Test
  void postIsRefusedWithoutAdminManagePermission() {
    addQueryParameter(widgetContext, "roleId", "2");
    addQueryParameter(widgetContext, "reason", "Trying anyway");

    try (MockedStatic<SaveRoleCapabilitiesCommand> saveCommand = mockStatic(SaveRoleCapabilitiesCommand.class)) {
      new RoleCapabilitiesFormWidget().post(widgetContext);

      saveCommand.verifyNoInteractions();
    }
  }

  @Test
  void postCollectsCheckedCapabilitiesAndSaves() throws Exception {
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));
    addQueryParameter(widgetContext, "roleId", "2");
    addQueryParameter(widgetContext, "reason", "Expanding scope");
    addQueryParameter(widgetContext, "capability1", "true");
    // capability2 intentionally left unchecked/absent

    Capability contentManage = capability("content:manage", 1L);
    Capability dataManage = capability("data:manage", 2L);
    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<SaveRoleCapabilitiesCommand> saveCommand = mockStatic(SaveRoleCapabilitiesCommand.class)) {
      roleRepo.when(() -> RoleRepository.findById(2)).thenReturn(contentManagerRole());
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(contentManage, dataManage));

      WidgetContext result = new RoleCapabilitiesFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveRoleCapabilitiesCommand.save(any(), any(),
          eq(Set.of("content:manage")), eq("Expanding scope")));
      assertEquals("/admin/role-capabilities", result.getRedirect());
    }
  }

  @Test
  void postShowsTheErrorAndReturnsToTheFormWhenTheCommandRefuses() throws Exception {
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));
    addQueryParameter(widgetContext, "roleId", "2");
    addQueryParameter(widgetContext, "reason", "Testing the guard");

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<SaveRoleCapabilitiesCommand> saveCommand = mockStatic(SaveRoleCapabilitiesCommand.class)) {
      roleRepo.when(() -> RoleRepository.findById(2)).thenReturn(contentManagerRole());
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of());
      saveCommand.when(() -> SaveRoleCapabilitiesCommand.save(any(), any(), any(), any()))
          .thenThrow(new DataException("Cannot revoke \"admin:manage\" - it's the only role that currently has it."));

      WidgetContext result = new RoleCapabilitiesFormWidget().post(widgetContext);

      assertEquals("Cannot revoke \"admin:manage\" - it's the only role that currently has it.", result.getErrorMessage());
      assertEquals("/admin/role-capabilities-form?roleId=2", result.getRedirect());
    }
  }
}
