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
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberCreatedEvent;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberUpdatedEvent;
import com.simisinc.platform.domain.model.maps.GeoIP;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
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
      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(saved, lists.get(0), false, null));
      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(saved, lists.get(1), false, null));
      memberCommand.verify(() -> MailingListMemberCommand.triggerEmailSubscriptionProcess(saved, lists.get(0), true));
      memberCommand.verify(() -> MailingListMemberCommand.triggerEmailSubscriptionProcess(saved, lists.get(1), true));
    }
  }

  @Test
  void firesAPerListEventCarryingThatListsOwnMemberNotAnotherListsWhenSubscribingToSeveralListsAtOnce()
      throws DataException {
    Email emailBean = email("subscriber@example.com");
    Email saved = email("subscriber@example.com");
    saved.setId(5L);
    MailingList listA = mailingList(1L, "News");
    MailingList listB = mailingList(2L, "Cybersecurity Bulletin");
    MailingListMember memberA = new MailingListMember();
    memberA.setId(10L);
    memberA.setEmailAddress("subscriber@example.com");
    MailingListMember memberB = new MailingListMember();
    memberB.setId(20L);
    memberB.setEmailAddress("subscriber@example.com");

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);
      memberRepo.when(() -> MailingListMemberRepository.addEmailToList(saved, listA, false, null))
          .thenReturn(new MailingListMemberRepository.AddToListResult(true, false, memberA));
      memberRepo.when(() -> MailingListMemberRepository.addEmailToList(saved, listB, false, null))
          .thenReturn(new MailingListMemberRepository.AddToListResult(true, false, memberB));

      SaveEmailCommand.saveEmail(emailBean, List.of(listA, listB));

      ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(eventCaptor.capture()), times(2));
      List<Event> fired = eventCaptor.getAllValues();
      MailingListMemberCreatedEvent firedForA = (MailingListMemberCreatedEvent) fired.get(0);
      MailingListMemberCreatedEvent firedForB = (MailingListMemberCreatedEvent) fired.get(1);
      assertEquals(memberA, firedForA.getMember(), "list A's event must carry list A's own member, not list B's");
      assertEquals(listA, firedForA.getMailingList());
      assertEquals(memberB, firedForB.getMember(), "list B's event must carry list B's own member, not list A's");
      assertEquals(listB, firedForB.getMailingList());
    }
  }

  @Test
  void resolvesTheActingUserFromTheSubmittedBeanNotThePossiblyStaleReFetchedEmailRecord() throws DataException {
    // issue #452 review finding: EmailRepository.update() deliberately never overwrites
    // created_by (the #810 fix), so on the duplicate-email/update branch, a re-fetched `email`
    // record's createdBy would still be whoever originally created that address years ago --
    // actingUser must come from the submitted emailBean (the current request), not that record
    Email emailBean = email("subscriber@example.com");
    emailBean.setCreatedBy(42L); // e.g. the admin submitting this specific request
    Email existingWithADifferentOriginalCreator = email("subscriber@example.com");
    existingWithADifferentOriginalCreator.setId(9L);
    existingWithADifferentOriginalCreator.setCreatedBy(7L); // some unrelated original creator
    MailingList mailingList = mailingList(1L, "News");
    MailingListMember member = new MailingListMember();
    member.setId(10L);

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(null); // duplicate -> update branch
      emailRepo.when(() -> EmailRepository.findByEmailAddress("subscriber@example.com"))
          .thenReturn(existingWithADifferentOriginalCreator);
      memberRepo.when(() -> MailingListMemberRepository.addEmailToList(existingWithADifferentOriginalCreator,
          mailingList, false, null))
          .thenReturn(new MailingListMemberRepository.AddToListResult(true, false, member));
      com.simisinc.platform.domain.model.User admin42 = new com.simisinc.platform.domain.model.User();
      admin42.setId(42L);
      userRepo.when(() -> UserRepository.findByUserId(42L)).thenReturn(admin42);

      SaveEmailCommand.saveEmail(emailBean, mailingList);

      userRepo.verify(() -> UserRepository.findByUserId(42L));
      userRepo.verify(() -> UserRepository.findByUserId(7L), never());
      ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(eventCaptor.capture()));
      MailingListMemberCreatedEvent fired = (MailingListMemberCreatedEvent) eventCaptor.getValue();
      assertEquals(42L, fired.getUser().getId(), "the event must attribute the actual submitter, not the address's original creator");
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

      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(saved, mailingList, false, null), times(1));
      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(any(), any(), anyBoolean(), any()), times(1));
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
      memberRepo.when(() -> MailingListMemberRepository.addEmailToList(saved, mailingList, false, null))
          .thenReturn(new MailingListMemberRepository.AddToListResult(true, false, member));

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
      memberRepo.when(() -> MailingListMemberRepository.addEmailToList(saved, mailingList, false, null))
          .thenReturn(new MailingListMemberRepository.AddToListResult(false, true, member));

      SaveEmailCommand.saveEmail(emailBean, mailingList);

      ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(eventCaptor.capture()));
      assertEquals(MailingListMemberUpdatedEvent.class, eventCaptor.getValue().getClass());
      MailingListMemberUpdatedEvent fired = (MailingListMemberUpdatedEvent) eventCaptor.getValue();
      assertEquals("resubscribed", fired.getChangeType());
      assertEquals(member, fired.getMember());
      assertEquals(mailingList, fired.getMailingList());
      assertEquals(false, fired.isPreviouslySubscribed(),
          "a reactivation means they were NOT subscribed immediately beforehand");
    }
  }

  @Test
  void doesNotFireAnEventWhenAddEmailToListReportsAnAlreadyActiveMemberReAdd() throws DataException {
    // mirrors addEmailToListDoesNotReportAnAlreadyActiveMemberAsReactivated at the repository
    // layer -- a harmless re-submit of an already-active member must not fire a misleading
    // "resubscribed" event for someone who never left
    Email emailBean = email("already-subscribed@example.com");
    Email saved = email("already-subscribed@example.com");
    saved.setId(5L);
    MailingList mailingList = mailingList(1L, "News");
    MailingListMember member = new MailingListMember();
    member.setId(12L);
    member.setEmailAddress("already-subscribed@example.com");

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);
      memberRepo.when(() -> MailingListMemberRepository.addEmailToList(saved, mailingList, false, null))
          .thenReturn(new MailingListMemberRepository.AddToListResult(false, false, member));

      SaveEmailCommand.saveEmail(emailBean, mailingList);

      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
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
      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(existing, lists.get(0), false, null));
    }
  }

  @Test
  void aFormSubmittedCountryWinsOverGeoIp() throws DataException {
    // A visitor behind a VPN or corporate proxy routinely resolves to the wrong country by IP --
    // the subscribe form's own Country field is more reliable when the caller already set one.
    Email emailBean = email("subscriber@example.com");
    emailBean.setIpAddress("203.0.113.1");
    emailBean.setCountry("United Kingdom");
    Email saved = email("subscriber@example.com");
    saved.setId(5L);
    MailingList mailingList = mailingList(1L, "News");
    GeoIP geoIp = new GeoIP("203.0.113.1", "North America", "US", "United States", "Reston", "VA", "Virginia",
        "20190", "America/New_York", 38.0, -77.0, 511);

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<GeoIPCommand> geoIpCommand = mockStatic(GeoIPCommand.class)) {
      geoIpCommand.when(() -> GeoIPCommand.getLocation("203.0.113.1")).thenReturn(geoIp);
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);

      SaveEmailCommand.saveEmail(emailBean, mailingList);

      assertEquals("United Kingdom", emailBean.getCountry(), "the form's own value must not be overwritten by GeoIP");
      // Every other geo field has no form-input path and stays unconditionally GeoIP-derived
      assertEquals("Reston", emailBean.getCity());
      assertEquals("US", emailBean.getCountryIso());
    }
  }

  @Test
  void geoIpFillsInCountryWhenTheCallerDidNotAlreadySetOne() throws DataException {
    Email emailBean = email("subscriber@example.com");
    emailBean.setIpAddress("203.0.113.1");
    Email saved = email("subscriber@example.com");
    saved.setId(5L);
    MailingList mailingList = mailingList(1L, "News");
    GeoIP geoIp = new GeoIP("203.0.113.1", "North America", "CA", "Canada", "Toronto", "ON", "Ontario",
        "M5V", "America/Toronto", 43.6, -79.4, -1);

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<GeoIPCommand> geoIpCommand = mockStatic(GeoIPCommand.class)) {
      geoIpCommand.when(() -> GeoIPCommand.getLocation("203.0.113.1")).thenReturn(geoIp);
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);

      SaveEmailCommand.saveEmail(emailBean, mailingList);

      assertEquals("Canada", emailBean.getCountry(), "unchanged existing behavior: GeoIP fills in an unset country");
    }
  }

  @Test
  void requiringConfirmationPassesTrueAndAResolvedExpiryToAddEmailToList() throws DataException {
    Email emailBean = email("subscriber@example.com");
    Email saved = email("subscriber@example.com");
    saved.setId(5L);
    MailingList mailingList = mailingList(1L, "News");
    MailingListMember pendingMember = new MailingListMember();
    pendingMember.setId(10L);
    pendingMember.setEmailAddress("subscriber@example.com");
    pendingMember.setConfirmToken("a-token");

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<MailingListConfirmationCommand> confirmationCommand = mockStatic(MailingListConfirmationCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.confirmation.expiryDays")).thenReturn("7");
      memberRepo.when(() -> MailingListMemberRepository.resolveConfirmationExpiryDays("7")).thenReturn(7);
      memberRepo.when(() -> MailingListMemberRepository.addEmailToList(eq(saved), eq(mailingList), eq(true), any()))
          .thenReturn(new MailingListMemberRepository.AddToListResult(true, false, true, pendingMember));

      SaveEmailCommand.saveEmailRequiringConfirmation(emailBean, mailingList);

      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(eq(saved), eq(mailingList), eq(true), any()));
    }
  }

  @Test
  void aPendingConfirmationResultSendsTheConfirmationEmailInsteadOfFiringTheCreatedEvent() throws DataException {
    Email emailBean = email("subscriber@example.com");
    Email saved = email("subscriber@example.com");
    saved.setId(5L);
    MailingList mailingList = mailingList(1L, "News");
    MailingListMember pendingMember = new MailingListMember();
    pendingMember.setId(10L);
    pendingMember.setEmailAddress("subscriber@example.com");
    pendingMember.setConfirmToken("a-token");

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<MailingListConfirmationCommand> confirmationCommand = mockStatic(MailingListConfirmationCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);
      memberRepo.when(() -> MailingListMemberRepository.addEmailToList(eq(saved), eq(mailingList), eq(true), any()))
          .thenReturn(new MailingListMemberRepository.AddToListResult(true, false, true, pendingMember));

      SaveEmailCommand.saveEmailRequiringConfirmation(emailBean, mailingList);

      confirmationCommand.verify(
          () -> MailingListConfirmationCommand.sendConfirmationEmail(pendingMember, mailingList));
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
      memberCommand.verify(
          () -> MailingListMemberCommand.triggerEmailSubscriptionProcess(any(), any(), anyBoolean()), never());
    }
  }

  @Test
  void aPendingResultThatDoesNotNeedANewConfirmationEmailDoesNotSendOne() throws DataException {
    // addEmailToList() reuses a still-live token instead of reissuing one on a resubmit --
    // requiresConfirmation() stays true (still not active) but confirmationEmailNeeded() is
    // false. Resending here on every resubmit would be an unthrottled mail-bomb primitive.
    Email emailBean = email("subscriber@example.com");
    Email saved = email("subscriber@example.com");
    saved.setId(5L);
    MailingList mailingList = mailingList(1L, "News");
    MailingListMember pendingMember = new MailingListMember();
    pendingMember.setId(10L);
    pendingMember.setEmailAddress("subscriber@example.com");
    pendingMember.setConfirmToken("still-live-token");

    try (MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<MailingListConfirmationCommand> confirmationCommand = mockStatic(MailingListConfirmationCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);
      memberRepo.when(() -> MailingListMemberRepository.addEmailToList(eq(saved), eq(mailingList), eq(true), any()))
          .thenReturn(new MailingListMemberRepository.AddToListResult(false, false, true, false, pendingMember));

      SaveEmailCommand.saveEmailRequiringConfirmation(emailBean, mailingList);

      confirmationCommand.verify(() -> MailingListConfirmationCommand.sendConfirmationEmail(any(), any()), never());
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
      memberCommand.verify(
          () -> MailingListMemberCommand.triggerEmailSubscriptionProcess(any(), any(), anyBoolean()), never());
    }
  }

  /**
   * Issue #1724: the name arrives from a public visitor's POST (the emailSubscribe widget passes
   * its mailingList preference straight through), so a name that no longer resolves must fail,
   * not quietly bring a list into existence under whatever the old configuration said.
   */
  @Test
  void refusesANameThatDoesNotResolveInsteadOfCreatingThatList() {
    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByName("Newsletter")).thenReturn(null);

      DataException exception = assertThrows(DataException.class,
          () -> SaveEmailCommand.saveEmailRequiringConfirmation(email("subscriber@example.com"), "Newsletter"));

      assertEquals(SaveEmailCommand.LIST_UNAVAILABLE_MESSAGE, exception.getMessage());
      listRepo.verify(() -> MailingListRepository.save(any()), never());
      emailRepo.verifyNoInteractions();
      memberRepo.verifyNoInteractions();
    }
  }

  @Test
  void resolvesTheDefaultListByNameWhenTheCallerNamesNone() throws DataException {
    Email emailBean = email("subscriber@example.com");
    Email saved = email("subscriber@example.com");
    saved.setId(5L);
    MailingList newsletter = mailingList(1L, SaveEmailCommand.DEFAULT_MAILING_LIST_NAME);

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberRepository> memberRepo = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<GeoIPCommand> geoIp = mockStatic(GeoIPCommand.class)) {
      listRepo.when(() -> MailingListRepository.findByName(SaveEmailCommand.DEFAULT_MAILING_LIST_NAME))
          .thenReturn(newsletter);
      emailRepo.when(() -> EmailRepository.add(emailBean)).thenReturn(saved);

      SaveEmailCommand.saveEmail(emailBean, (String) null);

      memberRepo.verify(() -> MailingListMemberRepository.addEmailToList(saved, newsletter, false, null));
      listRepo.verify(() -> MailingListRepository.save(any()), never());
    }
  }

  /** A blank preference means "nothing configured", not a list whose name is the empty string --
   *  which is what the previous auto-create would have created from it. */
  @Test
  void treatsABlankNameAsTheDefaultRatherThanLookingUpAnEmptyName() {
    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByName(SaveEmailCommand.DEFAULT_MAILING_LIST_NAME))
          .thenReturn(null);

      assertThrows(DataException.class,
          () -> SaveEmailCommand.saveEmailRequiringConfirmation(email("subscriber@example.com"), "   "));

      listRepo.verify(() -> MailingListRepository.findByName(SaveEmailCommand.DEFAULT_MAILING_LIST_NAME));
      listRepo.verify(() -> MailingListRepository.save(any()), never());
      emailRepo.verifyNoInteractions();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // findMailingList(): what an emailSubscribe widget's own preferences resolve to. unique_id is
  // assigned once and never rewritten, so it survives the rename that breaks a name preference.
  // ---------------------------------------------------------------------------------------------

  @Test
  void findsAListByItsUniqueIdWithoutConsultingTheNamePreference() {
    MailingList newsletter = mailingList(1L, "Company Announcements");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByUniqueId("newsletter")).thenReturn(newsletter);

      // The list has been renamed since the page was configured -- which is exactly the case the
      // uniqueId is for, so the stale name must not be consulted at all
      assertEquals(newsletter, SaveEmailCommand.findMailingList("newsletter", "Newsletter"));

      listRepo.verify(() -> MailingListRepository.findByName(any()), never());
    }
  }

  @Test
  void aUniqueIdThatDoesNotResolveReturnsNullRatherThanFallingBackToTheName() {
    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByUniqueId("deleted-list")).thenReturn(null);
      listRepo.when(() -> MailingListRepository.findByName("Newsletter")).thenReturn(mailingList(1L, "Newsletter"));

      assertEquals(null, SaveEmailCommand.findMailingList("deleted-list", "Newsletter"));

      listRepo.verify(() -> MailingListRepository.findByName(any()), never());
    }
  }

  @Test
  void fallsBackToTheNamePreferenceWhenNoUniqueIdIsConfigured() {
    // Every emailSubscribe widget already published in the wild carries a name, not a uniqueId
    MailingList newsletter = mailingList(1L, "Newsletter");

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByName("Newsletter")).thenReturn(newsletter);

      assertEquals(newsletter, SaveEmailCommand.findMailingList(null, "Newsletter"));
      assertEquals(newsletter, SaveEmailCommand.findMailingList("  ", "Newsletter"));
    }
  }

  @Test
  void findsTheDefaultListWhenNeitherPreferenceIsConfigured() {
    MailingList newsletter = mailingList(1L, SaveEmailCommand.DEFAULT_MAILING_LIST_NAME);

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(() -> MailingListRepository.findByName(SaveEmailCommand.DEFAULT_MAILING_LIST_NAME))
          .thenReturn(newsletter);

      assertEquals(newsletter, SaveEmailCommand.findMailingList(null, null));
    }
  }
}
