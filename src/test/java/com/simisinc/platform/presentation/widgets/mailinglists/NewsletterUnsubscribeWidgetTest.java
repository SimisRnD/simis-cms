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
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

class NewsletterUnsubscribeWidgetTest extends WidgetBase {

  private static MailingListMember member(long id, boolean alreadyUnsubscribed) {
    MailingListMember member = new MailingListMember();
    member.setId(id);
    member.setUnsubscribeToken("tok-123");
    if (alreadyUnsubscribed) {
      member.setUnsubscribed(new Timestamp(System.currentTimeMillis()));
    }
    return member;
  }

  @Test
  void executeUnsubscribesAValidToken() {
    MailingListMember member = member(1L, false);
    addQueryParameter(widgetContext, "token", "tok-123");

    try (MockedStatic<RateLimitCommand> rateLimit = mockStatic(RateLimitCommand.class);
        MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      rateLimit.when(() -> RateLimitCommand.isIpAllowedRightNow(any(), eq(false))).thenReturn(true);
      repository.when(() -> MailingListMemberRepository.findByUnsubscribeToken("tok-123")).thenReturn(member);

      WidgetContext result = new NewsletterUnsubscribeWidget().execute(widgetContext);

      assertEquals("/mailinglists/newsletter-unsubscribed.jsp", result.getJsp());
      repository.verify(() -> MailingListMemberRepository.unsubscribeByToken(member));
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
