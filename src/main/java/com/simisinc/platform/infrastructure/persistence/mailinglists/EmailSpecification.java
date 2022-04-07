/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.infrastructure.persistence.mailinglists;

/**
 * Encapsulates the records being returned and the record count for paging
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class EmailSpecification {

  private long mailingListId = -1;
  private String matchesEmail = null;
  private String matchesName = null;

  public EmailSpecification() {
  }

  public long getMailingListId() {
    return mailingListId;
  }

  public void setMailingListId(long mailingListId) {
    this.mailingListId = mailingListId;
  }

  public String getMatchesEmail() {
    return matchesEmail;
  }

  public void setMatchesEmail(String matchesEmail) {
    this.matchesEmail = matchesEmail;
  }

  public String getMatchesName() {
    return matchesName;
  }

  public void setMatchesName(String matchesName) {
    this.matchesName = matchesName;
  }
}
