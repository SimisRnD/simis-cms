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
 * Description
 *
 * @author matt rajkowski
 * @created 3/24/19 10:51 PM
 */
public class SaveMailingListCommand {

  private static Log LOG = LogFactory.getLog(SaveMailingListCommand.class);

  public static MailingList saveMailingList(MailingList mailingListBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(mailingListBean.getName())) {
      errorMessages.append("A name is required");
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
    } else {
      LOG.debug("Saving a new record... ");
      mailingList = new MailingList();
      mailingList.setEnabled(true);
    }
    mailingList.setCreatedBy(mailingListBean.getCreatedBy());
    mailingList.setModifiedBy(mailingListBean.getCreatedBy());
    mailingList.setName(mailingListBean.getName());
    mailingList.setTitle(mailingListBean.getTitle());
    mailingList.setDescription(mailingListBean.getDescription());
    mailingList.setShowOnline(mailingListBean.getShowOnline());
    return MailingListRepository.save(mailingList);
  }

}
