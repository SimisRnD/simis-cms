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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;

class ContentImageSrcsetCommandTest {

  private static ImageVariant variant(String variantType, int width) {
    ImageVariant variant = new ImageVariant();
    variant.setVariantType(variantType);
    variant.setWidth(width);
    return variant;
  }

  private static MockedStatic<ImageVariantRepository> mockOneVariant() {
    MockedStatic<ImageVariantRepository> mocked = mockStatic(ImageVariantRepository.class);
    mocked.when(() -> ImageVariantRepository.findByImageId(anyLong()))
        .thenReturn(List.of(variant("medium", 800)));
    return mocked;
  }

  @Test
  void returnsTheSameStringWhenBlank() {
    assertEquals("", ContentImageSrcsetCommand.injectSrcset(""));
  }

  @Test
  void returnsNullWhenNull() {
    assertEquals(null, ContentImageSrcsetCommand.injectSrcset(null));
  }

  @Test
  void returnsTheSameObjectWhenThereIsNoImgTagAtAll() {
    String html = "<p>Just some text, no images here.</p>";
    assertSame(html, ContentImageSrcsetCommand.injectSrcset(html));
  }

  @Test
  void leavesATagWithNoSrcUntouched() {
    String html = "<p><img alt=\"no src here\" /></p>";
    assertEquals(html, ContentImageSrcsetCommand.injectSrcset(html));
  }

  @Test
  void leavesAnExternalSrcUntouched() {
    String html = "<p><img src=\"https://example.com/photo.jpg\" alt=\"external\" /></p>";
    assertEquals(html, ContentImageSrcsetCommand.injectSrcset(html));
  }

  @Test
  void leavesANonAssetInternalSrcUntouched() {
    String html = "<p><img src=\"/images/theme-banner.png\" /></p>";
    assertEquals(html, ContentImageSrcsetCommand.injectSrcset(html));
  }

  @Test
  void leavesAnUnquotedMaliciousSrcUntouched() {
    // Reusing the exact literal already used as a fixture elsewhere in this codebase
    // (RenderWikiMarkdownCommandTest, DeltaContentCommandTest) -- refuses to guess attribute
    // boundaries on an unquoted value rather than risk misparsing it.
    String html = "<img src=x onerror=alert(1)>";
    assertEquals(html, ContentImageSrcsetCommand.injectSrcset(html));
  }

  @Test
  void leavesATruncatedTagUntouchedAndStopsScanning() {
    String html = "<p>text <img src=\"/assets/img/1-1/x.jpg\" alt=\"unterminated";
    assertEquals(html, ContentImageSrcsetCommand.injectSrcset(html));
  }

  @Test
  void isIdempotentWhenSrcsetAlreadyPresent() {
    String html = "<img src=\"/assets/img/1-1/x.jpg\" srcset=\"/assets/img/1-1/x.jpg?variant=medium 800w\" />";
    try (MockedStatic<ImageVariantRepository> mocked = mockOneVariant()) {
      assertEquals(html, ContentImageSrcsetCommand.injectSrcset(html));
    }
  }

  @Test
  void leavesTagUntouchedWhenNoVariantsExistYet() {
    String html = "<img src=\"/assets/img/1-1/x.jpg\" />";
    try (MockedStatic<ImageVariantRepository> mocked = mockStatic(ImageVariantRepository.class)) {
      mocked.when(() -> ImageVariantRepository.findByImageId(anyLong())).thenReturn(List.of());
      assertEquals(html, ContentImageSrcsetCommand.injectSrcset(html));
    }
  }

  @Test
  void injectsSrcsetIntoAQualifyingSelfClosingTag() {
    String html = "<p><img src=\"/assets/img/20180503171549-5/photo.jpg\" alt=\"Desk\" /></p>";
    try (MockedStatic<ImageVariantRepository> mocked = mockOneVariant()) {
      String result = ContentImageSrcsetCommand.injectSrcset(html);
      // Note the double space before "srcset" and no space before the closing "/>": the input's own
      // trailing space (before "/>") survives, the insertion contributes its own leading space but
      // no trailing one -- cosmetically odd, functionally identical HTML either way.
      assertEquals("<p><img src=\"/assets/img/20180503171549-5/photo.jpg\" alt=\"Desk\" "
          + " srcset=\"/assets/img/20180503171549-5/photo.jpg?variant=medium 800w\""
          + " sizes=\"(max-width: 1200px) 100vw, 1200px\" decoding=\"async\" loading=\"lazy\"/></p>", result);
    }
  }

  @Test
  void injectsSrcsetIntoANonSelfClosingTag() {
    String html = "<img src=\"/assets/img/1-1/x.jpg\">";
    try (MockedStatic<ImageVariantRepository> mocked = mockOneVariant()) {
      String result = ContentImageSrcsetCommand.injectSrcset(html);
      assertEquals("<img src=\"/assets/img/1-1/x.jpg\""
          + " srcset=\"/assets/img/1-1/x.jpg?variant=medium 800w\""
          + " sizes=\"(max-width: 1200px) 100vw, 1200px\" decoding=\"async\" loading=\"lazy\">", result);
    }
  }

  @Test
  void usesTheAuthorSetWidthAttributeForSizesWhenPresent() {
    // TinyMCE's actual attribute order (class before src) -- confirms nothing is reordered.
    String html = "<img class=\"align-left\" src=\"/assets/img/20210219211416-3/Office%20Desk.jpg\""
        + " alt=\"Desk\" width=\"129\" height=\"97\" />";
    try (MockedStatic<ImageVariantRepository> mocked = mockOneVariant()) {
      String result = ContentImageSrcsetCommand.injectSrcset(html);
      assertTrue(result.contains("sizes=\"(max-width: 129px) 100vw, 129px\""), result);
      assertTrue(result.startsWith("<img class=\"align-left\" src=\""), "pre-existing attributes must not be reordered: " + result);
      assertTrue(result.contains("width=\"129\" height=\"97\""), "pre-existing attributes must survive untouched: " + result);
    }
  }

  @Test
  void handlesMultipleImagesLeavingUnqualifiedNeighborsIntact() {
    String html = "<p><img src=\"/assets/img/1-1/a.jpg\" /></p>"
        + "<p><img src=\"https://example.com/external.jpg\" /></p>"
        + "<p><img src=\"/assets/img/1-2/b.jpg\" /></p>";
    try (MockedStatic<ImageVariantRepository> mocked = mockOneVariant()) {
      String result = ContentImageSrcsetCommand.injectSrcset(html);
      assertTrue(result.contains("<img src=\"https://example.com/external.jpg\" />"),
          "the external-src neighbor must be untouched: " + result);
      assertTrue(result.contains("/assets/img/1-1/a.jpg?variant=medium 800w"), result);
      assertTrue(result.contains("/assets/img/1-2/b.jpg?variant=medium 800w"), result);
    }
  }

  @Test
  void composesCorrectlyWithContentCarouselWidgetsOwnAttributeExtraction() {
    // ContentCarouselWidget's "images" display mode does its own naive, non-quote-aware extraction
    // of everything between "<img " and the next ">" (see ContentCarouselWidget.java) on HTML that
    // has already been through ContentHtmlCommand.toHtml() -> injectSrcset(). This reproduces that
    // exact extraction against injected output to confirm the two compose correctly.
    String card = "<p><img src=\"/assets/img/20190826142844-128/Small%20Business.jpg\" alt=\"\" /></p>";
    try (MockedStatic<ImageVariantRepository> mocked = mockOneVariant()) {
      String injected = ContentImageSrcsetCommand.injectSrcset(card);

      int imgAttributesStartIdx = injected.indexOf("<img ") + 5;
      int imgAttributesEndIdx = injected.indexOf(">", imgAttributesStartIdx);
      String attributes = injected.substring(imgAttributesStartIdx, imgAttributesEndIdx);
      if (attributes.endsWith("/")) {
        attributes = attributes.substring(0, attributes.length() - 1);
      }

      assertTrue(attributes.contains("src=\"/assets/img/20190826142844-128/Small%20Business.jpg\""), attributes);
      assertTrue(attributes.contains("alt=\"\""), attributes);
      assertTrue(attributes.contains("srcset=\"/assets/img/20190826142844-128/Small%20Business.jpg?variant=medium 800w\""),
          attributes);
      assertTrue(attributes.contains("loading=\"lazy\""), attributes);
      assertTrue(!attributes.endsWith("/"), "the self-closing slash must have been stripped: " + attributes);
    }
  }
}
