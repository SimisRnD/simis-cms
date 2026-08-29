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

import java.util.List;
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

  // A checkbox-group or select field's userValue stores the chosen options' DISPLAY LABELS, which
  // isn't safely reversible back to option keys if two options ever share a label -- so the
  // originally-submitted keys are tracked here separately, purely so a validation-error redisplay
  // (see FormWidget#post) can mark the right boxes checked, or the right option selected, again.
  // A select records the single chosen key; a checkbox group records every checked one.
  // Never persisted -- FormDataJSONCommand builds the stored JSON from explicit fields and doesn't
  // touch this one.
  private List<String> checkedOptionKeys = null;

  // Database-backed form builder bookkeeping (issue #409). Both default to values the existing
  // XML-preference rendering path (FormFieldCommand#parseFieldContent) never reads or sets, so this
  // reuse of FormField for the admin-CRUD shape is additive and does not affect XML-defined forms.
  private long formDefinitionId = -1;
  private int fieldOrder = -1;

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

  public List<String> getCheckedOptionKeys() {
    return checkedOptionKeys;
  }

  public void setCheckedOptionKeys(List<String> checkedOptionKeys) {
    this.checkedOptionKeys = checkedOptionKeys;
  }

  public boolean isRequired() {
    return isRequired;
  }

  public void setRequired(boolean required) {
    isRequired = required;
  }

  public long getFormDefinitionId() {
    return formDefinitionId;
  }

  public void setFormDefinitionId(long formDefinitionId) {
    this.formDefinitionId = formDefinitionId;
  }

  public int getFieldOrder() {
    return fieldOrder;
  }

  public void setFieldOrder(int fieldOrder) {
    this.fieldOrder = fieldOrder;
  }
}
