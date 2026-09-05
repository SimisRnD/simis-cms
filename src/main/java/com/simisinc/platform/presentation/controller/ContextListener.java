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

import static com.simisinc.platform.infrastructure.cache.CacheManager.CONTENT_UNIQUE_ID_CACHE;

import java.io.File;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.jsp.jstl.core.Config;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.ApplicationInfo;
import com.simisinc.platform.application.FeatureFlagCommand;
import com.simisinc.platform.application.SecretCryptoCommand;
import com.simisinc.platform.application.admin.DatabaseCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ImportLegacyRedirectsCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.database.DatabaseProperties;
import com.simisinc.platform.infrastructure.instance.InstanceManager;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import com.simisinc.platform.infrastructure.scheduler.cms.LoadSystemFilesJob;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;

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

    // Cache-busting token for the platform's own stylesheets (#1333). The vendored CSS carries its
    // version in the path (foundation-6.8.1, swiper-12.1.2, ...) so a new release is a new URL and
    // caches never serve a stale copy. platform.css and platform-tokens.css have no such marker,
    // so a CDN or browser holding the previous build keeps serving it after a deploy -- the deploy
    // succeeds, the site looks unchanged, and it reads as a failed deploy rather than a cache hit.
    // Resolved once at startup rather than per request: these are static files that cannot change
    // while the app is running, and every page render reads this.
    // Widened from the original two to every platform-owned stamped asset (#1872). The rest were
    // still interpolating ApplicationInfo.VERSION straight into their ?v=, and that constant is a
    // release identity edited by hand -- it had not moved since August while these files changed
    // repeatedly, so their cache-buster did not bust. One token for all of them means a change to
    // any one re-fetches the set; that over-busts slightly and is the right trade, since they ship
    // together on a deploy anyway and a single value stays easy to reason about.
    //
    // The two vendored add-to-calendar files are deliberately NOT listed: they carry their version
    // in the path (add-to-calendar-0.1.0), so a new release is already a new URL, and their
    // modification time says nothing useful. They still render this token for uniformity.
    servletContextEvent.getServletContext().setAttribute("assetVersion",
        resolveAssetVersion(servletContextEvent.getServletContext(), ApplicationInfo.VERSION,
            STAMPED_ASSET_PATHS));

    // At-rest secret encryption (#16): warn loudly when the key is absent. Secret storage fails closed, so
    // storing TOTP seeds or integration/payment credentials will be refused until the key is set.
    if (!SecretCryptoCommand.isEnabled()) {
      LOG.warn("CMS_SECRET_KEY is not configured -- at-rest secret encryption is DISABLED. Storing TOTP seeds or "
          + "integration/payment credentials will be refused (fail-closed). Set CMS_SECRET_KEY (a base64-encoded "
          + "256-bit key) to enable those features.");
    }

    // Monitor the success
    boolean isSuccessful = true;

    // Determine the instance type
    InstanceManager.init();

    // Show the system's timezone
    LocalDateTime now = LocalDateTime.now();
    ZoneId serverZoneId = ZoneId.systemDefault();
    LOG.info("Server Time: " + now.atZone(serverZoneId).toString());
    LOG.info("Server TimeZone Id: " + serverZoneId.getId());

    // Startup the database first
    // @todo create and use a separate Rest DataSource pool
    Properties databaseProperties = new Properties();
    try (InputStream is = servletContextEvent.getServletContext()
        .getResourceAsStream("/WEB-INF/classes/database.properties")) {
      LOG.info("Starting up the web database connection pool...");
      // Use the default properties
      databaseProperties.load(is);
      // Check for environment variables, including optional Azure SPN authentication (#1129)
      DatabaseProperties.applyEnvironmentOverrides(databaseProperties);
      DataSource.init(databaseProperties);
      // See if this is a new install or an upgrade
      if (!DatabaseCommand.initialize(databaseProperties)) {
        isSuccessful = false;
        LOG.error("Could not initialize the database");
        servletContextEvent.getServletContext().setAttribute(ContextConstants.STARTUP_FAILED, "database");
      }
    } catch (Exception e) {
      isSuccessful = false;
      LOG.error("Could not find database properties", e);
      servletContextEvent.getServletContext().setAttribute(ContextConstants.STARTUP_FAILED, "database");
    }

    // Startup the CacheManager (Before any LoadSitePropertyCommand.loadByName() can be used)
    LOG.info("Startup the cache manager...");
    CacheManager.startup();

    // Verify the filesystem entry
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    if (StringUtils.isBlank(serverRootPath)) {
      LOG.error("Missing system.filepath");
      isSuccessful = false;
      servletContextEvent.getServletContext().setAttribute(ContextConstants.STARTUP_FAILED,
          "missing system.filepath in database");
    } else {
      LOG.info("Checking the file path: " + serverRootPath);
      File directory = new File(serverRootPath);
      if (!directory.exists()) {
        LOG.info("Creating directory at: " + serverRootPath);
        directory.mkdirs();
      }
      if (!directory.isDirectory()) {
        isSuccessful = false;
        LOG.error("Check system.filepath, directory was not found: " + serverRootPath);
        servletContextEvent.getServletContext().setAttribute(ContextConstants.STARTUP_FAILED,
            "system.filepath setting exists but the directory '" + serverRootPath + "' was not found");
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

    // Log the active feature posture (issue #410) so the log reflects which features.* flags are on
    List<String> activeFeatureFlags = FeatureFlagCommand.getActiveFlagNames();
    LOG.info("Active feature flags: " + (activeFeatureFlags.isEmpty() ? "(none)" : String.join(", ", activeFeatureFlags)));

    // Start up the GeoIP
    GeoIPCommand.setConfig(servletContextEvent.getServletContext());

    // Load the filesystem lists (these are also scheduled in SchedulerManager)
    LoadSystemFilesJob.execute();

    // One-time (idempotent) import of any legacy config/cms/redirects.csv rows into the
    // database-backed web_redirects table (issue #408); see ImportLegacyRedirectsCommand for why
    // this runs here at startup rather than as a Flyway migration or an admin action
    ImportLegacyRedirectsCommand.importFromCsv();

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
    servletContextEvent.getServletContext().setAttribute(ContextConstants.STARTUP_SUCCESSFUL, "true");
  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    LOG.info("Shutting down...");

    LOG.info("Shutting down the distributed job scheduler...");
    SchedulerManager.shutdown();

    LOG.info("Shutting down the database...");
    DataSource.shutdown();
  }

  /**
   * Builds a cache-busting token for the given static assets, from the newest file modification
   * time among them.
   *
   * <p>A modification time is used rather than {@link ApplicationInfo#VERSION} because VERSION is
   * hand-edited per release, not per build -- a stylesheet change that ships without a version bump
   * (the common case) would reuse the same token and keep serving a stale cached file, which is the
   * exact failure this exists to prevent.
   *
   * <p>Falls back to the supplied value when no file can be resolved. {@code getRealPath} returns
   * null for a WAR served unexpanded, so this cannot assume a filesystem path exists. The fallback
   * still changes between releases, which is weaker than per-build but never emits an empty or
   * malformed query string.
   *
   * @param servletContext the context used to resolve each path
   * @param fallback the token to use when no file's timestamp can be read
   * @param paths context-relative asset paths, e.g. {@code /css/platform.css}
   * @return a non-blank token safe to use as a query-string value
   */
  /**
   * The platform-owned assets whose modification time drives the {@code ?v=} token.
   *
   * <p>A named constant rather than an inline argument list so a test can check every entry
   * actually resolves to a file. A typo here fails silently -- resolveAssetVersion skips a path it
   * cannot find, so a misspelled entry simply stops contributing and nothing reports it.
   */
  static final String[] STAMPED_ASSET_PATHS = {
      "/css/platform.css",
      "/css/platform-tokens.css",
      "/css/platform-calendar.css",
      "/css/platform-ecommerce.css",
      "/css/platform-editor.css",
      "/css/platform-leaderboard.css",
      "/css/platform-sitemap-editor.css",
      "/css/platform-todo-list.css",
      "/javascript/platform-editor.js",
      "/javascript/platform-password-reveal.js",
      "/javascript/web-vitals-collector.js",
  };

  /**
   * The same set, for the cache-header decision in WebRequestFilter.
   *
   * <p>Immutable and O(1); the array above stays because resolveAssetVersion takes varargs. One
   * source of truth matters here more than usual: an asset may only be served {@code immutable} if
   * it is in this set, because that is exactly the set whose {@code ?v=} token is recomputed from
   * these files' modification times.
   */
  static final java.util.Set<String> STAMPED_ASSET_PATH_SET = java.util.Set.of(STAMPED_ASSET_PATHS);

  static String resolveAssetVersion(ServletContext servletContext, String fallback, String... paths) {
    long newest = 0L;
    for (String path : paths) {
      String realPath = servletContext.getRealPath(path);
      if (realPath == null) {
        continue;
      }
      File file = new File(realPath);
      if (file.isFile() && file.lastModified() > newest) {
        newest = file.lastModified();
      }
    }
    if (newest > 0L) {
      return String.valueOf(newest);
    }
    LOG.warn("Could not resolve a modification time for the platform stylesheets; falling back to the "
        + "release version for cache-busting. A stylesheet change without a version bump may serve stale.");
    return fallback;
  }
}
