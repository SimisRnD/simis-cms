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

package com.simisinc.platform.presentation.controller;

import static com.simisinc.platform.presentation.controller.RequestConstants.WIDGET_RENDER_TIMES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The per-widget breakdown of {@code totalRenderTime} (issue #2027): what it collects, and the
 * order it hands to the JSP.
 *
 * @author elizabeth houser
 */
class WidgetRenderTimeTest {

  private Map<String, Object> attributes;
  private HttpServletRequest request;

  /** A request mock that actually stores attributes, since the helper reads back what it wrote. */
  @BeforeEach
  void setUpRequest() {
    attributes = new HashMap<>();
    request = mock(HttpServletRequest.class);
    when(request.getAttribute(anyString())).thenAnswer(i -> attributes.get(i.getArgument(0, String.class)));
    doAnswer(i -> attributes.put(i.getArgument(0, String.class), i.getArgument(1))).when(request)
        .setAttribute(anyString(), any());
  }

  @SuppressWarnings("unchecked")
  private List<WidgetRenderTime> recorded() {
    return (List<WidgetRenderTime>) attributes.get(WIDGET_RENDER_TIMES);
  }

  @Test
  void nothingIsRecordedUntilAWidgetRenders() {
    assertNull(recorded(), "the attribute should not exist before the first widget is timed");
  }

  @Test
  void theFirstWidgetCreatesTheListAndLaterOnesAppendToIt() {
    WebContainerCommand.recordWidgetRenderTime(request, "logo", "header", 2);
    List<WidgetRenderTime> afterFirst = recorded();
    assertEquals(1, afterFirst.size());

    WebContainerCommand.recordWidgetRenderTime(request, "content", "/", 31);

    assertEquals(2, recorded().size());
    assertSame(afterFirst, recorded(), "the same list is reused rather than replaced each time");
  }

  @Test
  void entriesCarryTheWidgetAndTheContainerItRenderedIn() {
    WebContainerCommand.recordWidgetRenderTime(request, "contentSlider", "/", 96);

    WidgetRenderTime entry = recorded().get(0);
    assertEquals("contentSlider", entry.getWidgetName());
    assertEquals("/", entry.getContainerName(),
        "page, header and footer all contribute to one list, so an entry has to say which");
    assertEquals(96, entry.getMillis());
  }

  @Test
  void theSlowestWidgetIsFirstWhateverOrderTheyRenderedIn() {
    // Rendered fastest-first, which is the order that would hide the expensive one at the bottom.
    WebContainerCommand.recordWidgetRenderTime(request, "logo", "header", 2);
    WebContainerCommand.recordWidgetRenderTime(request, "content", "/", 31);
    WebContainerCommand.recordWidgetRenderTime(request, "contentSlider", "/", 96);
    WebContainerCommand.recordWidgetRenderTime(request, "footerLinks", "footer", 4);

    assertEquals(List.of("contentSlider", "content", "footerLinks", "logo"),
        recorded().stream().map(WidgetRenderTime::getWidgetName).toList(),
        "slowest first -- the reason to read this list is to find what to fix");
  }

  /**
   * The per-widget reset wipes ordinary request attributes so one widget's leftovers cannot bleed
   * into the next. This list has to outlive that, for the same reason the #1999 style rules do: it
   * accumulates across every widget in the page, header and footer and is printed after the walk.
   * Without it the strip showed one entry -- whichever widget happened to render last.
   */
  @Test
  void theTimingListSurvivesThePerWidgetAttributeReset() {
    assertTrue(WebContainerCommand.isPreservedAcrossWidgetReset(WIDGET_RENDER_TIMES),
        WIDGET_RENDER_TIMES + " must be preserved across the reset or the breakdown keeps only the last widget");
  }

  @Test
  void anOrdinaryWidgetAttributeIsStillWiped() {
    // Guard the blast radius of the line above: the reset must keep doing its job for everything
    // that is genuinely per-widget.
    assertFalse(WebContainerCommand.isPreservedAcrossWidgetReset("title"),
        "an ordinary per-widget attribute must still be wiped between widgets");
  }

  @Test
  void aWidgetThatRenderedNothingIsStillRecorded() {
    // A widget that spends time and produces no content is exactly what this is meant to surface,
    // so it must not be filtered out for having no output.
    WebContainerCommand.recordWidgetRenderTime(request, "expensiveButEmpty", "/", 120);

    assertEquals(1, recorded().size());
    assertEquals(120, recorded().get(0).getMillis());
  }
}
