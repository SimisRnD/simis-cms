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

import org.apache.commons.lang3.StringUtils;

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
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Anonymous, no-login-required double opt-in confirmation link. Modeled on
 * NewsletterUnsubscribeWidget: the mutation happens directly on GET, no form/POST required, since
 * confirming needs no additional input from the visitor. The token itself is the authorization; a
 * missing, invalid, or expired token gets the same graceful "not found" page rather than an
 * error, so a re-clicked email link never looks broken.
 *
 * @author SimIS Inc.
 */
public class MailingListConfirmSubscriptionWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908894L;

  static String JSP = "/mailinglists/confirm-subscription.jsp";
  static String NOT_FOUND_JSP = "/mailinglists/confirm-subscription-not-found.jsp";
  static String RATE_LIMITED_JSP = "/cms/error-rate-limited.jsp";

  public WidgetContext execute(WidgetContext context) {

    // The token is a guessable-length concern only in aggregate (brute force); rate limit the
    // same way NewsletterUnsubscribeWidget rate limits its own anonymous, token-adjacent surface.
    if (!RateLimitCommand.isIpAllowedRightNow(context.getRequest().getRemoteAddr(), false)) {
      context.setJsp(RATE_LIMITED_JSP);
      return context;
    }

    String token = context.getParameter("token");
    if (StringUtils.isBlank(token)) {
      context.setJsp(NOT_FOUND_JSP);
      return context;
    }

    MailingListMember member = MailingListMemberRepository.findByConfirmToken(token);
    if (member == null) {
      RateLimitCommand.isIpAllowedRightNow(context.getRequest().getRemoteAddr(), true);
      context.setJsp(NOT_FOUND_JSP);
      return context;
    }

    MailingList mailingList = MailingListRepository.findById(member.getListId());
    if (mailingList == null) {
      context.setJsp(NOT_FOUND_JSP);
      return context;
    }
    context.getRequest().setAttribute("mailingList", mailingList);

    if (member.getConfirmed() == null) {
      // A live confirm_token only ever exists on a pending row (see addEmailToList) -- distinguish
      // a fresh signup from a returning subscriber reconfirming after having unsubscribed, so the
      // right lifecycle event fires. This is the moment the membership actually becomes active, so
      // this is when that event fires -- not back when the pending row was first inserted.
      boolean wasReactivation = member.getUnsubscribed() != null;
      MailingListMemberRepository.confirmByToken(member);

      Email email = EmailRepository.findById(member.getEmailId());
      MailingListMemberCommand.triggerEmailSubscriptionProcess(email, mailingList, true);

      MailingListMember confirmedMember = MailingListMemberRepository.findByListAndEmail(mailingList.getId(),
          member.getEmailId());
      if (confirmedMember != null) {
        if (wasReactivation) {
          WorkflowManager.triggerWorkflowForEvent(
              new MailingListMemberUpdatedEvent(confirmedMember, mailingList, null, "resubscribed", false));
        } else {
          WorkflowManager.triggerWorkflowForEvent(
              new MailingListMemberCreatedEvent(confirmedMember, mailingList, null));
        }
      }
    }

    context.setJsp(JSP);
    return context;
  }
}
