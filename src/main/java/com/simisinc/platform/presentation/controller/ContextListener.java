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

package com.simisinc.platform.presentation.controller;

import com.simisinc.platform.ApplicationInfo;
import com.simisinc.platform.application.admin.DatabaseCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import com.simisinc.platform.infrastructure.scheduler.cms.LoadSystemFilesJob;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.jsp.jstl.core.Config;
import java.io.File;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static com.simisinc.platform.infrastructure.cache.CacheManager.CONTENT_UNIQUE_ID_CACHE;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/8/18 4:52 PM
 */
public class ContextListener implements ServletContextListener {

  private static Log LOG = LogFactory.getLog(ContextListener.class);

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {

    LOG.info(ApplicationInfo.PRODUCT_NAME + " (" + ApplicationInfo.VERSION + ")");
    LOG.info("Learn more here: " + ApplicationInfo.PRODUCT_URL);

    // System properties
    System.setProperty("java.awt.headless", "true");

    // Monitor the success
    boolean isSuccessful = true;

    // Show the system's timezone
    LocalDateTime now = LocalDateTime.now();
    ZoneId serverZoneId = ZoneId.systemDefault();
    LOG.info("Server Time: " + now.atZone(serverZoneId).toString());
    LOG.info("Server TimeZone Id: " + serverZoneId.getId());

    // Startup the database first
    // @todo create and use a separate Rest DataSource pool
    Properties databaseProperties = new Properties();
    try (InputStream is = servletContextEvent.getServletContext().getResourceAsStream("/WEB-INF/classes/database.properties")) {
      LOG.info("Starting up the web database connection pool...");
      // Use the default properties
      databaseProperties.load(is);
      // Check for environment variables
      if (System.getenv().containsKey("DB_SERVER_NAME")) {
        LOG.info("Found variable DB_SERVER_NAME=" + System.getenv("DB_SERVER_NAME"));
        databaseProperties.setProperty("dataSource.serverName", System.getenv("DB_SERVER_NAME"));
      }
      if (System.getenv().containsKey("DB_USER")) {
        LOG.info("Found variable DB_USER");
        databaseProperties.setProperty("dataSource.user", System.getenv("DB_USER"));
      }
      if (System.getenv().containsKey("DB_PASSWORD")) {
        LOG.info("Found variable DB_PASSWORD");
        databaseProperties.setProperty("dataSource.password", System.getenv("DB_PASSWORD"));
      }
      if (System.getenv().containsKey("DB_NAME")) {
        LOG.info("Found variable DB_NAME=" + System.getenv("DB_NAME"));
        databaseProperties.setProperty("dataSource.databaseName", System.getenv("DB_NAME"));
      }
      DataSource.init(databaseProperties);
      // See if this is a new install or an upgrade
      if (!DatabaseCommand.initialize(databaseProperties)) {
        isSuccessful = false;
        LOG.error("Could not initialize the database");
        servletContextEvent.getServletContext().setAttribute("STARTUP_FAILED", "database");
      }
    } catch (Exception e) {
      isSuccessful = false;
      LOG.error("Could not find database properties", e);
      servletContextEvent.getServletContext().setAttribute("STARTUP_FAILED", "database");
    }

    // Startup the CacheManager (Before any LoadSitePropertyCommand.loadByName() can be used)
    LOG.info("Startup the cache manager...");
    CacheManager.startup();

    // Verify the database filesystem entry
    LOG.info("Checking the file server...");
    String serverRootPath = LoadSitePropertyCommand.loadByName("system.filepath");
    if (StringUtils.isBlank(serverRootPath)) {
      LOG.error("Missing system.filepath");
      isSuccessful = false;
      servletContextEvent.getServletContext().setAttribute("STARTUP_FAILED", "missing system.filepath in database");
    } else {
      File directory = new File(serverRootPath);
      if (!directory.exists()) {
        directory.mkdirs();
      }
      if (!directory.isDirectory()) {
        isSuccessful = false;
        LOG.error("Check system.filepath, directory was not found: " + serverRootPath);
        servletContextEvent.getServletContext().setAttribute("STARTUP_FAILED", "system.filepath setting exists but the directory '" + serverRootPath + "' was not found");
      }
    }

    // The system is not properly setup
    if (!isSuccessful) {
      return;
    }

    // Set a default time zone for JSPs
    String timezone = LoadSitePropertyCommand.loadByName("site.timezone", "America/New_York");
    Config.set(servletContextEvent.getServletContext(), Config.FMT_TIME_ZONE, timezone);

    // Show the timezone's date/time
    Instant timeStamp = Instant.now();
    ZonedDateTime displayDateTime = timeStamp.atZone(ZoneId.of(timezone));
    LOG.info("Display Time: " + displayDateTime);

    // Start up the GeoIP
    GeoIPCommand.setConfig(servletContextEvent.getServletContext());

    // Load the filesystem lists (these are also scheduled in SchedulerManager)
    LoadSystemFilesJob.execute();

    // Preload all the content (@todo change to async)
    List<Content> contentList = ContentRepository.findAll();
    if (contentList != null) {
      ArrayList<String> contentUniqueIdList = new ArrayList<>();
      for (Content content : contentList) {
        contentUniqueIdList.add(content.getUniqueId());
      }
      LOG.info("Load the content cache: " + contentUniqueIdList.size() + " entries");
      CacheManager.getLoadingCache(CONTENT_UNIQUE_ID_CACHE).getAll(contentUniqueIdList);
    }

    // Initialize the workflow engine
    LOG.info("Add the workflows...");
    WorkflowManager.startup(servletContextEvent.getServletContext(), "/WEB-INF/workflows");

    // Startup the distributed job scheduler
    LOG.info("Startup the distributed job scheduler...");
    SchedulerManager.startup(servletContextEvent.getServletContext());

    // Give the go ahead
    servletContextEvent.getServletContext().setAttribute("STARTUP_SUCCESSFUL", "true");
  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    LOG.info("Shutting down...");

    LOG.info("Shutting down the distributed job scheduler...");
    SchedulerManager.shutdown();

    LOG.info("Shutting down the database...");
    DataSource.shutdown();
  }
}
