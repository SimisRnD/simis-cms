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

package com.simisinc.platform.infrastructure.scheduler.cms;

import com.simisinc.platform.application.SessionCommand;
import com.simisinc.platform.application.cms.BlockedIPListCommand;
import com.simisinc.platform.application.cms.FormCommand;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

/**
 * Loads external configuration files
 *
 * @author matt rajkowski
 * @created 3/19/2022 7:47 AM
 */
public class LoadSystemFilesJob {

  private static Log LOG = LogFactory.getLog(LoadSystemFilesJob.class);

  @Job(name = "Load system files")
  public static void execute() {
    BlockedIPListCommand.load();
    FormCommand.load();
    SessionCommand.load();
  }
}
