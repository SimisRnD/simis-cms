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

package com.simisinc.platform.rest.services.mailinglists;

import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;

/**
 * The response body for both {@code POST /api/mailing-list-members} and
 * {@code PUT /api/mailing-list-members/{memberId}}. Built from both a {@link MailingListMember}
 * (list-membership fields: id, list, status) and an {@link Email} (the shared, cross-list
 * name/organization fields) rather than a single repository read, since no existing lookup returns
 * both together for a single row -- see {@link MailingListMemberService} for detail.
 *
 * @author SimIS Inc.
 */
public class MailingListMemberResponse {

  private long memberId;
  private long mailingListId;
  private String email;
  private String firstName;
  private String lastName;
  private String organization;
  private boolean valid;
  private boolean unsubscribed;

  public MailingListMemberResponse(MailingListMember member, Email emailRecord) {
    this.memberId = member.getId();
    this.mailingListId = member.getListId();
    this.email = emailRecord.getEmail();
    this.firstName = emailRecord.getFirstName();
    this.lastName = emailRecord.getLastName();
    this.organization = emailRecord.getOrganization();
    this.valid = member.getIsValid();
    this.unsubscribed = member.getUnsubscribed() != null;
  }

  public long getMemberId() {
    return memberId;
  }

  public void setMemberId(long memberId) {
    this.memberId = memberId;
  }

  public long getMailingListId() {
    return mailingListId;
  }

  public void setMailingListId(long mailingListId) {
    this.mailingListId = mailingListId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getOrganization() {
    return organization;
  }

  public void setOrganization(String organization) {
    this.organization = organization;
  }

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public boolean isUnsubscribed() {
    return unsubscribed;
  }

  public void setUnsubscribed(boolean unsubscribed) {
    this.unsubscribed = unsubscribed;
  }
}
