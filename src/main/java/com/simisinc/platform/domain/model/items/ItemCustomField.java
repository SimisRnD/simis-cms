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

package com.simisinc.platform.domain.model.items;

import java.util.Map;

/**
 * Additional custom information related to an item
 *
 * @author matt rajkowski
 * @created 8/9/18 3:26 PM
 */
public class ItemCustomField {

  final static long serialVersionUID = 8345648404174283569L;

  private String label = null;
  private String name = null;
  private String type = null;
  private boolean isRequired = false;
  private String placeholder = null;
  private Map<String, String> listOfOptions = null;
  private String defaultValue = null;
  private String value = null;

  public ItemCustomField() {
  }

  public ItemCustomField(String name, String label, String value) {
    this.name = name;
    this.label = label;
    this.value = value;
  }

  public ItemCustomField(String name, String label, String type, String value) {
    this.name = name;
    this.label = label;
    this.type = type;
    this.value = value;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public boolean isRequired() {
    return isRequired;
  }

  public void setRequired(boolean required) {
    isRequired = required;
  }

  public String getPlaceholder() {
    return placeholder;
  }

  public void setPlaceholder(String placeholder) {
    this.placeholder = placeholder;
  }

  public Map<String, String> getListOfOptions() {
    return listOfOptions;
  }

  public void setListOfOptions(Map<String, String> listOfOptions) {
    this.listOfOptions = listOfOptions;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }
}
