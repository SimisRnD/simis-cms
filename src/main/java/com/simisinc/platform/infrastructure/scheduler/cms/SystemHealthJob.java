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

import com.simisinc.platform.application.HealthCommand;
import com.simisinc.platform.domain.model.cms.SystemHealthCheck;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.cms.SystemHealthCheckRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import java.time.Duration;

/**
 * Runs HealthCommand's individual readiness checks (database, filesystem) and persists each result,
 * so the admin Health Dashboard has real current status and history to show (issue #466). Uses a
 * distributed lock so exactly one node writes each interval's checks, giving a single unambiguous
 * cluster-wide history rather than one row per replica.
 *
 * @author matt rajkowski
 * @created 3/26/2023 7:47 AM
 */
public class SystemHealthJob {

  private static Log LOG = LogFactory.getLog(SystemHealthJob.class);

  @Job(name = "System Health Check")
  public static void execute() {
    String lock = LockManager.lock(SchedulerManager.SYSTEM_HEALTH_JOB, Duration.ofMinutes(5));
    if (lock == null) {
      return;
    }

    try {
      for (HealthCommand.CheckResult result : HealthCommand.runAllChecks()) {
        SystemHealthCheck record = new SystemHealthCheck();
        record.setServiceName(result.getServiceName());
        record.setStatus(result.isUp() ? SystemHealthCheck.STATUS_UP : SystemHealthCheck.STATUS_DOWN);
        record.setResponseTimeMs((int) result.getResponseTimeMs());
        record.setErrorMessage(result.getErrorMessage());
        SystemHealthCheckRepository.save(record);
        if (!result.isUp()) {
          LOG.warn("Health check failed for " + result.getServiceName() + ": " + result.getErrorMessage());
        }
      }
    } catch (Exception e) {
      LOG.error("Error running system health checks", e);
    }
  }
}
