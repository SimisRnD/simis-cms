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

package com.simisinc.platform.application;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletContext;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.presentation.controller.ContextConstants;

/**
 * A lightweight readiness check for load balancers and the platform health probe (Azure App Service
 * "Health check", container HEALTHCHECK, Kubernetes readiness). It reports UP only when the app finished
 * startup, the database is reachable, and the file store is writable -- the three things that must hold for
 * the app to actually serve requests. Deliberately returns no detail (avoids version/topology disclosure).
 *
 * <p><b>Readiness, not liveness.</b> Because the DB is a shared dependency, this must gate an instance OUT
 * of rotation, never restart it -- do NOT wire it as a Kubernetes liveness probe (a DB outage would fail
 * every replica at once and crash-loop the fleet). The individual checks are best-effort; a hung dependency
 * (e.g. a stalled file mount or an exhausted pool) is bounded by the probe caller's own timeout.
 *
 * @author SimIS Inc.
 */
public class HealthCommand {

  private HealthCommand() {
    // Static utility
  }

  public static final String DATABASE_SERVICE = "database";
  public static final String FILESYSTEM_SERVICE = "filesystem";

  /** True only when every readiness check passes. Never throws. */
  public static boolean isReady(ServletContext context) {
    return startedUp(context) && databaseReachable() && fileStoreWritable();
  }

  /** The ContextListener finished initialization successfully (DB pool up, migrations applied). */
  static boolean startedUp(ServletContext context) {
    return context != null && "true".equals(context.getAttribute(ContextConstants.STARTUP_SUCCESSFUL));
  }

  /** A pooled connection is obtainable and valid (the DB has not gone away since startup). */
  static boolean databaseReachable() {
    return checkDatabase().isUp();
  }

  /** The file store (CMS_PATH / Azure Files mount) exists and is writable. */
  static boolean fileStoreWritable() {
    return checkFileStore().isUp();
  }

  /**
   * The database check, with timing and an error message on failure -- used by SystemHealthJob to
   * populate the admin Health Dashboard (issue #466). Behaviorally identical to
   * {@link #databaseReachable()}, just with the detail that a plain boolean throws away.
   */
  public static CheckResult checkDatabase() {
    long start = System.currentTimeMillis();
    try (Connection connection = DB.getConnection()) {
      boolean up = connection != null && connection.isValid(2);
      return new CheckResult(DATABASE_SERVICE, up, System.currentTimeMillis() - start,
          up ? null : "Connection could not be validated");
    } catch (Exception e) {
      return new CheckResult(DATABASE_SERVICE, false, System.currentTimeMillis() - start, e.getMessage());
    }
  }

  /**
   * The file store check, with timing and an error message on failure -- used by SystemHealthJob to
   * populate the admin Health Dashboard (issue #466). Behaviorally identical to
   * {@link #fileStoreWritable()}, just with the detail that a plain boolean throws away.
   */
  public static CheckResult checkFileStore() {
    long start = System.currentTimeMillis();
    try {
      String root = FileSystemCommand.getFileServerRootPath();
      if (StringUtils.isBlank(root)) {
        return new CheckResult(FILESYSTEM_SERVICE, false, System.currentTimeMillis() - start,
            "File store root path is not configured");
      }
      File dir = new File(root);
      boolean up = dir.isDirectory() && dir.canWrite();
      return new CheckResult(FILESYSTEM_SERVICE, up, System.currentTimeMillis() - start,
          up ? null : "File store root is missing or not writable: " + root);
    } catch (Exception e) {
      return new CheckResult(FILESYSTEM_SERVICE, false, System.currentTimeMillis() - start, e.getMessage());
    }
  }

  /** The individually-checkable results, for anything that needs per-service detail rather than the
   * single ANDed {@link #isReady(ServletContext)} boolean. Never throws. */
  public static List<CheckResult> runAllChecks() {
    List<CheckResult> results = new ArrayList<>();
    results.add(checkDatabase());
    results.add(checkFileStore());
    return results;
  }

  /** The outcome of a single readiness check: which service, whether it passed, how long it took,
   * and (on failure) why. */
  public static class CheckResult {
    private final String serviceName;
    private final boolean up;
    private final long responseTimeMs;
    private final String errorMessage;

    public CheckResult(String serviceName, boolean up, long responseTimeMs, String errorMessage) {
      this.serviceName = serviceName;
      this.up = up;
      this.responseTimeMs = responseTimeMs;
      this.errorMessage = errorMessage;
    }

    public String getServiceName() {
      return serviceName;
    }

    public boolean isUp() {
      return up;
    }

    public long getResponseTimeMs() {
      return responseTimeMs;
    }

    public String getErrorMessage() {
      return errorMessage;
    }
  }
}
