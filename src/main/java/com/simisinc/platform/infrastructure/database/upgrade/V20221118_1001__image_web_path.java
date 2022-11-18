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

import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.simisinc.platform.application.cms.ReplaceImagePathCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

/**
 * Updates existing content values with image web path
 *
 * @author matt rajkowski
 * @created 11/18/22 1:00 PM
 */
public class V20221118_1001__image_web_path extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {

    // Iterate through the repository, replacing image references with the web path value
    List<WebPage> webPageList = WebPageRepository.findAll();
    if (webPageList == null || webPageList.isEmpty()) {
      return;
    }

    for (WebPage webPage : webPageList) {
      boolean doSave = false;
      if (StringUtils.isNotBlank(webPage.getPageXml())) {
        String newContent = ReplaceImagePathCommand.updateImageReferences(webPage.getPageXml());
        if (!newContent.equals(webPage.getPageXml())) {
          webPage.setPageXml(newContent);
          doSave = true;
        }
      }
      if (StringUtils.isNotBlank(webPage.getDraftPageXml())) {
        String newContent = ReplaceImagePathCommand.updateImageReferences(webPage.getDraftPageXml());
        if (!newContent.equals(webPage.getDraftPageXml())) {
          webPage.setDraftPageXml(newContent);
          doSave = true;
        }
      }
      if (doSave) {
        WebPageRepository.save(webPage);
      }
    }
  }
}
