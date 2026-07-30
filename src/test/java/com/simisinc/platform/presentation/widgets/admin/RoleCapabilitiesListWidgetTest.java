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
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.infrastructure.persistence.CapabilityRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

class RoleCapabilitiesListWidgetTest extends WidgetBase {

  private static Role role(String code, int id) {
    Role role = new Role();
    role.setId(id);
    role.setCode(code);
    role.setTitle(code);
    return role;
  }

  private static Capability capability(String code, long id) {
    Capability capability = new Capability();
    capability.setId(id);
    capability.setCode(code);
    return capability;
  }

  @Test
  void isRefusedWithoutAdminManagePermission() {
    WidgetContext result = new RoleCapabilitiesListWidget().execute(widgetContext);

    assertNull(result);
  }

  @Test
  void listsEveryRoleWithItsGrantedCapabilities() {
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));

    Role admin = role("admin", 5);
    Role contentManager = role("content-manager", 2);

    try (MockedStatic<RoleRepository> roleRepo = mockStatic(RoleRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class)) {
      roleRepo.when(RoleRepository::findAll).thenReturn(List.of(admin, contentManager));
      capabilityRepo.when(() -> CapabilityRepository.findAllByRoleId(5)).thenReturn(List.of(capability("admin:manage", 5L)));
      capabilityRepo.when(() -> CapabilityRepository.findAllByRoleId(2)).thenReturn(List.of(capability("content:manage", 1L)));

      WidgetContext result = new RoleCapabilitiesListWidget().execute(widgetContext);

      assertEquals(RoleCapabilitiesListWidget.JSP, result.getJsp());
      @SuppressWarnings("unchecked")
      Map<Integer, List<Capability>> capabilitiesByRoleId =
          (Map<Integer, List<Capability>>) result.getRequest().getAttribute("capabilitiesByRoleId");
      assertEquals("admin:manage", capabilitiesByRoleId.get(5).get(0).getCode());
      assertEquals("content:manage", capabilitiesByRoleId.get(2).get(0).getCode());
    }
  }
}
