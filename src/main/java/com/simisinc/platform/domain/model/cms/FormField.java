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

import java.util.Map;

/**
 * The field definition and user response value
 *
 * @author matt rajkowski
 * @created 6/1/18 11:42 AM
 */
public class FormField extends Entity {

  private Long id = -1L;

  private String label = null;
  private String name = null;
  private String type = null;
  private boolean isRequired = false;
  private String placeholder = null;
  private Map<String, String> listOfOptions = null;
  private String defaultValue = null;
  private String userValue = null;

  public FormField() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public String getUserValue() {
    return userValue;
  }

  public void setUserValue(String userValue) {
    this.userValue = userValue;
  }

  public boolean isRequired() {
    return isRequired;
  }

  public void setRequired(boolean required) {
    isRequired = required;
  }
}
