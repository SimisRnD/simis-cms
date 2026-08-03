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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sanctionco.jmail.JMail;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberCreatedEvent;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberUpdatedEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.maps.GeoIP;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;

/**
 * Validates and saves an email address to a mailing list
 *
 * @author matt rajkowski
 * @created 3/24/19 10:02 PM
 */
public class SaveEmailCommand {

  private static Log LOG = LogFactory.getLog(SaveEmailCommand.class);

  public static Email saveEmail(Email emailBean) throws DataException {
    return saveEmail(emailBean, (String) null);
  }

  public static Email saveEmail(Email emailBean, String mailingListName) throws DataException {
    if (mailingListName == null) {
      mailingListName = "Newsletter";
    }
    MailingList mailingList = MailingListRepository.findByName(mailingListName);
    if (mailingList == null) {
      // Create it
      mailingList = new MailingList();
      mailingList.setName(mailingListName);
      mailingList.setTitle(mailingListName);
      mailingList.setEnabled(true);
      mailingList = MailingListRepository.save(mailingList);
    }
    return saveEmail(emailBean, mailingList);
  }

  public static Email saveEmail(Email emailBean, MailingList mailingList) throws DataException {
    return saveEmail(emailBean, List.of(mailingList));
  }

  /** Subscribes to every list in mailingLists (issue #598 -- a signup can choose more than one). */
  public static Email saveEmail(Email emailBean, List<MailingList> mailingLists) throws DataException {
    if (mailingLists == null || mailingLists.isEmpty()) {
      throw new DataException("Please choose at least one list to subscribe to");
    }

    // Validate the required fields
    if (!JMail.isValid(emailBean.getEmail())) {
      throw new DataException("Please check the email address and try again");
    }
    if (emailBean.getEmail().length() > 255) {
      throw new DataException("Please check the email address and try again");
    }

    // Set the GeoIP information if there's an IP
    if (StringUtils.isNotBlank(emailBean.getIpAddress())) {
      GeoIP geoIP = GeoIPCommand.getLocation(emailBean.getIpAddress());
      if (geoIP != null) {
        emailBean.setContinent(geoIP.getContinent());
        emailBean.setCountryIso(geoIP.getCountryISOCode());
        emailBean.setCountry(geoIP.getCountry());
        emailBean.setCity(geoIP.getCity());
        emailBean.setStateIso(geoIP.getStateISOCode());
        emailBean.setState(geoIP.getState());
        emailBean.setPostalCode(geoIP.getPostalCode());
        emailBean.setTimezone(geoIP.getTimezone());
        emailBean.setLatitude(geoIP.getLatitude());
        emailBean.setLongitude(geoIP.getLongitude());
        emailBean.setMetroCode(geoIP.getMetroCode());
      }
    }

    // Save the email
    Email email = EmailRepository.add(emailBean);
    if (email == null) {
      // If there's an error (duplicate unique email address) then update the record and enable it
      EmailRepository.update(emailBean);
      email = EmailRepository.findByEmailAddress(emailBean.getEmail());
    }
    if (email == null) {
      throw new DataException("Please check the email address and try again");
    }
    // Add email to each list (even if user is already on it), and send to mailing list integration
    // issue #452: resolve the acting user from the submitted bean, not the persisted/re-fetched
    // `email` record -- EmailRepository.update() deliberately never overwrites created_by (the
    // #810 fix), so on the duplicate-email/update branch above, email.getCreatedBy() would still
    // be whoever originally created that address, not who is submitting this request
    User actingUser = emailBean.getCreatedBy() > -1 ? UserRepository.findByUserId(emailBean.getCreatedBy()) : null;
    for (MailingList mailingList : mailingLists) {
      MailingListMemberRepository.AddToListResult result = MailingListMemberRepository.addEmailToList(email,
          mailingList);
      MailingListMemberCommand.triggerEmailSubscriptionProcess(email, mailingList, true);
      if (result != null && result.getMember() != null) {
        // issue #452: webhook/workflow event for the mailing-list-member lifecycle. A "not
        // created" result also covers a harmless re-add of an already-active member (the (list,
        // email) unique constraint just rejected a duplicate insert) -- only a genuine
        // reactivation of a previously-unsubscribed member is a real state change worth an event
        if (result.isCreated()) {
          WorkflowManager.triggerWorkflowForEvent(
              new MailingListMemberCreatedEvent(result.getMember(), mailingList, actingUser));
        } else if (result.wasPreviouslyUnsubscribed()) {
          WorkflowManager.triggerWorkflowForEvent(
              new MailingListMemberUpdatedEvent(result.getMember(), mailingList, actingUser, "resubscribed", false));
        }
      }
    }
    return email;
  }
}
