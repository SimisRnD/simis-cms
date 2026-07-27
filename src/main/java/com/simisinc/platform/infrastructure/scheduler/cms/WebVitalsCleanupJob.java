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

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import java.time.Duration;

/**
 * Deletes old web vitals data per retention policy:
 * - Raw metrics: deleted after 30 days
 * - Aggregates: deleted after 1 year
 *
 * @author claude
 * @created 8/27/26
 */
public class WebVitalsCleanupJob {

  private static Log LOG = LogFactory.getLog(WebVitalsCleanupJob.class);

  @Job(name = "Delete old web vitals data")
  public static void execute() {
    // Distributed lock: prevent multiple instances from running
    String lock = LockManager.lock(SchedulerManager.WEB_VITALS_CLEANUP_JOB, Duration.ofHours(2));
    if (lock == null) {
      return;
    }

    try {
      deleteOldRawMetrics();
      deleteOldAggregates();
    } catch (Exception e) {
      LOG.error("Error cleaning web vitals", e);
    }
  }

  private static void deleteOldRawMetrics() {
    // Delete raw vitals older than 30 days
    SqlUtils where = new SqlUtils()
        .add("recorded_at < NOW() - INTERVAL '30 days'");

    int deleted = DB.deleteFrom("web_vitals", where);
    if (deleted > 0) {
      LOG.info("Deleted " + deleted + " old web vitals raw metric rows");
    }
  }

  private static void deleteOldAggregates() {
    // Delete aggregates older than 1 year
    SqlUtils where = new SqlUtils()
        .add("aggregated_at < NOW() - INTERVAL '1 year'");

    int deleted = DB.deleteFrom("web_vitals_aggregates", where);
    if (deleted > 0) {
      LOG.info("Deleted " + deleted + " old web vitals aggregate rows");
    }
  }
}
