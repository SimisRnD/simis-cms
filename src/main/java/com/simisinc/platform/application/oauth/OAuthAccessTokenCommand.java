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

 import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.simisinc.platform.domain.model.login.OAuthToken;
 
 /**
  * Configures and verifies access tokens
  *
  * @author matt rajkowski
  * @created 4/20/22 6:19 PM
  */
 public class OAuthAccessTokenCommand {
 
   private static Log LOG = LogFactory.getLog(OAuthAccessTokenCommand.class);
 
   public static OAuthToken retrieveAccessToken(String state, String code) {
     if (StringUtils.isAnyBlank(state, code)) {
       LOG.warn("State and code parameters are required");
       return null;
     }
     String resource = OAuthAuthorizationCommand.resourceIfStateIsValid(state);
     if (resource == null) {
       LOG.warn("State not found in cache, may have expired");
       return null;
     }
     LOG.debug("Found resource: " + resource);
 
     Map<String, String> params = new HashMap<>();
     params.put("grant_type", "authorization_code");
     params.put("code", code);
 
     JsonNode json = OAuthHttpCommand.sendHttpPost(OAuthConfigurationCommand.retrieveTokenEndpoint(), params);
     if (json == null) {
       return null;
     }
 
     // Populate the token and the requested resource
     OAuthToken oAuthToken = new OAuthToken();
     oAuthToken.setResource(resource);
     return populateTokenFromJson(oAuthToken, json);
   }
 
   public static OAuthToken refreshAccessToken(OAuthToken oAuthToken) {
     Map<String, String> params = new HashMap<>();
     params.put("grant_type", "refresh_token");
     params.put("refresh_token", oAuthToken.getRefreshToken());
 
     JsonNode json = OAuthHttpCommand.sendHttpPost(OAuthConfigurationCommand.retrieveTokenEndpoint(), params);
     if (json == null) {
       return null;
     }
 
     return populateTokenFromJson(oAuthToken, json);
   }
 
   public static OAuthToken populateTokenFromJson(OAuthToken oAuthToken, JsonNode json) {
     oAuthToken.setProvider("oauth");
     oAuthToken.setTokenType("bearer");
     if (json.has("access_token")) {
       String accessToken = json.get("access_token").asText();
       LOG.debug("ACCESS TOKEN: " + accessToken);
       // Check for access token payload
       if (accessToken.contains(".")) {
         
         // JWT
         String[] chunks = accessToken.split("\\.");
         Base64.Decoder decoder = Base64.getUrlDecoder();
 
         // @todo
         // String header = new String(decoder.decode(chunks[0]));
         // String payload = new String(decoder.decode(chunks[1]));
         
         // String tokenWithoutSignature = chunks[0].trim() + "." + chunks[1].trim();
         // String signature = chunks[2];
 
         // accessToken = tokenWithoutSignature;
       }
       oAuthToken.setAccessToken(accessToken);
       oAuthToken.setExpires(null);
       if (json.has("expires_in")) {
         oAuthToken.setExpiresIn(json.get("expires_in").asInt());
       } else {
         // extend any existing expiration
         oAuthToken.setExpiresIn(oAuthToken.getExpiresIn());
       }
       // If you do not get back a new refresh token, then it means your existing refresh token will continue to work when the new access token expires
       if (json.has("refresh_token")) {
         String refreshToken = json.get("refresh_token").asText();
         // Check for refresh token payload
         if (refreshToken.contains(".")) {
           // JWT
           String[] chunks = refreshToken.split("\\.");
           // String tokenWithoutSignature = chunks[0].trim() + "." + chunks[1].trim();
           // refreshToken = tokenWithoutSignature;
         }
         oAuthToken.setRefreshToken(refreshToken);
       }
       oAuthToken.setRefreshExpires(null);
       if (json.has("refresh_expires_in")) {
         oAuthToken.setRefreshExpiresIn(json.get("refresh_expires_in").asInt());
       } else {
         // extend any existing expiration
         oAuthToken.setRefreshExpiresIn(oAuthToken.getRefreshExpiresIn());
       }
       if (json.has("scope")) {
         oAuthToken.setScope(json.get("scope").asText());
       }
       if (json.has("id_token")) {
         
       }
     }
     return oAuthToken;
   }
 }
 