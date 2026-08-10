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

/**
 * The JSON body for {@code POST /api/mailing-list-members}. {@code mailingListId} takes priority
 * over {@code mailingListName} when both are present; if neither is given, the list defaults to
 * "Newsletter", matching {@link com.simisinc.platform.application.mailinglists.SaveEmailCommand}'s
 * own default -- but unlike that command's internal helper, a name that doesn't resolve to an
 * existing list here is rejected (400), not auto-created; see {@link MailingListMemberService} for
 * why.
 *
 * @author SimIS Inc.
 */
public class MailingListMemberCreateRequest {

  private String email;
  private String firstName;
  private String lastName;
  private String organization;
  private long mailingListId = -1;
  private String mailingListName;

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

  public long getMailingListId() {
    return mailingListId;
  }

  public void setMailingListId(long mailingListId) {
    this.mailingListId = mailingListId;
  }

  public String getMailingListName() {
    return mailingListName;
  }

  public void setMailingListName(String mailingListName) {
    this.mailingListName = mailingListName;
  }
}
