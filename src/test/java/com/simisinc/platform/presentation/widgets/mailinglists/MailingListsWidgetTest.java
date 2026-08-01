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

package com.simisinc.platform.presentation.widgets.mailinglists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * The "Delete" row action on /admin/mailing-lists submits via confirmPostAction(), a real POST --
 * WebContainerContext checks isPost() before isDelete(), so this always reaches post(), never
 * delete() directly. This widget had no post() override, so every click fell through to
 * GenericWidget's default (a no-op that just logs "MUST OVERRIDE THE DEFAULT POST METHOD"), and the
 * row was never removed. Same dispatch-gap shape as #658/PR #659 (blocked/allowed IP lists) and
 * #562/#646 (MailingListMembersWidget). These tests call post() directly, the method a real request
 * actually reaches, so they fail if this dispatch gap reopens.
 */
class MailingListsWidgetTest extends WidgetBase {

  private static MailingList mailingList(long memberCount) {
    MailingList list = new MailingList();
    list.setId(1L);
    list.setName("newsletter");
    list.setTitle("Newsletter");
    list.setMemberCount(memberCount);
    return list;
  }

  @Test
  void deleteMailingListViaPostRemovesItAndRedirects() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "delete");
    addQueryParameter(widgetContext, "mailingListId", "1");

    MailingList list = mailingList(0);

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      repository.when(() -> MailingListRepository.findById(1L)).thenReturn(list);
      repository.when(() -> MailingListRepository.remove(list)).thenReturn(true);

      WidgetContext result = new MailingListsWidget().post(widgetContext);

      repository.verify(() -> MailingListRepository.remove(list), times(1));
      assertEquals("Mailing list deleted", result.getSuccessMessage());
      assertEquals("/admin/mailing-lists", result.getRedirect());
    }
  }

  @Test
  void postWithoutDeleteCommandDoesNotTouchTheRepository() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "mailingListId", "1");
    // No "command" parameter -- not a delete request.

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      WidgetContext result = new MailingListsWidget().post(widgetContext);

      repository.verify(() -> MailingListRepository.remove(any()), never());
      assertNull(result);
    }
  }

  @Test
  void deleteMailingListViaPostLeavesRecordsWithTooManyMembers() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "delete");
    addQueryParameter(widgetContext, "mailingListId", "1");

    MailingList list = mailingList(11);

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      repository.when(() -> MailingListRepository.findById(1L)).thenReturn(list);

      WidgetContext result = new MailingListsWidget().post(widgetContext);

      repository.verify(() -> MailingListRepository.remove(any()), never());
      assertEquals("Mailing list cannot be deleted, there are related records", result.getWarningMessage());
    }
  }
}
