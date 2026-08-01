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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

import jakarta.servlet.ServletContext;

/**
 * Determines which page(s) reference a content block, for the "Used on: ..." / "Orphaned" display
 * on /admin/content-list (issue #499). A content block is embedded into a page via a widget from
 * the content family (content, contentTabs, contentCards, contentAccordion, contentSlider,
 * contentReveal, contentGallery, contentCarousel, contentEditor -- see widget-library.xml) --
 * almost all of them via a direct {@code <uniqueId>} child of the {@code <widget>} element, except
 * contentTabs, which references one content block per tab via a {@code contentUniqueId} attribute
 * on a nested {@code <tab>} element instead.
 *
 * <p>
 * The ecommerce {@code cart} widget (see CartWidget#execute) is not part of that cms-package
 * "content family" by class, but it genuinely embeds up to two content blocks of its own, via
 * {@code card1uniqueId}/{@code card2uniqueId} preferences instead of the family's plain
 * {@code <uniqueId>} convention -- e.g. the real ecommerce-layout.xml {@code /cart} page. It is
 * therefore also tracked, via {@link #CONTENT_WIDGET_NAMES} plus {@link #EXTRA_CONTENT_REFERENCE_CHILD_TAGS}.
 * </p>
 *
 * <p>
 * {@code <uniqueId>} is a generic tag name reused by unrelated widgets (item/collection/table-of-
 * contents widgets also use it), so matching MUST be scoped to widgets whose {@code name} is in the
 * content family -- a bare text/regex scan for {@code <uniqueId>} anywhere in the page XML would
 * false-positive against those unrelated widgets. See {@link #CONTENT_WIDGET_NAMES}.
 * </p>
 *
 * <p>
 * Content blocks live in two places: (a) admin-authored pages, as {@code web_pages} DB rows
 * ({@link WebPageRepository#findAll()}, each with a link and a page XML document), and (b) shared
 * structural filesystem templates under {@code WEB-INF/web-layouts/**}(header, footer, etc. -- e.g.
 * the footer layout embeds the real "site-footer" content block). Both are scanned; a block used
 * only by a filesystem template (never by any {@code web_pages} row) would otherwise incorrectly
 * show as orphaned.
 * </p>
 *
 * <p>
 * Only the published page XML ({@link WebPage#getPageXml()}) is scanned, not a page's unpublished
 * draft -- "used" reflects what is actually live, consistent with draft/publish workflow being out
 * of scope for this feature.
 * </p>
 *
 * @author SimIS Inc.
 */
public class ContentUsageCommand {

  private static final Log LOG = LogFactory.getLog(ContentUsageCommand.class);

  static final Set<String> CONTENT_WIDGET_NAMES = Set.of(
      "content", "contentTabs", "contentCards", "contentAccordion", "contentSlider",
      "contentReveal", "contentGallery", "contentCarousel", "contentEditor",
      "cart");

  /** Direct child tag names that reference a content block's uniqueId, for widgets in {@link
   * #CONTENT_WIDGET_NAMES} whose preference shape doesn't match the family's plain {@code
   * <uniqueId>} convention. {@code cart} can embed up to two content blocks via {@code
   * card1uniqueId}/{@code card2uniqueId} preferences (see CartWidget#execute); contentTabs's own
   * outlier shape (a {@code contentUniqueId} attribute on each nested {@code <tab>}) is handled
   * separately below since it isn't a direct child element at all. */
  private static final Map<String, List<String>> EXTRA_CONTENT_REFERENCE_CHILD_TAGS = Map.of(
      "cart", List.of("card1uniqueId", "card2uniqueId"));

  private static final String WEB_LAYOUTS_PATH = "/WEB-INF/web-layouts";

  private ContentUsageCommand() {
  }

  /**
   * Scans every admin-authored page and every filesystem web-layout template for content-family
   * widget references, and returns a map of content uniqueId to the (deduplicated, order-preserving)
   * list of locations that reference it: a page's link (e.g. "/careers") for a {@code web_pages} row,
   * or a template's resource path (e.g. "/WEB-INF/web-layouts/footer/footer-layout.xml") for a
   * filesystem template. A content block with no entry in the returned map is unreferenced ("Orphaned").
   *
   * @param servletContext used to enumerate and read the filesystem web-layout templates; may be null
   *     (e.g. in a unit test), in which case only the web_pages scan runs
   */
  public static Map<String, List<String>> findUsageMap(ServletContext servletContext) {
    Map<String, List<String>> usageMap = new LinkedHashMap<>();

    List<WebPage> webPageList = WebPageRepository.findAll();
    if (webPageList != null) {
      for (WebPage webPage : webPageList) {
        addUsageFromXml(usageMap, webPage.getPageXml(), webPage.getLink());
      }
    }

    if (servletContext != null) {
      for (String file : findWebLayoutFiles(servletContext)) {
        String xml = readResource(servletContext, file);
        addUsageFromXml(usageMap, xml, file);
      }
    }

    return usageMap;
  }

  private static void addUsageFromXml(Map<String, List<String>> usageMap, String xml, String location) {
    if (StringUtils.isBlank(xml) || StringUtils.isBlank(location)) {
      return;
    }
    Document document;
    try {
      document = parseDocument(xml);
    } catch (Exception e) {
      LOG.debug("Could not parse XML for content usage scanning: " + location, e);
      return;
    }
    NodeList widgetTags = document.getElementsByTagName("widget");
    for (int i = 0; i < widgetTags.getLength(); i++) {
      Element widgetElement = (Element) widgetTags.item(i);
      String widgetName = widgetElement.getAttribute("name");
      if (!CONTENT_WIDGET_NAMES.contains(widgetName)) {
        // Not a content-family widget -- a <uniqueId> here (if any) belongs to something else
        // entirely (an item/collection/table-of-contents widget, etc.) and must not be matched.
        continue;
      }
      String uniqueId = directChildText(widgetElement, "uniqueId");
      if (StringUtils.isNotBlank(uniqueId)) {
        addLocation(usageMap, uniqueId.trim(), location);
      }
      List<String> extraChildTags = EXTRA_CONTENT_REFERENCE_CHILD_TAGS.get(widgetName);
      if (extraChildTags != null) {
        for (String childTag : extraChildTags) {
          String extraUniqueId = directChildText(widgetElement, childTag);
          if (StringUtils.isNotBlank(extraUniqueId)) {
            addLocation(usageMap, extraUniqueId.trim(), location);
          }
        }
      }
      if ("contentTabs".equals(widgetName)) {
        // Each tab references its own content block via a contentUniqueId attribute on a nested
        // <tab> element (see ContentTabsWidget), not the simple <uniqueId> child the rest of the
        // content family uses.
        NodeList tabTags = widgetElement.getElementsByTagName("tab");
        for (int t = 0; t < tabTags.getLength(); t++) {
          Element tab = (Element) tabTags.item(t);
          String contentUniqueId = tab.getAttribute("contentUniqueId");
          if (StringUtils.isNotBlank(contentUniqueId)) {
            addLocation(usageMap, contentUniqueId.trim(), location);
          }
        }
      }
    }
  }

  /** The text of the first DIRECT child element named {@code childTagName}, or null if absent. Does
   * not search descendants -- matches XMLContainerCommands#addWidgetPreferences, which only reads a
   * widget's immediate child tags as its preferences. */
  private static String directChildText(Element parent, String childTagName) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE && childTagName.equals(((Element) node).getTagName())) {
        return node.getTextContent();
      }
    }
    return null;
  }

  private static void addLocation(Map<String, List<String>> usageMap, String uniqueId, String location) {
    List<String> locations = usageMap.computeIfAbsent(uniqueId, k -> new ArrayList<>());
    if (!locations.contains(location)) {
      locations.add(location);
    }
  }

  private static Document parseDocument(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
      return builder.parse(is);
    }
  }

  /** Recursively enumerates every *.xml file under WEB-INF/web-layouts (header/, footer/, page/, collection/, ...). */
  private static List<String> findWebLayoutFiles(ServletContext servletContext) {
    List<String> files = new ArrayList<>();
    Set<String> visited = new LinkedHashSet<>();
    collectXmlFiles(servletContext, WEB_LAYOUTS_PATH, files, visited);
    return files;
  }

  private static void collectXmlFiles(ServletContext servletContext, String path, List<String> files, Set<String> visited) {
    if (!visited.add(path)) {
      // Defends against a resource-path cycle (e.g. a symlink loop); should not happen in practice.
      return;
    }
    Set<String> resourcePaths = servletContext.getResourcePaths(path);
    if (resourcePaths == null) {
      return;
    }
    for (String resourcePath : resourcePaths) {
      if (resourcePath.endsWith("/")) {
        collectXmlFiles(servletContext, resourcePath, files, visited);
      } else if (resourcePath.endsWith(".xml")) {
        files.add(resourcePath);
      }
    }
  }

  private static String readResource(ServletContext servletContext, String file) {
    try (InputStream is = servletContext.getResourceAsStream(file)) {
      if (is == null) {
        return null;
      }
      return IOUtils.toString(is, StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOG.debug("Could not read web-layout resource: " + file, e);
      return null;
    }
  }
}
