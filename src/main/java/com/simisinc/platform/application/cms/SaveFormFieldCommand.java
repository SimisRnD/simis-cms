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

package com.simisinc.platform.application.cms;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormFieldRepository;

/**
 * Validates and saves a field belonging to a database-backed {@link FormDefinition} (issue #409).
 *
 * @author SimIS Inc.
 */
public class SaveFormFieldCommand {

  private static Log LOG = LogFactory.getLog(SaveFormFieldCommand.class);

  // The set form-field-form.jsp's "Type" select actually offers, and the exact list
  // NEW_10010__new_cms.sql's form_fields.field_type comment documents as valid -- that comment
  // promises this is "validated in application code, not a DB CHECK constraint", so this is where
  // that promise is kept.
  private static final Set<String> VALID_FIELD_TYPES = Set.of("text", "email", "textarea", "select", "checkbox", "date");

  public static FormField saveField(FormField fieldBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (fieldBean.getFormDefinitionId() == -1 || FormDefinitionRepository.findById(fieldBean.getFormDefinitionId()) == null) {
      errorMessages.append("A form is required");
    }
    if (StringUtils.isBlank(fieldBean.getLabel())) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("A label is required");
    }
    if (StringUtils.isNotBlank(fieldBean.getType()) && !VALID_FIELD_TYPES.contains(fieldBean.getType())) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("A valid field type is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    FormField field;
    if (fieldBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      field = FormFieldRepository.findById(fieldBean.getId());
      if (field == null || field.getFormDefinitionId() != fieldBean.getFormDefinitionId()) {
        // Either the field doesn't exist, or the request is trying to move it under a different
        // form -- treat both the same as "not found" rather than silently reparenting a field
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      field = new FormField();
      field.setFormDefinitionId(fieldBean.getFormDefinitionId());
      // A new field must get an explicit, distinct field_order -- otherwise every un-reordered add
      // defaults to the field_order column's own DEFAULT 100 and two consecutive adds collide, with
      // findAllByFormDefinitionId's insertion-order tiebreak only coincidentally masking it (issue
      // #409 follow-up). Matches SaveMenuItemCommand's use of MenuItemRepository#getNextTabOrder.
      field.setFieldOrder(FormFieldRepository.getNextFieldOrder(fieldBean.getFormDefinitionId()));
    }
    // The html/database field name defaults to a slug of the label, the same conversion
    // FormFieldCommand#generateHtmlName already performs for XML-defined fields, so an admin can
    // leave "Name" blank and still get a valid one
    String requestedName = StringUtils.isNotBlank(fieldBean.getName())
        ? fieldBean.getName().trim()
        : FormFieldCommand.generateHtmlName(fieldBean.getLabel(), null);
    // On the live form, every field's submitted value is read back by this exact string
    // (FormWidget reads context.getParameter(widgetUniqueId + field.getName())), and a repeated
    // HTML form-field name only ever yields its FIRST value to getParameter() -- so two fields on
    // the same form sharing a Name isn't cosmetic, it's silent data loss: the second field's real
    // answer is discarded and a blank second required field can pass validation by inheriting the
    // first field's value. Neither the "Name" input's own uniqueness nor the label-based slug this
    // falls back to when Name is left blank are deduped against sibling fields, so guarantee it here.
    Set<String> siblingNames = new HashSet<>();
    List<FormField> siblingFields = FormFieldRepository.findAllByFormDefinitionId(fieldBean.getFormDefinitionId());
    for (FormField sibling : siblingFields) {
      if (sibling.getId() != field.getId()) {
        siblingNames.add(sibling.getName());
      }
    }
    String name = requestedName;
    int suffix = 2;
    while (siblingNames.contains(name)) {
      name = requestedName + "-" + suffix;
      ++suffix;
    }
    field.setName(name);
    field.setLabel(fieldBean.getLabel());
    field.setType(StringUtils.isNotBlank(fieldBean.getType()) ? fieldBean.getType() : "text");
    field.setRequired(fieldBean.isRequired());
    field.setPlaceholder(fieldBean.getPlaceholder());
    field.setDefaultValue(fieldBean.getDefaultValue());
    field.setListOfOptions(fieldBean.getListOfOptions());
    return FormFieldRepository.save(field);
  }
}
