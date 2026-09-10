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

package com.simisinc.platform.application.login;

import java.util.List;

import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * The one home for the "highest role level held by this account" rule, and for the
 * actor-versus-target comparison built on top of it (AC-6, least privilege).
 *
 * <p>The rule itself is small: an account's level is the highest {@code level} among the roles it
 * holds, and an actor may not act on an account whose level is above their own. What makes it worth
 * a class of its own is where it sits -- it is the only thing standing between a community-manager
 * (level 90), or a {@code users:manage} capability-only grantee who holds no legacy role at all
 * (effective level 0), and a level-100 admin account. Both reach /admin/users and
 * /admin/user-details.
 *
 * <p>This class exists because the identical rule had been written six separate times -- twice in
 * UserDetailsWidget, once each in UserFormWidget, UnsuspendRequestsWidget, UnsuspendAccountCommand
 * and SaveUserCommand -- and over a single day the check was found missing from five different
 * privileged actions, one at a time, each an oversight rather than a decision. Independent copies
 * are the structural reason it kept being absent somewhere: nothing tied a new privileged action to
 * the rule. A new action should now call into here rather than grow a seventh copy.
 *
 * <p>Zero is the fail-closed answer throughout. An actor resolved to level 0 outranks nothing and
 * can grant nothing, so a session that matches no role, or a null role list, denies rather than
 * permits.
 *
 * <p>Lives in the application layer, not in presentation, so that commands can depend on it without
 * depending on widgets. It takes a {@link UserSession} -- a controller type, not a widget one --
 * which several other commands in this package already do.
 *
 * @author SimIS Inc.
 */
public class RoleLevelCommand {

  private RoleLevelCommand() {
  }

  /**
   * The highest role level in the given list, or 0 when the account holds no roles. Used for the
   * target side of the comparison, where the roles have already been loaded onto the account.
   */
  public static int highestRoleLevel(List<Role> roleList) {
    int max = 0;
    if (roleList == null) {
      return max;
    }
    for (Role role : roleList) {
      if (role.getLevel() > max) {
        max = role.getLevel();
      }
    }
    return max;
  }

  /**
   * The highest role level the acting user holds, found by matching their session role codes against
   * the authoritative role list (which is what carries the levels). Returns 0 when nothing matches,
   * which fails closed -- no role above 0 can then be granted.
   */
  public static int highestRoleLevel(UserSession userSession, List<Role> allRoles) {
    int max = 0;
    if (userSession == null || allRoles == null) {
      return max;
    }
    for (Role role : allRoles) {
      if (userSession.hasRole(role.getCode()) && role.getLevel() > max) {
        max = role.getLevel();
      }
    }
    return max;
  }

  /**
   * The highest role level held by the account with this id, loading its roles. For callers that
   * have an id rather than a populated {@link User}.
   */
  public static int highestRoleLevelForUser(long userId) {
    return highestRoleLevel(RoleRepository.findAllByUserId(userId));
  }

  /**
   * Whether the target account outranks the acting session -- the guard a privileged action places
   * on the account it is about to affect. Loads the authoritative role list to resolve the actor's
   * level from their session role codes.
   */
  public static boolean targetOutranksActor(UserSession userSession, User target) {
    List<Role> allRoles = RoleRepository.findAll();
    int actingLevel = highestRoleLevel(userSession, allRoles);
    int targetLevel = highestRoleLevel(target.getRoleList());
    return targetLevel > actingLevel;
  }
}
