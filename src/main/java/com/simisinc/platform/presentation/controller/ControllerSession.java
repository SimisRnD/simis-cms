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

package com.simisinc.platform.presentation.controller;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/9/18 8:32 AM
 */
public class ControllerSession implements Serializable {

  final static long serialVersionUID = 8345648404174283569L;

  private long created = System.currentTimeMillis();
  private Map<String, Object> widgetData = new HashMap<>();

  public ControllerSession() {

  }

  public long getCreated() {
    return created;
  }

  public void setCreated(long created) {
    this.created = created;
  }

  public void addWidgetData(String parameter, Object value) {
    this.widgetData.put(parameter, value);
  }

  public void addWidgetData(String widgetUniqueId, String parameter, Object value) {
    this.widgetData.put(widgetUniqueId + parameter, value);
  }

  public boolean hasWidgetData(String parameter) {
    return this.widgetData.containsKey(parameter);
  }

  public Object getWidgetData(String parameter) {
    return this.widgetData.get(parameter);
  }

  public Object getWidgetData(String widgetUniqueId, String parameter) {
    return this.widgetData.get(widgetUniqueId + parameter);
  }

  public void clearWidgetData(String widgetUniqueId, String parameter) {
    this.widgetData.remove(widgetUniqueId + parameter);
  }

  public void clearAllWidgetData() {
    this.widgetData.clear();
  }

}
