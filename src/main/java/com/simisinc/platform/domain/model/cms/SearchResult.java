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
 * A search result to be included with the search results
 *
 * @author matt rajkowski
 * @created 8/28/19 3:19 PM
 */
public class SearchResult extends Entity {

  private String link = null;
  private String pageTitle = null;
  private String pageDescription = null;
  private String htmlExcerpt = null;

  public SearchResult() {
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getPageTitle() {
    return pageTitle;
  }

  public void setPageTitle(String pageTitle) {
    this.pageTitle = pageTitle;
  }

  public String getPageDescription() {
    return pageDescription;
  }

  public void setPageDescription(String pageDescription) {
    this.pageDescription = pageDescription;
  }

  public String getHtmlExcerpt() {
    return htmlExcerpt;
  }

  public void setHtmlExcerpt(String htmlExcerpt) {
    this.htmlExcerpt = htmlExcerpt;
  }
}
