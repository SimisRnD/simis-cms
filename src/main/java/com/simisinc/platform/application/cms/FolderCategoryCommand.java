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

import com.simisinc.platform.domain.model.cms.FolderCategory;
import com.simisinc.platform.infrastructure.persistence.cms.FolderCategoryRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Displays the folder's category name
 *
 * @author matt rajkowski
 * @created 9/6/19 2:11 PM
 */
public class FolderCategoryCommand {

  private static Log LOG = LogFactory.getLog(FolderCategoryCommand.class);

  public static String text(Long categoryId) {
    FolderCategory folderCategory = FolderCategoryRepository.findById(categoryId);
    if (folderCategory == null) {
      return null;
    }
    return folderCategory.getName();
  }

}
