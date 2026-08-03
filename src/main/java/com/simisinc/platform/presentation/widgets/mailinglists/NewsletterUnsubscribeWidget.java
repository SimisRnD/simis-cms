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

import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberUpdatedEvent;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

/**
 * Anonymous, no-login-required unsubscribe link (issue #600). Modeled on
 * AccountValidationWidget's no-password-needed branch: the mutation happens directly on GET, no
 * form/POST required, since unsubscribing needs no additional input from the visitor. The token
 * itself is the authorization; a missing, invalid, or already-used token gets the same graceful
 * "not found" page rather than an error, so a re-clicked email link never looks broken.
 *
 * @author SimIS Inc.
 */
public class NewsletterUnsubscribeWidget extends GenericWidget {

  static final long serialVersionUID = -3963577018820473218L;

  static String JSP = "/mailinglists/newsletter-unsubscribed.jsp";
  static String NOT_FOUND_JSP = "/mailinglists/newsletter-unsubscribe-not-found.jsp";
  static String RATE_LIMITED_JSP = "/cms/error-rate-limited.jsp";

  public WidgetContext execute(WidgetContext context) {

    // The token is a guessable-length concern only in aggregate (brute force); rate limit the
    // same way ForgotPasswordWidget rate limits its own anonymous, token-adjacent surface.
    if (!RateLimitCommand.isIpAllowedRightNow(context.getRequest().getRemoteAddr(), false)) {
      context.setJsp(RATE_LIMITED_JSP);
      return context;
    }

    String token = context.getParameter("token");
    if (StringUtils.isBlank(token)) {
      context.setJsp(NOT_FOUND_JSP);
      return context;
    }

    MailingListMember member = MailingListMemberRepository.findByUnsubscribeToken(token);
    if (member == null) {
      RateLimitCommand.isIpAllowedRightNow(context.getRequest().getRemoteAddr(), true);
      context.setJsp(NOT_FOUND_JSP);
      return context;
    }

    if (member.getUnsubscribed() == null) {
      MailingListMemberRepository.unsubscribeByToken(member);
      // issue #452: webhook/workflow event for the mailing-list-member lifecycle -- no acting
      // User for this anonymous, token-authorized self-service action. unsubscribeByToken()
      // mutates the DB row but not this in-memory `member` object, so re-fetch the post-mutation
      // state rather than passing the stale (still-subscribed-looking) snapshot into the event.
      MailingList mailingList = MailingListRepository.findById(member.getListId());
      if (mailingList != null) {
        MailingListMember updatedMember = MailingListMemberRepository.findByListAndEmail(mailingList.getId(),
            member.getEmailId());
        if (updatedMember != null) {
          WorkflowManager.triggerWorkflowForEvent(
              new MailingListMemberUpdatedEvent(updatedMember, mailingList, null, "unsubscribed", true));
        }
      }
    }

    context.setJsp(JSP);
    return context;
  }
}
