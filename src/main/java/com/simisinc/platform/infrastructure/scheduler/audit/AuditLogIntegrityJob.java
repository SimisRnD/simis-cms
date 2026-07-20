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

import com.simisinc.platform.application.audit.AuditLogIntegrityCommand;
import com.simisinc.platform.application.audit.AuditLogIntegrityCommand.AuditIntegrityResult;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Continuously verifies the audit log's tamper-evidence chain (NIST AU-6 / AU-9). When the chain is broken
 * -- a record was altered, deleted, reordered, or inserted out of band -- it records a failure audit event,
 * which is itself chained and shipped to the SIEM so a detection rule can alert on it. An intact result is
 * logged only, to avoid noise.
 *
 * @author SimIS Inc.
 */
public class AuditLogIntegrityJob {

  private static Log LOG = LogFactory.getLog(AuditLogIntegrityJob.class);

  @Job(name = "Verify the audit log tamper-evidence chain")
  public static void execute() {
    // Distributed lock so only one node verifies
    String lock = LockManager.lock(SchedulerManager.AUDIT_LOG_INTEGRITY_JOB, Duration.ofHours(4));
    if (lock == null) {
      return;
    }

    AuditIntegrityResult result = AuditLogIntegrityCommand.verify();
    if (result.isIntact()) {
      LOG.info("Audit chain verified intact (" + result.getCheckedCount() + " record(s))");
      return;
    }

    LOG.error("Audit chain integrity FAILED at audit_id=" + result.getFirstInvalidAuditId()
        + " (" + result.getCheckedCount() + " valid before it): " + result.getReason());
    SaveAuditEventCommand.recordAdminEvent("configuration", "audit.integrity.check", "failure",
        -1L, "system", null, null, "audit_log", String.valueOf(result.getFirstInvalidAuditId()), null,
        "checked=" + result.getCheckedCount() + ";reason=" + result.getReason());
  }
}
