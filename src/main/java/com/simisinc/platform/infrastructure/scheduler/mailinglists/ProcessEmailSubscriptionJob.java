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

package com.simisinc.platform.infrastructure.scheduler.mailinglists;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.mailinglists.MailChimpCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

/**
 * Sends mailing list subscription data to external service provider
 *
 * @author matt rajkowski
 * @created 9/12/2019 11:36 AM
 */
@NoArgsConstructor
public class ProcessEmailSubscriptionJob implements JobRequest {

  private static Log LOG = LogFactory.getLog(ProcessEmailSubscriptionJob.class);

  public static final String STATUS_SUBSCRIBED = "subscribed";
  public static final String STATUS_UNSUBSCRIBED = "unsubscribed";

  @Getter
  @Setter
  private Email email = null;

  @Getter
  @Setter
  private MailingList mailingList = null;

  @Getter
  @Setter
  private String status = null;

  public ProcessEmailSubscriptionJob(Email email, MailingList mailingList, String status) {
    this.email = email;
    this.mailingList = mailingList;
    this.status = status;
  }

  @Override
  public Class<ProcessEmailSubscriptionJobRequestHandler> getJobRequestHandler() {
    return ProcessEmailSubscriptionJobRequestHandler.class;
  }

  public static class ProcessEmailSubscriptionJobRequestHandler implements JobRequestHandler<ProcessEmailSubscriptionJob> {
    @Override
    @Job(name = "Save an email subscription request", retries = 1)
    public void run(ProcessEmailSubscriptionJob jobRequest) {

      // Determine the service to use
      String service = LoadSitePropertyCommand.loadByName("mailing-list.service");
      if (!"mailchimp".equalsIgnoreCase(service)) {
        LOG.info("MailChimp is not configured");
        return;
      }

      // Check for MailChimp credentials
      String apiKey = LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.apiKey");
      String listId = LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.listId");
      if (StringUtils.isBlank(apiKey) || StringUtils.isBlank(listId)) {
        LOG.info("MailChimp API is not configured");
        return;
      }

      // Determine the objects
      Email email = jobRequest.getEmail();
      MailingList mailingList = jobRequest.getMailingList();
      String status = jobRequest.getStatus();

      // Reset the sync flag
      EmailRepository.markNotSynced(email);
      // Perform the sync action
      if (STATUS_SUBSCRIBED.equals(status)) {
        MailChimpCommand.addEmailToList(email, mailingList);
      } else {
        MailChimpCommand.unsubscribeFromList(email, mailingList);
      }
    }
  }
}
