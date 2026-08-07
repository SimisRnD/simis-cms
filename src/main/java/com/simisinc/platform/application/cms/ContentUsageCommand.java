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
   * The result of one usage scan: the real, matchable usage map (see {@link #findUsageMap}), plus the
   * literal prefixes seen in a {@code <uniqueId>} value that carries an unresolved EL placeholder --
   * e.g. {@code product-details-} from {@code product-details-${item.uniqueId}} in
   * {@code products-layout.xml}, keyed to the location(s) it was found in, same shape as {@code
   * usageMap}. A real {@code Content} row whose uniqueId starts with one of these prefixes cannot be
   * proven used by this static scan (the placeholder is only resolved per-item at render time), but it
   * is not truly orphaned either -- it is <i>templated</i>. See
   * {@link com.simisinc.platform.presentation.widgets.admin.cms.ContentListWidget} for how this
   * distinguishes a "Templated" badge from "Orphaned" so admins aren't told to delete something that is
   * actually wired to a live per-item template.
   */
  public record UsageScan(Map<String, List<String>> usageMap, Map<String, List<String>> templatedPrefixLocations) {
  }

  /**
   * Scans every admin-authored page and every filesystem web-layout template for content-family
   * widget references, and returns a map of content uniqueId to the (deduplicated, order-preserving)
   * list of locations that reference it: a page's link (e.g. "/careers") for a {@code web_pages} row,
   * or a template's resource path (e.g. "/WEB-INF/web-layouts/footer/footer-layout.xml") for a
   * filesystem template. A content block with no entry in the returned map is unreferenced ("Orphaned"),
   * unless it matches a {@link UsageScan#templatedPrefixLocations()} entry from {@link #scanUsage} --
   * this convenience method exposes only the literal-match half of that scan, for the one caller
   * ({@link ContentHtmlCommand}/{@link com.simisinc.platform.presentation.widgets.cms.ContentEditorWidget})
   * that only ever looks up a single, already-resolved uniqueId and has no use for the templated half.
   *
   * @param servletContext used to enumerate and read the filesystem web-layout templates; may be null
   *     (e.g. in a unit test), in which case only the web_pages scan runs
   */
  public static Map<String, List<String>> findUsageMap(ServletContext servletContext) {
    return scanUsage(servletContext).usageMap();
  }

  /**
   * The full scan behind {@link #findUsageMap}, also returning which literal uniqueId prefixes were
   * seen behind an unresolved EL placeholder (see {@link UsageScan}). Both halves come from the same
   * single walk of the web_pages table and the filesystem web-layout templates, so a caller that needs
   * both (e.g. {@link com.simisinc.platform.presentation.widgets.admin.cms.ContentListWidget}, for the
   * Orphaned/Templated/Shared columns) does not pay for the scan twice.
   *
   * @param servletContext used to enumerate and read the filesystem web-layout templates; may be null
   *     (e.g. in a unit test), in which case only the web_pages scan runs
   */
  public static UsageScan scanUsage(ServletContext servletContext) {
    Map<String, List<String>> usageMap = new LinkedHashMap<>();
    Map<String, List<String>> templatedPrefixLocations = new LinkedHashMap<>();

    List<WebPage> webPageList = WebPageRepository.findAll();
    if (webPageList != null) {
      for (WebPage webPage : webPageList) {
        addUsageFromXml(usageMap, templatedPrefixLocations, webPage.getPageXml(), webPage.getLink());
      }
    }

    if (servletContext != null) {
      for (String file : findWebLayoutFiles(servletContext)) {
        String xml = readResource(servletContext, file);
        addUsageFromXml(usageMap, templatedPrefixLocations, xml, file);
      }
    }

    return new UsageScan(usageMap, templatedPrefixLocations);
  }

  /** Separates a multi-page template file's path from the specific {@code <page name="...">} it was
   * found in, e.g. {@code /WEB-INF/web-layouts/page/cms-layout.xml#/login} -- see {@link
   * #scopedLocation}. Not a real filesystem path character, so it can't collide with one. */
  private static final char PAGE_SCOPE_SEPARATOR = '#';

  /**
   * Whether a usage location is a genuinely site-wide filesystem web-layout template (e.g. {@code
   * footer-layout.xml}, which has no {@code <page>} wrapper at all and is rendered on every page that
   * includes it) rather than a single {@code <page>} route bundled inside a multi-page template file
   * like {@code admin-layout.xml}/{@code cms-layout.xml} (which each define dozens of independent
   * routes in one physical file -- a widget reference inside one {@code <page>} block only affects
   * that single route, exactly like a single admin-authored {@code web_pages} row). {@link
   * #scopedLocation} records the latter kind with a {@code #pageName} suffix specifically so this
   * method can tell them apart: only the un-suffixed, truly-global kind counts as inherently
   * site-wide from a single reference.
   */
  public static boolean isFilesystemTemplateLocation(String location) {
    return location != null && location.startsWith(WEB_LAYOUTS_PATH) && location.indexOf(PAGE_SCOPE_SEPARATOR) < 0;
  }

  /**
   * Whether a content block's usage counts as "Shared" -- editing it can affect more than the single
   * page an author might be looking at. True when the block is referenced from more than one location,
   * OR from even a single {@link #isFilesystemTemplateLocation(String) filesystem template} location:
   * a raw count of 1 undercounts a block like "site-footer", which has exactly one entry in the usage
   * map (footer-layout.xml) but is actually included on every page on the site.
   */
  public static boolean isShared(List<String> locations) {
    if (locations == null || locations.isEmpty()) {
      return false;
    }
    if (locations.size() > 1) {
      return true;
    }
    return isFilesystemTemplateLocation(locations.get(0));
  }

  /**
   * Builds the "this will affect other pages" warning shown before a shared content block is
   * republished (issue #499 slice 2), or {@code null} when there is nothing to warn about. A
   * block used on 0 or 1 locations isn't "shared" -- publishing it only ever affects the page the
   * author is already looking at, so no warning is needed.
   *
   * <p>
   * Shared by the two real publish surfaces that can affect other pages: {@code
   * ContentEditorWidget}'s "Publish Immediately" button on the full {@code /content-editor} page,
   * and the DRAFT-badge "Publish this content?" confirm in {@code content.jsp}. Both describe the
   * same locations the same way rather than duplicating the message-building logic. (A third
   * publish surface, the step-up APPROVE flow reached from {@code content.jsp}, is a different UI
   * shape -- a full re-authentication form, not a single confirm-on-click -- and is intentionally
   * out of scope for this slice.)
   * </p>
   *
   * @param uniqueId the content block being published
   * @param usageMap the result of {@link #findUsageMap(ServletContext)}
   * @return a warning message with the affected page/template locations, or {@code null} if the
   *     block is not shared
   */
  public static String buildReusabilityWarning(String uniqueId, Map<String, List<String>> usageMap) {
    if (uniqueId == null || usageMap == null) {
      return null;
    }
    List<String> locations = usageMap.get(uniqueId);
    if (!isShared(locations)) {
      return null;
    }
    if (locations.size() == 1) {
      // isShared() only returns true here because the lone location is a site-wide filesystem
      // template (see #isFilesystemTemplateLocation) -- a raw "appears on 1 pages" would both read
      // oddly and understate the real blast radius (a template like footer-layout.xml renders on
      // every page, not just one).
      return "This content is part of a site-wide template (" + locations.get(0)
          + "). Update will affect every page that uses it.";
    }
    return "This content appears on " + locations.size() + " pages. Update will affect: "
        + String.join(", ", locations) + ".";
  }

  private static void addUsageFromXml(Map<String, List<String>> usageMap, Map<String, List<String>> templatedPrefixLocations,
      String xml, String location) {
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
      String widgetLocation = scopedLocation(widgetElement, location);
      String uniqueId = directChildText(widgetElement, "uniqueId");
      recordUsage(usageMap, templatedPrefixLocations, uniqueId, widgetLocation);
      List<String> extraChildTags = EXTRA_CONTENT_REFERENCE_CHILD_TAGS.get(widgetName);
      if (extraChildTags != null) {
        for (String childTag : extraChildTags) {
          String extraUniqueId = directChildText(widgetElement, childTag);
          recordUsage(usageMap, templatedPrefixLocations, extraUniqueId, widgetLocation);
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
          recordUsage(usageMap, templatedPrefixLocations, contentUniqueId, widgetLocation);
        }
      }
    }
  }

  /**
   * Refines a filesystem template's bare resource path to {@code path#pageName} when the widget was
   * found inside a {@code <page name="...">} element -- the shape every file directly under {@code
   * WEB-INF/web-layouts/page/} uses (e.g. {@code admin-layout.xml}, {@code cms-layout.xml}), each
   * bundling dozens of independent routes in one physical file. Without this, a widget used on just
   * one of those routes would be indistinguishable from a truly file-scoped, render-on-every-page
   * fragment like {@code footer-layout.xml} (which has no {@code <page>} wrapper at all, so this
   * walk finds none and returns {@code location} unchanged) -- see {@link
   * #isFilesystemTemplateLocation}, which relies on this suffix to tell the two apart.
   * <p>
   * Only applies when {@code location} is itself a filesystem path: a {@code web_pages} row's own
   * XML is ALSO {@code <page name="...">}-wrapped (that's the literal format {@link
   * com.simisinc.platform.domain.model.cms.WebPage#getPageXml()} is stored in), but its {@code
   * location} is already the page's unique link (e.g. {@code /careers}), so appending the same page
   * name again would just double it into {@code /careers#/careers}. That location is already
   * maximally specific -- one row, one page -- so it needs no further scoping.
   */
  private static String scopedLocation(Element widgetElement, String location) {
    if (!location.startsWith(WEB_LAYOUTS_PATH)) {
      return location;
    }
    Node ancestor = widgetElement.getParentNode();
    while (ancestor != null && ancestor.getNodeType() == Node.ELEMENT_NODE) {
      Element ancestorElement = (Element) ancestor;
      if ("page".equals(ancestorElement.getTagName())) {
        String pageName = ancestorElement.getAttribute("name");
        if (StringUtils.isNotBlank(pageName)) {
          return location + PAGE_SCOPE_SEPARATOR + pageName;
        }
        break;
      }
      ancestor = ancestor.getParentNode();
    }
    return location;
  }

  /** Matches an unresolved EL placeholder anywhere in a candidate uniqueId, e.g. the {@code
   * ${item.uniqueId}} in {@code product-details-${item.uniqueId}} (products-layout.xml). */
  private static final String EL_PLACEHOLDER_MARKER = "${";

  /**
   * Records one {@code <uniqueId>}-shaped value found on a content-family widget. A value containing
   * an unresolved EL placeholder (e.g. {@code product-details-${item.uniqueId}}) is never a real
   * {@code content_unique_id} -- it is only resolved per-item at render time -- so recording it
   * literally in {@code usageMap} would create a permanently-dead key that can never match a real
   * Content row (issue #499 follow-up). Instead, the literal prefix before the placeholder is recorded
   * in {@code templatedPrefixLocations}, so a real Content row that starts with it can be shown as
   * "Templated" rather than falsely "Orphaned". A placeholder with no literal prefix at all (e.g. a
   * bare {@code ${item.uniqueId}}) is skipped entirely -- an empty prefix would match every Content
   * row, which is worse than not recording it.
   */
  private static void recordUsage(Map<String, List<String>> usageMap, Map<String, List<String>> templatedPrefixLocations,
      String rawUniqueId, String location) {
    if (StringUtils.isBlank(rawUniqueId)) {
      return;
    }
    String uniqueId = rawUniqueId.trim();
    int placeholderIdx = uniqueId.indexOf(EL_PLACEHOLDER_MARKER);
    if (placeholderIdx > -1) {
      String prefix = uniqueId.substring(0, placeholderIdx);
      if (StringUtils.isNotBlank(prefix)) {
        addLocation(templatedPrefixLocations, prefix, location);
      }
      return;
    }
    addLocation(usageMap, uniqueId, location);
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
