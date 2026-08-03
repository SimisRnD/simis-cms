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
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberUpdatedEvent;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.mockito.ArgumentCaptor;

class NewsletterUnsubscribeWidgetTest extends WidgetBase {

  private static MailingListMember member(long id, boolean alreadyUnsubscribed) {
    MailingListMember member = new MailingListMember();
    member.setId(id);
    member.setListId(3L);
    member.setEmailId(4L);
    member.setUnsubscribeToken("tok-123");
    if (alreadyUnsubscribed) {
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

  @Test
  void executeUnsubscribesAValidToken() {
    MailingListMember member = member(1L, false);
    // unsubscribeByToken() mutates the DB row but not this in-memory object, so the widget
    // re-fetches -- stub a distinct post-mutation snapshot to prove the event carries THAT,
    // not the stale, still-subscribed-looking `member` returned by findByUnsubscribeToken
    MailingListMember updatedMember = member(1L, true);
    MailingList mailingList = mailingList();
    addQueryParameter(widgetContext, "token", "tok-123");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);
      repository.when(() -> MailingListMemberRepository.findByUnsubscribeToken("tok-123")).thenReturn(member);
      mailingListRepo.when(() -> MailingListRepository.findById(3L)).thenReturn(mailingList);
      repository.when(() -> MailingListMemberRepository.findByListAndEmail(3L, 4L)).thenReturn(updatedMember);

      WidgetContext result = new NewsletterUnsubscribeWidget().execute(widgetContext);

      assertEquals("/mailinglists/newsletter-unsubscribed.jsp", result.getJsp());
      repository.verify(() -> MailingListMemberRepository.unsubscribeByToken(member));

      ArgumentCaptor<MailingListMemberUpdatedEvent> eventCaptor = ArgumentCaptor
          .forClass(MailingListMemberUpdatedEvent.class);
      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(eventCaptor.capture()));
      MailingListMemberUpdatedEvent fired = eventCaptor.getValue();
      assertEquals("unsubscribed", fired.getChangeType());
      assertEquals(null, fired.getUser(), "self-service, token-authorized -- no acting User");
      assertEquals(updatedMember, fired.getMember(),
          "must carry the re-fetched post-mutation member, not the stale pre-mutation snapshot");
      assertEquals(mailingList, fired.getMailingList());
    }
  }

  @Test
  void executeDoesNotFireAnEventWhenTheMailingListNoLongerExists() {
    MailingListMember member = member(1L, false);
    addQueryParameter(widgetContext, "token", "tok-123");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);
      repository.when(() -> MailingListMemberRepository.findByUnsubscribeToken("tok-123")).thenReturn(member);
      // mailingListRepo left unstubbed -- default null

      new NewsletterUnsubscribeWidget().execute(widgetContext);

      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }

  @Test
  void executeDoesNotFireAnEventWhenTheReFetchAfterUnsubscribeFindsNoRow() {
    MailingListMember member = member(1L, false);
    addQueryParameter(widgetContext, "token", "tok-123");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class);
        MockedStatic<MailingListRepository> mailingListRepo = mockStatic(MailingListRepository.class);
        MockedStatic<WorkflowManager> workflowManager = mockStatic(WorkflowManager.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);
      repository.when(() -> MailingListMemberRepository.findByUnsubscribeToken("tok-123")).thenReturn(member);
      mailingListRepo.when(() -> MailingListRepository.findById(3L)).thenReturn(mailingList());
      // findByListAndEmail left unstubbed -- default null, as if the row vanished mid-request

      new NewsletterUnsubscribeWidget().execute(widgetContext);

      workflowManager.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }

  @Test
  void executeDoesNotReUnsubscribeAnAlreadyUnsubscribedMember() {
    MailingListMember member = member(1L, true);
    addQueryParameter(widgetContext, "token", "tok-123");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);
      repository.when(() -> MailingListMemberRepository.findByUnsubscribeToken("tok-123")).thenReturn(member);

      WidgetContext result = new NewsletterUnsubscribeWidget().execute(widgetContext);

      assertEquals("/mailinglists/newsletter-unsubscribed.jsp", result.getJsp());
      repository.verify(() -> MailingListMemberRepository.unsubscribeByToken(any()), never());
    }
  }

  @Test
  void executeShowsNotFoundForAnUnknownToken() {
    addQueryParameter(widgetContext, "token", "does-not-exist");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(true))).thenReturn(true);
      repository.when(() -> MailingListMemberRepository.findByUnsubscribeToken("does-not-exist")).thenReturn(null);

      WidgetContext result = new NewsletterUnsubscribeWidget().execute(widgetContext);

      assertEquals("/mailinglists/newsletter-unsubscribe-not-found.jsp", result.getJsp());
    }
  }

  @Test
  void executeShowsNotFoundForAMissingToken() {
    // No "token" param at all

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);

      WidgetContext result = new NewsletterUnsubscribeWidget().execute(widgetContext);

      assertEquals("/mailinglists/newsletter-unsubscribe-not-found.jsp", result.getJsp());
      repository.verify(() -> MailingListMemberRepository.findByUnsubscribeToken(any()), never());
    }
  }

  @Test
  void executeShowsRateLimitedPageWhenThrottled() {
    addQueryParameter(widgetContext, "token", "tok-123");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(false);

      WidgetContext result = new NewsletterUnsubscribeWidget().execute(widgetContext);

      assertEquals("/cms/error-rate-limited.jsp", result.getJsp());
      repository.verify(() -> MailingListMemberRepository.findByUnsubscribeToken(any()), never());
    }
  }
}
