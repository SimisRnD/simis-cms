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

package com.simisinc.platform.domain.model.cms;

import com.simisinc.platform.domain.model.Entity;

/**
 * A single image-to-tag assignment row (the {@code image_tag_map} join table). Mirrors items'
 * ItemTag, minus the collectionId column items carry (images have no collection scope).
 *
 * @author SimIS
 * @created 8/5/2026
 */
public class ImageTagMap extends Entity {

  private Long id = -1L;

  private long imageId = -1;
  private long imageTagId = -1;

  public ImageTagMap() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getImageId() {
    return imageId;
  }

  public void setImageId(long imageId) {
    this.imageId = imageId;
  }

  public long getImageTagId() {
    return imageTagId;
  }

  public void setImageTagId(long imageTagId) {
    this.imageTagId = imageTagId;
  }
}
