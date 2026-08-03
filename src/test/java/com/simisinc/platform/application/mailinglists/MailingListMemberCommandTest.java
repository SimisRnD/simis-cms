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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.jobrunr.scheduling.BackgroundJobRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberUpdatedEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;

/**
 * Verifies {@link MailingListMemberCommand#unsubscribe}, including the issue #452
 * mailing-list-member-updated webhook/workflow event it now fires.
 */
class MailingListMemberCommandTest {

  private static MailingList mailingList(long id, String name) {
    MailingList mailingList = new MailingList();
    mailingList.setId(id);
    mailingList.setName(name);
    return mailingList;
  }

  private static User user(long id, String email) {
    User user = new User();
    user.setId(id);
    user.setEmail(email);
    return user;
  }

  @Test
  void unsubscribeRejectsAMissingMailingList() {
    assertThrows(DataException.class, () -> MailingListMemberCommand.unsubscribe(null, user(1L, "a@example.com")));
  }

  @Test
  void unsubscribeRejectsAMissingUser() {
    assertThrows(DataException.class, () -> MailingListMemberCommand.unsubscribe(mailingList(1L, "News"), null));
  }

  @Test
  void unsubscribeDoesNothingWhenNoEmailRecordExists() throws DataException {
    User user = user(1L, "ghost@example.com");
    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      emailRepo.when(() -> EmailRepository.findByEmailAddress("ghost@example.com")).thenReturn(null);

      MailingListMemberCommand.unsubscribe(mailingList(1L, "News"), user);

      memberRepo.verify(() -> MailingListMemberRepository.unsubscribe(any(), any(), any()), never());
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }

  @Test
  void unsubscribeFiresAnUnsubscribedUpdatedEvent() throws DataException {
    User user = user(1L, "subscriber@example.com");
    MailingList mailingList = mailingList(1L, "News");
    Email email = new Email();
    email.setId(2L);
    email.setEmail("subscriber@example.com");
    MailingListMember member = new MailingListMember();
    member.setId(9L);
    member.setEmailAddress("subscriber@example.com");

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      emailRepo.when(() -> EmailRepository.findByEmailAddress("subscriber@example.com")).thenReturn(email);
      memberRepo.when(() -> MailingListMemberRepository.findByListAndEmail(1L, 2L)).thenReturn(member);

      MailingListMemberCommand.unsubscribe(mailingList, user);

      memberRepo.verify(() -> MailingListMemberRepository.unsubscribe(mailingList, email, user));
      ArgumentCaptor<MailingListMemberUpdatedEvent> eventCaptor = ArgumentCaptor
          .forClass(MailingListMemberUpdatedEvent.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(eventCaptor.capture()));
      MailingListMemberUpdatedEvent fired = eventCaptor.getValue();
      assertEquals(member, fired.getMember());
      assertEquals(mailingList, fired.getMailingList());
      assertEquals(user, fired.getUser());
      assertEquals("unsubscribed", fired.getChangeType());
      assertEquals(true, fired.isPreviouslySubscribed());
    }
  }

  @Test
  void unsubscribeDoesNotFireAnEventWhenTheMemberRowIsGoneAfterUpdate() throws DataException {
    User user = user(1L, "subscriber@example.com");
    MailingList mailingList = mailingList(1L, "News");
    Email email = new Email();
    email.setId(2L);
    email.setEmail("subscriber@example.com");

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      emailRepo.when(() -> EmailRepository.findByEmailAddress("subscriber@example.com")).thenReturn(email);
      // findByListAndEmail left unstubbed -- default null

      MailingListMemberCommand.unsubscribe(mailingList, user);

      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }
}
