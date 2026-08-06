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

package com.simisinc.platform.application.oauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.register.SaveUserCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.OAuthToken;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;

/**
 * The New User modal (users-list.jsp) has no checkbox for "All Guests" ("not a logged in user
 * group"), and the edit-user form mirrors that exclusion server-side (see
 * UserFormWidgetTest#postCannotGrantAllGuestsGroupEvenIfSubmitted). An OAuth login maps an IdP
 * group claim to a local Group via {@code oauth_path} and must refuse "All Guests" too -- checked
 * against the resolved group's name, since the admin-configured oauth_path value need not
 * literally read "All Guests".
 *
 * @author SimIS Inc.
 */
class OAuthUserInfoCommandGroupMappingTest {

  private static Group group(long id, String name) {
    Group g = new Group();
    g.setId(id);
    g.setName(name);
    return g;
  }

  private static OAuthToken token() {
    OAuthToken token = new OAuthToken();
    token.setAccessToken("access-token");
    return token;
  }

  @Test
  void oauthGroupClaimCannotGrantAllGuests() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode json = mapper.createObjectNode();
    json.put("preferred_username", "oauthuser");
    json.put("email", "oauthuser@example.com");
    json.put("email_verified", true);
    ArrayNode groups = json.putArray("groups");
    groups.add("guest-idp-group");

    Group allUsers = group(1L, "All Users");
    Group allGuests = group(2L, "All Guests");

    try (MockedStatic<OAuthHttpCommand> http = mockStatic(OAuthHttpCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<GroupRepository> groupRepo = mockStatic(GroupRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class);
        MockedStatic<SaveUserCommand> saveCmd = mockStatic(SaveUserCommand.class)) {

      http.when(() -> OAuthHttpCommand.sendHttpGet(anyString(), any())).thenReturn(json);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("oauth.role.attribute")).thenReturn(null);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("oauth.group.attribute")).thenReturn("groups");
      groupRepo.when(() -> GroupRepository.findByName("All Users")).thenReturn(allUsers);
      groupRepo.when(() -> GroupRepository.findByOAuthPath("guest-idp-group")).thenReturn(allGuests);
      userRepo.when(() -> UserRepository.findByEmailAddress(anyString())).thenReturn(null);
      userRepo.when(() -> UserRepository.findByUsername(anyString())).thenReturn(null);

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      User saved = new User();
      saved.setId(42L);
      saveCmd.when(() -> SaveUserCommand.saveUser(captor.capture(), eq(true))).thenReturn(saved);

      OAuthUserInfoCommand.createUser(token());

      List<Group> savedGroups = captor.getValue().getGroupList();
      assertFalse(savedGroups.stream().anyMatch(g -> "All Guests".equals(g.getName())),
          "an OAuth group claim must never be able to grant 'All Guests' membership");
      assertTrue(savedGroups.stream().anyMatch(g -> "All Users".equals(g.getName())),
          "the default 'All Users' group should still be assigned");
    }
  }
}
