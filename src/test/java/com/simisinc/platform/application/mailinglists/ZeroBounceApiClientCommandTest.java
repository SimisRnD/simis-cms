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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.infrastructure.persistence.mailinglists.EmailRepository;

/**
 * Verifies {@link ZeroBounceApiClientCommand#validateEmail} against realistic ZeroBounce v2
 * {@code /validate} response payloads, without ever making a real network call:
 * {@link HttpGetCommand}, {@link LoadSitePropertyCommand}, and {@link EmailRepository} are all
 * statically mocked, so the only thing exercised for real is the client's own parsing/branching
 * logic (and the real {@code JsonLoader} parse of the mocked response body).
 *
 * @author SimIS Inc.
 */
class ZeroBounceApiClientCommandTest {

  private static final String API_KEY = "test-api-key-123";

  // A realistic ZeroBounce v2 /validate success response (field shape per ZeroBounce's public
  // API docs) for an address with a reason code attached.
  private static final String INVALID_WITH_SUB_STATUS_RESPONSE = """
      {
        "address": "invalid@example.com",
        "status": "invalid",
        "sub_status": "mailbox_not_found",
        "account": null,
        "domain": null,
        "did_you_mean": null,
        "domain_age_days": "9855",
        "free_email": false,
        "mx_found": "true",
        "mx_record": "aspmx.l.google.com",
        "smtp_provider": "google",
        "firstname": "",
        "lastname": "",
        "gender": "",
        "country": null,
        "region": null,
        "city": null,
        "zipcode": null,
        "processed_at": "2026-07-28 10:00:00.000"
      }
      """;

  // Same shape, but the vendor omits sub_status entirely (as it does for a clean "valid" result).
  private static final String VALID_WITHOUT_SUB_STATUS_RESPONSE = """
      {
        "address": "valid@example.com",
        "status": "valid",
        "free_email": false,
        "mx_found": "true",
        "mx_record": "aspmx.l.google.com",
        "smtp_provider": "google",
        "processed_at": "2026-07-28 10:00:00.000"
      }
      """;

  // A defensive case ZeroBounce's documented v2 shape does not currently produce (their sub_status
  // is always a string, empty or not) but the parser must not mishandle if a vendor ever does this:
  // sub_status present as an explicit JSON null rather than omitted or an empty string.
  private static final String VALID_WITH_EXPLICIT_NULL_SUB_STATUS_RESPONSE = """
      {
        "address": "valid@example.com",
        "status": "valid",
        "sub_status": null,
        "free_email": false,
        "mx_found": "true",
        "mx_record": "aspmx.l.google.com",
        "smtp_provider": "google",
        "processed_at": "2026-07-28 10:00:00.000"
      }
      """;

  // ZeroBounce's error shape has no "status" field at all.
  private static final String VENDOR_ERROR_RESPONSE = """
      {"error": "Invalid API Key or your account ran out of credits"}
      """;

  @Test
  void validateEmailParsesSuccessResponseAndPersistsClassification() {
    Email email = persistedEmail(101L, "invalid@example.com");

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.zerobounce.apiKey"))
          .thenReturn(API_KEY);
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn(INVALID_WITH_SUB_STATUS_RESPONSE);

      JsonNode result = ZeroBounceApiClientCommand.validateEmail(email);

      assertTrue(result != null, "a successful response should be returned");
      assertEquals("invalid", result.get("status").asText());
      assertEquals("mailbox_not_found", result.get("sub_status").asText());
      assertEquals("google", result.get("smtp_provider").asText());

      // The classification was persisted with the vendor's exact status/sub_status
      emailRepository.verify(() -> EmailRepository.markValidated(email, "invalid", "mailbox_not_found"));

      // The request itself: fixed base URL, email + api_key url-encoded onto the query string
      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      httpGet.verify(() -> HttpGetCommand.execute(urlCaptor.capture(), anyMap()));
      String url = urlCaptor.getValue();
      assertTrue(url.startsWith("https://api.zerobounce.net/v2/validate?"), "unexpected base url: " + url);
      assertTrue(url.contains("email=invalid%40example.com"), "email should be url-encoded: " + url);
      assertTrue(url.contains("api_key=" + API_KEY), "api key should be present: " + url);
      assertFalse(url.contains("ip_address="), "ip_address should be omitted when blank: " + url);
    }
  }

  @Test
  void validateEmailIncludesUrlEncodedIpAddressWhenPresent() {
    Email email = persistedEmail(102L, "geo@example.com");
    email.setIpAddress("203.0.113.5");

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.zerobounce.apiKey"))
          .thenReturn(API_KEY);
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn(VALID_WITHOUT_SUB_STATUS_RESPONSE);

      ZeroBounceApiClientCommand.validateEmail(email);

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      httpGet.verify(() -> HttpGetCommand.execute(urlCaptor.capture(), anyMap()));
      assertTrue(urlCaptor.getValue().contains("ip_address=203.0.113.5"));
    }
  }

  @Test
  void validateEmailPassesNullSubStatusWhenVendorOmitsIt() {
    Email email = persistedEmail(103L, "valid@example.com");

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.zerobounce.apiKey"))
          .thenReturn(API_KEY);
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn(VALID_WITHOUT_SUB_STATUS_RESPONSE);

      JsonNode result = ZeroBounceApiClientCommand.validateEmail(email);

      assertTrue(result != null);
      assertEquals("valid", result.get("status").asText());
      emailRepository.verify(() -> EmailRepository.markValidated(email, "valid", null));
    }
  }

  @Test
  void validateEmailPassesNullSubStatusWhenVendorSendsAnExplicitJsonNull() {
    // Regression guard for a Jackson footgun: NullNode.asText() returns the literal string "null",
    // not a real null, so this must be checked explicitly rather than relying on json.has().
    Email email = persistedEmail(108L, "valid@example.com");

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.zerobounce.apiKey"))
          .thenReturn(API_KEY);
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap()))
          .thenReturn(VALID_WITH_EXPLICIT_NULL_SUB_STATUS_RESPONSE);

      JsonNode result = ZeroBounceApiClientCommand.validateEmail(email);

      assertTrue(result != null);
      emailRepository.verify(() -> EmailRepository.markValidated(email, "valid", null));
    }
  }

  @Test
  void validateEmailReturnsNullAndDoesNotPersistOnVendorErrorResponse() {
    Email email = persistedEmail(104L, "whatever@example.com");

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.zerobounce.apiKey"))
          .thenReturn(API_KEY);
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn(VENDOR_ERROR_RESPONSE);

      JsonNode result = ZeroBounceApiClientCommand.validateEmail(email);

      assertNull(result, "a vendor error response must not be treated as a classification");
      emailRepository.verifyNoInteractions();
    }
  }

  @Test
  void validateEmailReturnsNullAndDoesNotPersistWhenStatusFieldIsMissing() {
    Email email = persistedEmail(105L, "weird@example.com");

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.zerobounce.apiKey"))
          .thenReturn(API_KEY);
      // Neither "error" nor "status" - a shape ZeroBounce should never send, but the client must
      // not assume "status" is present just because "error" is absent.
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn("{\"address\":\"weird@example.com\"}");

      JsonNode result = ZeroBounceApiClientCommand.validateEmail(email);

      assertNull(result);
      emailRepository.verifyNoInteractions();
    }
  }

  @Test
  void validateEmailReturnsNullWithoutPersistingWhenHttpCallFails() {
    Email email = persistedEmail(106L, "unreachable@example.com");

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.zerobounce.apiKey"))
          .thenReturn(API_KEY);
      // HttpGetCommand.execute's own contract: null on any failure (bad status, timeout, empty body)
      httpGet.when(() -> HttpGetCommand.execute(anyString(), anyMap())).thenReturn(null);

      JsonNode result = ZeroBounceApiClientCommand.validateEmail(email);

      assertNull(result);
      emailRepository.verifyNoInteractions();
    }
  }

  @Test
  void validateEmailReturnsNullWithoutAnyHttpCallWhenApiKeyIsNotConfigured() {
    Email email = persistedEmail(107L, "someone@example.com");

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class);
        MockedStatic<EmailRepository> emailRepository = mockStatic(EmailRepository.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.zerobounce.apiKey")).thenReturn("");

      JsonNode result = ZeroBounceApiClientCommand.validateEmail(email);

      assertNull(result);
      httpGet.verify(() -> HttpGetCommand.execute(anyString(), anyMap()), never());
      emailRepository.verifyNoInteractions();
    }
  }

  @Test
  void validateEmailReturnsNullWithoutAnyHttpCallForAnUnpersistedEmail() {
    Email email = new Email();
    email.setEmail("never-saved@example.com");
    // Default id is -1 (never persisted) - there is nowhere to store the classification

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      JsonNode result = ZeroBounceApiClientCommand.validateEmail(email);

      assertNull(result);
      httpGet.verify(() -> HttpGetCommand.execute(anyString(), anyMap()), never());
      // The guard clause returns before even looking at site properties
      siteProperty.verify(() -> LoadSitePropertyCommand.loadByName(anyString()), never());
    }
  }

  private static Email persistedEmail(long id, String address) {
    Email email = new Email();
    email.setId(id);
    email.setEmail(address);
    return email;
  }
}
