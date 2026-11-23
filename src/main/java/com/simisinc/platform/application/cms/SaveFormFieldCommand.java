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
  // that promise is kept. The seventh type, "file" (issue #2130), is checked separately below,
  // because whether it is allowed depends on the site's feature flag.
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
    boolean isFileField = FormFileFieldCommand.isFileField(fieldBean);
    if (StringUtils.isNotBlank(fieldBean.getType()) && !VALID_FIELD_TYPES.contains(fieldBean.getType()) && !isFileField) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("A valid field type is required");
    }
    // A new file field needs the feature flag. A field that is already a file field can still be
    // edited and saved with the flag off, so turning the flag off never forces an editor to change
    // the field's type just to fix a label.
    if (isFileField && !FormFileFieldCommand.isEnabled()
        && !FormFileFieldCommand.isFileField(FormFieldRepository.findById(fieldBean.getId()))) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("File upload fields are turned off for this site");
    }
    String acceptedKinds = null;
    long maxBytes = -1;
    if (isFileField) {
      try {
        acceptedKinds = FormFileFieldCommand.normalizeKinds(fieldBean.getAcceptedKinds());
      } catch (IllegalArgumentException e) {
        if (errorMessages.length() > 0) {
          errorMessages.append(", ");
        }
        errorMessages.append(e.getMessage());
      }
      try {
        maxBytes = FormFileFieldCommand.normalizeMaxBytes(fieldBean.getMaxBytes());
      } catch (IllegalArgumentException e) {
        if (errorMessages.length() > 0) {
          errorMessages.append(", ");
        }
        errorMessages.append(e.getMessage());
      }
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
    if (isFileField) {
      // A file field has no default answer, and Options would make form.jsp render it as a
      // dropdown instead (Options are checked before Type), so neither is kept
      field.setDefaultValue(null);
      field.setListOfOptions(null);
    } else {
      field.setDefaultValue(fieldBean.getDefaultValue());
      field.setListOfOptions(fieldBean.getListOfOptions());
    }
    // Null and -1 on every other type, so a field changed away from "file" drops its file limits
    field.setAcceptedKinds(acceptedKinds);
    field.setMaxBytes(maxBytes);
    return FormFieldRepository.save(field);
  }
}
