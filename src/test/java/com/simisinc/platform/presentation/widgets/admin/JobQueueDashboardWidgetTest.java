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

package com.simisinc.platform.presentation.widgets.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.JobStats;
import org.jobrunr.storage.Page;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.navigation.PageRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author SimIS
 * @created 7/30/2026
 */
class JobQueueDashboardWidgetTest extends WidgetBase {

  private static JobStats jobStats(long scheduled, long enqueued, long processing, long failed, long succeeded) {
    JobStats stats = mock(JobStats.class);
    when(stats.getScheduled()).thenReturn(scheduled);
    when(stats.getEnqueued()).thenReturn(enqueued);
    when(stats.getProcessing()).thenReturn(processing);
    when(stats.getFailed()).thenReturn(failed);
    when(stats.getSucceeded()).thenReturn(succeeded);
    return stats;
  }

  private static Job job(StateName state, String className, String methodName) {
    Job job = mock(Job.class);
    JobDetails jobDetails = mock(JobDetails.class);
    when(jobDetails.getClassName()).thenReturn(className);
    when(jobDetails.getMethodName()).thenReturn(methodName);
    when(job.getId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    when(job.getState()).thenReturn(state);
    when(job.getJobDetails()).thenReturn(jobDetails);
    when(job.getCreatedAt()).thenReturn(Instant.parse("2026-07-29T12:00:00Z"));
    when(job.getUpdatedAt()).thenReturn(Instant.parse("2026-07-29T12:05:00Z"));
    return job;
  }

  @SuppressWarnings("unchecked")
  private static Page<Job> page(long total, List<Job> items) {
    Page<Job> page = mock(Page.class);
    when(page.getTotal()).thenReturn(total);
    when(page.getItems()).thenReturn(items);
    return page;
  }

  @Test
  void executeDoesNothingForAUserWithoutAdmin() {
    // WidgetBase's default logged-in test user has no roles at all
    try (MockedStatic<SchedulerManager> scheduler = mockStatic(SchedulerManager.class)) {
      WidgetContext result = new JobQueueDashboardWidget().execute(widgetContext);

      assertNull(result.getJsp());
      scheduler.verifyNoInteractions();
    }
  }

  @Test
  void executeShowsAnUnavailableMessageWhenTheStorageProviderIsNotSetYet() {
    setRoles(widgetContext, ADMIN);
    try (MockedStatic<SchedulerManager> scheduler = mockStatic(SchedulerManager.class)) {
      scheduler.when(SchedulerManager::getStorageProvider).thenReturn(null);

      WidgetContext result = new JobQueueDashboardWidget().execute(widgetContext);

      assertEquals("/admin/job-queue-dashboard.jsp", result.getJsp());
      assertEquals(Boolean.TRUE, result.getRequest().getAttribute("storageProviderUnavailable"));
      assertNull(result.getRequest().getAttribute("jobList"));
    }
  }

  @Test
  void executeDefaultsToEnqueuedWhenNoJobsAreFailed() {
    setRoles(widgetContext, ADMIN);
    StorageProvider storageProvider = mock(StorageProvider.class);
    JobStats stats = jobStats(1, 4, 0, 0, 10);
    Job enqueuedJob = job(StateName.ENQUEUED,
        "com.simisinc.platform.infrastructure.scheduler.cms.SystemHealthJob", "execute");

    // Built before the when(...) chain below starts: constructing a mock inline as a .thenReturn()
    // argument runs that mock's own when(...) calls while the outer stub is still "open", which
    // Mockito's stubbing state machine treats as an "unfinished stubbing" error on the outer mock.
    Page<Job> jobPage = page(1, List.of(enqueuedJob));

    try (MockedStatic<SchedulerManager> scheduler = mockStatic(SchedulerManager.class)) {
      scheduler.when(SchedulerManager::getStorageProvider).thenReturn(storageProvider);
      when(storageProvider.getJobStats()).thenReturn(stats);
      when(storageProvider.getJobs(eq(StateName.ENQUEUED), any(PageRequest.class))).thenReturn(jobPage);

      WidgetContext result = new JobQueueDashboardWidget().execute(widgetContext);

      assertEquals("/admin/job-queue-dashboard.jsp", result.getJsp());
      assertEquals("ENQUEUED", result.getRequest().getAttribute("selectedState"));

      @SuppressWarnings("unchecked")
      Map<String, Long> stateCounts = (Map<String, Long>) result.getRequest().getAttribute("stateCounts");
      assertEquals(4L, stateCounts.get("ENQUEUED"));
      assertEquals(0L, stateCounts.get("FAILED"));

      @SuppressWarnings("unchecked")
      List<JobQueueDashboardWidget.JobRow> jobList =
          (List<JobQueueDashboardWidget.JobRow>) result.getRequest().getAttribute("jobList");
      assertEquals(1, jobList.size());
      assertEquals("SystemHealthJob.execute", jobList.get(0).getJobType());
      assertEquals("ENQUEUED", jobList.get(0).getState());

      DataConstraints recordPaging = (DataConstraints) result.getRequest().getAttribute("recordPaging");
      assertEquals(1, recordPaging.getPageNumber());
      assertEquals(1L, recordPaging.getTotalRecordCount());
      assertEquals("state=ENQUEUED", result.getRequest().getAttribute("recordPagingParams"));
    }
  }

  @Test
  void executeDefaultsToFailedWhenAnyJobsAreFailed() {
    setRoles(widgetContext, ADMIN);
    StorageProvider storageProvider = mock(StorageProvider.class);
    JobStats stats = jobStats(0, 2, 0, 3, 10);
    Page<Job> jobPage = page(3, List.of());

    try (MockedStatic<SchedulerManager> scheduler = mockStatic(SchedulerManager.class)) {
      scheduler.when(SchedulerManager::getStorageProvider).thenReturn(storageProvider);
      when(storageProvider.getJobStats()).thenReturn(stats);
      when(storageProvider.getJobs(eq(StateName.FAILED), any(PageRequest.class))).thenReturn(jobPage);

      WidgetContext result = new JobQueueDashboardWidget().execute(widgetContext);

      assertEquals("FAILED", result.getRequest().getAttribute("selectedState"));
    }
  }

  @Test
  void executeHonorsAnExplicitStateParameterEvenWhenFailedJobsExist() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "state", "SUCCEEDED");
    StorageProvider storageProvider = mock(StorageProvider.class);
    JobStats stats = jobStats(0, 0, 0, 3, 10);
    Page<Job> jobPage = page(10, List.of());

    try (MockedStatic<SchedulerManager> scheduler = mockStatic(SchedulerManager.class)) {
      scheduler.when(SchedulerManager::getStorageProvider).thenReturn(storageProvider);
      when(storageProvider.getJobStats()).thenReturn(stats);
      when(storageProvider.getJobs(eq(StateName.SUCCEEDED), any(PageRequest.class))).thenReturn(jobPage);

      WidgetContext result = new JobQueueDashboardWidget().execute(widgetContext);

      assertEquals("SUCCEEDED", result.getRequest().getAttribute("selectedState"));
    }
  }

  @Test
  void executeIgnoresAnUnfilterableStateParameterAndFallsBackToTheDefault() {
    setRoles(widgetContext, ADMIN);
    // DELETED is a real JobRunr state but not one of Phase 1's filterable states
    addQueryParameter(widgetContext, "state", "DELETED");
    StorageProvider storageProvider = mock(StorageProvider.class);
    JobStats stats = jobStats(0, 5, 0, 0, 10);
    Page<Job> jobPage = page(5, List.of());

    try (MockedStatic<SchedulerManager> scheduler = mockStatic(SchedulerManager.class)) {
      scheduler.when(SchedulerManager::getStorageProvider).thenReturn(storageProvider);
      when(storageProvider.getJobStats()).thenReturn(stats);
      when(storageProvider.getJobs(eq(StateName.ENQUEUED), any(PageRequest.class))).thenReturn(jobPage);

      WidgetContext result = new JobQueueDashboardWidget().execute(widgetContext);

      assertEquals("ENQUEUED", result.getRequest().getAttribute("selectedState"));
    }
  }
}
