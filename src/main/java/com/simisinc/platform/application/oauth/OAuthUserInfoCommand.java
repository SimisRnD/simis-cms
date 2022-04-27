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

package com.simisinc.platform.application.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.OAuthToken;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.security.auth.login.AccountException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Retrieves user information and uses it to login the user
 *
 * @author matt rajkowski
 * @created 4/20/22 6:19 PM
 */
public class OAuthUserInfoCommand {

  private static Log LOG = LogFactory.getLog(OAuthUserInfoCommand.class);

  public static User createUser(OAuthToken oAuthToken) {
    if (oAuthToken == null || StringUtils.isBlank(oAuthToken.getAccessToken())) {
      LOG.warn("accessToken is required");
      return null;
    }
    // http://localhost:8100/realms/name/protocol/openid-connect/userinfo
    JsonNode json = OAuthHttpCommand.sendHttpGet("protocol/openid-connect/userinfo", oAuthToken);
    if (json == null) {
      LOG.warn("userinfo request failed");
      return null;
    }

    if (!json.has("preferred_username")) {
      LOG.warn("preferred_username is required");
      return null;
    }

    LOG.debug("Determining user information...");

    // Find the user or create a new one
    String username = json.get("preferred_username").asText();
    User user = UserRepository.findByUsername(username);
    if (user == null) {
      user = new User();
      user.setUsername(username);
    }
    user.setModifiedBy(-1);
    // Update related values
    if (json.has("given_name")) {
      JsonNode node = json.get("given_name");
      user.setFirstName(node.asText());
    }
    if (json.has("family_name")) {
      JsonNode node = json.get("family_name");
      user.setLastName(node.asText());
    }
    if (json.has("email")) {
      JsonNode node = json.get("email");
      user.setEmail(node.asText());
    }
    if (json.has("email_verified")) {
      JsonNode node = json.get("email_verified");
      if (node.asBoolean(false)) {
        user.setValidated(new Timestamp(System.currentTimeMillis()));
      }
    }

    // Check for CMS Roles
    List<Role> userRoleList = new ArrayList<>();
    String roleAttribute = LoadSitePropertyCommand.loadByName("oauth.role.attribute");
    if (StringUtils.isNotBlank(roleAttribute) && json.has(roleAttribute)) {
      for (JsonNode jsonNode : json.get(roleAttribute)) {
        String roleValue = jsonNode.asText();
        Role thisRole = RoleRepository.findByOAuthPath(roleValue);
        if (thisRole != null) {
          userRoleList.add(thisRole);
        }
      }
    }
    user.setRoleList(userRoleList);

    // Check for CMS User Groups
    // The remote text *might* be configured to send the roles/groups map
    Group defaultGroup = GroupRepository.findByName("All Users");
    List<Group> userGroupList = new ArrayList<>();
    userGroupList.add(defaultGroup);
    String groupAttribute = LoadSitePropertyCommand.loadByName("oauth.group.attribute");
    if (StringUtils.isNotBlank(groupAttribute) && json.has(groupAttribute)) {
      for (JsonNode jsonNode : json.get(groupAttribute)) {
        String groupValue = jsonNode.asText();
        Group thisGroup = GroupRepository.findByOAuthPath(groupValue);
        if (thisGroup != null) {
          userGroupList.add(thisGroup);
        }
      }
    }
    user.setGroupList(userGroupList);

    try {
      // Save everything
      user = SaveUserCommand.saveUser(user, true);
      if (user == null) {
        LOG.error("User is null");
        throw new DataException("Save user error");
      }
      // Skip email validation
      UserRepository.updateValidated(user);
      return user;
    } catch (DataException | AccountException de) {
      LOG.error("User could not be saved: " + de.getMessage(), de);
      return null;
    }
  }
}
