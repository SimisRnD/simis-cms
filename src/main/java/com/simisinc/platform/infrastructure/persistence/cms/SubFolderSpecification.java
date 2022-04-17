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

package com.simisinc.platform.infrastructure.persistence.cms;

import com.simisinc.platform.domain.model.Entity;
import com.simisinc.platform.presentation.controller.DataConstants;

/**
 * Properties for querying objects from the sub-folder repository
 *
 * @author matt rajkowski
 * @created 8/27/19 3:26 PM
 */
public class SubFolderSpecification extends Entity {

  private Long id = -1L;
  private long folderId = -1L;
  private Long forUserId = -1L;
  private int hasFiles = DataConstants.UNDEFINED;
  private long year = -1L;

  public SubFolderSpecification() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getFolderId() {
    return folderId;
  }

  public void setFolderId(long folderId) {
    this.folderId = folderId;
  }

  public Long getForUserId() {
    return forUserId;
  }

  public void setForUserId(Long forUserId) {
    this.forUserId = forUserId;
  }

  public int getHasFiles() {
    return hasFiles;
  }

  public void setHasFiles(int hasFiles) {
    this.hasFiles = hasFiles;
  }

  public void setHasFiles(boolean hasFiles) {
    this.hasFiles = (hasFiles ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public long getYear() {
    return year;
  }

  public void setYear(long year) {
    this.year = year;
  }
}
