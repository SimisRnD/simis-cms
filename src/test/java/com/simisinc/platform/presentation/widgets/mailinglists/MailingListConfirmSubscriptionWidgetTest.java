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

package com.simisinc.platform.presentation.widgets.mailinglists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.mailinglists.MailingListMemberCommand;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberCreatedEvent;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberUpdatedEvent;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;

class MailingListConfirmSubscriptionWidgetTest extends WidgetBase {

  private static MailingListMember pendingMember(boolean previouslyUnsubscribed) {
    MailingListMember member = new MailingListMember();
    member.setId(1L);
    member.setListId(3L);
    member.setEmailId(4L);
    member.setEmailAddress("subscriber@example.com");
    member.setConfirmToken("tok-123");
    if (previouslyUnsubscribed) {
      member.setUnsubscribed(new Timestamp(System.currentTimeMillis()));
    }
    return member;
  }

  private static MailingList mailingList() {
    MailingList mailingList = new MailingList();
    mailingList.setId(3L);
    mailingList.setName("Newsletter");
    return mailingList;
  }

  private static Email email() {
    Email email = new Email();
    email.setId(4L);
    email.setEmail("subscriber@example.com");
    return email;
  }

  @Test
  void executeConfirmsANewSignupAndFiresACreatedEvent() {
    MailingListMember member = pendingMember(false);
    MailingListMember confirmedMember = pendingMember(false);
    confirmedMember.setConfirmed(new Timestamp(System.currentTimeMillis()));
    confirmedMember.setConfirmToken(null);
    MailingList mailingList = mailingList();
    Email email = email(); // Email has no equals() override -- reuse one instance for stub + verify
    addQueryParameter(widgetContext, "token", "tok-123");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);
      repository.when(() -> MailingListMemberRepository.findByConfirmToken("tok-123")).thenReturn(member);
      mailingListRepo.when(() -> MailingListRepository.findById(3L)).thenReturn(mailingList);
      emailRepo.when(() -> EmailRepository.findById(4L)).thenReturn(email);
      repository.when(() -> MailingListMemberRepository.findByListAndEmail(3L, 4L)).thenReturn(confirmedMember);

      WidgetContext result = new MailingListConfirmSubscriptionWidget().execute(widgetContext);

      assertEquals("/mailinglists/confirm-subscription.jsp", result.getJsp());
      repository.verify(() -> MailingListMemberRepository.confirmByToken(member));
      memberCommand.verify(() -> MailingListMemberCommand.triggerEmailSubscriptionProcess(email, mailingList, true));

      ArgumentCaptor<MailingListMemberCreatedEvent> eventCaptor = ArgumentCaptor
          .forClass(MailingListMemberCreatedEvent.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(eventCaptor.capture()));
      assertEquals(confirmedMember, eventCaptor.getValue().getMember());
      assertEquals(mailingList, eventCaptor.getValue().getMailingList());
    }
  }

  @Test
  void executeConfirmsAReactivationAndFiresAResubscribedEventNotACreatedEvent() {
    MailingListMember member = pendingMember(true);
    MailingListMember confirmedMember = pendingMember(true);
    confirmedMember.setUnsubscribed(null);
    confirmedMember.setConfirmed(new Timestamp(System.currentTimeMillis()));
    confirmedMember.setConfirmToken(null);
    MailingList mailingList = mailingList();
    addQueryParameter(widgetContext, "token", "tok-123");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<EmailRepository> emailRepo = mockStatic(EmailRepository.class);
        MockedStatic<MailingListMemberCommand> memberCommand = mockStatic(MailingListMemberCommand.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);
      repository.when(() -> MailingListMemberRepository.findByConfirmToken("tok-123")).thenReturn(member);
      mailingListRepo.when(() -> MailingListRepository.findById(3L)).thenReturn(mailingList);
      emailRepo.when(() -> EmailRepository.findById(4L)).thenReturn(email());
      repository.when(() -> MailingListMemberRepository.findByListAndEmail(3L, 4L)).thenReturn(confirmedMember);

      new MailingListConfirmSubscriptionWidget().execute(widgetContext);

      ArgumentCaptor<MailingListMemberUpdatedEvent> eventCaptor = ArgumentCaptor
          .forClass(MailingListMemberUpdatedEvent.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(eventCaptor.capture()));
      assertEquals("resubscribed", eventCaptor.getValue().getChangeType());
    }
  }

  @Test
  void executeDoesNotReconfirmAnAlreadyConfirmedMember() {
    MailingListMember alreadyConfirmed = pendingMember(false);
    alreadyConfirmed.setConfirmed(new Timestamp(System.currentTimeMillis()));
    MailingList mailingList = mailingList();
    addQueryParameter(widgetContext, "token", "tok-123");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);
      repository.when(() -> MailingListMemberRepository.findByConfirmToken("tok-123")).thenReturn(alreadyConfirmed);
      mailingListRepo.when(() -> MailingListRepository.findById(3L)).thenReturn(mailingList);

      WidgetContext result = new MailingListConfirmSubscriptionWidget().execute(widgetContext);

      assertEquals("/mailinglists/confirm-subscription.jsp", result.getJsp());
      repository.verify(() -> MailingListMemberRepository.confirmByToken(any()), never());
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }

  @Test
  void executeShowsNotFoundForAnUnknownOrExpiredToken() {
    addQueryParameter(widgetContext, "token", "does-not-exist");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      repository.when(() -> MailingListMemberRepository.findByConfirmToken("does-not-exist")).thenReturn(null);

      WidgetContext result = new MailingListConfirmSubscriptionWidget().execute(widgetContext);

      assertEquals("/mailinglists/confirm-subscription-not-found.jsp", result.getJsp());
    }
  }

  @Test
  void executeShowsNotFoundForAMissingToken() {
    // No "token" param at all

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);

      WidgetContext result = new MailingListConfirmSubscriptionWidget().execute(widgetContext);

      assertEquals("/mailinglists/confirm-subscription-not-found.jsp", result.getJsp());
      repository.verify(() -> MailingListMemberRepository.findByConfirmToken(any()), never());
    }
  }

  @Test
  void executeShowsRateLimitedPageWhenThrottled() {
    addQueryParameter(widgetContext, "token", "tok-123");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(false);

      WidgetContext result = new MailingListConfirmSubscriptionWidget().execute(widgetContext);

      assertEquals("/cms/error-rate-limited.jsp", result.getJsp());
      repository.verify(() -> MailingListMemberRepository.findByConfirmToken(any()), never());
    }
  }
}
