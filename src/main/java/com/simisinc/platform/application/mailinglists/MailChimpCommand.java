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

package com.simisinc.platform.application.mailinglists;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Mailing List integration with Mail Chimp
 *
 * @author matt rajkowski
 * @created 9/10/19 7:30 AM
 */
public class MailChimpCommand {

  private static Log LOG = LogFactory.getLog(MailChimpCommand.class);

  private static String BASE_URL = ".api.mailchimp.com/3.0";

  public static boolean addEmailToList(Email emailAddress, MailingList mailingList) {
    // Determine if this email is already in the list
    JsonNode mailChimpMember = retrieveMailChimpMember(emailAddress);
    if (mailChimpMember == null) {
      LOG.debug("Adding email to mail chimp...");
      return addNewEmailToList(emailAddress, mailingList);
    } else {
      // This email is already on the list, so update it (as subscribed and add/update tags)
      LOG.debug("Updating email in mail chimp...");
      return subscribeExistingEmailToList(mailChimpMember, emailAddress, mailingList);
    }
  }

  public static boolean unsubscribeFromList(Email emailAddress, MailingList mailingList) {

    // Determine if this email is already in the list
    JsonNode mailChimpMember = retrieveMailChimpMember(emailAddress);
    if (mailChimpMember == null) {
      // Not a member
      return true;
    }

    if (!mailChimpMember.has("tags")) {
      // This email is on the list, so update it (as unsubscribed and remove/update tags)
      return unsubscribeFromAll(emailAddress);
    }

    // Make a list of tags and their new status
    Map<String, String> memberTags = new HashMap<>();
    JsonNode tagsNode = mailChimpMember.get("tags");
    if (tagsNode.isArray()) {
      for (JsonNode arrayElement : tagsNode) {
        // "tags":[{"id":88347,"name":"Tech Newsletter"},{"id":88339,"name":"Newsletter"}]
        if (arrayElement.has("name")) {
          String tag = arrayElement.get("name").asText();
          if (mailingList.getName().equals(tag)) {
            memberTags.put(tag, "inactive");
          } else {
            memberTags.put(tag, "active");
          }
        }
      }
    }

    // Determine the MailChimp operation
    if (memberTags.isEmpty()) {
      // No tags, so unsubscribe
      LOG.debug("Unsubscribing member in mail chimp...");
      return unsubscribeFromAll(emailAddress);
    } else {
      // Update the member using the updated tagList
      LOG.debug("Removing member tags...");
      return updateMemberTags(emailAddress, memberTags);
    }
  }

  private static String[] getApiSettings() {
    // Load the API settings
    String apiKey = LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.apiKey");
    String listId = LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.listId");
    if (StringUtils.isBlank(apiKey) || StringUtils.isBlank(listId)) {
      LOG.debug("MailChimp API is not configured");
      return null;
    }
    return new String[]{apiKey, listId};
  }

  // MailingList: Newsletter/Mailing List/Product Interest (these will be tags in MailChimp)
  // Consider: Abandoned carts, First purchases, Specific product follow-ups, Any product follow-ups, Category follow-ups
  //    Best customers

  private static JsonNode retrieveMailChimpMember(Email emailAddress) {

    try {
      // /3.0/lists/9e67587f52/members/62eeb292278cc15f5817cb78f7790b08
      String[] apiSettings = getApiSettings();
      String dc = apiSettings[0].substring(apiSettings[0].indexOf("-") + 1);
      String userMd5Hex = DigestUtils.md5Hex(emailAddress.getEmail().toLowerCase());
      String url = "https://" + dc + BASE_URL + "/lists/" + apiSettings[1] + "/members/" + userMd5Hex;

      HttpClient client = HttpClientBuilder.create().build();
      HttpGet httpGet = new HttpGet(url);
      httpGet.setHeader("Accept", "application/json");
      httpGet.setHeader("Content-type", "application/json");

      UsernamePasswordCredentials creds = new UsernamePasswordCredentials("user", apiSettings[0]);
      httpGet.addHeader(new BasicScheme().authenticate(creds, httpGet, null));

      HttpResponse response = client.execute(httpGet);

      // a 404 means the contact does not exist
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() == 404) {
        // Not in the system
        LOG.debug("HttpGet Email is not in system");
        return null;
      }

      // Return the json data
      HttpEntity entity = response.getEntity();
      String value = EntityUtils.toString(entity);
      LOG.debug("HttpGet Value: " + value);
      JsonNode json = JsonLoader.fromString(value);
      if (json.has("id")) {
        LOG.debug("HttpGet Found member");
        return json;
      }
    } catch (Exception e) {
      // Anything could have gone wrong - limits exceeded, bad token, communication issue
      LOG.warn("HttpGet MailChimp list/members issues: " + e.getMessage());
    }
    return null;
  }

  private static boolean addNewEmailToList(Email emailAddress, MailingList mailingList) {

    // Option 1: load, compare tags, then add or update
    // Option 2: try to update the email address first, then if that fails, add the address.
    // Option 3: create addresses first and only update if the creation fails.

    // POST lists/733714a229/members
    // POST request to the List Members endpoint: /3.0/lists/733714a229/members

    // Build the API JSON record
    StringBuilder sb = new StringBuilder();
    sb.append("\"email_address\": \"").append(JsonCommand.toJson(emailAddress.getEmail())).append("\"");
    sb.append(", \"status\": \"subscribed\"");
    if (StringUtils.isNotBlank(emailAddress.getIpAddress())) {
      sb.append(", \"ip_signup\": \"").append(emailAddress.getIpAddress()).append("\"");
    }
    if (emailAddress.hasGeoPoint()) {
      sb.append(", \"location\": {\"latitude\":").append(emailAddress.getLatitude()).append(", \"longitude\":").append(emailAddress.getLongitude()).append("}");
    }
    if (StringUtils.isNotBlank(emailAddress.getFirstName()) && StringUtils.isNotBlank(emailAddress.getLastName())) {
      sb.append(", \"merge_fields\": {\"FIRST_NAME\":\"").append(JsonCommand.toJson(emailAddress.getFirstName())).append("\", \"LAST_NAME\":\"").append(JsonCommand.toJson(emailAddress.getLastName())).append("\"}");
    }
    if (StringUtils.isNotBlank(emailAddress.getSource())) {
      sb.append(", \"source\":\"").append(JsonCommand.toJson(emailAddress.getSource())).append("\"");
    }
    if (mailingList != null) {
      sb.append(", \"tags\":[");
      sb.append("\"").append(JsonCommand.toJson(mailingList.getName().trim())).append("\"");
      sb.append("]");
    }

    String jsonString = "{" + sb.toString() + "}";
    LOG.debug("addNewEmailToList JSON STRING: " + jsonString);

    String[] apiSettings = getApiSettings();
    String dc = apiSettings[0].substring(apiSettings[0].indexOf("-") + 1);
    String url = "https://" + dc + BASE_URL + "/lists/" + apiSettings[1] + "/members";
    return sendMailChimpHttpPost(url, jsonString, emailAddress);
  }

  private static boolean subscribeExistingEmailToList(JsonNode mailChimpMember, Email emailAddress, MailingList mailingList) {

    String[] apiSettings = getApiSettings();
    String dc = apiSettings[0].substring(apiSettings[0].indexOf("-") + 1);
    String userMd5Hex = DigestUtils.md5Hex(emailAddress.getEmail().toLowerCase());

    // Call #1 - Update the member status, make sure "status": "subscribed"
    String statusChangeUrl = "https://" + dc + BASE_URL + "/lists/" + apiSettings[1] + "/members/" + userMd5Hex;
    sendMailChimpHttpPatch(statusChangeUrl, "{\"status\": \"subscribed\"}", emailAddress);

    // Call #2 - Update the associated tags
    Map<String, String> memberTags = new HashMap<>();
    JsonNode tagsNode = mailChimpMember.get("tags");
    if (tagsNode.isArray()) {
      for (JsonNode arrayElement : tagsNode) {
        // "tags":[{"id":88347,"name":"Tech Newsletter"},{"id":88339,"name":"Newsletter"}]
        if (arrayElement.has("name")) {
          String tag = arrayElement.get("name").asText();
          if (StringUtils.isNotBlank(tag)) {
            memberTags.put(tag, "active");
          }
        }
      }
    }
    if (mailingList != null) {
      memberTags.put(mailingList.getName().trim(), "active");
    }

    return updateMemberTags(emailAddress, memberTags);
  }

  private static boolean updateMemberTags(Email emailAddress, Map<String, String> memberTags) {
    if (memberTags.isEmpty()) {
      return false;
    }

    // Build the API JSON record
    StringBuilder sb = new StringBuilder();

    // Update the tags
    sb.append("\"tags\":[");
    int tagCount = 0;
    for (String tag : memberTags.keySet()) {
      String value = memberTags.get(tag);
      if (tagCount > 0) {
        sb.append(",");
      }
      ++tagCount;
      sb.append("{\"name\":\"").append(JsonCommand.toJson(tag)).append("\", \"status\":\"").append(value).append("\"}");
    }
    sb.append("]");
    String jsonString = "{" + sb.toString() + "}";
    LOG.debug("updateMemberTags JSON STRING: " + jsonString);

    // HTTP Patch
    String[] apiSettings = getApiSettings();
    String dc = apiSettings[0].substring(apiSettings[0].indexOf("-") + 1);
    String userMd5Hex = DigestUtils.md5Hex(emailAddress.getEmail().toLowerCase());
    String url = "https://" + dc + BASE_URL + "/lists/" + apiSettings[1] + "/members/" + userMd5Hex + "/tags";
    return sendMailChimpHttpPost(url, jsonString, emailAddress);
  }

  private static boolean unsubscribeFromAll(Email emailAddress) {
    // PATCH request to /3.0/lists/9e67587f52/members/62eeb292278cc15f5817cb78f7790b08
    // For the ID, the MD5 hash of the lowercase version of the contact’s email address is used

    // Build the API JSON record
    String jsonString = "{\"status\": \"unsubscribed\"}";
    LOG.debug("unsubscribeFromAll JSON STRING: " + jsonString);

    // HTTP Patch
    String[] apiSettings = getApiSettings();
    String dc = apiSettings[0].substring(apiSettings[0].indexOf("-") + 1);
    String userMd5Hex = DigestUtils.md5Hex(emailAddress.getEmail().toLowerCase());
    String url = "https://" + dc + BASE_URL + "/lists/" + apiSettings[1] + "/members/" + userMd5Hex;
    return sendMailChimpHttpPatch(url, jsonString, emailAddress);
  }

  private static boolean sendMailChimpHttpPost(String url, String jsonString, Email emailAddress) {
    // Send to MailChimp
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpPost httpPost = new HttpPost(url);
      httpPost.setHeader("Accept", "application/json");
      httpPost.setHeader("Content-type", "application/json");

      String[] apiSettings = getApiSettings();
      UsernamePasswordCredentials creds = new UsernamePasswordCredentials("user", apiSettings[0]);
      httpPost.addHeader(new BasicScheme().authenticate(creds, httpPost, null));

      StringEntity requestEntity = new StringEntity(
          jsonString,
          ContentType.APPLICATION_JSON);
      httpPost.setEntity(requestEntity);

      CloseableHttpResponse response = client.execute(httpPost);
      if (response == null) {
        LOG.error("HttpPost Response is empty");
        return false;
      }

      HttpEntity entity = response.getEntity();
      if (entity == null) {
        LOG.error("HttpPost Entity is null");
        return false;
      }

      // Check for content
      String remoteContent = EntityUtils.toString(entity);
      if (StringUtils.isBlank(remoteContent)) {
        LOG.error("HttpPost Remote content is empty");
        return false;
      }

      // Check for errors... HTTP/1.1 405 Method Not Allowed
      // {"type":"https://developer.mailchimp.com/documentation/mailchimp/guides/error-glossary/#405",
      // "title":"Method Not Allowed",
      // "status":405,
      // "detail":"The requested method and resource are not compatible. See the Allow header for this resource's available methods.",
      // "instance":""}
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() > 299) {
        LOG.error("HttpPost Error for URL (" + url + "): " + statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
        return false;
      }

      // {
      //  "id": "852aaa9532cb36adfb5e9fef7a4206a9",
      //  "email_address": "example@example.com",
      //  "unique_email_id": "fab20fa03d",
      //  "email_type": "html",
      //  "status": "subscribed",
      //  "status_if_new": "",
      //  ...
      if (LOG.isDebugEnabled()) {
        LOG.debug("REMOTE TEXT: " + remoteContent);
      }
      JsonNode json = JsonLoader.fromString(remoteContent);
      if (json.has("id") && json.has("status")) {
        // Update the record to mark it as 'synced'
        EmailRepository.markSynced(emailAddress);
        return true;
      }
    } catch (Exception e) {
      LOG.error("validateRequest", e);
    }
    return false;
  }

  private static boolean sendMailChimpHttpPatch(String url, String jsonString, Email emailAddress) {

    // Send to MailChimp
    try (CloseableHttpClient client = HttpClients.createDefault()) {

      HttpPatch httpPatch = new HttpPatch(url);
      httpPatch.setHeader("Accept", "application/json");
      httpPatch.setHeader("Content-type", "application/json");

      String[] apiSettings = getApiSettings();
      UsernamePasswordCredentials creds = new UsernamePasswordCredentials("user", apiSettings[0]);
      httpPatch.addHeader(new BasicScheme().authenticate(creds, httpPatch, null));

      StringEntity requestEntity = new StringEntity(
          jsonString,
          ContentType.APPLICATION_JSON);
      httpPatch.setEntity(requestEntity);

      CloseableHttpResponse response = client.execute(httpPatch);
      if (response == null) {
        LOG.error("HttpPatch Response is empty");
        return false;
      }

      HttpEntity entity = response.getEntity();
      if (entity == null) {
        LOG.error("HttpPatch Entity is null");
        return false;
      }

      // Check for content
      String remoteContent = EntityUtils.toString(entity);
      if (StringUtils.isBlank(remoteContent)) {
        LOG.error("HttpPatch Remote content is empty");
        return false;
      }

      // Check for errors... HTTP/1.1 405 Method Not Allowed
      // {"type":"https://developer.mailchimp.com/documentation/mailchimp/guides/error-glossary/#405",
      // "title":"Method Not Allowed",
      // "status":405,
      // "detail":"The requested method and resource are not compatible. See the Allow header for this resource's available methods.",
      // "instance":""}
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() > 299) {
        LOG.error("HttpPatch Error for URL (" + url + "): " + statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
        return false;
      }

      // {
      //  "id": "852aaa9532cb36adfb5e9fef7a4206a9",
      //  "email_address": "example@example.com",
      //  "unique_email_id": "fab20fa03d",
      //  "email_type": "html",
      //  "status": "subscribed",
      //  "status_if_new": "",
      //  ...
      if (LOG.isDebugEnabled()) {
        LOG.debug("HttpPatch REMOTE TEXT: " + remoteContent);
      }
      JsonNode json = JsonLoader.fromString(remoteContent);
      if (json.has("id") && json.has("status")) {
        // Update the record to mark it as 'synced'
        EmailRepository.markSynced(emailAddress);
        return true;
      }
    } catch (Exception e) {
      LOG.error("validateRequest", e);
    }
    return false;
  }
}
