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

package com.simisinc.platform.domain.events.mailinglists;

import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;

/**
 * Fired when a public signup path (double opt-in) creates or reactivates a mailing list
 * membership that is pending confirmation, so the confirm-subscription email can be sent.
 * Deliberately distinct from {@link MailingListMemberCreatedEvent}/{@link
 * MailingListMemberUpdatedEvent} -- those represent an actually-active membership change and must
 * not fire (nor sync to a third-party list) until the address owner actually confirms.
 *
 * @author SimIS Inc.
 */
public class MailingListMemberConfirmationRequestedEvent extends Event {

  public static final String ID = "mailing-list-member-confirmation-requested";

  private final MailingListMember member;
  private final MailingList mailingList;
  private final String confirmUrl;

  public MailingListMemberConfirmationRequestedEvent(MailingListMember member, MailingList mailingList, String confirmUrl) {
    this.member = member;
    this.mailingList = mailingList;
    this.confirmUrl = confirmUrl;
  }

  @Override
  public String getDomainEventType() {
    return ID;
  }

  public MailingListMember getMember() {
    return member;
  }

  public MailingList getMailingList() {
    return mailingList;
  }

  public String getConfirmUrl() {
    return confirmUrl;
  }
}
