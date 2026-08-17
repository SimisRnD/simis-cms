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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.SaveCollectionCommand;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.WebPageTemplateRule;
import com.simisinc.platform.domain.model.items.Collection;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Applies a web page template's rules (issue #1287): for each rule, creates the named
 * collection or folder if one with that uniqueId doesn't already exist, so the template's
 * widgets have something to point at as soon as the page is created.
 *
 * @author matt rajkowski
 */
public class ProvisionWebPageTemplateRulesCommand {

  private static Log LOG = LogFactory.getLog(ProvisionWebPageTemplateRulesCommand.class);

  private ProvisionWebPageTemplateRulesCommand() {
  }

  public static void provisionRules(List<WebPageTemplateRule> ruleList, long createdBy) {
    if (ruleList == null || ruleList.isEmpty()) {
      return;
    }
    for (WebPageTemplateRule rule : ruleList) {
      if (rule == null || StringUtils.isBlank(rule.getUniqueId()) || StringUtils.isBlank(rule.getName())) {
        LOG.warn("Skipping web page template rule with a missing uniqueId or name");
        continue;
      }
      try {
        if ("collection".equals(rule.getType())) {
          provisionCollection(rule, createdBy);
        } else if ("folder".equals(rule.getType())) {
          provisionFolder(rule, createdBy);
        } else {
          LOG.warn("Unknown web page template rule type '" + rule.getType() + "' for uniqueId '" + rule.getUniqueId() + "'");
        }
      } catch (DataException e) {
        // A provisioning hiccup must not block the page save that triggered it; worst case the
        // admin creates the collection/folder by hand, same as before this feature existed
        LOG.warn("Could not provision " + rule.getType() + " '" + rule.getUniqueId() + "' from a template rule: " + e.getMessage());
      }
    }
  }

  private static void provisionCollection(WebPageTemplateRule rule, long createdBy) throws DataException {
    if (LoadCollectionCommand.loadCollectionByUniqueId(rule.getUniqueId()) != null) {
      return;
    }
    Collection collectionBean = new Collection();
    collectionBean.setName(rule.getName());
    collectionBean.setCreatedBy(createdBy);
    Collection saved = SaveCollectionCommand.saveCollection(collectionBean);
    if (saved != null && !rule.getUniqueId().equals(saved.getUniqueId())) {
      LOG.warn("Provisioned collection uniqueId '" + saved.getUniqueId() + "' does not match the template rule's expected uniqueId '"
          + rule.getUniqueId() + "' -- widgets referencing '" + rule.getUniqueId() + "' will not find it");
    }
  }

  private static void provisionFolder(WebPageTemplateRule rule, long createdBy) throws DataException {
    if (LoadFolderCommand.loadFolderByUniqueId(rule.getUniqueId()) != null) {
      return;
    }
    Folder folderBean = new Folder();
    folderBean.setName(rule.getName());
    folderBean.setCreatedBy(createdBy);
    Folder saved = SaveFolderCommand.saveFolder(folderBean);
    if (saved != null && !rule.getUniqueId().equals(saved.getUniqueId())) {
      LOG.warn("Provisioned folder uniqueId '" + saved.getUniqueId() + "' does not match the template rule's expected uniqueId '"
          + rule.getUniqueId() + "' -- widgets referencing '" + rule.getUniqueId() + "' will not find it");
    }
  }
}
