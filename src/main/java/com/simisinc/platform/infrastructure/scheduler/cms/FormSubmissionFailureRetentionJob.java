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

package com.simisinc.platform.infrastructure.scheduler.cms;

import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.cms.FormSubmissionFailureRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Deletes form submission failure records past the configured retention window. Unlike
 * AuditLogRetentionJob, this does not record an audit event for its own purge -- this table is
 * deliberately lean, operational bot/spam-pressure telemetry (see FormSubmissionFailureRepository),
 * not a compliance-grade evidentiary trail, so a routine cleanup of it isn't audit-worthy.
 *
 * @author SimIS Inc.
 */
public class FormSubmissionFailureRetentionJob {

  private static Log LOG = LogFactory.getLog(FormSubmissionFailureRetentionJob.class);

  @Job(name = "Delete form submission failure records past the retention window")
  public static void execute() {
    // Distributed lock so only one node purges
    String lock = LockManager.lock(SchedulerManager.FORM_SUBMISSION_FAILURE_RETENTION_JOB, Duration.ofHours(4));
    if (lock == null) {
      return;
    }

    int days = FormSubmissionFailureRepository.resolveRetentionDays(
        LoadSitePropertyCommand.loadByName("formData.failureRetentionDays"));
    int deleted = FormSubmissionFailureRepository.deleteOlderThan(days);
    if (deleted > 0) {
      LOG.info("Form submission failure retention: deleted " + deleted + " record(s) older than " + days + " days");
    }
  }
}
