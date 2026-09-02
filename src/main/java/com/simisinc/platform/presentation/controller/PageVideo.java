/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.presentation.controller;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * A self-hosted video a page actually shows, described well enough to emit schema.org VideoObject
 * (issue #1795).
 *
 * <p>
 * Every field is authored, none is inferred. The descriptive half -- name, description, upload date,
 * format -- comes from the video's own record in the file library, where an administrator edits it
 * once and every page showing that video says the same thing. The thumbnail comes from the
 * {@code poster} attribute on the {@code <video>} element, because a poster frame is a choice about
 * this page's presentation rather than a property of the file; it is also the attribute a browser
 * already uses, so setting it improves what a visitor sees before pressing play, not only what a
 * crawler reads.
 * </p>
 *
 * <p>
 * URLs are kept exactly as the content and the file record hold them -- usually site-relative.
 * Making them absolute needs the site URL, which is a rendering concern, so
 * {@link StructuredDataCommand} does it at emit time, the same as it already does for the site logo
 * and the page image.
 * </p>
 *
 * @author SimIS Inc.
 */
public class PageVideo implements Serializable {

  static final long serialVersionUID = 8484048371911908901L;

  private String name = null;
  private String description = null;
  private String thumbnailUrl = null;
  private String contentUrl = null;
  private String encodingFormat = null;
  private Timestamp uploadDate = null;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getThumbnailUrl() {
    return thumbnailUrl;
  }

  public void setThumbnailUrl(String thumbnailUrl) {
    this.thumbnailUrl = thumbnailUrl;
  }

  public String getContentUrl() {
    return contentUrl;
  }

  public void setContentUrl(String contentUrl) {
    this.contentUrl = contentUrl;
  }

  public String getEncodingFormat() {
    return encodingFormat;
  }

  public void setEncodingFormat(String encodingFormat) {
    this.encodingFormat = encodingFormat;
  }

  public Timestamp getUploadDate() {
    return uploadDate;
  }

  public void setUploadDate(Timestamp uploadDate) {
    this.uploadDate = uploadDate;
  }
}
