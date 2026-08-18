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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.database.DB;

/**
 * Finds where an {@link Image} is referenced across the site, for the /admin/images delete-warning
 * and "Orphaned" badge (issue #498).
 * <p>
 * An image is referenced by its literal URL string ({@link Image#getUrl()}, embedded id and all)
 * appearing in one of two different kinds of place -- there is no structural/foreign-key reference
 * to walk, so both are plain string scans:
 * <ul>
 * <li>plain varchar "image URL" columns on several domain models (e.g. {@code web_pages.page_image_url},
 * {@code products.image_url}) -- set when an admin picks an image via the image-browser popup
 * (see {@code cms/image-browser.jsp}'s {@code mySubmit()}), which stores the URL verbatim.</li>
 * <li>raw {@code <img src="...">} tags embedded inside rich-text HTML bodies (CMS content blocks,
 * blog post bodies) -- inserted the same way, via the TinyMCE image-browser integration
 * (see the same JSP's {@code postMessage({mceAction: 'FileSelected', ...})} handler).</li>
 * <li>the same URL, in markdown image syntax or raw HTML, inside a wiki page body -- unlike the
 * sources above, {@code wiki-editor.jsp} has no image-browser integration of its own, so an author
 * gets the URL by copying it from the image-browser popup or from {@code /admin/images}' "Image Link"
 * and pastes it into the markdown by hand.</li>
 * <li>a {@code site_properties} row whose {@code property_type} is {@code 'image'} (e.g.
 * {@code site.logo}, {@code site.logo.white}, {@code site.logo.mixed}, {@code site.image}) -- a
 * key/value table rather than a fixed column, and site-wide rather than tied to one page/post, so
 * it's queried separately from {@link #VARCHAR_SOURCES} rather than folded into that array.</li>
 * </ul>
 * This is unrelated to the widget-XML-tree scan the sibling content-block-usage work (#499) uses --
 * that walks page layout structure; this matches a literal URL substring.
 * <p>
 * By design this is computed on demand for one image at a time (called from the delete-confirmation
 * flow, or lazily per row after the page has rendered), not eagerly for the whole 200+ image list in
 * one page load -- each call here runs one small query per known source table, which is fine for a
 * single image but would be a lot of avoidable work multiplied across a full list render.
 *
 * @author SimIS Inc.
 */
public class ImageUsageCommand {

  private static Log LOG = LogFactory.getLog(ImageUsageCommand.class);

  /**
   * Plain varchar columns known to store an image's URL verbatim: {table, column, labelColumn, sourceType}.
   * Table/column names here are fixed, developer-supplied constants (never derived from a request), so
   * concatenating them into the SQL text is safe -- only the searched-for value is ever bound as a
   * placeholder (see {@link #queryUsages}).
   */
  private static final String[][] VARCHAR_SOURCES = {
      { "web_pages", "page_image_url", "link", "Web Page" },
      { "blog_posts", "image_url", "title", "Blog Post" },
      { "calendar_events", "image_url", "title", "Calendar Event" },
      { "products", "image_url", "name", "Product" },
      { "collections", "image_url", "name", "Collection" },
      { "items", "image_url", "name", "Item" },
      { "users", "image_url", "email", "User Profile" },
  };

  /**
   * Rich-text HTML body columns that can carry a raw {@code <img src="...">} reference:
   * {table, column, labelColumn, sourceType}.
   */
  private static final String[][] HTML_BODY_SOURCES = {
      { "content", "content", "content_unique_id", "Content Block" },
      { "content", "draft_content", "content_unique_id", "Content Block (draft)" },
      { "blog_posts", "body", "title", "Blog Post" },
      { "wiki_pages", "body", "title", "Wiki Page" },
  };

  private ImageUsageCommand() {
    // Static utility
  }

  /**
   * A single place that references an image.
   */
  public static class UsageReference {
    private final String sourceType;
    private final String label;

    public UsageReference(String sourceType, String label) {
      this.sourceType = sourceType;
      this.label = label;
    }

    public String getSourceType() {
      return sourceType;
    }

    public String getLabel() {
      return label;
    }

    @Override
    public String toString() {
      return sourceType + ": " + (StringUtils.isNotBlank(label) ? label : "(untitled)");
    }
  }

  /**
   * Finds every known reference to the given image. An empty list means the image is orphaned.
   */
  public static List<UsageReference> findUsages(Image image) {
    if (image == null || image.getId() == null || image.getId() == -1) {
      return Collections.emptyList();
    }
    // getUrl() embeds this image's own id ("{webPath}-{id}/{filename}"), so a substring match
    // against it cannot cross-match a different image record, even one sharing the same filename.
    String urlToken = image.getUrl();
    if (StringUtils.isBlank(urlToken)) {
      return Collections.emptyList();
    }
    String likeValue = "%" + urlToken + "%";

    List<UsageReference> usages = new ArrayList<>();
    for (String[] source : VARCHAR_SOURCES) {
      usages.addAll(queryUsages(source[0], source[1], source[2], source[3], likeValue));
    }
    for (String[] source : HTML_BODY_SOURCES) {
      usages.addAll(queryUsages(source[0], source[1], source[2], source[3], likeValue));
    }
    usages.addAll(querySitePropertyUsages(likeValue));
    return usages;
  }

  /**
   * Convenience for the "Orphaned" badge: true when nothing references the image.
   */
  public static boolean isOrphaned(Image image) {
    return findUsages(image).isEmpty();
  }

  private static List<UsageReference> queryUsages(String table, String column, String labelColumn,
      String sourceType, String likeValue) {
    List<UsageReference> results = new ArrayList<>();
    String sql = "SELECT " + labelColumn + " FROM " + table + " WHERE " + column + " LIKE ?";
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql)) {
      pst.setString(1, likeValue);
      try (ResultSet rs = pst.executeQuery()) {
        while (rs.next()) {
          results.add(new UsageReference(sourceType, rs.getString(1)));
        }
      }
    } catch (SQLException se) {
      // A usage scan failing must never look like "this image is safe to delete" -- but it also
      // must not break the page. Log and treat this one source as having found nothing.
      LOG.warn("Usage scan failed for " + table + "." + column + ": " + se.getMessage());
    }
    return results;
  }

  /**
   * Site-wide image settings -- filtered to {@code property_type = 'image'} so this automatically
   * covers every current and future image-typed property (currently {@code site.logo},
   * {@code site.logo.white}, {@code site.logo.mixed}, {@code site.image}) without hardcoding their
   * names, and so a coincidental substring match in an unrelated text/url-typed property never
   * counts as a usage.
   */
  private static List<UsageReference> querySitePropertyUsages(String likeValue) {
    List<UsageReference> results = new ArrayList<>();
    String sql = "SELECT COALESCE(NULLIF(property_label, ''), property_name) FROM site_properties "
        + "WHERE property_type = 'image' AND property_value LIKE ?";
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql)) {
      pst.setString(1, likeValue);
      try (ResultSet rs = pst.executeQuery()) {
        while (rs.next()) {
          results.add(new UsageReference("Site Setting", rs.getString(1)));
        }
      }
    } catch (SQLException se) {
      LOG.warn("Usage scan failed for site_properties.property_value: " + se.getMessage());
    }
    return results;
  }

}
