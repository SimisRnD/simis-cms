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

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;

/**
 * Event details for when a mailing list member is removed (issue #452). {@code member} is the
 * last-known snapshot taken immediately before deletion -- the row no longer exists by the time
 * this event is dispatched.
 *
 * @author SimIS Inc.
 */
public class MailingListMemberDeletedEvent extends Event {

  public static final String ID = "mailing-list-member-deleted";

  private final MailingListMember member;
  private final MailingList mailingList;
  private final User user;

  public MailingListMemberDeletedEvent(MailingListMember member, MailingList mailingList, User user) {
    this.member = member;
    this.mailingList = mailingList;
    this.user = user;
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

  /** The admin who removed the member, or null if removed by an automated process. */
  public User getUser() {
    return user;
  }
}
