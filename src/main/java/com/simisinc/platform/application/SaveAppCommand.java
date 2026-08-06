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

package com.simisinc.platform.application;

import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.infrastructure.persistence.AppRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.UUID;

/**
 * Validates and saves an app object
 *
 * @author matt rajkowski
 * @created 4/30/18 8:41 AM
 */
public class SaveAppCommand {

  private static Log LOG = LogFactory.getLog(SaveAppCommand.class);

  public static App saveApp(WidgetContext context, App appBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(appBean.getName())) {
      errorMessages.append("A name is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    App app;
    boolean isUpdate = appBean.getId() > -1;
    boolean previouslyEnabled = false;
    if (isUpdate) {
      LOG.debug("Saving an existing record... ");
      app = AppRepository.findById(appBean.getId());
      if (app == null) {
        throw new DataException("The existing record could not be found");
      }
      previouslyEnabled = app.isEnabled();
      // createdBy is intentionally left untouched on edit -- app already carries the original
      // creator loaded from AppRepository.findById(). Silently reassigning it to the editing user
      // here was dead/misleading (the update() SQL never wrote created_by), and this codebase has
      // an established rule against ever doing that for real (precedent: the createdBy-overwrite
      // family of fixes around issue #989).
    } else {
      LOG.debug("Saving a new record... ");
      app = new App();
      app.setPublicKey(generateKey());
      app.setPrivateKey(generateKey());
      app.setCreatedBy(appBean.getCreatedBy());
    }
    app.setName(appBean.getName());
    app.setSummary(appBean.getSummary());
    app.setEnabled(appBean.isEnabled());

    App saved = AppRepository.save(app);

    // Record the enabled/disabled transition as its own audit event, distinct from the generic
    // create/update event the calling widget records -- only fires on an actual flip, and only for
    // an edit (a brand-new app's initial enabled state is fully captured by the create event).
    if (isUpdate && saved != null && previouslyEnabled != saved.isEnabled()) {
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION,
          saved.isEnabled() ? "app.enable" : "app.disable", AuditEventCommand.SUCCESS,
          "app", String.valueOf(saved.getId()), saved.getName(), null);
    }

    return saved;
  }

  /**
   * Non-blocking duplicate-name check: returns a warning message when another App already has this
   * name, or null when the name is unique (or blank/not yet saved). A hard unique constraint would
   * be too strict -- there can be a legitimate reason to reuse a name (e.g. staging vs. production
   * credentials for the same integration) -- so this never blocks the save, it only surfaces a note
   * so the admin can tell same-named entries apart (see apps-list.jsp's Client ID column).
   */
  public static String checkForDuplicateName(App appBean) {
    String name = StringUtils.trimToNull(appBean.getName());
    if (name == null) {
      return null;
    }
    List<App> allApps = AppRepository.findAll();
    if (allApps == null) {
      return null;
    }
    for (App existing : allApps) {
      if (existing.getId() != appBean.getId() && name.equalsIgnoreCase(StringUtils.trimToEmpty(existing.getName()))) {
        return "Note: another App is already named '" + name + "' -- consider a more specific name to avoid confusion";
      }
    }
    return null;
  }

  private static String generateKey() {
    return UUID.randomUUID().toString();
  }

}
