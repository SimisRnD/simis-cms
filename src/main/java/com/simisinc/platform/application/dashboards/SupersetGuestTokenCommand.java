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

package com.simisinc.platform.application.dashboards;

import com.fasterxml.jackson.databind.JsonNode;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.OAuthToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Superset functions
 *
 * @author matt rajkowski
 * @created 6/29/2022 7:52 AM
 */
public class SupersetGuestTokenCommand {

  private static Log LOG = LogFactory.getLog(SupersetGuestTokenCommand.class);

  public static String retrieveGuestTokenForDashboard(User user, String dashboardId, String rlsValue) {
    OAuthToken oAuthToken = SupersetJWTCommand.retrieveAccessToken();
    if (oAuthToken == null) {
      return null;
    }
    return retrieveGuestTokenForDashboard(oAuthToken, user, dashboardId, rlsValue);
  }

  public static String retrieveGuestTokenForDashboard(OAuthToken oAuthToken, User user, String dashboardId, String rlsClause) {
    String clientId = LoadSitePropertyCommand.loadByName("bi.superset.id");
    String secret = LoadSitePropertyCommand.loadByName("bi.superset.secret");
    if (StringUtils.isAnyBlank(clientId, secret)) {
      return null;
    }
    if (oAuthToken == null || StringUtils.isBlank(oAuthToken.getAccessToken())) {
      LOG.warn("Access token is required");
      return null;
    }

    List<Object> resourcesArray = new ArrayList<>();
    Map<String, Object> resourcesParameters = new HashMap<>();
    resourcesParameters.put("id", dashboardId);
    resourcesParameters.put("type", "dashboard");
    resourcesArray.add(resourcesParameters);

    List<Object> rlsArray = new ArrayList<>();
    if (StringUtils.isNotBlank(rlsClause)) {
      Map<String, Object> rls = new HashMap<>();
//      rls.put("filterType", "Regular|Base");
//      rls.put("tables", "[public.all_entities]");
//      rls.put("roles", "[Public]");
//      rls.put("groupKey", "department = 'Finance' OR department = 'Marketing'");
      rls.put("clause", rlsClause);
//    rls.put("dataset", 0);
      rlsArray.add(rls);
    }

    Map<String, Object> userParameters = new HashMap<>();
    userParameters.put("first_name", user.getFirstName());
    userParameters.put("last_name", user.getLastName());
    userParameters.put("username", clientId);

    // Construct the final jason node
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("resources", resourcesArray);
    parameters.put("rls", rlsArray);
    parameters.put("user", userParameters);

    String jsonString = JsonCommand.createJsonNode(parameters).toString();
    LOG.debug("Sending: " + jsonString);

    JsonNode json = SupersetApiClientCommand.sendHttpPost(oAuthToken, SupersetApiClientCommand.POST_SECURITY_GUEST_TOKEN, jsonString);
    if (json == null) {
      return null;
    }
    if (!json.has("token")) {
      return null;
    }

    return json.get("token").asText();
  }
}
