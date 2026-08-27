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

package com.simisinc.platform.application.admin;

import com.simisinc.platform.application.email.EmailCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.mail.ImageHtmlEmail;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends emails to community managers
 *
 * @author matt rajkowski
 * @created 7/13/18 8:31 AM
 */
public class SendCommunityManagerEmailCommand {

  private static Log LOG = LogFactory.getLog(SendCommunityManagerEmailCommand.class);

  public static List<User> getUserList(String roleValue) {
    // Send to all Community Managers
    Role role = RoleRepository.findByCode(roleValue);
    if (role == null) {
      return null;
    }
    List<User> userList = new ArrayList<>(UserRepository.findAllByRole(role));

    // Administrators are always included, rather than only when the named role is empty.
    //
    // This used to be a fallback: if nobody held the role, the mail went to admins instead. The
    // effect was that a site with no community manager -- the common case -- had its administrators
    // receiving these, and the first person ever granted that role silently took the mail away from
    // them. Nothing announced the change, and nothing in the interface predicted it: the capability
    // page shows System Administrator holding community:manage, but recipients are resolved by role
    // membership, not capability, so holding the capability never put an admin on the list.
    //
    // Adding rather than replacing keeps today's behaviour permanent instead of accidental. An
    // administrator who can grant, revoke and reset every account is a reasonable recipient of
    // "somebody registered" regardless of who else is watching.
    Role adminRole = RoleRepository.findByCode("admin");
    if (adminRole != null) {
      for (User admin : UserRepository.findAllByRole(adminRole)) {
        boolean alreadyListed = false;
        for (User existing : userList) {
          if (existing.getId() == admin.getId()) {
            alreadyListed = true;
            break;
          }
        }
        if (!alreadyListed) {
          userList.add(admin);
        }
      }
    }
    return userList;
  }

  @Deprecated
  public static void sendMessage(String subject, String html, String text) {
    List<User> userList = getUserList("community-manager");
    if (userList == null || userList.isEmpty()) {
      return;
    }
    try {
      ImageHtmlEmail email = EmailCommand.prepareNewEmail();
      for (User user : userList) {
        if (user.getEmail().contains("@")) {
          LOG.debug("Sending community-manager email to: " + user.getEmail() + " " + user.getFullName());
          email.addTo(user.getEmail(), user.getFullName());
        }
      }
      email.setSubject(subject);
      email.setHtmlMsg(html);
      email.setTextMsg(text);
      email.send();
    } catch (Exception e) {
      LOG.error("sendMessage could not send mail", e);
    }
  }
}
