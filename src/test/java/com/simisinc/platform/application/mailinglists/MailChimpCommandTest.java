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

package com.simisinc.platform.application.mailinglists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.application.http.HttpPostCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.mailinglists.MailingList;

/**
 * Verifies the MailChimp Campaigns API additions ({@code isEnabled}, {@code getTagMemberCount},
 * {@code createCampaign}, {@code setCampaignContent}, {@code sendCampaign}) added for issue #600's
 * rework to send a newsletter as a real MailChimp Campaign. {@link HttpGetCommand} and
 * {@link HttpPostCommand} are statically mocked so no real network call is ever made; the
 * pre-existing Members-API sync methods (addEmailToList/unsubscribeFromList/etc.) are unchanged by
 * this rework and are out of scope here.
 *
 * @author SimIS Inc.
 */
class MailChimpCommandTest {

  // A real MailChimp API key has exactly one hyphen (key-datacenter); MailChimpCommand splits on
  // the first "-" to get the datacenter, so a fake key with extra hyphens would misrepresent that.
  private static final String API_KEY = "testapikey-us6";
  private static final String LIST_ID = "abc123";

  private static MailingList mailingList(String name) {
    MailingList mailingList = new MailingList();
    mailingList.setName(name);
    return mailingList;
  }

  private static void stubConfigured(MockedStatic<LoadSitePropertyCommand> siteProperty) {
    siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.service")).thenReturn("mailchimp");
    siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.apiKey")).thenReturn(API_KEY);
    siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.listId")).thenReturn(LIST_ID);
  }

  // --- isEnabled ---

  @Test
  void isEnabledTrueWhenServiceIsMailchimpAndCredentialsArePresent() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      stubConfigured(siteProperty);

      assertTrue(MailChimpCommand.isEnabled());
    }
  }

  @Test
  void isEnabledIsCaseInsensitiveOnTheServiceName() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.service")).thenReturn("MailChimp");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.apiKey")).thenReturn(API_KEY);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.listId")).thenReturn(LIST_ID);

      assertTrue(MailChimpCommand.isEnabled());
    }
  }

  @Test
  void isEnabledFalseWhenServiceIsNotMailchimp() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.service")).thenReturn("smtp");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.apiKey")).thenReturn(API_KEY);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.listId")).thenReturn(LIST_ID);

      assertFalse(MailChimpCommand.isEnabled());
    }
  }

  @Test
  void isEnabledFalseWhenApiKeyIsMissing() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.service")).thenReturn("mailchimp");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.apiKey")).thenReturn("");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.listId")).thenReturn(LIST_ID);

      assertFalse(MailChimpCommand.isEnabled());
    }
  }

  // --- getTagMemberCount ---

  private static final String SEGMENTS_RESPONSE = """
      {
        "segments": [
          {"id": 111, "name": "Newsletter", "member_count": 42},
          {"id": 222, "name": "Cybersecurity Bulletin", "member_count": 7}
        ]
      }
      """;

  @Test
  void getTagMemberCountReturnsTheMatchingSegmentsMemberCount() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      stubConfigured(siteProperty);
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn(SEGMENTS_RESPONSE);

      int count = MailChimpCommand.getTagMemberCount(mailingList("Cybersecurity Bulletin"));

      assertEquals(7, count);
    }
  }

  @Test
  void getTagMemberCountReturnsMinusOneWhenNoSegmentMatchesTheListName() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      stubConfigured(siteProperty);
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn(SEGMENTS_RESPONSE);

      int count = MailChimpCommand.getTagMemberCount(mailingList("Never Tagged"));

      assertEquals(-1, count);
    }
  }

  @Test
  void getTagMemberCountReturnsMinusOneWhenMailChimpIsNotConfigured() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.apiKey")).thenReturn("");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.listId")).thenReturn("");

      int count = MailChimpCommand.getTagMemberCount(mailingList("Newsletter"));

      assertEquals(-1, count);
      httpGet.verify(() -> HttpGetCommand.execute(anyString(), anyMap()), never());
    }
  }

  // --- createCampaign ---

  @Test
  void createCampaignBuildsTheExpectedRequestAndReturnsTheNewId() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      stubConfigured(siteProperty);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mail.from_name")).thenReturn("Example Site");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mail.from_address")).thenReturn("news@example.com");
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn(SEGMENTS_RESPONSE);
      httpPost.when(() -> HttpPostCommand.execute(anyString(), anyMap(), anyString()))
          .thenReturn("{\"id\": \"campaign-123\"}");

      String campaignId = MailChimpCommand.createCampaign(mailingList("Newsletter"), "Big News");

      assertEquals("campaign-123", campaignId);

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
      httpPost.verify(() -> HttpPostCommand.execute(urlCaptor.capture(), anyMap(), bodyCaptor.capture()));
      assertTrue(urlCaptor.getValue().endsWith("/campaigns"), "unexpected url: " + urlCaptor.getValue());
      assertTrue(urlCaptor.getValue().startsWith("https://us6."), "datacenter should come from the api key suffix");

      String body = bodyCaptor.getValue();
      assertTrue(body.contains("\"type\":\"regular\""));
      assertTrue(body.contains("\"list_id\":\"" + LIST_ID + "\""));
      assertTrue(body.contains("\"saved_segment_id\":111"), "should target the matching segment's id: " + body);
      assertTrue(body.contains("\"subject_line\":\"Big News\""));
      assertTrue(body.contains("\"from_name\":\"Example Site\""));
      assertTrue(body.contains("\"reply_to\":\"news@example.com\""));
    }
  }

  @Test
  void createCampaignOmitsFromNameAndReplyToWhenNotConfigured() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      stubConfigured(siteProperty);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mail.from_name")).thenReturn("");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mail.from_address")).thenReturn("");
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn(SEGMENTS_RESPONSE);
      httpPost.when(() -> HttpPostCommand.execute(anyString(), anyMap(), anyString()))
          .thenReturn("{\"id\": \"campaign-123\"}");

      MailChimpCommand.createCampaign(mailingList("Newsletter"), "Big News");

      ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
      httpPost.verify(() -> HttpPostCommand.execute(anyString(), anyMap(), bodyCaptor.capture()));
      assertFalse(bodyCaptor.getValue().contains("from_name"));
      assertFalse(bodyCaptor.getValue().contains("reply_to"));
    }
  }

  @Test
  void createCampaignReturnsNullWhenTheListHasNoMatchingSegmentYet() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      stubConfigured(siteProperty);
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn(SEGMENTS_RESPONSE);

      String campaignId = MailChimpCommand.createCampaign(mailingList("Never Tagged"), "Big News");

      assertNull(campaignId);
      httpPost.verify(() -> HttpPostCommand.execute(anyString(), anyMap(), anyString()), never());
    }
  }

  @Test
  void createCampaignReturnsNullWhenTheApiCallFails() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      stubConfigured(siteProperty);
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn(SEGMENTS_RESPONSE);
      httpPost.when(() -> HttpPostCommand.execute(anyString(), anyMap(), anyString())).thenReturn(null);

      String campaignId = MailChimpCommand.createCampaign(mailingList("Newsletter"), "Big News");

      assertNull(campaignId);
    }
  }

  // --- setCampaignContent ---

  @Test
  void setCampaignContentPutsTheHtmlAndReturnsTrueOnSuccess() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      stubConfigured(siteProperty);
      httpPost
          .when(() -> HttpPostCommand.execute(anyString(), anyMap(), anyString(), eq(HttpPostCommand.PUT)))
          .thenReturn("{\"id\": \"campaign-123\"}");

      boolean result = MailChimpCommand.setCampaignContent("campaign-123", "<p>hello</p>");

      assertTrue(result);
      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
      httpPost.verify(
          () -> HttpPostCommand.execute(urlCaptor.capture(), anyMap(), bodyCaptor.capture(), eq(HttpPostCommand.PUT)));
      assertTrue(urlCaptor.getValue().endsWith("/campaigns/campaign-123/content"));
      // JsonCommand.toJson escapes forward slashes ("/" -> "\/"), so the raw HTML tag doesn't
      // survive as a literal substring -- assert against its own escaping of the same input.
      assertTrue(bodyCaptor.getValue().contains(JsonCommand.toJson("<p>hello</p>")));
    }
  }

  @Test
  void setCampaignContentReturnsFalseOnFailure() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      stubConfigured(siteProperty);
      httpPost.when(() -> HttpPostCommand.execute(anyString(), anyMap(), anyString(), eq(HttpPostCommand.PUT)))
          .thenReturn(null);

      boolean result = MailChimpCommand.setCampaignContent("campaign-123", "<p>hello</p>");

      assertFalse(result);
    }
  }

  // --- sendCampaign ---

  @Test
  void sendCampaignReturnsTrueOnA204() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      stubConfigured(siteProperty);
      httpPost
          .when(() -> HttpPostCommand.executeForStatusCode(anyString(), anyMap(), anyString(), eq(HttpPostCommand.POST)))
          .thenReturn(204);

      boolean result = MailChimpCommand.sendCampaign("campaign-123");

      assertTrue(result);
      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      httpPost.verify(() -> HttpPostCommand.executeForStatusCode(urlCaptor.capture(), anyMap(), anyString(),
          eq(HttpPostCommand.POST)));
      assertTrue(urlCaptor.getValue().endsWith("/campaigns/campaign-123/actions/send"));
    }
  }

  @Test
  void sendCampaignReturnsFalseOnAFailureStatus() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      stubConfigured(siteProperty);
      httpPost
          .when(() -> HttpPostCommand.executeForStatusCode(anyString(), anyMap(), anyString(), eq(HttpPostCommand.POST)))
          .thenReturn(404);

      boolean result = MailChimpCommand.sendCampaign("campaign-123");

      assertFalse(result);
    }
  }

  @Test
  void sendCampaignReturnsFalseWhenNotConfigured() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpPostCommand> httpPost = mockStatic(HttpPostCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.apiKey")).thenReturn("");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.mailchimp.listId")).thenReturn("");

      boolean result = MailChimpCommand.sendCampaign("campaign-123");

      assertFalse(result);
      httpPost.verify(
          () -> HttpPostCommand.executeForStatusCode(anyString(), anyMap(), anyString(), eq(HttpPostCommand.POST)),
          never());
    }
  }
}
