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
import com.simisinc.platform.infrastructure.scheduler.admin.DatasetsDownloadAndSyncJob;
import com.simisinc.platform.infrastructure.scheduler.cms.LoadSystemFilesJob;
import com.simisinc.platform.infrastructure.scheduler.cms.RecordWebPageHitJob;
import com.simisinc.platform.infrastructure.scheduler.cms.SystemHealthJob;
import com.simisinc.platform.infrastructure.scheduler.cms.WebPageHitSnapshotJob;
import com.simisinc.platform.infrastructure.scheduler.cms.WebPageHitsCleanupJob;
import com.simisinc.platform.infrastructure.scheduler.ecommerce.OrderManagementProcessNewOrders;
import com.simisinc.platform.infrastructure.scheduler.ecommerce.OrderManagementProcessShippingUpdates;
import com.simisinc.platform.infrastructure.scheduler.login.UserTokensCleanupJob;
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

import javax.servlet.ServletContext;
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
  private static Log LOG = LogFactory.getLog(SchedulerManager.class);

  // Jobs for every replica
  public static final String SYSTEM_HEALTH_JOB = "SystemHealth";
  public static final String LOAD_SYSTEM_FILES_JOB = "LoadSystemFiles";
  public static final String RECORD_WEB_PAGE_HITS_JOB = "RecordWebPageHits";

  // Jobs to be run once across many replicas
  public static final String WEB_PAGE_HIT_SNAPSHOT_JOB = "WebPageHitSnapshot";
  public static final String WEB_PAGE_HITS_CLEANUP_JOB = "WebPageHitsCleanup";
  public static final String USER_TOKENS_CLEANUP_JOB = "UserTokensCleanup";
  public static final String INSTAGRAM_MEDIA_SNAPSHOT_JOB = "InstagramMediaSnapshot";
  public static final String ORDER_MANAGEMENT_PROCESS_NEW_ORDERS_JOB = "OrderManagementProcessNewOrders";
  public static final String ORDER_MANAGEMENT_PROCESS_SHIPPING_UPDATES_JOB = "OrderManagementProcessShippingUpdates";
  public static final String PROCESS_MEDICINE_SCHEDULES_JOB = "ProcessMedicineSchedules";

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

      // Schedule background jobs
      // BackgroundJob.scheduleRecurrently(SYSTEM_HEALTH_JOB, Cron.every15seconds(), SystemHealthJob::execute);
      BackgroundJob.scheduleRecurrently(LOAD_SYSTEM_FILES_JOB, Cron.every5minutes(), LoadSystemFilesJob::execute);

      BackgroundJob.scheduleRecurrently(RECORD_WEB_PAGE_HITS_JOB, Cron.every15seconds(), RecordWebPageHitJob::execute);
      BackgroundJob.scheduleRecurrently(WEB_PAGE_HIT_SNAPSHOT_JOB, Cron.every5minutes(), WebPageHitSnapshotJob::execute);
      BackgroundJob.scheduleRecurrently(WEB_PAGE_HITS_CLEANUP_JOB, Cron.daily(4), WebPageHitsCleanupJob::execute);

      BackgroundJob.scheduleRecurrently(USER_TOKENS_CLEANUP_JOB, Cron.hourly(), UserTokensCleanupJob::execute);

      // Dataset jobs... a single job can continuously check or multiple
      BackgroundJob.scheduleRecurrently(DATASETS_DOWNLOAD_AND_SYNC_JOB, Cron.minutely(), DatasetsDownloadAndSyncJob::execute);

      // @todo While the job checks for Instagram settings, it would be nice to turn off the job if not configured
      BackgroundJob.scheduleRecurrently(INSTAGRAM_MEDIA_SNAPSHOT_JOB, Cron.hourly(25), InstagramMediaSnapshotJob::execute);

      // @todo While the job checks for E-Commerce settings, it would be nice to turn off the job if disabled
      BackgroundJob.scheduleRecurrently(ORDER_MANAGEMENT_PROCESS_NEW_ORDERS_JOB, Cron.minutely(), OrderManagementProcessNewOrders::execute);
      BackgroundJob.scheduleRecurrently(ORDER_MANAGEMENT_PROCESS_SHIPPING_UPDATES_JOB, Cron.hourly(), OrderManagementProcessShippingUpdates::execute);

      BackgroundJob.scheduleRecurrently(PROCESS_MEDICINE_SCHEDULES_JOB, Cron.daily(23, 43), ProcessMedicineSchedulesJob::execute);

    } catch (Exception se) {
      LOG.error("Error starting jobrunr: ", se);
    }
  }

  public static void shutdown() {
    JobRunr.destroy();
    servletContext = null;
  }

  public static ServletContext getServletContext() {
    return servletContext;
  }
}
