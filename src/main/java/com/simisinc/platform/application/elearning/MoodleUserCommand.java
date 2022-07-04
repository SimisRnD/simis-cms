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

package com.simisinc.platform.application.elearning;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.simisinc.platform.domain.model.User;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.simisinc.platform.application.elearning.MoodleApiClientCommand.GET_USERS_API;

/**
 * Commands for working with Moodle Users
 *
 * @author matt rajkowski
 * @created 6/14/2022 9:34 PM
 */
public class MoodleUserCommand {

  private static LoadingCache<String, Long> userCache = Caffeine.newBuilder()
      .maximumSize(1_000_000)
      .expireAfterAccess(60, TimeUnit.MINUTES)
      .build(MoodleUserCommand::retrieveUserId);

  private static Log LOG = LogFactory.getLog(MoodleUserCommand.class);

  public static long retrieveUserId(User user) {
    Long moodleUserId = userCache.get(user.getEmail());
    if (moodleUserId == null || moodleUserId <= 0) {
      return -1;
    }
    return moodleUserId;
  }

  public static long retrieveUserId(String email) {
    // Determine the user's Moodle id
    Map<String, String> parameters = new HashMap<>();
    parameters.put("field", "email");
    parameters.put("values[]", email);
    JsonNode json = MoodleApiClientCommand.sendHttpGet(GET_USERS_API, parameters);

    // Verify that record(s) have been returned
    if (json == null || (json.isArray() && json.isEmpty()) || !json.isArray()) {
      return -1;
    }

    // Use the first record
    json = json.get(0);
    if (json.has("id")) {
      long userId = json.get("id").longValue();
      if (userId > 0) {
        LOG.debug("Found UserId: " + userId);
        return userId;
      }
    }
    return -1;
  }
}
