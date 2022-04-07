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

package com.simisinc.platform.domain.model.ecommerce;

import com.simisinc.platform.domain.model.Entity;

/**
 * E-commerce fulfillment option
 *
 * @author matt rajkowski
 * @created 4/9/20 1:29 PM
 */
public class FulfillmentOption extends Entity {

  public static final String IN_HOUSE = "IN-HOUSE";
  public static final String BOXZOOKA = "BOXZOOKA";

  private long id = -1;
  private String code = null;
  private String title = null;
  private boolean enabled = true;
  private boolean overridesOthers = false;

  public FulfillmentOption() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean getOverridesOthers() {
    return overridesOthers;
  }

  public void setOverridesOthers(boolean overridesOthers) {
    this.overridesOthers = overridesOthers;
  }
}
