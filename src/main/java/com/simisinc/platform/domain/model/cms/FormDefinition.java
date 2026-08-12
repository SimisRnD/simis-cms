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

import java.sql.Timestamp;

import com.simisinc.platform.domain.model.Entity;

/**
 * A database-backed form configuration (issue #409) -- the admin-editable alternative to
 * configuring a {@code form} widget's fields entirely through page XML preferences. FormWidget
 * matches a request to one of these by {@code uniqueId}, the same role {@code formUniqueId} plays
 * for an XML-defined form.
 *
 * @author SimIS Inc.
 */
public class FormDefinition extends Entity {

  private Long id = -1L;

  private String uniqueId = null;
  private String name = null;
  private String title = null;
  private String subtitle = null;
  private String buttonName = null;
  private String successTitle = null;
  private String successMessage = null;
  private String emailTo = null;
  private boolean useCaptcha = false;
  private boolean checkForSpam = true;
  private boolean enabled = true;
  private boolean showPrivacyNotice = false;
  private boolean sendConfirmationToSubmitter = false;
  private String confirmationSubject = null;
  private String confirmationMessage = null;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;

  public FormDefinition() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSubtitle() {
    return subtitle;
  }

  public void setSubtitle(String subtitle) {
    this.subtitle = subtitle;
  }

  public String getButtonName() {
    return buttonName;
  }

  public void setButtonName(String buttonName) {
    this.buttonName = buttonName;
  }

  public String getSuccessTitle() {
    return successTitle;
  }

  public void setSuccessTitle(String successTitle) {
    this.successTitle = successTitle;
  }

  public String getSuccessMessage() {
    return successMessage;
  }

  public void setSuccessMessage(String successMessage) {
    this.successMessage = successMessage;
  }

  public String getEmailTo() {
    return emailTo;
  }

  public void setEmailTo(String emailTo) {
    this.emailTo = emailTo;
  }

  public boolean getUseCaptcha() {
    return useCaptcha;
  }

  public void setUseCaptcha(boolean useCaptcha) {
    this.useCaptcha = useCaptcha;
  }

  public boolean getCheckForSpam() {
    return checkForSpam;
  }

  public void setCheckForSpam(boolean checkForSpam) {
    this.checkForSpam = checkForSpam;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean getShowPrivacyNotice() {
    return showPrivacyNotice;
  }

  public void setShowPrivacyNotice(boolean showPrivacyNotice) {
    this.showPrivacyNotice = showPrivacyNotice;
  }

  public boolean getSendConfirmationToSubmitter() {
    return sendConfirmationToSubmitter;
  }

  public void setSendConfirmationToSubmitter(boolean sendConfirmationToSubmitter) {
    this.sendConfirmationToSubmitter = sendConfirmationToSubmitter;
  }

  public String getConfirmationSubject() {
    return confirmationSubject;
  }

  public void setConfirmationSubject(String confirmationSubject) {
    this.confirmationSubject = confirmationSubject;
  }

  public String getConfirmationMessage() {
    return confirmationMessage;
  }

  public void setConfirmationMessage(String confirmationMessage) {
    this.confirmationMessage = confirmationMessage;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }
}
