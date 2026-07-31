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

package com.simisinc.platform.infrastructure.scheduler;

import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.instance.InstanceManager;
import com.simisinc.platform.infrastructure.scheduler.admin.CapabilityGrantExpirationJob;
import com.simisinc.platform.infrastructure.scheduler.admin.DatasetsDownloadAndSyncJob;
import com.simisinc.platform.infrastructure.scheduler.audit.AuditLogIntegrityJob;
import com.simisinc.platform.infrastructure.scheduler.audit.AuditLogRetentionJob;
import com.simisinc.platform.infrastructure.scheduler.cms.FormSubmissionFailureRetentionJob;
import com.simisinc.platform.infrastructure.scheduler.cms.LoadSystemFilesJob;
import com.simisinc.platform.infrastructure.scheduler.cms.RecordWebPageHitJob;
import com.simisinc.platform.infrastructure.scheduler.cms.SearchAnalyticsCleanupJob;
import com.simisinc.platform.infrastructure.scheduler.cms.SessionsPiiScrubJob;
import com.simisinc.platform.infrastructure.scheduler.cms.SystemHealthCheckCleanupJob;
import com.simisinc.platform.infrastructure.scheduler.cms.SystemHealthJob;
import com.simisinc.platform.infrastructure.scheduler.cms.WebPageHitSnapshotJob;
import com.simisinc.platform.infrastructure.scheduler.cms.WebPageHitsCleanupJob;
import com.simisinc.platform.infrastructure.scheduler.cms.WebVitalsAggregationJob;
import com.simisinc.platform.infrastructure.scheduler.cms.WebVitalsCleanupJob;
import com.simisinc.platform.infrastructure.scheduler.ecommerce.OrderManagementProcessNewOrders;
import com.simisinc.platform.infrastructure.scheduler.ecommerce.OrderManagementProcessShippingUpdates;
import com.simisinc.platform.infrastructure.scheduler.login.UserTokensCleanupJob;
import com.simisinc.platform.infrastructure.scheduler.mailinglists.EmailClassificationJob;
import com.simisinc.platform.infrastructure.scheduler.mailinglists.MailingListQuarantineJob;
import com.simisinc.platform.infrastructure.scheduler.mailinglists.NewsletterQueueJob;
import com.simisinc.platform.infrastructure.scheduler.medicine.ProcessMedicineSchedulesJob;
import com.simisinc.platform.infrastructure.scheduler.socialmedia.InstagramMediaSnapshotJob;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.jobs.filters.RetryFilter;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.cron.Cron;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.StorageProviderUtils;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;

import jakarta.servlet.ServletContext;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;

/**
 * Initializes background jobs to be run on a schedule
 *
 * @author matt rajkowski
 * @created 1/12/22 9:00 PM
 */
public class SchedulerManager {

  private static ServletContext servletContext = null;
  private static StorageProvider storageProvider = null;
  private static Log LOG = LogFactory.getLog(SchedulerManager.class);

  // Jobs for every replica
  public static final String LOAD_SYSTEM_FILES_JOB = "LoadSystemFiles";
  public static final String RECORD_WEB_PAGE_HITS_JOB = "RecordWebPageHits";

  // Jobs to be run once across many replicas
  public static final String SYSTEM_HEALTH_JOB = "SystemHealth";
  public static final String SYSTEM_HEALTH_CHECK_CLEANUP_JOB = "SystemHealthCheckCleanup";
  public static final String WEB_PAGE_HIT_SNAPSHOT_JOB = "WebPageHitSnapshot";
  public static final String WEB_PAGE_HITS_CLEANUP_JOB = "WebPageHitsCleanup";
  public static final String SEARCH_ANALYTICS_CLEANUP_JOB = "SearchAnalyticsCleanup";
  public static final String WEB_VITALS_AGGREGATION_JOB = "WebVitalsAggregation";
  public static final String WEB_VITALS_CLEANUP_JOB = "WebVitalsCleanup";
  public static final String USER_TOKENS_CLEANUP_JOB = "UserTokensCleanup";
  public static final String INSTAGRAM_MEDIA_SNAPSHOT_JOB = "InstagramMediaSnapshot";
  public static final String ORDER_MANAGEMENT_PROCESS_NEW_ORDERS_JOB = "OrderManagementProcessNewOrders";
  public static final String ORDER_MANAGEMENT_PROCESS_SHIPPING_UPDATES_JOB = "OrderManagementProcessShippingUpdates";
  public static final String PROCESS_MEDICINE_SCHEDULES_JOB = "ProcessMedicineSchedules";

  public static final String SESSIONS_PII_SCRUB_JOB = "SessionsPiiScrub";
  public static final String AUDIT_LOG_RETENTION_JOB = "AuditLogRetention";
  public static final String AUDIT_LOG_INTEGRITY_JOB = "AuditLogIntegrity";
  public static final String CAPABILITY_GRANT_EXPIRATION_JOB = "CapabilityGrantExpiration";
  public static final String EMAIL_CLASSIFICATION_JOB = "EmailClassification";
  public static final String MAILING_LIST_QUARANTINE_JOB = "MailingListQuarantine";
  public static final String FORM_SUBMISSION_FAILURE_RETENTION_JOB = "FormSubmissionFailureRetention";
  public static final String NEWSLETTER_QUEUE_JOB = "NewsletterQueue";

  // Jobs which can be run by multiple clients
  public static final String DATASETS_DOWNLOAD_AND_SYNC_JOB = "DatasetsDownloadAndSync";
  
  public SchedulerManager() {
  }

  public static void startup(ServletContext servletContext1) {
    // Some jobs need the servlet context for resources
    servletContext = servletContext1;

    // Load properties
    Properties properties = new Properties();
    try (InputStream is = servletContext.getResourceAsStream("/WEB-INF/classes/jobrunr.properties")) {
      properties.load(is);
      LOG.info("Jobrunr properties found: " + properties.size());
    } catch (Exception e) {
      LOG.warn("Jobrunr properties were not found");
    }

    // Determine the run mode
    boolean canRunClusterJobs = !InstanceManager.isWebNodeOnly();

    // Configure the scheduler
    try {
      // Determine some settings
      boolean inMemoryStorage = "true".equals(properties.getProperty("org.jobrunr.configuration.useInMemoryStore", "true"));
      boolean isBackgroundJobServerEnabled = "true".equals(properties.getProperty("org.jobrunr.configuration.useBackgroundJobServer", "true"));
      boolean isDashboardEnabled = "true".equals(properties.getProperty("org.jobrunr.configuration.useDashboard", "false"));
      int dashboardPort = Integer.parseInt(properties.getProperty("org.jobrunr.configuration.dashboardPort", "8000"));
      int workerCount = Integer.parseInt(properties.getProperty("org.jobrunr.configuration.workerCount", "3"));
      int pollInterval = Integer.parseInt(properties.getProperty("org.jobrunr.configuration.pollIntervalInSeconds", "10"));
      long deleteSucceededJobsHours = Long.parseLong(properties.getProperty("org.jobrunr.configuration.deleteSucceededJobsInHours", "36"));
      long deleteDeletedJobsHours = Long.parseLong(properties.getProperty("org.jobrunr.configuration.deleteDeletedJobsInHours", "10"));

      // Configure the storage
      StorageProvider jobStorageProvider = (inMemoryStorage ? new InMemoryStorageProvider() : SqlStorageProviderFactory.using(DataSource.getDataSource(), null, StorageProviderUtils.DatabaseOptions.CREATE));
      storageProvider = jobStorageProvider;

      // Initialize the scheduler
      JobRunr.configure()
          .useStorageProvider(jobStorageProvider)
//          .useJobActivator(new JobActivator() {
//            public <T> T activateJob(Class<T> aClass) {
//              try {
//                return aClass.newInstance();
//              } catch (InstantiationException | IllegalAccessException e) {
//                e.printStackTrace();
//              }
//              return null;
//            }
//          })
          .withJobFilter(new RetryFilter(2))
          .useBackgroundJobServerIf(isBackgroundJobServerEnabled,
              usingStandardBackgroundJobServerConfiguration()
                  .andWorkerCount(workerCount)
                  .andPollIntervalInSeconds(pollInterval)
                  .andDeleteSucceededJobsAfter(Duration.ofHours(deleteSucceededJobsHours))
                  .andPermanentlyDeleteDeletedJobsAfter(Duration.ofHours(deleteDeletedJobsHours)))
          .useDashboardIf(isDashboardEnabled, dashboardPort)
          .initialize();

      // These background jobs are run by every node
      BackgroundJob.scheduleRecurrently(LOAD_SYSTEM_FILES_JOB, Cron.every5minutes(), LoadSystemFilesJob::execute);
      BackgroundJob.scheduleRecurrently(RECORD_WEB_PAGE_HITS_JOB, Cron.every15seconds(), RecordWebPageHitJob::execute);

      // These jobs need to be run by at least 1 node, preferably not the web-only nodes
      if (canRunClusterJobs) {
        // Distributed-locked (LockManager) so exactly one node writes each interval's checks --
        // see SystemHealthJob's own javadoc for why this isn't a per-replica "every node" job.
        BackgroundJob.scheduleRecurrently(SYSTEM_HEALTH_JOB, Cron.minutely(), SystemHealthJob::execute);
        BackgroundJob.scheduleRecurrently(SYSTEM_HEALTH_CHECK_CLEANUP_JOB, Cron.daily(4, 20), SystemHealthCheckCleanupJob::execute);
        BackgroundJob.scheduleRecurrently(WEB_PAGE_HIT_SNAPSHOT_JOB, Cron.every5minutes(), WebPageHitSnapshotJob::execute);
        BackgroundJob.scheduleRecurrently(WEB_PAGE_HITS_CLEANUP_JOB, Cron.daily(4), WebPageHitsCleanupJob::execute);
        // Offset from the other 4am-ish cleanup jobs so they aren't all competing for DB time at once
        BackgroundJob.scheduleRecurrently(SEARCH_ANALYTICS_CLEANUP_JOB, Cron.daily(4, 10), SearchAnalyticsCleanupJob::execute);
        BackgroundJob.scheduleRecurrently(WEB_VITALS_AGGREGATION_JOB, Cron.daily(23), WebVitalsAggregationJob::execute);
        BackgroundJob.scheduleRecurrently(WEB_VITALS_CLEANUP_JOB, Cron.daily(4, 5), WebVitalsCleanupJob::execute);
        BackgroundJob.scheduleRecurrently(USER_TOKENS_CLEANUP_JOB, Cron.hourly(), UserTokensCleanupJob::execute);
        BackgroundJob.scheduleRecurrently(INSTAGRAM_MEDIA_SNAPSHOT_JOB, Cron.hourly(), InstagramMediaSnapshotJob::execute);
        BackgroundJob.scheduleRecurrently(DATASETS_DOWNLOAD_AND_SYNC_JOB, Cron.minutely(), DatasetsDownloadAndSyncJob::execute);
        BackgroundJob.scheduleRecurrently(ORDER_MANAGEMENT_PROCESS_NEW_ORDERS_JOB, Cron.minutely(), OrderManagementProcessNewOrders::execute);
        BackgroundJob.scheduleRecurrently(ORDER_MANAGEMENT_PROCESS_SHIPPING_UPDATES_JOB, Cron.hourly(), OrderManagementProcessShippingUpdates::execute);
        BackgroundJob.scheduleRecurrently(PROCESS_MEDICINE_SCHEDULES_JOB, Cron.daily(23, 43), ProcessMedicineSchedulesJob::execute);
        BackgroundJob.scheduleRecurrently(SESSIONS_PII_SCRUB_JOB, Cron.daily(4, 45), SessionsPiiScrubJob::execute);
        BackgroundJob.scheduleRecurrently(AUDIT_LOG_RETENTION_JOB, Cron.daily(4, 15), AuditLogRetentionJob::execute);
        BackgroundJob.scheduleRecurrently(AUDIT_LOG_INTEGRITY_JOB, Cron.daily(4, 30), AuditLogIntegrityJob::execute);
        BackgroundJob.scheduleRecurrently(CAPABILITY_GRANT_EXPIRATION_JOB, Cron.hourly(),
            CapabilityGrantExpirationJob::execute);
        // Once daily is plenty for a backlog job, and keeps it off ZeroBounce's real per-lookup API
        // billing except when there's actually unvalidated backlog; runs ahead of the 4am cluster above
        // so it isn't competing with those jobs for DB/API time.
        BackgroundJob.scheduleRecurrently(EMAIL_CLASSIFICATION_JOB, Cron.daily(3), EmailClassificationJob::execute);
        // Runs after EMAIL_CLASSIFICATION_JOB so same-day classifications are quarantined the same
        // night, not a full day later.
        BackgroundJob.scheduleRecurrently(MAILING_LIST_QUARANTINE_JOB, Cron.daily(3, 30), MailingListQuarantineJob::execute);
        BackgroundJob.scheduleRecurrently(FORM_SUBMISSION_FAILURE_RETENTION_JOB, Cron.daily(5), FormSubmissionFailureRetentionJob::execute);
        BackgroundJob.scheduleRecurrently(NEWSLETTER_QUEUE_JOB, Cron.minutely(), NewsletterQueueJob::execute);
      }
    } catch (Exception se) {
      LOG.error("Error starting jobrunr: ", se);
    }
  }

  public static void shutdown() {
    JobRunr.destroy();
    servletContext = null;
    storageProvider = null;
  }

  public static ServletContext getServletContext() {
    return servletContext;
  }

  /** The JobRunr StorageProvider backing the scheduler, so admin tooling (e.g. the Job Queue
   * Dashboard, issue #464) can query job counts and lists directly. Null until {@link #startup}
   * has run. */
  public static StorageProvider getStorageProvider() {
    return storageProvider;
  }
}
