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

import javax.servlet.ServletContext;

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
    try (Connection connection = DB.getConnection()) {
      return connection != null && connection.isValid(2);
    } catch (Exception e) {
      return false;
    }
  }

  /** The file store (CMS_PATH / Azure Files mount) exists and is writable. */
  static boolean fileStoreWritable() {
    try {
      String root = FileSystemCommand.getFileServerRootPath();
      if (StringUtils.isBlank(root)) {
        return false;
      }
      File dir = new File(root);
      return dir.isDirectory() && dir.canWrite();
    } catch (Exception e) {
      return false;
    }
  }
}
