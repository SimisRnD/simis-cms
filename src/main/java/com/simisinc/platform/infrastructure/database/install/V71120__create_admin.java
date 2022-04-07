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

package com.simisinc.platform.infrastructure.database.install;

import com.simisinc.platform.application.UserPasswordCommand;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UserRole;
import com.simisinc.platform.domain.model.xapi.XapiStatement;
import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserRoleRepository;
import com.simisinc.platform.infrastructure.persistence.xapi.XapiStatementRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.util.UUID;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class V71120__create_admin extends BaseJavaMigration {

  private static Log LOG = LogFactory.getLog(BaseJavaMigration.class);

  @Override
  public void migrate(Context context) throws Exception {

    // Create a random user and password visible in the log
    String tempName = "admin" + System.currentTimeMillis();
    String tempPW = UUID.randomUUID().toString();
    String hash = UserPasswordCommand.hash(tempPW);

    LOG.info("account: " + tempName);
    LOG.info("checksum: " + tempPW);
    System.out.println("account: " + tempName);
    System.out.println("checksum: " + tempPW);

    // Create a user
    User user = new User();
    user.setUniqueId("system-administrator");
    user.setFirstName("System");
    user.setLastName("Administrator");
    user.setEmail(tempName);
    user.setUsername(tempName);
    user.setPassword(hash);

    // Save the user
    user = UserRepository.add(user);

    // Set as validated
    UserRepository.updateValidated(user);

    // Set as Admin role
    Role role = RoleRepository.findByCode("admin");
    UserRole userRole = new UserRole(user, role);
    UserRoleRepository.add(userRole);

    // Create an activity
    XapiStatement statement = new XapiStatement();
    statement.setMessage("_{{ user.fullName }}_ **{{ verb }}** the site");
    statement.setMessageSnapshot("_System administrator_ **installed** the site");
    statement.setActorId(user.getId());
    statement.setVerb("installed");
    statement.setObject("site");
    XapiStatementRepository.save(statement);
  }
}
