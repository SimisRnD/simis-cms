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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberCreatedEvent;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberUpdatedEvent;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;

class SaveEmailCommandTest {

  private static Email email(String address) {
    Email email = new Email();
    email.setEmail(address);
    return email;
  }

  private static MailingList mailingList(long id, String name) {
    MailingList mailingList = new MailingList();
    mailingList.setId(id);
    mailingList.setName(name);
    return mailingList;
  }

  @Test
  void rejectsAnEmptyListOfMailingLists() {
    assertThrows(DataException.class, () -> SaveEmailCommand.saveEmail(email("a@example.com"), new ArrayList<MailingList>()));
  }

  @Test
  void rejectsANullListOfMailingLists() {
    assertThrows(DataException.class, () -> SaveEmailCommand.saveEmail(email("a@example.com"), (List<MailingList>) null));
  }

  @Test
  void rejectsAnInvalidEmailBeforeSavingAnything() {
    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class)) {
      List<MailingList> lists = List.of(mailingList(1L, "News"));

      assertThrows(DataException.class, () -> SaveEmailCommand.saveEmail(email("not-an-email"), lists));

      emailRepo.verifyNoInteractions();
      memberRepo.verifyNoInteractions();
    }
  }

  @Test
  void subscribesToEveryListExactlyOnceAndSavesTheEmailOnlyOnce() throws DataException {
    Email emailBean = email("subscriber@example.com");
    Email saved = email("subscriber@example.com");
    saved.setId(5L);
    List<MailingList> lists = new ArrayList<>();
    lists.add(mailingList(1L, "News"));
    lists.add(mailingList(2L, "Cybersecurity Bulletin"));

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);

      Email result = SaveEmailCommand.saveEmail(emailBean, lists);

      assertEquals(saved, result);
      emailRepo.verify(() -> EmailRepository.add(emailBean), times(1));
      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(saved, lists.get(0)));
      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(saved, lists.get(1)));
      memberCommand.verify(() -> MailingListMemberCommand.triggerEmailSubscriptionProcess(saved, lists.get(0), true));
      memberCommand.verify(() -> MailingListMemberCommand.triggerEmailSubscriptionProcess(saved, lists.get(1), true));
    }
  }

  @Test
  void singleListOverloadDelegatesToTheMultiListSave() throws DataException {
    Email emailBean = email("subscriber@example.com");
    Email saved = email("subscriber@example.com");
    saved.setId(5L);
    MailingList mailingList = mailingList(1L, "News");

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);

      SaveEmailCommand.saveEmail(emailBean, mailingList);

      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(saved, mailingList), times(1));
      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(any(), any()), times(1));
    }
  }

  @Test
  void firesACreatedEventWhenAddEmailToListReportsANewMember() throws DataException {
    Email emailBean = email("subscriber@example.com");
    Email saved = email("subscriber@example.com");
    saved.setId(5L);
    MailingList mailingList = mailingList(1L, "News");
    MailingListMember member = new MailingListMember();
    member.setId(10L);
    member.setEmailAddress("subscriber@example.com");

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);
      memberRepo.when(() -> MailingListMemberRepository.addEmailToList(saved, mailingList))
          .thenReturn(new MailingListMemberRepository.AddToListResult(true, member));

      SaveEmailCommand.saveEmail(emailBean, mailingList);

      ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(eventCaptor.capture()));
      assertEquals(MailingListMemberCreatedEvent.class, eventCaptor.getValue().getClass());
      MailingListMemberCreatedEvent fired = (MailingListMemberCreatedEvent) eventCaptor.getValue();
      assertEquals(member, fired.getMember());
      assertEquals(mailingList, fired.getMailingList());
    }
  }

  @Test
  void firesAnUpdatedResubscribedEventWhenAddEmailToListReportsAReactivation() throws DataException {
    Email emailBean = email("returning@example.com");
    Email saved = email("returning@example.com");
    saved.setId(5L);
    MailingList mailingList = mailingList(1L, "News");
    MailingListMember member = new MailingListMember();
    member.setId(11L);
    member.setEmailAddress("returning@example.com");

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);
      memberRepo.when(() -> MailingListMemberRepository.addEmailToList(saved, mailingList))
          .thenReturn(new MailingListMemberRepository.AddToListResult(false, member));

      SaveEmailCommand.saveEmail(emailBean, mailingList);

      ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(eventCaptor.capture()));
      assertEquals(MailingListMemberUpdatedEvent.class, eventCaptor.getValue().getClass());
      MailingListMemberUpdatedEvent fired = (MailingListMemberUpdatedEvent) eventCaptor.getValue();
      assertEquals("resubscribed", fired.getChangeType());
    }
  }

  @Test
  void doesNotFireAnEventWhenAddEmailToListReturnsNoMember() throws DataException {
    Email emailBean = email("subscriber@example.com");
    Email saved = email("subscriber@example.com");
    saved.setId(5L);
    MailingList mailingList = mailingList(1L, "News");

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);
      // addEmailToList left unstubbed -- default Mockito return is null, matching every other
      // test in this file that never stubs it either

      SaveEmailCommand.saveEmail(emailBean, mailingList);

      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }

  @Test
  void aDuplicateEmailUpdatesTheExistingRecordInsteadOfFailing() throws DataException {
    Email emailBean = email("subscriber@example.com");
    Email existing = email("subscriber@example.com");
    existing.setId(9L);
    List<MailingList> lists = List.of(mailingList(1L, "News"));

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(null);
      emailRepo.when(() -> EmailRepository.findByEmailAddress("subscriber@example.com")).thenReturn(existing);

      Email result = SaveEmailCommand.saveEmail(emailBean, lists);

      assertEquals(existing, result);
      emailRepo.verify(() -> EmailRepository.update(emailBean));
      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(existing, lists.get(0)));
    }
  }
}
