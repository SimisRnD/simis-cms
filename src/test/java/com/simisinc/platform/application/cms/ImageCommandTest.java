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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;

class ImageCommandTest {

  private static ImageVariant variant(String variantType, int width) {
    ImageVariant variant = new ImageVariant();
    variant.setVariantType(variantType);
    variant.setWidth(width);
    return variant;
  }

  // -- parseImageId --------------------------------------------------------

  @Test
  void parseImageIdReturnsNullForNull() {
    assertNull(ImageCommand.parseImageId(null));
  }

  @Test
  void parseImageIdReturnsNullForBlank() {
    assertNull(ImageCommand.parseImageId("   "));
  }

  @Test
  void parseImageIdReturnsNullForExternalUrl() {
    assertNull(ImageCommand.parseImageId("https://example.com/photo.jpg"));
  }

  @Test
  void parseImageIdReturnsNullForNonAssetPath() {
    // A bundled theme asset, or any other non-/assets/img/ path (e.g. a productImageMap override)
    assertNull(ImageCommand.parseImageId("/images/logo-header.png"));
  }

  @Test
  void parseImageIdParsesAWellFormedPath() {
    assertEquals(5L, ImageCommand.parseImageId("/assets/img/20180503171549-5/logo.png"));
  }

  @Test
  void parseImageIdHandlesAWebPathContainingDashes() {
    // lastIndexOf, not indexOf -- a dash inside webPath itself must not confuse the split
    assertEquals(42L, ImageCommand.parseImageId("/assets/img/2018-05-03-171549-42/photo.jpg"));
  }

  @Test
  void parseImageIdReturnsNullWhenNoDashPresent() {
    assertNull(ImageCommand.parseImageId("/assets/img/nodashhere/photo.jpg"));
  }

  @Test
  void parseImageIdReturnsNullWhenTrailingSegmentIsNotNumeric() {
    assertNull(ImageCommand.parseImageId("/assets/img/20180503171549-abc/photo.jpg"));
  }

  @Test
  void parseImageIdReturnsNullWhenNoFilenameSegmentFollows() {
    assertNull(ImageCommand.parseImageId("/assets/img/"));
  }

  @Test
  void parseImageIdIgnoresAQuerySuffix() {
    assertEquals(5L, ImageCommand.parseImageId("/assets/img/20180503171549-5/logo.png?v=2"));
  }

  @Test
  void parseImageIdReturnsNullForNonPositiveId() {
    assertNull(ImageCommand.parseImageId("/assets/img/20180503171549-0/logo.png"));
  }

  // -- buildSrcset -----------------------------------------------------------

  @Test
  void buildSrcsetReturnsEmptyForNullVariants() {
    assertEquals("", ImageCommand.buildSrcset("/assets/img/1-1/x.jpg", null));
  }

  @Test
  void buildSrcsetReturnsEmptyForNoVariants() {
    assertEquals("", ImageCommand.buildSrcset("/assets/img/1-1/x.jpg", List.of()));
  }

  @Test
  void buildSrcsetIncludesOnlyOneVariantWhenOnlyOneExists() {
    String result = ImageCommand.buildSrcset("/assets/img/1-1/x.jpg", List.of(variant("thumbnail", 200)));
    assertEquals("/assets/img/1-1/x.jpg?variant=thumbnail 200w", result);
  }

  @Test
  void buildSrcsetJoinsMultipleVariants() {
    String result = ImageCommand.buildSrcset("/assets/img/1-1/x.jpg",
        List.of(variant("thumbnail", 200), variant("medium", 800), variant("large", 1600)));
    assertEquals(
        "/assets/img/1-1/x.jpg?variant=thumbnail 200w, /assets/img/1-1/x.jpg?variant=medium 800w, /assets/img/1-1/x.jpg?variant=large 1600w",
        result);
  }

  @Test
  void buildSrcsetSkipsAVariantWithNonPositiveWidth() {
    // Defensive: the DB column is NOT NULL, but a malformed/partial row must never produce "0w"
    String result = ImageCommand.buildSrcset("/assets/img/1-1/x.jpg",
        List.of(variant("thumbnail", 0), variant("medium", 800)));
    assertEquals("/assets/img/1-1/x.jpg?variant=medium 800w", result);
  }

  @Test
  void buildSrcsetOffersTheOriginalAsACandidate() {
    // the whole point of #1370: a 626px upload only ever gets a 200w thumbnail, because
    // GenerateImageVariantsCommand skips medium/large as upscales -- so without the original in
    // the list the browser stretches 200px across a much larger slot
    String result = ImageCommand.buildSrcset("/assets/img/1-1/x.jpg", List.of(variant("thumbnail", 200)), 626);
    assertEquals("/assets/img/1-1/x.jpg?variant=thumbnail 200w, /assets/img/1-1/x.jpg 626w", result);
  }

  @Test
  void buildSrcsetOffersTheOriginalEvenWithNoVariants() {
    String result = ImageCommand.buildSrcset("/assets/img/1-1/x.jpg", List.of(), 626);
    assertEquals("/assets/img/1-1/x.jpg 626w", result);
  }

  @Test
  void buildSrcsetOmitsTheOriginalWhenItsWidthIsUnknown() {
    // width 0 means "not recorded" -- claiming a descriptor we cannot substantiate would be worse
    // than offering variants alone, so the old behaviour is preserved exactly
    String result = ImageCommand.buildSrcset("/assets/img/1-1/x.jpg", List.of(variant("thumbnail", 200)), 0);
    assertEquals("/assets/img/1-1/x.jpg?variant=thumbnail 200w", result);
  }

  @Test
  void buildSrcsetReturnsEmptyWhenThereIsNeitherAVariantNorAWidth() {
    assertEquals("", ImageCommand.buildSrcset("/assets/img/1-1/x.jpg", null, 0));
  }

  @Test
  void buildSrcsetSkipsANullVariantEntry() {
    List<ImageVariant> variants = new java.util.ArrayList<>();
    variants.add(null);
    variants.add(variant("medium", 800));
    assertEquals("/assets/img/1-1/x.jpg?variant=medium 800w", ImageCommand.buildSrcset("/assets/img/1-1/x.jpg", variants));
  }

  // -- srcset / srcsetBatch (thin glue over the repository) ------------------

  @Test
  void srcsetReturnsEmptyWhenImageUrlIsNotParseable() {
    assertEquals("", ImageCommand.srcset("https://example.com/photo.jpg"));
  }

  @Test
  void srcsetLooksUpVariantsByParsedImageId() {
    try (MockedStatic<ImageVariantRepository> mocked = mockStatic(ImageVariantRepository.class)) {
      mocked.when(() -> ImageVariantRepository.findByImageId(anyLong()))
          .thenReturn(List.of(variant("medium", 800)));
      String result = ImageCommand.srcset("/assets/img/20180503171549-5/logo.png");
      assertEquals("/assets/img/20180503171549-5/logo.png?variant=medium 800w", result);
      mocked.verify(() -> ImageVariantRepository.findByImageId(5L));
    }
  }

  @Test
  void srcsetBatchReturnsEmptyWhenImageUrlIsNotParseable() {
    assertEquals("", ImageCommand.srcsetBatch("/images/logo-header.png", Map.of()));
  }

  @Test
  void srcsetBatchReturnsEmptyWhenMapIsNull() {
    assertEquals("", ImageCommand.srcsetBatch("/assets/img/20180503171549-5/logo.png", null));
  }

  @Test
  void srcsetBatchReturnsEmptyWhenImageIdIsNotInTheMap() {
    assertEquals("", ImageCommand.srcsetBatch("/assets/img/20180503171549-5/logo.png", Map.of(999L, List.of(variant("medium", 800)))));
  }

  @Test
  void srcsetBatchUsesTheSuppliedMapWithoutQueryingTheRepository() {
    Map<Long, List<ImageVariant>> variantsByImageId = Map.of(5L, List.of(variant("thumbnail", 200)));
    try (MockedStatic<ImageVariantRepository> mocked = mockStatic(ImageVariantRepository.class)) {
      String result = ImageCommand.srcsetBatch("/assets/img/20180503171549-5/logo.png", variantsByImageId);
      assertEquals("/assets/img/20180503171549-5/logo.png?variant=thumbnail 200w", result);
      mocked.verifyNoInteractions();
    }
  }
}
