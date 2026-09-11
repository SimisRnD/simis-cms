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
    assertEquals(6, rules.split("\n").length);
  }

  @Test
  void aRegisteredWidgetValueIsInTheHeadAndItsHookIsReturned() {
    HttpServletRequest request = request();
    String hook = PageStyleRules.register(request, "background:#336699;color:#ffffff");
    assertEquals(StyleRuleCommand.hook("background:#336699;color:#ffffff"), hook);
    assertEquals(StyleRuleCommand.rule("background:#336699;color:#ffffff") + "\n" + PageStyleRules.PRINT_RESET,
        PageStyleRules.headRules(request));
  }

  @Test
  void theSameStyleIsServedOnce() {
    HttpServletRequest request = request();
    for (int i = 0; i < 25; i++) {
      PageStyleRules.register(request, "background:#336699;color:#ffffff");
    }
    assertEquals(2, PageStyleRules.headRules(request).split("\n").length);
  }

  @Test
  void printGetsFoundationsResetBackAfterEveryRule() {
    // Foundation's print reset used to beat the attribute; the !important rules would beat it instead
    HttpServletRequest request = request();
    PageStyleRules.register(request, "background:#336699;color:#ffffff");
    PageStyleRules.register(request, "box-shadow: 0 2px 4px #000");
    String rules = PageStyleRules.headRules(request);
    assertTrue(rules.endsWith("\n" + PageStyleRules.PRINT_RESET), rules);
    assertEquals(1, rules.split("@media print", -1).length - 1);
    assertEquals("@media print{[data-sc-style]{background:0 0 !important;color:#000 !important;"
        + "box-shadow:none !important;text-shadow:none !important}}", PageStyleRules.PRINT_RESET);
  }

  @Test
  void anUnsafeValueRegistersNothingAndGetsNoHook() {
    HttpServletRequest request = request();
    assertEquals("", PageStyleRules.register(request, "color: red}</style>"));
    assertEquals("", PageStyleRules.headRules(request));
  }

  @Test
  void aUrlNeedsNoQuotesAndStillPassesTheGrammar() {
    String url = PageStyleRules.url("/assets/view/2026/photo (1) 'a\".png");
    assertEquals("url(/assets/view/2026/photo%20%281%29%20%27a%22.png)", url);
    assertEquals("background-image: " + url + " !important",
        StyleRuleCommand.safeDeclarations("background-image:" + url));
    // A YouTube poster, as VideoWidget builds it
    assertFalse(PageStyleRules.register(request(), "background-image: "
        + PageStyleRules.url("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg")).isEmpty());
    // Encoding is not validation: an address the grammar refuses is still refused
    assertEquals("", PageStyleRules.register(request(), "background-image: " + PageStyleRules.url("//evil.example/a.png")));
    assertEquals("", PageStyleRules.url(null));
    assertEquals("", PageStyleRules.url("  "));
  }

  @Test
  void theMenuTabWidthMatchesTheScriptletItReplaced() {
    // layout-header-standard.jspf computed 100 / (tabs - 1) in integer arithmetic; one tab is the hidden Home link
    assertEquals("width: 100%", PageStyleRules.menuTabWidth(List.of("home", "a")));
    assertEquals("width: 33%", PageStyleRules.menuTabWidth(List.of("home", "a", "b", "c")));
    assertEquals("width: 25%", PageStyleRules.menuTabWidth(List.of("home", "a", "b", "c", "d")));
    assertEquals("width: 14%", PageStyleRules.menuTabWidth(List.of("home", "a", "b", "c", "d", "e", "f", "g")));
    // With one tab shown the scriptlet divided by zero; there is nothing to size then
    assertEquals("", PageStyleRules.menuTabWidth(List.of("home")));
    assertEquals("", PageStyleRules.menuTabWidth(List.of()));
    assertEquals("", PageStyleRules.menuTabWidth(null));
    assertEquals("", PageStyleRules.register(request(), PageStyleRules.menuTabWidth(null)));
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
