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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Structural gate: every {@code <widget name="...">} referenced from a page layout must actually
 * be registered in widget-library.xml, or {@code XMLPageLoader}/{@code WebComponentCommand} fail to
 * resolve it at render time and the page silently breaks.
 *
 * <p>This repo has repeatedly lost widget/page registrations to a merge that keeps one side's
 * change but drops the other's whole-line addition, with no conflict marker to catch it --
 * {@code WebPageReviewWidget} being the most recent instance (its class and JSP survived, but its
 * {@code <widget name="webPageReview">} entry in widget-library.xml and its {@code <page
 * name="/admin/web-page-review">} entry in admin-layout.xml were both silently dropped). Rather
 * than add one narrow regression test per incident, this scans every real page layout file for
 * every widget name it references and fails if any of them isn't registered, so the whole bug
 * shape is caught regardless of which widget it happens to next.
 *
 * @author elizabeth houser
 */
class WidgetRegistrationReachabilityTest {

  private static final File WIDGET_LIBRARY = new File("src/main/webapp/WEB-INF/widgets/widget-library.xml");
  private static final File LAYOUT_DIR = new File("src/main/webapp/WEB-INF/web-layouts/page");
  private static final File TEMPLATE_DIR = new File("src/main/webapp/WEB-INF/web-templates");

  @Test
  void everyWidgetReferencedByAPageLayoutIsRegistered() throws Exception {
    assertTrue(WIDGET_LIBRARY.isFile(),
        "widget-library.xml not found (run from the project root): " + WIDGET_LIBRARY.getAbsolutePath());
    assertTrue(LAYOUT_DIR.isDirectory(),
        "layout directory not found (run from the project root): " + LAYOUT_DIR.getAbsolutePath());

    Set<String> registeredWidgets = parseRegisteredWidgetNames();
    assertTrue(registeredWidgets.size() > 50,
        "parsed suspiciously few widget registrations (" + registeredWidgets.size()
            + ") -- check the parser/path");

    File[] layouts = LAYOUT_DIR.listFiles((dir, name) -> name.endsWith(".xml"));
    assertTrue(layouts != null && layouts.length > 0, "no page layout files found in " + LAYOUT_DIR.getAbsolutePath());

    Set<String> unregistered = new TreeSet<>();
    int referencesScanned = 0;
    for (File layout : layouts) {
      Document document = parse(layout);
      NodeList widgets = document.getElementsByTagName("widget");
      for (int i = 0; i < widgets.getLength(); i++) {
        referencesScanned++;
        Element widget = (Element) widgets.item(i);
        String name = widget.getAttribute("name");
        if (!name.isEmpty() && !registeredWidgets.contains(name)) {
          unregistered.add(layout.getName() + " -> widget name=\"" + name + "\"");
        }
      }
    }

    assertTrue(unregistered.isEmpty(),
        "Page layouts reference widgets that aren't registered in widget-library.xml -- these will "
            + "fail to resolve at render time. Unregistered references: " + unregistered);
    // Guard against a path/parser change silently turning this into a no-op that always passes.
    assertTrue(referencesScanned > 0, "scanned zero <widget> elements in the page layouts -- check the parser/path");
  }

  /**
   * Same guarantee for the designer's starter templates (issue #1218): every {@code <widget name>}
   * a template seeds into a new page's XML must be a registered widget, or a page created from that
   * template silently drops it. Covers the whole {@code web-templates/} tree recursively, so a new
   * template referencing a misspelled or unregistered widget fails here instead of at page render.
   */
  @Test
  void everyWidgetReferencedByAWebTemplateIsRegistered() throws Exception {
    assertTrue(WIDGET_LIBRARY.isFile(),
        "widget-library.xml not found (run from the project root): " + WIDGET_LIBRARY.getAbsolutePath());
    assertTrue(TEMPLATE_DIR.isDirectory(),
        "web-templates directory not found (run from the project root): " + TEMPLATE_DIR.getAbsolutePath());

    Set<String> registeredWidgets = parseRegisteredWidgetNames();
    assertTrue(registeredWidgets.size() > 50,
        "parsed suspiciously few widget registrations (" + registeredWidgets.size()
            + ") -- check the parser/path");

    List<File> templates = new ArrayList<>();
    collectXmlFiles(TEMPLATE_DIR, templates);
    assertTrue(templates.size() > 20,
        "found suspiciously few page templates (" + templates.size() + ") under "
            + TEMPLATE_DIR.getAbsolutePath() + " -- check the path");

    Set<String> unregistered = new TreeSet<>();
    int referencesScanned = 0;
    for (File template : templates) {
      Document document = parse(template);
      NodeList widgets = document.getElementsByTagName("widget");
      for (int i = 0; i < widgets.getLength(); i++) {
        referencesScanned++;
        Element widget = (Element) widgets.item(i);
        String name = widget.getAttribute("name");
        if (!name.isEmpty() && !registeredWidgets.contains(name)) {
          unregistered.add(template.getName() + " -> widget name=\"" + name + "\"");
        }
      }
    }

    assertTrue(unregistered.isEmpty(),
        "Starter templates reference widgets that aren't registered in widget-library.xml -- a page "
            + "created from the template would silently drop them. Unregistered references: " + unregistered);
    assertTrue(referencesScanned > 0, "scanned zero <widget> elements across the web templates -- check the parser/path");
  }

  private static void collectXmlFiles(File dir, List<File> out) {
    File[] entries = dir.listFiles();
    if (entries == null) {
      return;
    }
    for (File entry : entries) {
      if (entry.isDirectory()) {
        collectXmlFiles(entry, out);
      } else if (entry.getName().endsWith(".xml")) {
        out.add(entry);
      }
    }
  }

  private static Set<String> parseRegisteredWidgetNames() throws Exception {
    Document document = parse(WIDGET_LIBRARY);
    NodeList widgets = document.getElementsByTagName("widget");
    Set<String> names = new HashSet<>();
    for (int i = 0; i < widgets.getLength(); i++) {
      Element widget = (Element) widgets.item(i);
      String name = widget.getAttribute("name");
      if (!name.isEmpty()) {
        names.add(name);
      }
    }
    return names;
  }

  private static Document parse(File file) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(file);
  }
}
