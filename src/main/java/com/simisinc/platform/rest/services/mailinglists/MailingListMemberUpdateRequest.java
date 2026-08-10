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
 * The JSON body for {@code PUT /api/mailing-list-members/{memberId}}. Every field is optional and
 * omitted fields are left untouched -- {@code firstName}/{@code lastName}/{@code organization} use
 * {@code null} (not blank-string) as "not supplied" (JSON-B leaves an absent field {@code null}
 * rather than throwing), and {@code unsubscribed} is boxed {@link Boolean}, not a primitive, for
 * the same reason: only {@code null} means "not supplied", so an explicit {@code false} can be
 * told apart and rejected (see {@link MailingListMemberService} for why {@code false} isn't
 * accepted -- resubscribing is a bigger decision than a raw field flip).
 *
 * @author SimIS Inc.
 */
public class MailingListMemberUpdateRequest {

  private String firstName;
  private String lastName;
  private String organization;
  private Boolean unsubscribed;

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

  public Boolean getUnsubscribed() {
    return unsubscribed;
  }

  public void setUnsubscribed(Boolean unsubscribed) {
    this.unsubscribed = unsubscribed;
  }
}
