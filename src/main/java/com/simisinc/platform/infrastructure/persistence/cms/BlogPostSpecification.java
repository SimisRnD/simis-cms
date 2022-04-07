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

import com.simisinc.platform.presentation.controller.DataConstants;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/7/18 9:16 AM
 */
public class BlogPostSpecification {

  private long id = -1L;
  private long blogId = -1L;
  private String uniqueId = null;
  private int publishedOnly = DataConstants.UNDEFINED;
  private int startDateIsBeforeNow = DataConstants.UNDEFINED;
  private int isWithinEndDate = DataConstants.UNDEFINED;

  public BlogPostSpecification() {
  }

  public BlogPostSpecification(long id) {
    this.id = id;
  }

  public BlogPostSpecification(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getBlogId() {
    return blogId;
  }

  public void setBlogId(long blogId) {
    this.blogId = blogId;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public int getPublishedOnly() {
    return publishedOnly;
  }

  public void setPublishedOnly(boolean publishedOnly) {
    this.publishedOnly = (publishedOnly ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public void setPublishedOnly(int publishedOnly) {
    this.publishedOnly = publishedOnly;
  }

  public int getStartDateIsBeforeNow() {
    return startDateIsBeforeNow;
  }

  public void setStartDateIsBeforeNow(int startDateIsBeforeNow) {
    this.startDateIsBeforeNow = startDateIsBeforeNow;
  }

  public void setStartDateIsBeforeNow(boolean startDateIsBeforeNow) {
    this.startDateIsBeforeNow = (startDateIsBeforeNow ? DataConstants.TRUE : DataConstants.FALSE);
  }

  public int getIsWithinEndDate() {
    return isWithinEndDate;
  }

  public void setIsWithinEndDate(int isWithinEndDate) {
    this.isWithinEndDate = isWithinEndDate;
  }

  public void setIsWithinEndDate(boolean isWithinEndDate) {
    this.isWithinEndDate = (isWithinEndDate ? DataConstants.TRUE : DataConstants.FALSE);
  }
}
