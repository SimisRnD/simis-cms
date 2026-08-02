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
import com.simisinc.platform.infrastructure.persistence.cms.FunnelEventRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Deletes conversion funnel event records past the configured retention window. Like
 * FormSubmissionFailureRetentionJob, this is anonymous-traffic telemetry, not a compliance-grade
 * evidentiary trail, so a routine cleanup of it isn't audit-worthy.
 *
 * @author SimIS Inc.
 */
public class FunnelEventRetentionJob {

  private static Log LOG = LogFactory.getLog(FunnelEventRetentionJob.class);

  @Job(name = "Delete funnel event records past the retention window")
  public static void execute() {
    // Distributed lock so only one node purges
    String lock = LockManager.lock(SchedulerManager.FUNNEL_EVENT_RETENTION_JOB, Duration.ofHours(4));
    if (lock == null) {
      return;
    }

    int days = FunnelEventRepository.resolveRetentionDays(
        LoadSitePropertyCommand.loadByName("funnel.retentionDays"));
    int deleted = FunnelEventRepository.deleteOlderThan(days);
    if (deleted > 0) {
      LOG.info("Funnel event retention: deleted " + deleted + " record(s) older than " + days + " days");
    }
  }
}
