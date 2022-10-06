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
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;

/**
 * Updates existing content values with image web path
 *
 * @author matt rajkowski
 * @created 10/3/22 8:15 PM
 */
public class V20220929_1002__image_web_path extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {

    // Iterate through the content repository, replacing image references with the web path value
    List<Content> contentList = ContentRepository.findAll();
    if (contentList == null || contentList.isEmpty()) {
      return;
    }

    for (Content content : contentList) {
      boolean doSave = false;
      if (StringUtils.isNotBlank(content.getContent())) {
        String newContent = ReplaceImagePathCommand.updateImageReferences(content.getContent());
        if (!newContent.equals(content.getContent())) {
          content.setContent(newContent);
          doSave = true;
        }
      }
      if (StringUtils.isNotBlank(content.getDraftContent())) {
        String newContent = ReplaceImagePathCommand.updateImageReferences(content.getDraftContent());
        if (!newContent.equals(content.getDraftContent())) {
          content.setDraftContent(newContent);
          doSave = true;
        }
      }
      if (doSave) {
        ContentRepository.save(content);
      }
    }
  }
}
