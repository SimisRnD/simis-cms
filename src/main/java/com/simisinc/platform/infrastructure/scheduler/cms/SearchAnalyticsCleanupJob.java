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

import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.cms.SearchAnalyticsRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Deletes old search analytics data
 *
 * @author SimIS
 * @created 7/29/2026
 */
public class SearchAnalyticsCleanupJob {

  private static Log LOG = LogFactory.getLog(SearchAnalyticsCleanupJob.class);

  @Job(name = "Delete old search analytics data")
  public static void execute() {
    // Distributed lock
    String lock = LockManager.lock(SchedulerManager.SEARCH_ANALYTICS_CLEANUP_JOB, Duration.ofHours(4));
    if (lock == null) {
      return;
    }

    SearchAnalyticsRepository.deleteOld();
  }
}
