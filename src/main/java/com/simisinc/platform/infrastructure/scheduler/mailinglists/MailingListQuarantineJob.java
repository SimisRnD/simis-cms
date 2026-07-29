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

package com.simisinc.platform.infrastructure.scheduler.mailinglists;

import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Automated mailing list hygiene (#564): quarantines (archives, never deletes) memberships whose
 * linked email has a confirmed-bad deliverability classification from
 * {@link com.simisinc.platform.infrastructure.scheduler.mailinglists.EmailClassificationJob},
 * replacing the manual-purge-only workflow. Scheduled after that job so same-day classifications
 * are picked up the same night, not a full day later.
 * <p>
 * Every quarantine action is itself recorded as an admin audit event -- a purge (or in this case,
 * an archive) is itself on the compliance trail, mirroring AuditLogRetentionJob's own precedent.
 *
 * @author SimIS Inc.
 */
public class MailingListQuarantineJob {

  private static Log LOG = LogFactory.getLog(MailingListQuarantineJob.class);

  @Job(name = "Quarantine mailing list members with a confirmed-bad deliverability classification")
  public static void execute() {
    // Distributed lock so only one node quarantines at a time
    String lock = LockManager.lock(SchedulerManager.MAILING_LIST_QUARANTINE_JOB, Duration.ofHours(2));
    if (lock == null) {
      return;
    }

    int quarantinedCount = MailingListMemberRepository.quarantineFlaggedMembers();
    if (quarantinedCount > 0) {
      LOG.info("Mailing list quarantine: archived " + quarantinedCount + " membership(s) with a confirmed-bad deliverability status");
      SaveAuditEventCommand.recordAdminEvent("configuration", "mailing_list.quarantine", "success",
          -1L, "system", null, null, "mailing_list_members", null, null,
          "quarantined=" + quarantinedCount);
    }
  }
}
