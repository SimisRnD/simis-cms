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

package com.simisinc.platform.application.mailinglists;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates and saves a mailing list object
 *
 * @author matt rajkowski
 * @created 3/24/19 10:51 PM
 */
public class SaveMailingListCommand {

  private static Log LOG = LogFactory.getLog(SaveMailingListCommand.class);

  /**
   * Both columns are VARCHAR(200) -- see mailing_lists in NEW_10070__new_mailing_lists.sql. Until
   * they were checked here an over-length name or title travelled all the way to Postgres, which
   * refused the write: MailingListRepository logs the SQLException and returns null, and
   * MailingListFormWidget turns that null into "Your information could not be saved due to a system
   * error" -- naming neither the field nor the limit, for what is an ordinary too-long entry.
   * Checking up front makes it the form's normal field-level message instead, the way
   * SaveEmailCommand already does for emails.email VARCHAR(255).
   */
  private static final int MAX_NAME_LENGTH = 200;
  private static final int MAX_TITLE_LENGTH = 200;

  public static MailingList saveMailingList(MailingList mailingListBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(mailingListBean.getName())) {
      errorMessages.append("A name is required");
    } else if (trimmedLength(mailingListBean.getName()) > MAX_NAME_LENGTH) {
      errorMessages.append("A name can be up to " + MAX_NAME_LENGTH + " characters");
    }
    // Issue #1734: the form marks Title required and mailing_lists.title is NOT NULL, but NOT NULL
    // permits '', so nothing stopped a blank title being saved through the normal admin form.
    // Title is what nearly every surface displays -- the /admin/mailing-lists row link, the
    // newsletter-send dropdown, the blog form and blog editor dropdowns, /my-email-preferences
    // checkboxes -- where a blank one renders as an empty link, an empty option and an unnamed
    // checkbox. confirm-subscription.jsp guards the display with an `empty` test; this is where
    // the blank stops being written in the first place.
    if (StringUtils.isBlank(mailingListBean.getTitle())) {
      if (errorMessages.length() > 0) {
        errorMessages.append("; ");
      }
      errorMessages.append("A title is required");
    } else if (trimmedLength(mailingListBean.getTitle()) > MAX_TITLE_LENGTH) {
      if (errorMessages.length() > 0) {
        errorMessages.append("; ");
      }
      errorMessages.append("A title can be up to " + MAX_TITLE_LENGTH + " characters");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    MailingList mailingList;
    if (mailingListBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      mailingList = MailingListRepository.findById(mailingListBean.getId());
      if (mailingList == null) {
        throw new DataException("The existing record could not be found");
      }
      // createdBy is set once, below, only for a genuinely new record -- an edit must not
      // reassign the original creator to whoever happens to be editing it today
    } else {
      LOG.debug("Saving a new record... ");
      mailingList = new MailingList();
      mailingList.setEnabled(true);
      mailingList.setCreatedBy(mailingListBean.getCreatedBy());
    }
    mailingList.setModifiedBy(mailingListBean.getCreatedBy());
    mailingList.setName(mailingListBean.getName());
    mailingList.setTitle(mailingListBean.getTitle());
    mailingList.setDescription(mailingListBean.getDescription());
    mailingList.setShowOnline(mailingListBean.getShowOnline());
    return MailingListRepository.save(mailingList);
  }

  /**
   * The length that actually reaches the column. MailingListRepository trims name and title on the
   * way in, so a value of exactly the maximum followed by whitespace still fits -- measuring the
   * raw string would reject an entry the database would have stored happily.
   */
  private static int trimmedLength(String value) {
    return StringUtils.trimToEmpty(value).length();
  }

}
