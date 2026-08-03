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
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;

/**
 * Event details for when an existing mailing list member's subscription status changes (issue
 * #452) -- currently fired for "resubscribed" (re-adding a previously-unsubscribed address) and
 * "unsubscribed". {@code previouslySubscribed} carries the one piece of prior state a receiver
 * needs to tell these apart without a second lookup.
 *
 * @author SimIS Inc.
 */
public class MailingListMemberUpdatedEvent extends Event {

  public static final String ID = "mailing-list-member-updated";

  private final MailingListMember member;
  private final MailingList mailingList;
  private final User user;
  private final String changeType;
  private final boolean previouslySubscribed;

  public MailingListMemberUpdatedEvent(MailingListMember member, MailingList mailingList, User user,
      String changeType, boolean previouslySubscribed) {
    this.member = member;
    this.mailingList = mailingList;
    this.user = user;
    this.changeType = changeType;
    this.previouslySubscribed = previouslySubscribed;
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

  /** The user who triggered the change, or null for an anonymous/self-service action. */
  public User getUser() {
    return user;
  }

  /** e.g. "resubscribed", "unsubscribed". */
  public String getChangeType() {
    return changeType;
  }

  public boolean isPreviouslySubscribed() {
    return previouslySubscribed;
  }
}
