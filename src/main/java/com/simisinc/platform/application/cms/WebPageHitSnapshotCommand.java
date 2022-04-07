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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.infrastructure.persistence.cms.WebPageHitRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/21/18 2:40 PM
 */
public class WebPageHitSnapshotCommand {

  private static Log LOG = LogFactory.getLog(WebPageHitSnapshotCommand.class);

  public static void updateSnapshots() {

    LOG.debug("Updating snapshots...");

    // Go back 7 days just in case...
    LocalDate now = LocalDate.now();
    for (int i = 0; i < 6; i++) {
      LocalDate startDate = now.minusDays(i);
      LocalDate endDate = startDate.plusDays(1);
      WebPageHitRepository.createSnapshot(
          Timestamp.valueOf(startDate.atStartOfDay()),
          Timestamp.valueOf(endDate.atStartOfDay()));
    }
  }
}
