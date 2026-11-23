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

import com.simisinc.platform.application.cms.PurgeFormUploadsCommand;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Nightly sweep of files visitors attached to forms (issue #2130): removes uploads whose submission
 * is gone or was never saved, then any file under forms/ that no upload names. Runs after
 * FormDataRetentionJob, which already removes the files of the submissions it deletes; this catches
 * what that and an interrupted submission leave behind.
 *
 * @author SimIS Inc.
 */
public class FormUploadSweepJob {

  private static Log LOG = LogFactory.getLog(FormUploadSweepJob.class);

  @Job(name = "Remove form upload files nothing refers to")
  public static void execute() {
    // Distributed lock so only one node sweeps
    String lock = LockManager.lock(SchedulerManager.FORM_UPLOAD_SWEEP_JOB, Duration.ofHours(4));
    if (lock == null) {
      return;
    }

    int records = PurgeFormUploadsCommand.removeUnlinkedUploads();
    int files = PurgeFormUploadsCommand.deleteUnreferencedFiles();
    if (records > 0 || files > 0) {
      LOG.info("Form upload sweep: removed " + records + " unlinked upload(s) and " + files + " unreferenced file(s)");
    }
  }
}
