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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * ContentSliderWidget.promoteFirstHeroImage: the first slide of a hero-banner is the LCP element, so
 * its image must be eager + fetchpriority=high while every later slide stays lazy -- and only for a
 * hero-banner, so an off-screen card slider is not forced to eagerly fetch its first image.
 */
class ContentSliderWidgetHeroLcpTest {

  private static final String LAZY_IMG =
      "<img src=\"/assets/view/1-1/hero.webp\" srcset=\"...\" sizes=\"100vw\" decoding=\"async\" loading=\"lazy\" />";

  @Test
  void firstHeroSlideBecomesEagerHighPriority() {
    List<String> out = ContentSliderWidget.promoteFirstHeroImage(
        Arrays.asList(LAZY_IMG, LAZY_IMG), "swiper-outer-container hero-banner");
    // first slide: eager + high priority, no lazy/async left
    assertTrue(out.get(0).contains("loading=\"eager\""), out.get(0));
    assertTrue(out.get(0).contains("fetchpriority=\"high\""), out.get(0));
    assertFalse(out.get(0).contains("loading=\"lazy\""), out.get(0));
    assertFalse(out.get(0).contains("decoding=\"async\""), out.get(0));
    // srcset/sizes preserved for responsive delivery
    assertTrue(out.get(0).contains("srcset="), out.get(0));
    // every later slide is untouched (stays lazy)
    assertEquals(LAZY_IMG, out.get(1));
  }

  @Test
  void nonHeroSliderIsUntouched() {
    List<String> in = Arrays.asList(LAZY_IMG, LAZY_IMG);
    assertEquals(in, ContentSliderWidget.promoteFirstHeroImage(in, "swiper-outer-container"));
    assertEquals(in, ContentSliderWidget.promoteFirstHeroImage(in, null));
  }

  @Test
  void emptyOrImagelessIsSafe() {
    assertTrue(ContentSliderWidget.promoteFirstHeroImage(List.of(), "hero-banner").isEmpty());
    List<String> noImg = List.of("<p>Just a caption, no image</p>");
    assertEquals(noImg, ContentSliderWidget.promoteFirstHeroImage(noImg, "hero-banner"));
  }
}
