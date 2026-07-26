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

package com.simisinc.platform.infrastructure.scheduler.cms;

import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Nullifies visitor PII (IP address, city, postal code, precise coordinates) from session rows
 * that are older than the configured analytics retention window. Rows already scrubbed are
 * skipped, so the job is idempotent. Country, state, and continent are retained for analytics.
 *
 * @author SimIS Inc.
 */
public class SessionsPiiScrubJob {

  private static Log LOG = LogFactory.getLog(SessionsPiiScrubJob.class);

  @Job(name = "Scrub visitor PII from sessions past the retention window")
  public static void execute() {
    String lock = LockManager.lock(SchedulerManager.SESSIONS_PII_SCRUB_JOB, Duration.ofHours(4));
    if (lock == null) {
      return;
    }

    int days = SessionRepository.resolveRetentionDays(LoadSitePropertyCommand.loadByName("analytics.retentionDays"));
    int scrubbed = SessionRepository.scrubOldPii(days);
    if (scrubbed > 0) {
      LOG.info("Sessions PII scrub: nullified PII in " + scrubbed + " session row(s) older than " + days + " days");
    }
  }
}
