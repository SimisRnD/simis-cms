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
 * Description
 *
 * @author matt rajkowski
 * @created 2/6/2022 12:15 PM
 */
public class WebPageSpecification extends Entity {

  private String link = null;
  private int enabled = DataConstants.UNDEFINED;
  private int draft = DataConstants.UNDEFINED;
  private int searchable = DataConstants.UNDEFINED;
  private int inSitemap = DataConstants.UNDEFINED;
  private int hasRedirect = DataConstants.UNDEFINED;

  public WebPageSpecification() {
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public int getEnabled() {
    return enabled;
  }

  public void setEnabled(int enabled) {
    this.enabled = enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = (enabled ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getDraft() {
    return draft;
  }

  public void setDraft(int draft) {
    this.draft = draft;
  }

  public void setDraft(boolean draft) {
    this.draft = (draft ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getSearchable() {
    return searchable;
  }

  public void setSearchable(int searchable) {
    this.searchable = searchable;
  }

  public void setSearchable(boolean searchable) {
    this.searchable = (searchable ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getInSitemap() {
    return inSitemap;
  }

  public void setInSitemap(int inSitemap) {
    this.inSitemap = inSitemap;
  }

  public void setInSitemap(boolean inSitemap) {
    this.inSitemap = (inSitemap ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getHasRedirect() {
    return hasRedirect;
  }

  public void setHasRedirect(int hasRedirect) {
    this.hasRedirect = hasRedirect;
  }

  public void setHasRedirect(boolean hasRedirect) {
    this.hasRedirect = (hasRedirect ? DataConstants.TRUE : DataConstants.FALSE);
  }
}
