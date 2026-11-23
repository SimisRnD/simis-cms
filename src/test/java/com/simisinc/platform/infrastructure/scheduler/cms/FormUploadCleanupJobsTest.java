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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.PurgeFormUploadsCommand;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;

/**
 * Verifies files attached to forms are removed when their submission is purged, and by the nightly
 * sweep (issue #2130).
 */
class FormUploadCleanupJobsTest {

  private MockedStatic<LockManager> lockManager;
  private MockedStatic<LoadSitePropertyCommand> siteProperty;
  private MockedStatic<FormDataRepository> formData;
  private MockedStatic<PurgeFormUploadsCommand> purge;

  @BeforeEach
  void setUp() {
    lockManager = mockStatic(LockManager.class);
    siteProperty = mockStatic(LoadSitePropertyCommand.class);
    formData = mockStatic(FormDataRepository.class);
    formData.when(() -> FormDataRepository.resolveRetentionDays(any())).thenReturn(90);
    purge = mockStatic(PurgeFormUploadsCommand.class);
  }

  @AfterEach
  void tearDown() {
    purge.close();
    formData.close();
    siteProperty.close();
    lockManager.close();
  }

  private void lockGranted(String job) {
    lockManager.when(() -> LockManager.lock(eq(job), any(Duration.class))).thenReturn("lock");
  }

  @Test
  void purgingSubmissionsRemovesTheirFilesInTheSameRun() {
    lockGranted(SchedulerManager.FORM_DATA_RETENTION_JOB);
    formData.when(() -> FormDataRepository.deleteOlderThan(90)).thenReturn(3);

    FormDataRetentionJob.execute();

    purge.verify(PurgeFormUploadsCommand::removeUnlinkedUploads);
  }

  @Test
  void whenNoSubmissionIsPurgedNoFilesAreLookedFor() {
    lockGranted(SchedulerManager.FORM_DATA_RETENTION_JOB);
    formData.when(() -> FormDataRepository.deleteOlderThan(90)).thenReturn(0);

    FormDataRetentionJob.execute();

    purge.verify(PurgeFormUploadsCommand::removeUnlinkedUploads, never());
  }

  @Test
  void theNightlySweepRemovesUnlinkedUploadsThenUnreferencedFiles() {
    lockGranted(SchedulerManager.FORM_UPLOAD_SWEEP_JOB);

    FormUploadSweepJob.execute();

    purge.verify(PurgeFormUploadsCommand::removeUnlinkedUploads);
    purge.verify(PurgeFormUploadsCommand::deleteUnreferencedFiles);
  }

  @Test
  void withoutTheLockNeitherJobDoesAnything() {
    FormDataRetentionJob.execute();
    FormUploadSweepJob.execute();

    formData.verify(() -> FormDataRepository.deleteOlderThan(anyInt()), never());
    purge.verify(PurgeFormUploadsCommand::removeUnlinkedUploads, never());
    purge.verify(PurgeFormUploadsCommand::deleteUnreferencedFiles, never());
  }
}
