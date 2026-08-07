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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.SaveCapabilityGrantCommand;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.CapabilityGrant;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.CapabilityGrantRepository;
import com.simisinc.platform.infrastructure.persistence.CapabilityRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

class CapabilityGrantsWidgetTest extends WidgetBase {

  private static User targetUser(long id, String username) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    return user;
  }

  private static Capability capability(String code, long id) {
    Capability capability = new Capability();
    capability.setId(id);
    capability.setCode(code);
    return capability;
  }

  private static CapabilityGrant grant(long id, long userId, long capabilityId, String capabilityCode) {
    CapabilityGrant grant = new CapabilityGrant();
    grant.setId(id);
    grant.setUserId(userId);
    grant.setCapabilityId(capabilityId);
    grant.setCapabilityCode(capabilityCode);
    return grant;
  }

  @Test
  void executeIsRefusedWithoutAdminManagePermission() {
    // WidgetBase's default session has no capability list populated at all (null)
    addQueryParameter(widgetContext, "userId", "10");

    WidgetContext result = new CapabilityGrantsWidget().execute(widgetContext);

    assertNull(result);
  }

  @Test
  void executeShowsTheUsersGrantsAndAllCapabilities() {
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));
    addQueryParameter(widgetContext, "userId", "10");

    Capability reportsExport = capability("reports:export", 3L);
    CapabilityGrant existingGrant = grant(1L, 10L, 3L, "reports:export");
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<CapabilityGrantRepository> grantRepo = mockStatic(CapabilityGrantRepository.class)) {
      userRepo.when(() -> UserRepository.findByUserId(10L)).thenReturn(targetUser(10L, "jsmith"));
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(reportsExport));
      grantRepo.when(() -> CapabilityGrantRepository.findAllByUserId(10L)).thenReturn(List.of(existingGrant));

      WidgetContext result = new CapabilityGrantsWidget().execute(widgetContext);

      assertEquals(CapabilityGrantsWidget.JSP, result.getJsp());
      assertEquals("jsmith", ((User) result.getRequest().getAttribute("targetUser")).getUsername());
      assertEquals(1, ((List<?>) result.getRequest().getAttribute("grantList")).size());
    }
  }

  @Test
  void executeStillRendersTheJspWithTheErrorWhenTheUserIsNotFound() {
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));
    addQueryParameter(widgetContext, "userId", "999");

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      userRepo.when(() -> UserRepository.findByUserId(999L)).thenReturn(null);

      WidgetContext result = new CapabilityGrantsWidget().execute(widgetContext);

      // Must not be null: the container only surfaces a widget's error message onto the page
      // when the widget returns a non-null context, so silently returning null here would drop
      // "User was not found" and render a blank page instead.
      assertEquals(CapabilityGrantsWidget.JSP, result.getJsp());
      assertEquals("User was not found", result.getErrorMessage());
      // context.setErrorMessage() alone is not enough -- page_messages.jspf reads ${errorMessage}
      // from REQUEST scope on a first, non-redirected GET, which only the request attribute (not
      // the WidgetContext field) satisfies.
      assertEquals("User was not found", result.getRequest().getAttribute("errorMessage"));
    }
  }

  @Test
  void executeStillRendersTheJspWithTheErrorWhenUserIdIsBlankOrMissing() {
    // A distinct code path from the "well-formed but nonexistent id" case above -- no userId
    // query parameter at all (e.g. a stale bookmark to the bare page), which resolves to -1
    // rather than a real id.
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));

    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      userRepo.when(() -> UserRepository.findByUserId(-1L)).thenReturn(null);

      WidgetContext result = new CapabilityGrantsWidget().execute(widgetContext);

      userRepo.verify(() -> UserRepository.findByUserId(-1L));
      assertEquals(CapabilityGrantsWidget.JSP, result.getJsp());
      assertEquals("User was not found", result.getErrorMessage());
      assertEquals("User was not found", result.getRequest().getAttribute("errorMessage"));
    }
  }

  @Test
  void postIsRefusedWithoutAdminManagePermission() {
    addQueryParameter(widgetContext, "userId", "10");
    addQueryParameter(widgetContext, "command", "add");

    try (MockedStatic<SaveCapabilityGrantCommand> saveCommand = mockStatic(SaveCapabilityGrantCommand.class)) {
      new CapabilityGrantsWidget().post(widgetContext);

      saveCommand.verifyNoInteractions();
    }
  }

  @Test
  void postAddsAGrant() throws Exception {
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));
    addQueryParameter(widgetContext, "userId", "10");
    addQueryParameter(widgetContext, "command", "add");
    addQueryParameter(widgetContext, "capabilityId", "3");
    addQueryParameter(widgetContext, "reason", "Temporary contractor access");

    Capability reportsExport = capability("reports:export", 3L);
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<SaveCapabilityGrantCommand> saveCommand = mockStatic(SaveCapabilityGrantCommand.class)) {
      userRepo.when(() -> UserRepository.findByUserId(10L)).thenReturn(targetUser(10L, "jsmith"));
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(reportsExport));

      WidgetContext result = new CapabilityGrantsWidget().post(widgetContext);

      saveCommand.verify(() -> SaveCapabilityGrantCommand.grant(any(), any(), eq(reportsExport), anyLong(),
          eq("Temporary contractor access"), isNull()));
      assertEquals("/admin/capability-grants?userId=10", result.getRedirect());
    }
  }

  @Test
  void postRejectsAnUnparseableExpirationDateInsteadOfGrantingPermanently() throws Exception {
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));
    addQueryParameter(widgetContext, "userId", "10");
    addQueryParameter(widgetContext, "command", "add");
    addQueryParameter(widgetContext, "capabilityId", "3");
    addQueryParameter(widgetContext, "reason", "Temporary contractor access");
    addQueryParameter(widgetContext, "expiresAt", "not-a-date");

    Capability reportsExport = capability("reports:export", 3L);
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<SaveCapabilityGrantCommand> saveCommand = mockStatic(SaveCapabilityGrantCommand.class)) {
      userRepo.when(() -> UserRepository.findByUserId(10L)).thenReturn(targetUser(10L, "jsmith"));
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(reportsExport));

      WidgetContext result = new CapabilityGrantsWidget().post(widgetContext);

      // A malformed date must be surfaced as a validation error, not silently treated as
      // "no expiration" (permanent) - that would be the wrong failure direction for a
      // security-relevant field.
      saveCommand.verifyNoInteractions();
      assertEquals("The expiration date could not be understood - please re-enter it", result.getErrorMessage());
    }
  }

  @Test
  void postRevokesAGrantBelongingToTheSameUser() throws Exception {
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));
    addQueryParameter(widgetContext, "userId", "10");
    addQueryParameter(widgetContext, "command", "revoke");
    addQueryParameter(widgetContext, "capabilityGrantId", "1");
    addQueryParameter(widgetContext, "reason", "No longer needed");

    CapabilityGrant existingGrant = grant(1L, 10L, 3L, "reports:export");
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<CapabilityGrantRepository> grantRepo = mockStatic(CapabilityGrantRepository.class);
        MockedStatic<SaveCapabilityGrantCommand> saveCommand = mockStatic(SaveCapabilityGrantCommand.class)) {
      userRepo.when(() -> UserRepository.findByUserId(10L)).thenReturn(targetUser(10L, "jsmith"));
      grantRepo.when(() -> CapabilityGrantRepository.findById(1L)).thenReturn(existingGrant);

      new CapabilityGrantsWidget().post(widgetContext);

      saveCommand.verify(() -> SaveCapabilityGrantCommand.revoke(any(), eq(existingGrant), any(), eq("No longer needed")));
    }
  }

  @Test
  void postRefusesToRevokeAGrantBelongingToADifferentUser() throws Exception {
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));
    addQueryParameter(widgetContext, "userId", "10");
    addQueryParameter(widgetContext, "command", "revoke");
    addQueryParameter(widgetContext, "capabilityGrantId", "1");
    addQueryParameter(widgetContext, "reason", "No longer needed");

    // Belongs to user 99, not the user 10 in this request
    CapabilityGrant otherUsersGrant = grant(1L, 99L, 3L, "reports:export");
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<CapabilityGrantRepository> grantRepo = mockStatic(CapabilityGrantRepository.class);
        MockedStatic<SaveCapabilityGrantCommand> saveCommand = mockStatic(SaveCapabilityGrantCommand.class)) {
      userRepo.when(() -> UserRepository.findByUserId(10L)).thenReturn(targetUser(10L, "jsmith"));
      grantRepo.when(() -> CapabilityGrantRepository.findById(1L)).thenReturn(otherUsersGrant);

      WidgetContext result = new CapabilityGrantsWidget().post(widgetContext);

      saveCommand.verifyNoInteractions();
      assertEquals("Capability grant was not found for this user", result.getErrorMessage());
    }
  }

  @Test
  void postShowsTheErrorWhenTheCommandRefuses() throws Exception {
    widgetContext.getUserSession().setCapabilityList(List.of(capability("admin:manage", 5L)));
    addQueryParameter(widgetContext, "userId", "10");
    addQueryParameter(widgetContext, "command", "add");
    addQueryParameter(widgetContext, "capabilityId", "3");
    addQueryParameter(widgetContext, "reason", "Temporary contractor access");

    Capability reportsExport = capability("reports:export", 3L);
    try (MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<CapabilityRepository> capabilityRepo = mockStatic(CapabilityRepository.class);
        MockedStatic<SaveCapabilityGrantCommand> saveCommand = mockStatic(SaveCapabilityGrantCommand.class)) {
      userRepo.when(() -> UserRepository.findByUserId(10L)).thenReturn(targetUser(10L, "jsmith"));
      capabilityRepo.when(CapabilityRepository::findAll).thenReturn(List.of(reportsExport));
      saveCommand.when(() -> SaveCapabilityGrantCommand.grant(any(), any(), any(), anyLong(), any(), any()))
          .thenThrow(new DataException("jsmith already has an active grant of \"reports:export\""));

      WidgetContext result = new CapabilityGrantsWidget().post(widgetContext);

      assertEquals("jsmith already has an active grant of \"reports:export\"", result.getErrorMessage());
    }
  }
}
