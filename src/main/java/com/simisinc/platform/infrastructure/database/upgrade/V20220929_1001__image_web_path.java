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

import java.util.List;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.simisinc.platform.application.cms.ReplaceImagePathCommand;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;

/**
 * Updates existing content values with image web path
 *
 * @author matt rajkowski
 * @created 9/29/22 8:00 PM
 */
public class V20220929_1001__image_web_path extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {

    // Check the system images, replace image references with the web path value
    List<SiteProperty> sitePropertyList = SitePropertyRepository.findAll();
    for (SiteProperty siteProperty : sitePropertyList) {
      if (!"image".equals(siteProperty.getType())) {
        continue;
      }
      if (siteProperty.getValue() == null || !siteProperty.getValue().contains("/assets/img/")) {
        continue;
      }

      String newContent = ReplaceImagePathCommand.updateImageReferences(siteProperty.getValue());
      if (!newContent.equals(siteProperty.getValue())) {
        siteProperty.setValue(newContent);
        SitePropertyRepository.save(siteProperty);
      }
    }
  }
}
