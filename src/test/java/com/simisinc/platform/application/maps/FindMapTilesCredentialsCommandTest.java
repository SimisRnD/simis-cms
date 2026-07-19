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

package com.simisinc.platform.application.maps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.maps.MapCredentials;

/**
 * Tests the map tile service selection, including the self-hosted 'custom' tile server option
 *
 * @author elizabeth houser
 */
class FindMapTilesCredentialsCommandTest {

  @Test
  void customServiceUsesTheConfiguredTileServer() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("maps.service.tiles")).thenReturn("custom");
      property.when(() -> LoadSitePropertyCommand.loadByName("maps.custom.tileserver.url"))
          .thenReturn("https://tiles.example.com/{z}/{x}/{y}.png");
      MapCredentials credentials = FindMapTilesCredentialsCommand.getCredentials();
      assertEquals("custom", credentials.getService());
      assertEquals("https://tiles.example.com/{z}/{x}/{y}.png", credentials.getTileServerUrl());
    }
  }

  @Test
  void customServiceMatchIsCaseInsensitiveButCanonical() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("maps.service.tiles")).thenReturn("Custom");
      property.when(() -> LoadSitePropertyCommand.loadByName("maps.custom.tileserver.url"))
          .thenReturn("https://tiles.example.com/{z}/{x}/{y}.png");
      MapCredentials credentials = FindMapTilesCredentialsCommand.getCredentials();
      // The JSPs compare the service case-sensitively, so the canonical value must be returned
      assertEquals("custom", credentials.getService());
    }
  }

  @Test
  void customServiceWithoutAUrlFallsBackToOpenStreetMap() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("maps.service.tiles")).thenReturn("custom");
      property.when(() -> LoadSitePropertyCommand.loadByName("maps.custom.tileserver.url")).thenReturn("");
      MapCredentials credentials = FindMapTilesCredentialsCommand.getCredentials();
      assertEquals("openstreetmap", credentials.getService());
      assertNull(credentials.getTileServerUrl());
    }
  }

  @Test
  void openStreetMapRemainsTheDefault() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("maps.service.tiles")).thenReturn("openstreetmap");
      MapCredentials credentials = FindMapTilesCredentialsCommand.getCredentials();
      assertEquals("openstreetmap", credentials.getService());
    }
  }

  @Test
  void urlValidationAcceptsATileTemplate() {
    assertEquals("https://tiles.example.com/{z}/{x}/{y}.png",
        FindMapTilesCredentialsCommand.validatedTileServerUrl("https://tiles.example.com/{z}/{x}/{y}.png"));
    // Subdomain templates and query parameters are allowed
    assertEquals("https://{s}.tiles.example.com/{z}/{x}/{y}.png?key=abc123",
        FindMapTilesCredentialsCommand.validatedTileServerUrl("https://{s}.tiles.example.com/{z}/{x}/{y}.png?key=abc123"));
  }

  @Test
  void urlValidationRejectsUnsafeValues() {
    // The url is rendered into page javascript, so quotes and markup must never pass
    assertNull(FindMapTilesCredentialsCommand.validatedTileServerUrl("https://x/{z}/{x}/{y}'+alert(1)+'"));
    assertNull(FindMapTilesCredentialsCommand.validatedTileServerUrl("https://x/{z}/{x}/{y}</script>"));
    assertNull(FindMapTilesCredentialsCommand.validatedTileServerUrl("https://x/{z}/{x}/{y}\"onerror=\"x"));
    assertNull(FindMapTilesCredentialsCommand.validatedTileServerUrl("javascript:alert(1)//{z}{x}{y}"));
    // Not a url at all
    assertNull(FindMapTilesCredentialsCommand.validatedTileServerUrl("tiles.example.com/{z}/{x}/{y}.png"));
    // Missing the tile coordinate placeholders
    assertNull(FindMapTilesCredentialsCommand.validatedTileServerUrl("https://tiles.example.com/tiles.png"));
    assertNull(FindMapTilesCredentialsCommand.validatedTileServerUrl(null));
    assertNull(FindMapTilesCredentialsCommand.validatedTileServerUrl("   "));
  }
}
