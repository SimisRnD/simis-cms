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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sanctionco.jmail.JMail;
import com.simisinc.platform.application.FieldLengthCommand;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;

/**
 * Validates and saves a database-backed form definition (issue #409). The uniqueId is derived from
 * the name the same way SaveCollectionCommand#generateUniqueId derives a Collection's uniqueId --
 * kept stable across an edit as long as the name is unchanged, and regenerated (with a numeric
 * suffix if needed for uniqueness) when it does.
 *
 * @author SimIS Inc.
 */
public class SaveFormDefinitionCommand {

  // @column form_definitions.name
  private static final int MAX_NAME_LENGTH = 255;
  // @column form_definitions.title
  private static final int MAX_TITLE_LENGTH = 255;
  // The narrowest field on this form by some way: a button caption that runs past 100 characters
  // is unusual but entirely typeable, and nothing on the way down was refusing it.
  // @column form_definitions.button_name
  private static final int MAX_BUTTON_NAME_LENGTH = 100;

  private static final String ALLOWED_CHARS = "abcdefghijklmnopqrstuvwxyz1234567890";
  private static Log LOG = LogFactory.getLog(SaveFormDefinitionCommand.class);

  public static FormDefinition saveFormDefinition(FormDefinition formDefinitionBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(formDefinitionBean.getName())) {
      errorMessages.append("A name is required");
    } else {
      FieldLengthCommand.appendIfTooLong(errorMessages, ", ", "A name",
          formDefinitionBean.getName(), MAX_NAME_LENGTH);
    }
    FieldLengthCommand.appendIfTooLong(errorMessages, ", ", "A title",
        formDefinitionBean.getTitle(), MAX_TITLE_LENGTH);
    FieldLengthCommand.appendIfTooLong(errorMessages, ", ", "A button name",
        formDefinitionBean.getButtonName(), MAX_BUTTON_NAME_LENGTH);
    if (formDefinitionBean.getModifiedBy() == -1) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("The user saving this form was not set");
    }
    // "Email submissions to" is optional (a blank value falls back to the community-manager role),
    // but if something's there, catch an unusable address before it's saved rather than let
    // notifications start silently disappearing -- see EmailTask, which only fails loudly on a
    // syntactically-invalid address, never on one that's merely wrong (a typo of a real address is
    // unrecoverable by any validation, so this only closes the invalid-syntax half of that gap)
    String emailToError = findInvalidEmailAddress(formDefinitionBean.getEmailTo());
    if (emailToError != null) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("'").append(emailToError).append("' is not a valid email address");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    FormDefinition formDefinition;
    if (formDefinitionBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      formDefinition = FormDefinitionRepository.findById(formDefinitionBean.getId());
      if (formDefinition == null) {
        throw new DataException("The existing record could not be found");
      }
      // createdBy is intentionally left untouched on an edit -- only an insert sets it, so the
      // audit trail keeps pointing at whoever originally created the form
    } else {
      LOG.debug("Saving a new record... ");
      formDefinition = new FormDefinition();
      formDefinition.setCreatedBy(formDefinitionBean.getCreatedBy());
    }
    // @note set the uniqueId before setting the name
    formDefinition.setUniqueId(generateUniqueId(formDefinition, formDefinitionBean));
    formDefinition.setName(formDefinitionBean.getName());
    formDefinition.setTitle(formDefinitionBean.getTitle());
    formDefinition.setSubtitle(formDefinitionBean.getSubtitle());
    formDefinition.setButtonName(formDefinitionBean.getButtonName());
    formDefinition.setSuccessTitle(formDefinitionBean.getSuccessTitle());
    formDefinition.setSuccessMessage(formDefinitionBean.getSuccessMessage());
    formDefinition.setEmailTo(formDefinitionBean.getEmailTo());
    formDefinition.setUseCaptcha(formDefinitionBean.getUseCaptcha());
    formDefinition.setCheckForSpam(formDefinitionBean.getCheckForSpam());
    formDefinition.setEnabled(formDefinitionBean.getEnabled());
    formDefinition.setShowPrivacyNotice(formDefinitionBean.getShowPrivacyNotice());
    formDefinition.setSendConfirmationToSubmitter(formDefinitionBean.getSendConfirmationToSubmitter());
    formDefinition.setConfirmationSubject(formDefinitionBean.getConfirmationSubject());
    formDefinition.setConfirmationMessage(formDefinitionBean.getConfirmationMessage());
    formDefinition.setModifiedBy(formDefinitionBean.getModifiedBy());
    return FormDefinitionRepository.save(formDefinition);
  }

  /**
   * "Email submissions to" accepts a comma-separated list (see EmailTask, which splits on comma
   * and calls addTo() per entry). Returns the first entry that fails JMail's syntax check -- the
   * same validator FormWidget.post() already uses for an "email"-type field -- or null if the
   * value is blank or every entry is valid. Public so FormDefinitionFormWidget's "Send Test Email"
   * action can run the identical check against an unsaved, just-typed value before sending.
   */
  public static String findInvalidEmailAddress(String emailTo) {
    if (StringUtils.isBlank(emailTo)) {
      return null;
    }
    for (String address : emailTo.split(",")) {
      String trimmed = address.trim();
      if (StringUtils.isBlank(trimmed)) {
        continue;
      }
      if (!JMail.isValid(trimmed)) {
        return trimmed;
      }
    }
    return null;
  }

  private static String generateUniqueId(FormDefinition previousItem, FormDefinition item) {

    // Use the existing uniqueId when the name hasn't changed
    if (previousItem.getUniqueId() != null && previousItem.getName() != null
        && previousItem.getName().equals(item.getName())) {
      return previousItem.getUniqueId();
    }

    // Create a new one from the name
    StringBuilder sb = new StringBuilder();
    String name = item.getName().toLowerCase();
    final int len = name.length();
    for (int i = 0; i < len; i++) {
      char c = name.charAt(i);
      if (ALLOWED_CHARS.indexOf(c) > -1) {
        sb.append(c);
      } else if (c == ' ') {
        sb.append("-");
      }
    }

    // Find the next available unique instance
    int count = 1;
    String originalUniqueId = sb.toString();
    String uniqueId = sb.toString();
    while (FormDefinitionRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = originalUniqueId + "-" + count;
    }
    return uniqueId;
  }
}
