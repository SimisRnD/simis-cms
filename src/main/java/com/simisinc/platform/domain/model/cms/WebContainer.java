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

package com.simisinc.platform.domain.model.cms;

import com.simisinc.platform.domain.model.Entity;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/21/21 9:39 PM
 */
public class WebContainer extends Entity {

  private Long id = -1L;
  private String name = null;
  private String label = null;
  private String imagePath = null;
  private boolean draft = false;
  private String containerXml = null;
  private String draftXml = null;

  public WebContainer() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getImagePath() {
    return imagePath;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  public boolean getDraft() {
    return draft;
  }

  public void setDraft(boolean draft) {
    this.draft = draft;
  }

  public String getContainerXml() {
    return containerXml;
  }

  public void setContainerXml(String containerXml) {
    this.containerXml = containerXml;
  }

  public String getDraftXml() {
    return draftXml;
  }

  public void setDraftXml(String draftXml) {
    this.draftXml = draftXml;
  }
}
