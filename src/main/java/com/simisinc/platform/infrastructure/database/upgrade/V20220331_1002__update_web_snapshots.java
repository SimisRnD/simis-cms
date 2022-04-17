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

package com.simisinc.platform.infrastructure.database.upgrade;

import com.simisinc.platform.infrastructure.persistence.cms.WebPageHitRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Updates the web snapshots, incorporating changes to the bot data
 *
 * @author matt rajkowski
 * @created 3/7/20 4:29 PM
 */
public class V20220331_1002__update_web_snapshots extends BaseJavaMigration {

  private static Log LOG = LogFactory.getLog(BaseJavaMigration.class);

  @Override
  public void migrate(Context context) throws Exception {

    // The snapshot filters out bots so good idea to re-run this
    LocalDate now = LocalDate.now();
    for (int i = 0; i < 365; i++) {
      LocalDate startDate = now.minusDays(i);
      LocalDate endDate = startDate.plusDays(1);
      WebPageHitRepository.createSnapshot(
          Timestamp.valueOf(startDate.atStartOfDay()),
          Timestamp.valueOf(endDate.atStartOfDay()));
    }
  }
}
