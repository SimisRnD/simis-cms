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

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.application.http.HttpPatchCommand;
import com.simisinc.platform.application.http.HttpPostCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;

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

  /** Whether MailChimp is the configured mailing-list service with usable API credentials. */
  public static boolean isEnabled() {
    String service = LoadSitePropertyCommand.loadByName("mailing-list.service");
    return "mailchimp".equalsIgnoreCase(service) && getApiSettings() != null;
  }

  private static String[] getApiSettings() {
    // Load the API settings
    String apiKey = LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.apiKey");
    String listId = LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.listId");
    if (StringUtils.isBlank(apiKey) || StringUtils.isBlank(listId)) {
      LOG.debug("MailChimp API is not configured");
      return null;
    }
    return new String[] { apiKey, listId };
  }

  private static Map<String, String> buildAuthHeaders(String apiKey) {
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");
    headers.put("Content-type", "application/json");
    String valueToEncode = "user" + ":" + apiKey;
    headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes()));
    return headers;
  }

  /** The outcome of a {@link #testConnection()} call -- a plain, immutable result so the admin UI
   * can show a specific reason rather than a bare pass/fail (issue #523). */
  public static class ConnectionTestResult {
    private final boolean success;
    private final String message;

    private ConnectionTestResult(boolean success, String message) {
      this.success = success;
      this.message = message;
    }

    public boolean isSuccess() {
      return success;
    }

    public String getMessage() {
      return message;
    }
  }

  /** Verifies the configured API key/list id actually work by fetching the list itself, without
   * mutating any data. Distinct from {@link #isEnabled()}, which only checks that a service name and
   * non-blank credentials are present -- this makes a real API call to confirm they're valid. */
  public static ConnectionTestResult testConnection() {
    String[] apiSettings = getApiSettings();
    if (apiSettings == null) {
      return new ConnectionTestResult(false, "API Key and Audience/List Id must both be set first.");
    }

    try {
      String dc = apiSettings[0].substring(apiSettings[0].indexOf("-") + 1);
      String url = "https://" + dc + BASE_URL + "/lists/" + apiSettings[1];
      String remoteContent = HttpGetCommand.execute(url, buildAuthHeaders(apiSettings[0]));
      if (remoteContent == null) {
        return new ConnectionTestResult(false, "No response from MailChimp -- check the API Key's datacenter suffix.");
      }
      JsonNode json = JsonLoader.fromString(remoteContent);
      if (json.has("name")) {
        return new ConnectionTestResult(true, "Connected to \"" + json.get("name").asText() + "\".");
      }
      if (json.has("detail")) {
        // MailChimp error responses use {"title":..., "detail":...}; surface the detail verbatim,
        // it's already written for a human (e.g. "The requested resource could not be found.")
        return new ConnectionTestResult(false, json.get("detail").asText());
      }
      return new ConnectionTestResult(false, "Unexpected response from MailChimp.");
    } catch (Exception e) {
      LOG.warn("HttpGet MailChimp connection test issue: " + e.getMessage());
      return new ConnectionTestResult(false, "Could not reach MailChimp: " + e.getMessage());
    }
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

      Map<String, String> headers = new HashMap<>();
      headers.put("Accept", "application/json");
      headers.put("Content-type", "application/json");

      String valueToEncode = "user" + ":" + apiSettings[0];
      String credentials = "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
      headers.put("Authorization", credentials);

      String remoteContent = HttpGetCommand.execute(url, headers);

      // Return the json data
      LOG.debug("HttpGet Value: " + remoteContent);
      JsonNode json = JsonLoader.fromString(remoteContent);
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
      sb.append(", \"location\": {\"latitude\":").append(emailAddress.getLatitude()).append(", \"longitude\":")
          .append(emailAddress.getLongitude()).append("}");
    }
    if (StringUtils.isNotBlank(emailAddress.getFirstName()) && StringUtils.isNotBlank(emailAddress.getLastName())) {
      sb.append(", \"merge_fields\": {\"FIRST_NAME\":\"").append(JsonCommand.toJson(emailAddress.getFirstName()))
          .append("\", \"LAST_NAME\":\"").append(JsonCommand.toJson(emailAddress.getLastName())).append("\"}");
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

  private static boolean subscribeExistingEmailToList(JsonNode mailChimpMember, Email emailAddress,
      MailingList mailingList) {

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

    String[] apiSettings = getApiSettings();

    // Send to MailChimp
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");
    headers.put("Content-type", "application/json");

    String valueToEncode = "user" + ":" + apiSettings[0];
    String credentials = "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
    headers.put("Authorization", credentials);

    String remoteContent = HttpPostCommand.execute(url, headers, jsonString);

    // Check for content
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
    try {
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

    String[] apiSettings = getApiSettings();

    // Send to MailChimp
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");
    headers.put("Content-type", "application/json");

    String valueToEncode = "user" + ":" + apiSettings[0];
    String credentials = "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
    headers.put("Authorization", credentials);

    String remoteContent = HttpPatchCommand.execute(url, headers, jsonString);

    // Check for content
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
    try {
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

  // --- Campaigns API (issue #600 rework: send a real MailChimp Campaign, rather than just sync
  // subscribers) -- distinct from the Members-API helpers above, none of which apply here: a
  // campaign send has no single associated Email record to mark synced, and its own success
  // signal for the "send" action is a 2xx with an empty body, not a JSON object with an id/status. ---

  /**
   * Finds the MailChimp tag for this mailing list, as a segment (MailChimp represents tags as
   * static segments internally). Returns null if MailChimp isn't configured, or if nobody has
   * ever been tagged with this list's name yet (the tag/segment doesn't exist until then).
   */
  private static JsonNode findTagSegment(String tagName) {
    String[] apiSettings = getApiSettings();
    if (apiSettings == null) {
      return null;
    }
    try {
      String dc = apiSettings[0].substring(apiSettings[0].indexOf("-") + 1);
      String url = "https://" + dc + BASE_URL + "/lists/" + apiSettings[1] + "/segments?type=static&count=1000";
      String remoteContent = HttpGetCommand.execute(url, buildAuthHeaders(apiSettings[0]));
      if (StringUtils.isBlank(remoteContent)) {
        return null;
      }
      JsonNode json = JsonLoader.fromString(remoteContent);
      if (!json.has("segments")) {
        return null;
      }
      for (JsonNode segment : json.get("segments")) {
        if (segment.has("name") && tagName.equals(segment.get("name").asText())) {
          return segment;
        }
      }
    } catch (Exception e) {
      LOG.warn("HttpGet MailChimp list/segments issues: " + e.getMessage());
    }
    return null;
  }

  /**
   * The number of MailChimp members currently tagged with this mailing list's name, or -1 if
   * MailChimp isn't configured or the tag doesn't exist yet (nobody has ever been added to it).
   */
  public static int getTagMemberCount(MailingList mailingList) {
    JsonNode segment = findTagSegment(mailingList.getName().trim());
    if (segment == null || !segment.has("member_count")) {
      return -1;
    }
    return segment.get("member_count").asInt();
  }

  /**
   * Creates a draft MailChimp Campaign targeting the members tagged with this mailing list's
   * name. Returns the new campaign's id, or null if MailChimp isn't configured, the list has no
   * matching tag/segment yet, or the call failed.
   */
  public static String createCampaign(MailingList mailingList, String subjectLine) {
    String[] apiSettings = getApiSettings();
    if (apiSettings == null) {
      return null;
    }
    JsonNode segment = findTagSegment(mailingList.getName().trim());
    if (segment == null || !segment.has("id")) {
      LOG.warn("No MailChimp tag/segment found for mailing list: " + mailingList.getName());
      return null;
    }

    String fromName = LoadSitePropertyCommand.loadByName("mail.from_name");
    String replyTo = LoadSitePropertyCommand.loadByName("mail.from_address");

    StringBuilder sb = new StringBuilder();
    sb.append("\"type\":\"regular\"");
    sb.append(", \"recipients\":{\"list_id\":\"").append(apiSettings[1]).append("\"")
        .append(", \"segment_opts\":{\"saved_segment_id\":").append(segment.get("id").asLong()).append("}}");
    sb.append(", \"settings\":{\"subject_line\":\"").append(JsonCommand.toJson(subjectLine)).append("\"");
    if (StringUtils.isNotBlank(fromName)) {
      sb.append(", \"from_name\":\"").append(JsonCommand.toJson(fromName)).append("\"");
    }
    if (StringUtils.isNotBlank(replyTo)) {
      sb.append(", \"reply_to\":\"").append(JsonCommand.toJson(replyTo)).append("\"");
    }
    sb.append("}");
    String jsonString = "{" + sb + "}";
    LOG.debug("createCampaign JSON STRING: " + jsonString);

    String dc = apiSettings[0].substring(apiSettings[0].indexOf("-") + 1);
    String url = "https://" + dc + BASE_URL + "/campaigns";
    String remoteContent = HttpPostCommand.execute(url, buildAuthHeaders(apiSettings[0]), jsonString);
    if (StringUtils.isBlank(remoteContent)) {
      return null;
    }
    try {
      JsonNode json = JsonLoader.fromString(remoteContent);
      if (json.has("id")) {
        return json.get("id").asText();
      }
    } catch (Exception e) {
      LOG.error("createCampaign", e);
    }
    return null;
  }

  /** Sets a campaign's HTML content. Must be called before sendCampaign(). */
  public static boolean setCampaignContent(String campaignId, String html) {
    String[] apiSettings = getApiSettings();
    if (apiSettings == null) {
      return false;
    }
    String jsonString = "{\"html\":\"" + JsonCommand.toJson(html) + "\"}";
    String dc = apiSettings[0].substring(apiSettings[0].indexOf("-") + 1);
    String url = "https://" + dc + BASE_URL + "/campaigns/" + campaignId + "/content";
    String remoteContent = HttpPostCommand.execute(url, buildAuthHeaders(apiSettings[0]), jsonString, HttpPostCommand.PUT);
    return StringUtils.isNotBlank(remoteContent);
  }

  /**
   * Sends a campaign immediately. MailChimp fans out delivery to every matched recipient on its
   * own infrastructure -- there is no per-recipient work left for this application to do. The
   * send action responds 204 No Content on success, so success is read from the status code
   * (executeForStatusCode), not the (always-empty) response body.
   */
  public static boolean sendCampaign(String campaignId) {
    String[] apiSettings = getApiSettings();
    if (apiSettings == null) {
      return false;
    }
    String dc = apiSettings[0].substring(apiSettings[0].indexOf("-") + 1);
    String url = "https://" + dc + BASE_URL + "/campaigns/" + campaignId + "/actions/send";
    int status = HttpPostCommand.executeForStatusCode(url, buildAuthHeaders(apiSettings[0]), "", HttpPostCommand.POST);
    return status >= 200 && status < 300;
  }
}
