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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.cms.Image;

/**
 * Which rungs of the variant ladder an original gets. Kept apart from
 * {@code GenerateImageVariantsCommandTest}, which needs Docker and ImageMagick, because this rule
 * needs neither.
 *
 * @author SimIS Inc.
 */
class GenerateImageVariantsNeedsVariantTest {

  private static Image image(int width, int height, String fileType) {
    Image image = new Image();
    image.setWidth(width);
    image.setHeight(height);
    image.setFileType(fileType);
    return image;
  }

  @Test
  void anOriginalLargerThanTheTargetGetsTheVariant() {
    assertTrue(GenerateImageVariantsCommand.needsVariant(image(3700, 2000, "image/png"), 1600));
  }

  @Test
  void anOriginalSmallerThanTheTargetDoesNot() {
    assertFalse(GenerateImageVariantsCommand.needsVariant(image(1200, 800, "image/jpeg"), 1600));
  }

  @Test
  void anUploadDownscaledToExactlyTheLargeSizeStillGetsAWebpLarge() {
    // DownscaleImageCommand scales wide uploads to exactly 1600px. Skipping the large rung for them
    // would make the JPEG original the srcset's 1600w candidate instead of a smaller WebP.
    assertTrue(GenerateImageVariantsCommand.needsVariant(
        image(DownscaleImageCommand.MAX_WIDTH, 865, "image/jpeg"), DownscaleImageCommand.MAX_WIDTH));
  }

  @Test
  void aWebpOriginalAtExactlyTheTargetSizeIsNotDuplicated() {
    assertFalse(GenerateImageVariantsCommand.needsVariant(image(1600, 900, "image/webp"), 1600));
  }

  @Test
  void aGifAtExactlyTheTargetSizeIsNotDuplicated() {
    // GIF variants stay GIF, so a same-size one would be a byte-for-byte copy
    assertFalse(GenerateImageVariantsCommand.needsVariant(image(800, 600, "image/gif"), 800));
  }

  @Test
  void theLongerSideDecides() {
    assertTrue(GenerateImageVariantsCommand.needsVariant(image(1000, 100, "image/png"), 200));
    assertTrue(GenerateImageVariantsCommand.needsVariant(image(900, 1600, "image/jpeg"), 1600));
  }
}
