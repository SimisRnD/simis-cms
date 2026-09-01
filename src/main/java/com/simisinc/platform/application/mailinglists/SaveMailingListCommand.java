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

import static com.simisinc.platform.application.mailinglists.GenerateMailingListUniqueIdCommand.generateUniqueId;

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

    // Load the record being edited up front -- both the duplicate-name check and the uniqueId
    // carry-forward below need to know what it looks like today, and there is no point validating
    // a form against a record that isn't there
    MailingList existingRecord = null;
    if (mailingListBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      existingRecord = MailingListRepository.findById(mailingListBean.getId());
      if (existingRecord == null) {
        throw new DataException("The existing record could not be found");
      }
    }

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(mailingListBean.getName())) {
      errorMessages.append("A name is required");
    } else if (trimmedLength(mailingListBean.getName()) > MAX_NAME_LENGTH) {
      // before the duplicate check, so an over-length name does not cost a findByName lookup it
      // can only fail behind
      errorMessages.append("A name can be up to " + MAX_NAME_LENGTH + " characters");
    } else if (isNameTakenByAnotherList(existingRecord, mailingListBean.getName())) {
      errorMessages.append("Another mailing list already uses that name");
    }
    // Issue #1734: the form marks Title required and mailing_lists.title is NOT NULL, but NOT NULL
    // permits '', so nothing stopped a blank title being saved through the normal admin form.
    // Title is what nearly every surface displays -- the /admin/mailing-lists row link, the
    // newsletter-send dropdown, the blog form and blog editor dropdowns, /my-email-preferences
    // checkboxes -- where a blank one renders as an empty link, an empty option and an unnamed
    // checkbox. confirm-subscription.jsp guards the display with an `empty` test; this is where
    // the blank stops being written in the first place.
    // No migration backfills a title that is already blank: this check asks for one the next time
    // that list is edited, which is a prompt to the admin rather than a value guessed for them.
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
    if (existingRecord != null) {
      mailingList = existingRecord;
      // createdBy is set once, below, only for a genuinely new record -- an edit must not
      // reassign the original creator to whoever happens to be editing it today
    } else {
      LOG.debug("Saving a new record... ");
      mailingList = new MailingList();
      mailingList.setEnabled(true);
      mailingList.setCreatedBy(mailingListBean.getCreatedBy());
    }
    mailingList.setModifiedBy(mailingListBean.getCreatedBy());
    // @note set the uniqueId before setting the name -- it is derived from the name a list is
    // created with, and generateUniqueId() reads the *previous* name to know it must not change
    mailingList.setUniqueId(generateUniqueId(existingRecord, mailingListBean));
    mailingList.setName(mailingListBean.getName());
    mailingList.setTitle(mailingListBean.getTitle());
    mailingList.setDescription(mailingListBean.getDescription());
    mailingList.setShowOnline(mailingListBean.getShowOnline());
    return MailingListRepository.save(mailingList);
  }

  /**
   * Issue #1724: mailing_lists.name has no unique constraint, and signup forms still resolve lists
   * by name, so two lists sharing one name means subscribers split between them with nothing to say
   * which one a signup reaches. Refusing the save here is where new duplicates stop.
   * <p>
   * No database constraint backs this up, deliberately. A site that already holds duplicates -- the
   * state the auto-creating signup form used to produce -- would fail the migration that added one,
   * and there is no safe automatic answer to which of two real lists should be renamed or merged.
   * So the check only guards the name a save is actually *changing*: leaving an existing duplicate's
   * name exactly as it is stays editable, or an admin could not fix the rest of the record.
   */
  private static boolean isNameTakenByAnotherList(MailingList existingRecord, String submittedName) {
    if (existingRecord != null && StringUtils.equalsIgnoreCase(
        StringUtils.trimToEmpty(existingRecord.getName()), submittedName.trim())) {
      return false;
    }
    MailingList clash = MailingListRepository.findByName(submittedName);
    return clash != null && (existingRecord == null || !clash.getId().equals(existingRecord.getId()));
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
