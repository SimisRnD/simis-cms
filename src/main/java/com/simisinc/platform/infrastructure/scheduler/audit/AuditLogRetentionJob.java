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

package com.simisinc.platform.infrastructure.scheduler.audit;

import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Deletes audit records past the configured retention window (NIST AU-11), mirroring the analytics cleanup
 * job. When it actually removes records it records an audit event of its own, so a retention purge is itself
 * on the trail (and shipped to the SIEM). The retention window is bounded in code so this job can never be
 * used to erase recent evidence -- see AuditLogRepository.resolveRetentionDays.
 *
 * @author SimIS Inc.
 */
public class AuditLogRetentionJob {

  private static Log LOG = LogFactory.getLog(AuditLogRetentionJob.class);

  @Job(name = "Delete audit records past the retention window")
  public static void execute() {
    // Distributed lock so only one node purges
    String lock = LockManager.lock(SchedulerManager.AUDIT_LOG_RETENTION_JOB, Duration.ofHours(4));
    if (lock == null) {
      return;
    }

    int days = AuditLogRepository.resolveRetentionDays(LoadSitePropertyCommand.loadByName("audit.retentionDays"));
    int deleted = AuditLogRepository.deleteOlderThan(days);
    if (deleted > 0) {
      LOG.info("Audit retention: deleted " + deleted + " record(s) older than " + days + " days");
      SaveAuditEventCommand.recordAdminEvent("configuration", "audit.retention.purge", "success",
          -1L, "system", null, null, "audit_log", null, null,
          "deleted=" + deleted + ";olderThanDays=" + days);
    }
  }
}
