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
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.simisinc.platform.application.cms.GenerateWikiPageUniqueIdCommand.generateUniqueId;

/**
 * Validates and saves wiki page objects
 *
 * @author matt rajkowski
 * @created 2/10/19 11:57 AM
 */
public class SaveWikiPageCommand {

  public static final String allowedChars = "abcdefghijklmnopqrstuvwyxz";
  private static Log LOG = LogFactory.getLog(SaveWikiPageCommand.class);

  public static WikiPage saveWikiPage(WikiPage wikiPageBean) throws DataException {

    // Required dependencies
    if (wikiPageBean.getCreatedBy() == -1) {
      throw new DataException("The user saving this wiki page was not set");
    }
    if (wikiPageBean.getWikiId() == -1) {
      throw new DataException("A wiki must be set");
    }

    // Validate the fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(wikiPageBean.getTitle())) {
      errorMessages.append("A title is required");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Clean the content
//    String cleanedContent = HtmlCommand.cleanContent(wikiPageBean.getBody());

    // Transform the fields and store...
    WikiPage wikiPage;
    if (wikiPageBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      wikiPage = WikiPageRepository.findById(wikiPageBean.getId());
      if (wikiPage == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      wikiPage = new WikiPage();
    }
    // @note set the uniqueId before setting the name
    wikiPage.setUniqueId(generateUniqueId(wikiPage, wikiPageBean));
    wikiPage.setWikiId(wikiPageBean.getWikiId());
    wikiPage.setTitle(wikiPageBean.getTitle());
    wikiPage.setBody(wikiPageBean.getBody());
    wikiPage.setSummary(wikiPageBean.getSummary());
    wikiPage.setCreatedBy(wikiPageBean.getCreatedBy());
    wikiPage.setModifiedBy(wikiPageBean.getModifiedBy());
    return WikiPageRepository.save(wikiPage);
  }
}
