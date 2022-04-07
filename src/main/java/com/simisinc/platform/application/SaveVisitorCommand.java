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

import com.simisinc.platform.domain.model.Visitor;
import com.simisinc.platform.infrastructure.persistence.VisitorRepository;
import com.simisinc.platform.presentation.controller.login.UserSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.UUID;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/7/19 11:36 AM
 */
public class SaveVisitorCommand {

  private static Log LOG = LogFactory.getLog(SaveVisitorCommand.class);

  private static String generateVisitorToken() {
    return UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
  }

  public static Visitor saveVisitor(UserSession userSession) {
    // Save the record
    Visitor visitor = new Visitor();
    visitor.setToken(generateVisitorToken());
    visitor.setSessionId(userSession.getSessionId());
    VisitorRepository.add(visitor);
    userSession.setVisitorId(visitor.getId());
    return visitor;
  }

}
