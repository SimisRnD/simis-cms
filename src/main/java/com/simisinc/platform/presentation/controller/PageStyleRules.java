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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.simisinc.platform.application.cms.StyleRuleCommand;

import jakarta.servlet.ServletRequest;

/**
 * The page's dynamic style rules, served from one nonced &lt;style&gt; element in the head instead of
 * inline style attributes (issue #1999). EL functions, exposed through style-functions.tld.
 *
 * <p>Two kinds of value land here. A page layout's section, column and widget style="" values are
 * read straight from the page, header and footer render info by {@link #headRules}. Values a widget
 * computes while it renders -- a category's header colors -- are added with {@link #register}, which
 * works because every widget's JSP is rendered into a buffer before main.jsp writes the head.</p>
 *
 * <p>The head is the only placement that changes nothing else. A &lt;style&gt; element beside the
 * styled element would become its sibling and shift :first-child and + matching; one at the end of the
 * body would apply after the content had painted.</p>
 *
 * @author elizabeth houser
 * @created 9/10/26
 */
public class PageStyleRules {

  /** Request attribute holding the rules registered so far, hook to rule, in first-seen order. */
  static final String RULES_ATTRIBUTE = PageStyleRules.class.getName() + ".rules";

  /**
   * Foundation's print reset, {@code @media print { * { background: 0 0 !important; color: #000
   * !important; ... } }}, beat a style="" attribute, which is never !important. The rules here are
   * !important and more specific than *, so they would beat it instead, and a hooked element would
   * print its colors, backgrounds and shadows where it used to print plain. This gives hooked elements
   * the same reset back. It follows the rules, so it wins at equal specificity.
   */
  static final String PRINT_RESET = "@media print{[" + StyleRuleCommand.HOOK_ATTRIBUTE + "]{background:0 0 !important;"
      + "color:#000 !important;box-shadow:none !important;text-shadow:none !important}}";

  private static final String[] CONTAINERS = {
      RequestConstants.PAGE_RENDER_INFO,
      RequestConstants.HEADER_RENDER_INFO,
      RequestConstants.FOOTER_RENDER_INFO
  };

  /** The hook for a style value, or an empty string when nothing in it is safe. */
  public static String hook(String css) {
    return StyleRuleCommand.hook(css);
  }

  /**
   * A CSS url() for a path or address, or an empty string when there is none.
   *
   * <p>Unquoted, with space, quotes, parentheses and backslash percent-encoded -- which the browser
   * decodes back to the same request -- so a value built in EL needs no quote characters. Quotes inside
   * an EL string inside a JSP attribute are escaped once by the JSP parser and again by EL, which is
   * how they get corrupted.</p>
   */
  public static String url(String address) {
    if (address == null || address.isBlank()) {
      return "";
    }
    StringBuilder encoded = new StringBuilder("url(");
    for (char c : address.trim().toCharArray()) {
      switch (c) {
        case ' ': encoded.append("%20"); break;
        case '"': encoded.append("%22"); break;
        case '\'': encoded.append("%27"); break;
        case '(': encoded.append("%28"); break;
        case ')': encoded.append("%29"); break;
        case '\\': encoded.append("%5C"); break;
        default:
          if (c < 0x20 || c == 0x7f) {
            encoded.append(String.format("%%%02X", (int) c));
          } else {
            encoded.append(c);
          }
      }
    }
    return encoded.append(')').toString();
  }

  /**
   * The standard header's equal tab width, "width: N%" -- exactly what its scriptlet computed,
   * 100 / (tabs - 1) in integer arithmetic, one tab being the hidden Home link -- or an empty string
   * when there are fewer than two tabs. The header renders after the head, so main.jsp registers this
   * and hands the header the hook.
   */
  public static String menuTabWidth(Collection<?> tabs) {
    if (tabs == null || tabs.size() < 2) {
      return "";
    }
    return "width: " + (100 / (tabs.size() - 1)) + "%";
  }

  /**
   * Adds a style value's rule to this request's head rules and returns its hook, or an empty string
   * when nothing in it is safe. For values a widget computes while rendering.
   */
  public static String register(ServletRequest request, String css) {
    String hook = StyleRuleCommand.hook(css);
    if (hook.isEmpty() || request == null) {
      return hook;
    }
    rules(request).putIfAbsent(hook, StyleRuleCommand.rule(css));
    return hook;
  }

  /**
   * Every rule this page needs: the layout's section, column and widget styles, plus whatever widgets
   * registered. One per distinct style, newline-separated and followed by {@link #PRINT_RESET}, or an
   * empty string when there are none.
   */
  public static String headRules(ServletRequest request) {
    if (request == null) {
      return "";
    }
    for (String name : CONTAINERS) {
      Object container = request.getAttribute(name);
      if (container instanceof ContainerRenderInfo) {
        registerLayout(request, (ContainerRenderInfo) container);
      }
    }
    Map<String, String> rules = rules(request);
    return rules.isEmpty() ? "" : String.join("\n", rules.values()) + "\n" + PRINT_RESET;
  }

  private static void registerLayout(ServletRequest request, ContainerRenderInfo container) {
    List<SectionRenderInfo> sections = container.getSectionRenderInfoList();
    if (sections == null) {
      return;
    }
    for (SectionRenderInfo section : sections) {
      register(request, section.getCssStyle());
      if (section.getColumnRenderInfoList() == null) {
        continue;
      }
      for (ColumnRenderInfo column : section.getColumnRenderInfoList()) {
        register(request, column.getCssStyle());
        if (column.getWidgetRenderInfoList() == null) {
          continue;
        }
        for (WidgetRenderInfo widget : column.getWidgetRenderInfoList()) {
          register(request, widget.getCssStyle());
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> rules(ServletRequest request) {
    Object existing = request.getAttribute(RULES_ATTRIBUTE);
    if (existing instanceof Map) {
      return (Map<String, String>) existing;
    }
    Map<String, String> rules = new LinkedHashMap<>();
    request.setAttribute(RULES_ATTRIBUTE, rules);
    return rules;
  }
}
