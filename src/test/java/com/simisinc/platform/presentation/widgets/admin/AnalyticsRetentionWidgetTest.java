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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * This widget was migrated from hasRole("admin") to hasPermission("admin:manage") as the
 * walking-skeleton call site for issue #701. These tests exist to prove that migration didn't
 * change the widget's actual access decision.
 *
 * @author elizabeth houser
 */
class AnalyticsRetentionWidgetTest extends WidgetBase {

  private static Capability adminManage() {
    Capability capability = new Capability();
    capability.setId(1L);
    capability.setCode("admin:manage");
    return capability;
  }

  @Test
  void grantsAccessWhenTheSessionHasTheAdminManageCapability() {
    widgetContext.getUserSession().setCapabilityList(List.of(adminManage()));

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SessionRepository> sessionRepo = mockStatic(SessionRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("analytics.retentionDays")).thenReturn("90");
      sessionRepo.when(() -> SessionRepository.resolveRetentionDays("90")).thenReturn(90);
      sessionRepo.when(SessionRepository::countSessionsWithPii).thenReturn(3L);

      WidgetContext result = new AnalyticsRetentionWidget().execute(widgetContext);

      assertNotNull(result, "a session with admin:manage should be granted access");
    }
  }

  @Test
  void deniesAccessWhenTheSessionLacksTheAdminManageCapability() {
    widgetContext.getUserSession().setCapabilityList(Collections.emptyList());

    WidgetContext result = new AnalyticsRetentionWidget().execute(widgetContext);

    assertNull(result, "a session without admin:manage should be denied access, same as the old hasRole(\"admin\") check");
  }

  @Test
  void deniesAccessWhenNoCapabilitiesWereEverResolved() {
    // capabilityList left null - simulates a session from before this migration, or any path
    // that never populated it. Must fail closed, exactly like hasRole() does when roleList is null.
    WidgetContext result = new AnalyticsRetentionWidget().execute(widgetContext);

    assertNull(result, "a null capability list must fail closed, not grant access");
  }
}
