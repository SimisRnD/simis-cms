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

package com.simisinc.platform.application.mailinglists;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.scheduler.mailinglists.ProcessEmailSubscriptionJob;
import com.simisinc.platform.presentation.controller.UserSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.scheduling.BackgroundJobRequest;

import java.sql.Timestamp;

import static com.simisinc.platform.infrastructure.scheduler.mailinglists.ProcessEmailSubscriptionJob.STATUS_SUBSCRIBED;
import static com.simisinc.platform.infrastructure.scheduler.mailinglists.ProcessEmailSubscriptionJob.STATUS_UNSUBSCRIBED;

/**
 * Methods for mailing list membership management
 *
 * @author matt rajkowski
 * @created 6/18/19 10:30 PM
 */
public class MailingListMemberCommand {

  private static Log LOG = LogFactory.getLog(MailingListMemberCommand.class);

  public static void subscribe(MailingList mailingList, User user, UserSession userSession) throws DataException {

    if (mailingList == null) {
      throw new DataException("Mailing list was not found");
    }
    if (user == null) {
      throw new DataException("User was not found");
    }

    // Use the email to add to a list
    Email emailBean = new Email();
    emailBean.setEmail(user.getEmail());
    emailBean.setSubscribed(new Timestamp(System.currentTimeMillis()));
    emailBean.setIpAddress(userSession.getIpAddress());
    emailBean.setSessionId(userSession.getSessionId());
    emailBean.setReferer(userSession.getReferer());
    emailBean.setUserAgent(userSession.getUserAgent());
    emailBean.setCreatedBy(user.getId());
    emailBean.setModifiedBy(user.getId());
    SaveEmailCommand.saveEmail(emailBean, mailingList);
  }


  public static void unsubscribe(MailingList mailingList, User user) throws DataException {
    if (mailingList == null) {
      throw new DataException("Mailing list was not found");
    }
    if (user == null) {
      throw new DataException("User was not found");
    }

    // Use the email to remove from the list
    Email email = EmailRepository.findByEmailAddress(user.getEmail());
    if (email != null) {
      email.setUnsubscribed(new Timestamp(System.currentTimeMillis()));
      MailingListMemberRepository.unsubscribe(mailingList, email, user);
      triggerEmailSubscriptionProcess(email, mailingList, false);
    }
  }

  public static void triggerEmailSubscriptionProcess(Email email, MailingList mailingList, boolean subscribed) {
    if (email == null) {
      return;
    }
    String status = (subscribed ? STATUS_SUBSCRIBED : STATUS_UNSUBSCRIBED);
    BackgroundJobRequest.enqueue(new ProcessEmailSubscriptionJob(email, mailingList, status));
  }

}
