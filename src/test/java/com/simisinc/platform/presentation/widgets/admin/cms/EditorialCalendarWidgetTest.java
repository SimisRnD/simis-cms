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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.presentation.controller.DataConstants;
import com.simisinc.platform.infrastructure.persistence.UserSpecification;

/**
 * Verifies {@link EditorialCalendarWidget} prepares the /admin/editorial-calendar page shell
 * (issue #426): the JSP, the icon/title preferences, and the Author filter dropdown's user list.
 * Mirrors {@code CalendarListWidgetTest}'s WidgetBase + mockStatic(Repository) pattern.
 *
 * @author SimIS Inc.
 */
class EditorialCalendarWidgetTest extends WidgetBase {

  private static User user(long id, String firstName, String lastName, String username) {
    User user = new User();
    user.setId(id);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setUsername(username);
    return user;
  }

  @Test
  void executeSetsTheJsp() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"editorialCalendar\" />");

    try (MockedStatic<UserRepository> users = mockStatic(UserRepository.class)) {
      users.when(() -> UserRepository.findAll(any(), any())).thenReturn(List.of());

      new EditorialCalendarWidget().execute(widgetContext);
    }

    Assertions.assertEquals(EditorialCalendarWidget.JSP, widgetContext.getJsp());
  }

  @Test
  void executeOnlyRequestsEnabledUsers() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"editorialCalendar\" />");

    try (MockedStatic<UserRepository> users = mockStatic(UserRepository.class)) {
      users.when(() -> UserRepository.findAll(any(), any())).thenReturn(List.of());

      new EditorialCalendarWidget().execute(widgetContext);

      users.verify(() -> UserRepository.findAll(
          argThat((UserSpecification s) -> s.getIsEnabled() == DataConstants.TRUE), any()));
    }
  }

  @Test
  void executePopulatesTheAuthorListSortedByDisplayNameCaseInsensitively() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"editorialCalendar\" />");

    User zack = user(1L, "Zack", "Zephyr", "zack");
    User amy = user(2L, "amy", "Anderson", "amy"); // lowercase first name -- proves case-insensitivity

    try (MockedStatic<UserRepository> users = mockStatic(UserRepository.class)) {
      users.when(() -> UserRepository.findAll(any(), any())).thenReturn(List.of(zack, amy));

      new EditorialCalendarWidget().execute(widgetContext);
    }

    List<User> authorList = (List<User>) request.getAttribute("authorList");
    Assertions.assertEquals(2, authorList.size());
    Assertions.assertEquals("amy", authorList.get(0).getFirstName());
    Assertions.assertEquals("Zack", authorList.get(1).getFirstName());
  }

  @Test
  void executeFallsBackToUsernameWhenAUserHasNoNameOnFile() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"editorialCalendar\" />");

    User named = user(1L, "Bo", "Baker", "bo");
    User nameless = user(2L, null, null, "anonymous");

    try (MockedStatic<UserRepository> users = mockStatic(UserRepository.class)) {
      users.when(() -> UserRepository.findAll(any(), any())).thenReturn(List.of(named, nameless));

      // Would throw a NullPointerException from the sort comparator if the null getFullName()
      // were not handled
      new EditorialCalendarWidget().execute(widgetContext);
    }

    List<User> authorList = (List<User>) request.getAttribute("authorList");
    Assertions.assertEquals(2, authorList.size());
    // "anonymous" sorts before "Bo" (a < b)
    Assertions.assertEquals("anonymous", authorList.get(0).getUsername());
    Assertions.assertEquals("bo", authorList.get(1).getUsername());
  }

  @Test
  void executeEchoesTheIconAndTitlePreferences() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"editorialCalendar\"><title>Editorial Calendar</title><icon>fa-calendar-check</icon></widget>");

    try (MockedStatic<UserRepository> users = mockStatic(UserRepository.class)) {
      users.when(() -> UserRepository.findAll(any(), any())).thenReturn(List.of());

      new EditorialCalendarWidget().execute(widgetContext);
    }

    Assertions.assertEquals("Editorial Calendar", request.getAttribute("title"));
    Assertions.assertEquals("fa-calendar-check", request.getAttribute("icon"));
  }
}
