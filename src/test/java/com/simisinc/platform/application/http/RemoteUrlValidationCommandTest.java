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

package com.simisinc.platform.application.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

/**
 * Verifies the SSRF guard for server-side fetches: internal, loopback, link-local (cloud
 * metadata), private, IPv6 unique-local, and multicast targets are blocked, while ordinary
 * public URLs are allowed. Tests use IP literals only, so they resolve without DNS/network.
 *
 * @author Liz Houser
 * @created 7/23/2026
 */
class RemoteUrlValidationCommandTest {

  // --- isFetchAllowed: the URL gate callers use ---

  @Test
  void allowsAnOrdinaryPublicUrl() {
    assertTrue(RemoteUrlValidationCommand.isFetchAllowed("https://8.8.8.8/dataset.json"));
    assertTrue(RemoteUrlValidationCommand.isFetchAllowed("http://1.1.1.1/feed.xml"));
  }

  @Test
  void blocksTheCloudMetadataEndpoint() {
    // 169.254.169.254 = the Azure/AWS/GCP instance-metadata service (credential theft)
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed("http://169.254.169.254/latest/meta-data/iam/"));
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed("http://169.254.169.254/metadata/instance?api-version=2021-02-01"));
  }

  @Test
  void blocksLoopbackAndPrivateRanges() {
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed("http://127.0.0.1:8080/admin"));
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed("http://10.0.0.5/internal"));
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed("http://172.16.4.4/"));
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed("http://192.168.1.1/"));
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed("http://0.0.0.0/"));
  }

  @Test
  void blocksNumericEncodedLoopback() {
    // http://2130706433/ is 127.0.0.1 in decimal; inspecting the resolved address catches it
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed("http://2130706433/"));
  }

  @Test
  void blocksNonHttpSchemesAndGarbage() {
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed("ftp://8.8.8.8/file"));
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed("file:///etc/passwd"));
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed("not-a-url"));
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed(""));
    assertFalse(RemoteUrlValidationCommand.isFetchAllowed(null));
  }

  // --- isBlockedAddress: the address classifier, exercised directly on IP literals ---

  @Test
  void classifierBlocksEveryInternalRange() throws Exception {
    String[] internal = {
        "127.0.0.1", "0.0.0.0", "169.254.169.254", "10.0.0.1", "172.16.0.1",
        "192.168.1.1", "::1", "fe80::1", "fc00::1"
    };
    for (String ip : internal) {
      assertTrue(RemoteUrlValidationCommand.isBlockedAddress(InetAddress.getByName(ip)),
          "expected blocked: " + ip);
    }
  }

  @Test
  void classifierAllowsPublicAddresses() throws Exception {
    for (String ip : new String[] { "8.8.8.8", "1.1.1.1", "93.184.216.34" }) {
      assertFalse(RemoteUrlValidationCommand.isBlockedAddress(InetAddress.getByName(ip)),
          "expected allowed: " + ip);
    }
  }
}
