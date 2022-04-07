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
 * @author matt rajkowski
 * @created 8/19/19 7:47 AM
 */
public class MailingListMemberSpecification {

  private long mailingListId = -1;

  public MailingListMemberSpecification() {
  }

  public long getMailingListId() {
    return mailingListId;
  }

  public void setMailingListId(long mailingListId) {
    this.mailingListId = mailingListId;
  }
}
