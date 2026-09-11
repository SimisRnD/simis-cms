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

package com.simisinc.platform.presentation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.application.cms.StyleRuleCommand;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The head rules must cover every style a page renders -- layout sections, columns and widgets in the
 * page, header and footer, plus what widgets registered -- or an element keeps its hook and loses its
 * style (issue #1999).
 *
 * @author elizabeth houser
 * @created 9/10/26
 */
class PageStyleRulesTest {

  private static HttpServletRequest request() {
    Map<String, Object> attributes = new HashMap<>();
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getAttribute(anyString())).thenAnswer(i -> attributes.get(i.getArgument(0, String.class)));
    doAnswer(i -> attributes.put(i.getArgument(0), i.getArgument(1))).when(request).setAttribute(anyString(), any());
    return request;
  }

  private static SectionRenderInfo section(String sectionStyle, String columnStyle, String widgetStyle) {
    WidgetRenderInfo widget = mock(WidgetRenderInfo.class);
    when(widget.getCssStyle()).thenReturn(widgetStyle);
    ColumnRenderInfo column = mock(ColumnRenderInfo.class);
    when(column.getCssStyle()).thenReturn(columnStyle);
    when(column.getWidgetRenderInfoList()).thenReturn(List.of(widget));
    SectionRenderInfo section = mock(SectionRenderInfo.class);
    when(section.getCssStyle()).thenReturn(sectionStyle);
    when(section.getColumnRenderInfoList()).thenReturn(List.of(column));
    return section;
  }

  @Test
  void everyLayoutLevelOfEveryContainerIsCovered() {
    HttpServletRequest request = request();
    // Built before stubbing: creating a mock inside thenReturn() would leave that stubbing unfinished.
    List<SectionRenderInfo> pageSections = List.of(section("padding-top: 48px", "margin-top: 12px", "max-width: 720px"));
    List<SectionRenderInfo> headerSections = List.of(section("background-color: #0b1024", null, null));
    List<SectionRenderInfo> footerSections = List.of(section(null, null, "border-top: 1px solid #ccc"));
    PageRenderInfo page = mock(PageRenderInfo.class);
    when(page.getSectionRenderInfoList()).thenReturn(pageSections);
    HeaderRenderInfo header = mock(HeaderRenderInfo.class);
    when(header.getSectionRenderInfoList()).thenReturn(headerSections);
    FooterRenderInfo footer = mock(FooterRenderInfo.class);
    when(footer.getSectionRenderInfoList()).thenReturn(footerSections);
    request.setAttribute(RequestConstants.PAGE_RENDER_INFO, page);
    request.setAttribute(RequestConstants.HEADER_RENDER_INFO, header);
    request.setAttribute(RequestConstants.FOOTER_RENDER_INFO, footer);

    String rules = PageStyleRules.headRules(request);
    for (String css : new String[] {"padding-top: 48px", "margin-top: 12px", "max-width: 720px",
        "background-color: #0b1024", "border-top: 1px solid #ccc"}) {
      assertTrue(rules.contains(StyleRuleCommand.rule(css)), "missing rule for " + css + " in " + rules);
    }
    assertEquals(5, rules.split("\n").length);
  }

  @Test
  void aRegisteredWidgetValueIsInTheHeadAndItsHookIsReturned() {
    HttpServletRequest request = request();
    String hook = PageStyleRules.register(request, "background:#336699;color:#ffffff");
    assertEquals(StyleRuleCommand.hook("background:#336699;color:#ffffff"), hook);
    assertEquals(StyleRuleCommand.rule("background:#336699;color:#ffffff"), PageStyleRules.headRules(request));
  }

  @Test
  void theSameStyleIsServedOnce() {
    HttpServletRequest request = request();
    for (int i = 0; i < 25; i++) {
      PageStyleRules.register(request, "background:#336699;color:#ffffff");
    }
    assertEquals(1, PageStyleRules.headRules(request).split("\n").length);
  }

  @Test
  void anUnsafeValueRegistersNothingAndGetsNoHook() {
    HttpServletRequest request = request();
    assertEquals("", PageStyleRules.register(request, "color: red}</style>"));
    assertEquals("", PageStyleRules.headRules(request));
  }

  @Test
  void aPageWithNoStylesHasNoRules() {
    HttpServletRequest request = request();
    List<SectionRenderInfo> sections = List.of(section(null, "", null));
    PageRenderInfo page = mock(PageRenderInfo.class);
    when(page.getSectionRenderInfoList()).thenReturn(sections);
    request.setAttribute(RequestConstants.PAGE_RENDER_INFO, page);
    assertEquals("", PageStyleRules.headRules(request));
    assertFalse(PageStyleRules.headRules(request).contains("data-sc-style"));
  }
}
