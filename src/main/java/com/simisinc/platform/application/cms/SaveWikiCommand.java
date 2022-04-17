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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.simisinc.platform.application.cms.GenerateWikiUniqueIdCommand.generateUniqueId;


/**
 * Validates and saves wiki objects
 *
 * @author matt rajkowski
 * @created 2/10/19 11:37 AM
 */
public class SaveWikiCommand {

  public static final String allowedChars = "abcdefghijklmnopqrstuvwyxz";
  private static Log LOG = LogFactory.getLog(SaveWikiCommand.class);

  public static Wiki saveWiki(Wiki wikiBean) throws DataException {

    // Required dependencies
    if (wikiBean.getCreatedBy() == -1) {
      throw new DataException("The user saving this item was not set");
    }

    // Validate the fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(wikiBean.getName())) {
      errorMessages.append("A name is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    Wiki wiki;
    if (wikiBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      wiki = WikiRepository.findById(wikiBean.getId());
      if (wiki == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      wiki = new Wiki();
    }
    // @note set the uniqueId before setting the name
    wiki.setUniqueId(generateUniqueId(wiki, wikiBean));
    wiki.setName(wikiBean.getName());
    wiki.setDescription(wikiBean.getDescription());
    wiki.setCreatedBy(wikiBean.getCreatedBy());
    wiki.setModifiedBy(wikiBean.getModifiedBy());
    wiki.setEnabled(wikiBean.getEnabled());
    return WikiRepository.save(wiki);
  }
}
