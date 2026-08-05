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

package com.simisinc.platform.rest.services.cms;

/**
 * The JSON body for {@code POST /api/content/{contentUniqueId}}. {@code format} defaults to
 * {@code "html"} when omitted -- deserialized (not read) by JSON-B, so a missing field simply
 * leaves the field's default rather than throwing.
 *
 * @author SimIS Inc.
 */
public class ContentUpdateRequest {

  public static final String FORMAT_HTML = "html";
  public static final String FORMAT_DELTA = "delta";

  private String content;
  private String format = FORMAT_HTML;
  private boolean publish = false;

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public boolean isPublish() {
    return publish;
  }

  public void setPublish(boolean publish) {
    this.publish = publish;
  }
}
