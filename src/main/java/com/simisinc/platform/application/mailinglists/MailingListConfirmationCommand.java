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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberConfirmationRequestedEvent;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;

/**
 * Sends the confirm-subscription email for a pending double opt-in membership. Mirrors
 * ForgotPasswordWidget/AccountValidationWidget's shape: the actual send happens through the
 * workflow engine's "mailing-list-member-confirmation-requested" playbook (see
 * mailinglists-workflows.yml), not synchronously here -- this command only builds the link and
 * fires the event.
 *
 * @author SimIS Inc.
 */
public class MailingListConfirmationCommand {

  public static void sendConfirmationEmail(MailingListMember member, MailingList mailingList) {
    if (member == null || mailingList == null || member.getConfirmToken() == null) {
      return;
    }
    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    String confirmUrl = siteUrl + "/confirm-subscription?token=" + UrlCommand.encodeUri(member.getConfirmToken());
    WorkflowManager.triggerWorkflowForEvent(new MailingListMemberConfirmationRequestedEvent(member, mailingList, confirmUrl));
  }
}
