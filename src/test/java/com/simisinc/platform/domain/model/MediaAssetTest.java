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

package com.simisinc.platform.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Guards the id-default regression (issue #773 follow-up): a brand-new {@link MediaAsset} used to
 * default {@code id} to {@code 0}, and {@code MediaAssetRepository.save()} routes to {@code update()}
 * whenever {@code id > -1} -- true for {@code 0} -- so every new asset was silently misrouted to an
 * UPDATE against a non-existent row instead of an INSERT, and every real upload/create failed. Every
 * other domain model in this codebase that follows this same save()-routing convention (e.g. App,
 * DatabaseVersion) defaults id to -1; this test is deliberately independent of any database so it
 * always runs, unlike {@code MediaAssetRepositoryTest}'s heavier Docker-gated integration coverage of
 * the same fix.
 *
 * @author SimIS Inc.
 */
class MediaAssetTest {

  @Test
  void aNewInstanceDefaultsIdToNegativeOneNotZero() {
    MediaAsset asset = new MediaAsset();

    assertEquals(-1, asset.getId());
  }
}
