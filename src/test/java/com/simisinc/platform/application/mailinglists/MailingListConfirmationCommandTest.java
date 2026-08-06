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

package com.simisinc.platform.application.mailinglists;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberConfirmationRequestedEvent;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailingListConfirmationCommandTest {

  @Test
  void sendConfirmationEmailBuildsTheLinkFromTheSiteUrlAndTheMembersToken() {
    MailingListMember member = new MailingListMember();
    member.setId(1L);
    member.setConfirmToken("tok-123");
    MailingList mailingList = new MailingList();
    mailingList.setId(3L);

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("site.url")).thenReturn("https://example.org");

      MailingListConfirmationCommand.sendConfirmationEmail(member, mailingList);

      ArgumentCaptor<MailingListMemberConfirmationRequestedEvent> captor = ArgumentCaptor
          .forClass(MailingListMemberConfirmationRequestedEvent.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(captor.capture()));
      assertEquals(member, captor.getValue().getMember());
      assertEquals(mailingList, captor.getValue().getMailingList());
      assertTrue(captor.getValue().getConfirmUrl().startsWith("https://example.org/confirm-subscription?token="));
      assertTrue(captor.getValue().getConfirmUrl().endsWith("tok-123"));
    }
  }

  @Test
  void sendConfirmationEmailDoesNothingWhenTheMemberHasNoToken() {
    MailingListMember member = new MailingListMember();
    member.setId(1L);
    MailingList mailingList = new MailingList();
    mailingList.setId(3L);

    try (MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      MailingListConfirmationCommand.sendConfirmationEmail(member, mailingList);

      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }
}
