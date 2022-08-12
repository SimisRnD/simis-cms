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

package com.simisinc.platform.application.socialmedia;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpGetToStringCommand;
import com.simisinc.platform.domain.model.socialmedia.InstagramMedia;

/**
 * Instagram integration
 *
 * @author matt rajkowski
 * @created 9/10/19 5:07 PM
 */
public class InstagramCommand {

  private static Log LOG = LogFactory.getLog(InstagramCommand.class);

  private static String BASE_URL = "https://graph.facebook.com/v6.0";

  public static String retrieveUserAccessToken() {
    // The user must use the Graph Explorer to create a User Access Token:
    // Choose the corresponding App, then Choose User Access Token
    // Choose "Get Token"
    return null;
  }

  public static String exchangeUserTokenForExtendedToken() {
    // curl -i -X GET "https://graph.facebook.com/v4.0/oauth/access_token?
    //    grant_type=fb_exchange_token
    //    client_id={app-id}&
    //    client_secret={app-secret}&
    //    fb_exchange_token={user-access-token}"
    return null;
  }

  public static String retrieveAppAccessToken() {
    // @todo this method requires a Facebook verified business account and approved permissions
    //    String appId = "---";
    //    String appSecret = "---";
    return null;
  }

  public static String[] retrieveFacebookPageIdAndToken(String pageName) {

    // The page's facebookId comes from using the Graph API to generate your user token,
    // to obtain the /me/account list of facebookId's and accessTokens

    // Check the app configuration
    String accessToken = LoadSitePropertyCommand.loadByName("social.instagram.accessToken");
    if (accessToken == null) {
      LOG.debug("Access token is not configured");
      return null;
    }

    String url = BASE_URL + "/me/accounts?access_token=" + accessToken;
    try {
      String value = HttpGetToStringCommand.execute(url);
      if (value == null) {
        return null;
      }
      LOG.debug("Value: " + value);

      JsonNode json = JsonLoader.fromString(value);
      if (json.has("data")) {
        JsonNode data = json.get("data");
        if (data.isArray()) {
          Iterator<JsonNode> accounts = data.elements();
          while (accounts.hasNext()) {
            JsonNode account = accounts.next();
            if (account.has("name") && pageName.equals(account.get("name").asText())) {
              String facebookPageId = account.get("id").asText();
              String accessTokenForPage = account.get("access_token").asText();
              return new String[] { facebookPageId, accessTokenForPage };
            }
          }
        }
      }
    } catch (Exception e) {
      // Anything could have gone wrong - limits exceeded, bad token, communication issue
      LOG.warn("Instagram me/accounts issues: " + e.getMessage());
    }
    return null;
  }

  public static String retrieveInstagramUserIdForFacebookPageId(String facebookId) {

    // Check the app configuration
    String accessToken = LoadSitePropertyCommand.loadByName("social.instagram.accessToken");
    if (accessToken == null) {
      LOG.debug("Access token is not configured");
      return null;
    }

    // Determine and return the igUserId
    String igUserId = null;

    String url = BASE_URL + "/" + facebookId + "?fields=instagram_business_account&access_token=" + accessToken;
    try {
      String value = HttpGetToStringCommand.execute(url);
      if (value == null) {
        return null;
      }
      LOG.debug("Value: " + value);

      int fieldNameIdx = value.indexOf("\"instagram_business_account\":");
      if (fieldNameIdx == -1) {
        LOG.warn("UserId not found");
        return null;
      }

      int igBaIdx = value.indexOf("\"id\":");
      int igIdStartIdx = value.indexOf("\"", igBaIdx + 5) + 1;
      int igIdEndIdx = value.indexOf("\"", igIdStartIdx);
      igUserId = value.substring(igIdStartIdx, igIdEndIdx);
      LOG.debug("IG UserId: " + igUserId);
      return igUserId;

    } catch (Exception e) {
      // Anything could have gone wrong - limits exceeded, bad token, communication issue
      LOG.warn("Instagram userId issues: " + e.getMessage());
    }

    return null;
  }

  public static ArrayList<String> retrieveMediaListForInstagramUserId(String igUserId) {

    // Check the app configuration
    String accessToken = LoadSitePropertyCommand.loadByName("social.instagram.accessToken");
    if (accessToken == null) {
      LOG.debug("Access token is not configured");
      return null;
    }

    String url = BASE_URL + "/" + igUserId + "/media?access_token=" + accessToken;
    try {
      String value = HttpGetToStringCommand.execute(url);
      if (value == null) {
        return null;
      }
      LOG.debug("Value: " + value);

      int dataIdx = value.indexOf("\"data\":");
      if (dataIdx == -1) {
        LOG.warn("Data not found");
        return null;
      }

      ArrayList<String> mediaList = new ArrayList<>();
      int count = 0;
      int lastIdx = dataIdx;
      int newIdx = -1;
      while (((newIdx = value.indexOf("\"id\":", lastIdx)) != -1) && count < 10) {
        ++count;
        int igIdStartIdx = value.indexOf("\"", newIdx + 5) + 1;
        int igIdEndIdx = value.indexOf("\"", igIdStartIdx);
        String mediaId = value.substring(igIdStartIdx, igIdEndIdx);
        LOG.debug("IG mediaId: " + mediaId);
        mediaList.add(mediaId);
        lastIdx = igIdEndIdx;
      }
      return mediaList;

    } catch (Exception e) {
      // Anything could have gone wrong - limits exceeded, bad token, communication issue
      LOG.warn("Instagram mediaList issues: " + e.getMessage());
    }

    return null;
  }

  public static InstagramMedia retrieveInstagramMediaForGraphId(String graphId) {

    // Check the app configuration
    String accessToken = LoadSitePropertyCommand.loadByName("social.instagram.accessToken");
    if (accessToken == null) {
      LOG.debug("Access token is not configured");
      return null;
    }

    String url = BASE_URL + "/" + graphId
        + "?fields=permalink,media_type,media_url,caption,shortcode,timestamp&access_token=" + accessToken;
    try {
      String value = HttpGetToStringCommand.execute(url);
      if (value == null) {
        return null;
      }
      LOG.debug("Value: " + value);

      JsonNode json = JsonLoader.fromString(value);
      LOG.debug("JSON Size: " + json.size());

      InstagramMedia instagramMedia = new InstagramMedia();
      if (json.has("permalink")) {
        instagramMedia.setPermalink(json.get("permalink").asText());
      }
      if (json.has("media_type")) {
        instagramMedia.setMediaType(json.get("media_type").asText());
      }
      if (json.has("media_url")) {
        instagramMedia.setMediaUrl(json.get("media_url").asText());
      }
      if (json.has("caption")) {
        instagramMedia.setCaption(json.get("caption").asText());
      }
      if (json.has("shortcode")) {
        instagramMedia.setShortCode(json.get("shortcode").asText());
      }
      if (json.has("timestamp")) {
        instagramMedia.setTimestamp(json.get("timestamp").asText());
      }
      if (json.has("id")) {
        instagramMedia.setGraphId(json.get("id").asText());
      }
      return instagramMedia;
    } catch (Exception e) {
      // Anything could have gone wrong - limits exceeded, bad token, communication issue
      LOG.warn("Instagram me/accounts issues: " + e.getMessage());
    }

    return null;
  }
}
