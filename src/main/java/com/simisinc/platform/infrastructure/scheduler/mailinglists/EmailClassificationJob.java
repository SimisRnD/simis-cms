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

import java.time.Duration;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.mailinglists.ZeroBounceApiClientCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Classifies emails that have never been run through deliverability validation
 * (see {@link EmailRepository#findUnvalidatedEmails}), using whichever vendor is configured
 * (currently {@link ZeroBounceApiClientCommand}). A no-op, not an error, when that vendor's API
 * key site property is blank -- this integration is optional, matching how
 * ProcessEmailSubscriptionJob bails out when MailChimp isn't configured.
 * <p>
 * Each run is capped to a bounded batch so a single execution can never exhaust the vendor's
 * per-lookup API credits or run long enough to bump into the next scheduled run. A single
 * address's failure (network error, vendor error) is logged and does not abort the rest of the
 * batch -- since a failed lookup leaves validated_at NULL, that address is simply picked up again
 * on the next run.
 *
 * @author SimIS Inc.
 */
public class EmailClassificationJob {

  private static Log LOG = LogFactory.getLog(EmailClassificationJob.class);

  // Bounds a single run to respect the vendor's real-time API rate limits and keep the job's
  // wall-clock time reasonable; the backlog is worked down over subsequent daily runs.
  private static final int BATCH_SIZE = 200;

  @Job(name = "Classify unvalidated emails for deliverability")
  public static void execute() {
    // This integration is optional -- skip cleanly (not an error) when it isn't configured, so an
    // unconfigured install doesn't spend a lock/DB round-trip on a batch that would just fail
    String apiKey = LoadSitePropertyCommand.loadByName("mailing-list.zerobounce.apiKey");
    if (StringUtils.isBlank(apiKey)) {
      LOG.debug("ZeroBounce is not configured, skipping email classification");
      return;
    }

    // Distributed lock so only one node classifies at a time
    String lock = LockManager.lock(SchedulerManager.EMAIL_CLASSIFICATION_JOB, Duration.ofHours(2));
    if (lock == null) {
      return;
    }

    List<Email> emailList = EmailRepository.findUnvalidatedEmails(new DataConstraints(1, BATCH_SIZE));
    if (emailList == null || emailList.isEmpty()) {
      LOG.debug("No unvalidated emails found");
      return;
    }

    int successCount = 0;
    int failureCount = 0;
    for (Email email : emailList) {
      try {
        if (ZeroBounceApiClientCommand.validateEmail(email) != null) {
          ++successCount;
        } else {
          ++failureCount;
        }
      } catch (Exception e) {
        // A single address must never abort the rest of the batch
        ++failureCount;
        LOG.warn("Email classification failed for email_id=" + email.getId() + ": " + e.getMessage());
      }
    }
    LOG.info("Email classification: " + successCount + " succeeded, " + failureCount + " failed, out of "
        + emailList.size() + " checked");
  }
}
