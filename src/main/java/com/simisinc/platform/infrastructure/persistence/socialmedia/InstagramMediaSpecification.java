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

package com.simisinc.platform.infrastructure.persistence.socialmedia;

/**
 * Properties for querying objects from the Instagram media repository
 *
 * @author matt rajkowski
 * @created 10/9/19 8:13 PM
 */
public class InstagramMediaSpecification {

  private String mediaType = null;

  public InstagramMediaSpecification() {
  }

  public String getMediaType() {
    return mediaType;
  }

  public void setMediaType(String mediaType) {
    this.mediaType = mediaType;
  }

  public void setIsImage() {
    this.mediaType = "IMAGE";
  }

  public void setIsVideo() {
    this.mediaType = "VIDEO";
  }

}
