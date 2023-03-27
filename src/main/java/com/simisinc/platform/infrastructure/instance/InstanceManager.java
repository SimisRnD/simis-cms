/*
 * Copyright 2023 SimIS Inc. (https://www.simiscms.com)
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
package com.simisinc.platform.infrastructure.instance;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Functions to determine the application instance type in a cluster; standalone/main, web
 *
 * @author matt rajkowski
 * @created 3/27/23 6:45 PM
 */
public class InstanceManager {
  private static Log LOG = LogFactory.getLog(InstanceManager.class);

  private static final String NODE_TYPE = "CMS_NODE_TYPE";

  public static void init() {
    if (System.getenv().containsKey(NODE_TYPE)) {
      LOG.info("Instance NODE_TYPE is configured: " + System.getenv(NODE_TYPE));
    }
  }

  /**
   * Checks to see if the current instance is a web only instance; mostly to limit overhead tasks
   * @return if the current instance is configured as 'web' only
   */
  public static boolean isWebNodeOnly() {
    if (System.getenv().containsKey(NODE_TYPE) && "web".equals(System.getenv(NODE_TYPE))) {
      return true;
    }
    return false;
  }
}
