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

package com.simisinc.platform.domain.model.dashboard;

import com.simisinc.platform.domain.model.Entity;

/**
 * Data for showing in the UI
 *
 * @author matt rajkowski
 * @created 5/19/22 9:23 PM
 */
public class ProgressCard extends Entity {

  private String label = null;
  private String value = null;
  private String icon = null;
  private String link = null;
  private int progress = 0;
  private int difference = 100;
  private int maxValue = 100;
  private String maxLabel = null;

  public ProgressCard() {
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public int getProgress() {
    return progress;
  }

  public void setProgress(int progress) {
    this.progress = progress;
  }

  public int getDifference() {
    return difference;
  }

  public void setDifference(int difference) {
    this.difference = difference;
  }

  public int getMaxValue() {
    return maxValue;
  }

  public void setMaxValue(int maxValue) {
    this.maxValue = maxValue;
  }

  public String getMaxLabel() {
    return maxLabel;
  }

  public void setMaxLabel(String maxLabel) {
    this.maxLabel = maxLabel;
  }
}
