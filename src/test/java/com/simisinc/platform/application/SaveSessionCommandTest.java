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

import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.domain.model.maps.GeoIP;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.presentation.controller.UserSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests for SaveSessionCommand geo precision handling and anonymous flag behavior.
 * Verifies that anonymous visitors are restricted to region/country-level geo data,
 * while authenticated users receive full precision including city and coordinates (issue #367).
 *
 * @author SimIS Inc.
 * @created 7/26/2026
 */
@Disabled("JaCoCo 0.8.11 incompatible with Java 21 bytecode (major version 70) - requires JaCoCo upgrade")
class SaveSessionCommandTest {

  @Test
  void anonymousVisitorRestrictedToRegionLevel() {
    // Arrange: Create a mocked UserSession for an anonymous visitor (GUEST_ID)
    UserSession userSession = mock(UserSession.class);
    when(userSession.getSessionId()).thenReturn("test-session-123");
    when(userSession.getUserId()).thenReturn(UserSession.GUEST_ID); // Anonymous
    when(userSession.getUserAgent()).thenReturn("Mozilla/5.0 Test Browser");
    when(userSession.getIpAddress()).thenReturn("192.168.1.100");
    when(userSession.getSource()).thenReturn("web");
    when(userSession.getAppId()).thenReturn(1L);
    when(userSession.getReferer()).thenReturn(null);

    GeoIP geoIP = new GeoIP();
    geoIP.setContinent("North America");
    geoIP.setCountry("United States");
    geoIP.setCountryISOCode("US");
    geoIP.setStateISOCode("CA");
    geoIP.setState("California");
    geoIP.setCity("San Francisco");
    geoIP.setPostalCode("94103");
    geoIP.setLatitude(37.7749);
    geoIP.setLongitude(-122.4194);
    geoIP.setMetroCode(807);
    geoIP.setTimezone("America/Los_Angeles");
    when(userSession.getGeoIP()).thenReturn(geoIP);

    // Act: Capture the saved session using mockStatic
    ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
    try (var mockedRepo = mockStatic(SessionRepository.class)) {
      SaveSessionCommand.saveSession(userSession);
      mockedRepo.verify(() -> SessionRepository.add(sessionCaptor.capture()));
    }

    // Assert: Verify anonymous visitor has restricted geo data
    Session saved = sessionCaptor.getValue();
    Assertions.assertTrue(saved.getIsAnonymous(), "Anonymous visitors should have is_anonymous = true");
    Assertions.assertEquals("North America", saved.getContinent(), "Continent should be stored");
    Assertions.assertEquals("United States", saved.getCountry(), "Country should be stored");
    Assertions.assertEquals("California", saved.getState(), "State should be stored");
    Assertions.assertNull(saved.getCity(), "City should be NULL for anonymous visitors");
    Assertions.assertNull(saved.getPostalCode(), "Postal code should be NULL for anonymous visitors");
    Assertions.assertEquals(0, saved.getLatitude(), "Latitude should be NULL (0 default) for anonymous visitors");
    Assertions.assertEquals(0, saved.getLongitude(), "Longitude should be NULL (0 default) for anonymous visitors");
    Assertions.assertEquals(-1, saved.getMetroCode(), "Metro code should be NULL (-1 default) for anonymous visitors");
  }

  @Test
  void authenticatedUserReceivesFullGeoPrecision() {
    // Arrange: Create a mocked UserSession for an authenticated user
    UserSession userSession = mock(UserSession.class);
    when(userSession.getSessionId()).thenReturn("test-session-456");
    when(userSession.getUserId()).thenReturn(123L); // Authenticated user (not GUEST_ID)
    when(userSession.getUserAgent()).thenReturn("Mozilla/5.0 Test Browser");
    when(userSession.getIpAddress()).thenReturn("192.168.1.100");
    when(userSession.getSource()).thenReturn("web");
    when(userSession.getAppId()).thenReturn(1L);
    when(userSession.getReferer()).thenReturn(null);

    GeoIP geoIP = new GeoIP();
    geoIP.setContinent("North America");
    geoIP.setCountry("United States");
    geoIP.setCountryISOCode("US");
    geoIP.setStateISOCode("CA");
    geoIP.setState("California");
    geoIP.setCity("San Francisco");
    geoIP.setPostalCode("94103");
    geoIP.setLatitude(37.7749);
    geoIP.setLongitude(-122.4194);
    geoIP.setMetroCode(807);
    geoIP.setTimezone("America/Los_Angeles");
    when(userSession.getGeoIP()).thenReturn(geoIP);

    // Act: Capture the saved session using mockStatic
    ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
    try (var mockedRepo = mockStatic(SessionRepository.class)) {
      SaveSessionCommand.saveSession(userSession);
      mockedRepo.verify(() -> SessionRepository.add(sessionCaptor.capture()));
    }

    // Assert: Verify authenticated user has full geo precision
    Session saved = sessionCaptor.getValue();
    Assertions.assertFalse(saved.getIsAnonymous(), "Authenticated users should have is_anonymous = false");
    Assertions.assertEquals("North America", saved.getContinent(), "Continent should be stored");
    Assertions.assertEquals("United States", saved.getCountry(), "Country should be stored");
    Assertions.assertEquals("California", saved.getState(), "State should be stored");
    Assertions.assertEquals("San Francisco", saved.getCity(), "City should be stored for authenticated users");
    Assertions.assertEquals("94103", saved.getPostalCode(), "Postal code should be stored for authenticated users");
    Assertions.assertEquals(37.7749, saved.getLatitude(), 0.0001, "Latitude should be stored for authenticated users");
    Assertions.assertEquals(-122.4194, saved.getLongitude(), 0.0001, "Longitude should be stored for authenticated users");
    Assertions.assertEquals(807, saved.getMetroCode(), "Metro code should be stored for authenticated users");
  }

  @Test
  void anonymousVisitorWithoutGeoIP() {
    // Arrange: Create a mocked UserSession for an anonymous visitor with no GeoIP data
    UserSession userSession = mock(UserSession.class);
    when(userSession.getSessionId()).thenReturn("test-session-789");
    when(userSession.getUserId()).thenReturn(UserSession.GUEST_ID); // Anonymous
    when(userSession.getUserAgent()).thenReturn("Mozilla/5.0 Test Browser");
    when(userSession.getIpAddress()).thenReturn("192.168.1.100");
    when(userSession.getSource()).thenReturn("web");
    when(userSession.getAppId()).thenReturn(1L);
    when(userSession.getReferer()).thenReturn(null);
    when(userSession.getGeoIP()).thenReturn(null); // No GeoIP data

    // Act: Capture the saved session using mockStatic
    ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
    try (var mockedRepo = mockStatic(SessionRepository.class)) {
      SaveSessionCommand.saveSession(userSession);
      mockedRepo.verify(() -> SessionRepository.add(sessionCaptor.capture()));
    }

    // Assert: Verify session was created with is_anonymous flag set appropriately
    // Note: isAnonymous flag should only be set when GeoIP is not null
    Session saved = sessionCaptor.getValue();
    Assertions.assertFalse(saved.getIsAnonymous(), "When GeoIP is null, is_anonymous should not be set");
  }
}
