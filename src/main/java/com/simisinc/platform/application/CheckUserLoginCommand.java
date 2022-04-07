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

import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/21/18 10:53 AM
 */
public class CheckUserLoginCommand {

  private static Log LOG = LogFactory.getLog(CheckUserLoginCommand.class);

  public static boolean loggedInToday(long userId) {
    if (userId == -1) {
      return false;
    }
    // @note consider a daily cache
    return (UserLoginRepository.queryTodaysLoginCount(userId) > 0);
  }
}
