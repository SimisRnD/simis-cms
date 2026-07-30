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

package com.simisinc.platform.application;

import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.CapabilityGrant;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.CapabilityGrantRepository;
import com.simisinc.platform.infrastructure.persistence.CapabilityRepository;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a user object and related information
 *
 * @author matt rajkowski
 * @created 6/19/18 9:04 PM
 */
public class LoadUserCommand {

  private static Log LOG = LogFactory.getLog(LoadUserCommand.class);

  public static User loadUser(long userId) {
    if (userId == -1) {
      return null;
    }
    User user = UserRepository.findByUserId(userId);
    if (user == null) {
      return null;
    }
    populateUserRecord(user);
    return user;
  }

  public static User loadUser(String username) {
    if (StringUtils.isBlank(username)) {
      return null;
    }
    User user = UserRepository.findByUsername(username);
    if (user == null) {
      return null;
    }
    populateUserRecord(user);
    return user;
  }

  public static User loadUserByEmailAddress(String email) {
    User user = UserRepository.findByEmailAddress(email);
    if (user == null) {
      return null;
    }
    populateUserRecord(user);
    return user;
  }

  private static void populateUserRecord(User user) {
    // Get the list of roles the user has
    List<Role> roleList = RoleRepository.findAllByUserId(user.getId());
    user.setRoleList(roleList);
    // Get the capabilities granted by those roles (issue #701), plus any active direct grants
    // (issue #702) - a capability can be held either way, so merge them into one effective list.
    user.setCapabilityList(loadEffectiveCapabilityList(user.getId()));
    // Get the list of user groups the user belongs to
    List<Group> groupList = GroupRepository.findAllByUserId(user.getId());
    user.setGroupList(groupList);
    // Retrieve the last login
    user.setLastLogin(UserLoginRepository.queryLastLogin(user.getId()));
  }

  private static List<Capability> loadEffectiveCapabilityList(long userId) {
    List<Capability> capabilityList = CapabilityRepository.findAllByUserId(userId);
    List<CapabilityGrant> activeGrantList = CapabilityGrantRepository.findActiveByUserId(userId);
    if (activeGrantList == null || activeGrantList.isEmpty()) {
      return capabilityList;
    }
    Map<String, Capability> capabilityByCode = new HashMap<>();
    if (capabilityList != null) {
      for (Capability capability : capabilityList) {
        capabilityByCode.put(capability.getCode(), capability);
      }
    }
    List<Capability> allCapabilityList = CapabilityRepository.findAll();
    Map<Long, Capability> capabilityById = new HashMap<>();
    if (allCapabilityList != null) {
      for (Capability capability : allCapabilityList) {
        capabilityById.put(capability.getId(), capability);
      }
    }
    for (CapabilityGrant capabilityGrant : activeGrantList) {
      Capability capability = capabilityById.get(capabilityGrant.getCapabilityId());
      if (capability != null) {
        capabilityByCode.putIfAbsent(capability.getCode(), capability);
      }
    }
    return new ArrayList<>(capabilityByCode.values());
  }
}
