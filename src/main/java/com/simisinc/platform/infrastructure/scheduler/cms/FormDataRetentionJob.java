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
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Deletes form_data records past the configured retention window, but only once they have reached a
 * terminal state (an admin has processed or dismissed them -- see FormDataRepository.deleteOlderThan).
 * Submissions still awaiting review are never deleted here, no matter how old, since they represent
 * unactioned work an admin may still need to see. Unlike AuditLogRetentionJob, this does not record
 * an audit event for its own purge, mirroring FormSubmissionFailureRetentionJob.
 *
 * @author SimIS Inc.
 */
public class FormDataRetentionJob {

  private static Log LOG = LogFactory.getLog(FormDataRetentionJob.class);

  @Job(name = "Delete terminal-state form data records past the retention window")
  public static void execute() {
    // Distributed lock so only one node purges
    String lock = LockManager.lock(SchedulerManager.FORM_DATA_RETENTION_JOB, Duration.ofHours(4));
    if (lock == null) {
      return;
    }

    int days = FormDataRepository.resolveRetentionDays(
        LoadSitePropertyCommand.loadByName("formData.retentionDays"));
    int deleted = FormDataRepository.deleteOlderThan(days);
    if (deleted > 0) {
      LOG.info("Form data retention: deleted " + deleted + " terminal-state record(s) older than " + days + " days");
    }
  }
}
