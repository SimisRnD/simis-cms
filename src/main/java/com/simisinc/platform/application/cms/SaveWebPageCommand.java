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
import com.simisinc.platform.domain.events.cms.WebPagePublishedEvent;
import com.simisinc.platform.domain.events.cms.WebPageUpdatedEvent;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;

/**
 * Validates and saves web page objects
 *
 * @author matt rajkowski
 * @created 5/4/18 6:21 PM
 */
public class SaveWebPageCommand {

  private static Log LOG = LogFactory.getLog(SaveWebPageCommand.class);

  public static WebPage saveWebPage(WebPage webPageBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(webPageBean.getLink())) {
      errorMessages.append("A link is required");
    }

    // Link requirements
    if (StringUtils.isNotBlank(webPageBean.getLink())) {
      // remove whitespace
      webPageBean.setLink(webPageBean.getLink().trim());
      // validate external links
      if (webPageBean.getLink().startsWith("http:") || webPageBean.getLink().startsWith("https:")) {
        if (UrlCommand.isUrlValid(webPageBean.getLink())) {
          errorMessages.append("The link cannot be external");
        }
      } else if (!webPageBean.getLink().startsWith("/")) {
        errorMessages.append("Link must start with a /");
      }
    }

    // Redirect requirements
    if (StringUtils.isNotBlank(webPageBean.getRedirectUrl())) {
      // remove whitespace
      webPageBean.setRedirectUrl(webPageBean.getRedirectUrl().trim());
      // validate external links
      if (webPageBean.getRedirectUrl().startsWith("http:") || webPageBean.getRedirectUrl().startsWith("https:")) {
        if (!UrlCommand.isUrlValid(webPageBean.getRedirectUrl())) {
          errorMessages.append("The redirect link formatting did not validate");
        }
      } else if (!webPageBean.getRedirectUrl().startsWith("/")) {
        errorMessages.append("Redirect must start with a /");
      }
      // Compare the link and redirect
      if (StringUtils.isNotBlank(webPageBean.getLink()) &&
          StringUtils.isNotBlank(webPageBean.getRedirectUrl()) &&
          webPageBean.getLink().equals(webPageBean.getRedirectUrl())) {
        errorMessages.append("A link cannot redirect to itself");
      }
    }

    /*
    if (webPageBean.getId() == -1 && WebPageRepository.findByName(webPageBean.getLink()) != null) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("A unique name is required");
    }
    */

    // @todo Make sure the template exists

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    WebPage webPage;
    if (webPageBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      webPage = WebPageRepository.findById(webPageBean.getId());
      if (webPage == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      webPage = new WebPage();
    }
    webPage.setCreatedBy(webPageBean.getCreatedBy());
    webPage.setModifiedBy(webPageBean.getCreatedBy());
    webPage.setLink(webPageBean.getLink());
    webPage.setRedirectUrl(webPageBean.getRedirectUrl());
    webPage.setTitle(webPageBean.getTitle());
    webPage.setKeywords(webPageBean.getKeywords());
    webPage.setDescription(webPageBean.getDescription());
    webPage.setImageUrl(webPageBean.getImageUrl());
    webPage.setComments(webPageBean.getComments());
    webPage.setPageXml(webPageBean.getPageXml());
    webPage.setSearchable(webPageBean.getSearchable());
    WebPage result = WebPageRepository.save(webPage);

    if (result != null) {
      // Check for events
      boolean isNewWebPage = (webPageBean.getId() != -1 || webPageBean.getModified() == null);
      boolean justUpdatedInTheLastDay =
          !isNewWebPage &&
              webPage.getModified() != null &&
              (new Date()).after(DateUtils.addDays(webPage.getModified(), 1));
      // Trigger events
      if (isNewWebPage) {
        WorkflowManager.triggerWorkflowForEvent(new WebPagePublishedEvent(result));
      } else if (justUpdatedInTheLastDay) {
        WorkflowManager.triggerWorkflowForEvent(new WebPageUpdatedEvent(result));
      }
    }
    return result;
  }
}
