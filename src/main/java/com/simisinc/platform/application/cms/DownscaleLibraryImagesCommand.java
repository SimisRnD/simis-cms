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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.StringEscapeUtils;

import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageTag;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageTagMapRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageTagRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

/**
 * Replaces oversized images already in the library with resized copies, page by page.
 *
 * <p>{@link DownscaleImageCommand} shrinks new uploads; this covers the images that arrived before
 * it. An admin starts it from /admin/images, and it never runs on its own. The report comes first:
 * every candidate, its current and new size, and every place it is referenced, computed without
 * changing anything. Only a separate, explicit apply does the work, one background job per image:
 * <ol>
 * <li>the resized bytes become a new library entry, with the original's alt text, focal point and
 * tags;</li>
 * <li>references to the original in content blocks, blog post bodies and page XML (each including
 * drafts) are rewritten to point at the new entry.</li>
 * </ol>
 * The original record and its file are never changed or deleted. A reference anywhere else (a page's
 * share image, a blog post's banner, a site setting, a stylesheet) keeps pointing at the original and
 * is listed in the report as such.
 *
 * <p>References are matched on the id-bearing part of the URL, {@code /assets/img/{webPath}-{id}/},
 * because the filename after it is spelled more than one way in stored content: page XML holds both
 * {@code My%20Image.png} and {@code My Image.png}. Each known spelling is replaced whole; an
 * unrecognized one keeps its filename and gets the new prefix, which still serves the new image,
 * since the image servlet reads only the id.
 *
 * @author SimIS Inc.
 */
public class DownscaleLibraryImagesCommand {

  private static final Log LOG = LogFactory.getLog(DownscaleLibraryImagesCommand.class);

  /** Smaller files are not worth a second library entry each. */
  public static final long MIN_FILE_LENGTH = 300L * 1024;

  private static final String IMAGE_PATH = "/assets/img/";

  /**
   * The columns this rewrites. Table and column names are fixed, developer-supplied constants, so
   * building them into SQL text is safe; every value is bound.
   */
  public enum Source {
    CONTENT("content", "content", "content_id", "content_unique_id", "Content Block"),
    CONTENT_DRAFT("content", "draft_content", "content_id", "content_unique_id", "Content Block (draft)"),
    BLOG_POST_BODY("blog_posts", "body", "post_id", "title", "Blog Post"),
    PAGE_XML("web_pages", "page_xml", "web_page_id", "link", "Web Page"),
    PAGE_XML_DRAFT("web_pages", "draft_page_xml", "web_page_id", "link", "Web Page (draft)");

    private final String table;
    private final String column;
    private final String idColumn;
    private final String labelColumn;
    private final String sourceType;

    Source(String table, String column, String idColumn, String labelColumn, String sourceType) {
      this.table = table;
      this.column = column;
      this.idColumn = idColumn;
      this.labelColumn = labelColumn;
      this.sourceType = sourceType;
    }

    public String getSourceType() {
      return sourceType;
    }

    boolean matches(String otherTable, String otherColumn) {
      return table.equals(otherTable) && column.equals(otherColumn);
    }
  }

  /**
   * How many times each spelling of the filename appears after the image's URL prefix.
   *
   * @param encoded as {@link Image#getUrl()} writes it, e.g. {@code My%20Image.png}
   * @param literal with literal spaces or other unencoded characters, e.g. {@code My Image.png}
   * @param other a filename that is neither; only the prefix will be replaced
   */
  public record Spellings(int encoded, int literal, int other) {

    public int getTotal() {
      return encoded + literal + other;
    }

    public int getEncoded() {
      return encoded;
    }

    public int getLiteral() {
      return literal;
    }

    public int getOther() {
      return other;
    }
  }

  /** One row that will be rewritten. */
  public record Reference(Source source, long rowId, String label, Spellings spellings) {

    public String getSourceType() {
      return source.getSourceType();
    }

    public String getLabel() {
      return label;
    }

    public Spellings getSpellings() {
      return spellings;
    }
  }

  /**
   * One oversized image, where it is used, and whether it will be replaced.
   *
   * @param skipReason why the apply will leave this image alone, or null when it will replace it;
   *                   decided without decoding, see {@link #preview} for the rest
   */
  public record Candidate(Image image, List<Reference> rewritable, List<ImageUsageCommand.UsageReference> elsewhere,
      String skipReason) {

    public Image getImage() {
      return image;
    }

    public List<Reference> getRewritable() {
      return rewritable;
    }

    public List<ImageUsageCommand.UsageReference> getElsewhere() {
      return elsewhere;
    }

    public String getSkipReason() {
      return skipReason;
    }
  }

  private DownscaleLibraryImagesCommand() {
    // Static utility, not instantiated
  }

  /** The images the downscale would change: over {@link #MIN_FILE_LENGTH}, wider than the limit, PNG or JPEG. */
  public static boolean isOversized(Image image) {
    return image != null
        && image.getFileLength() > MIN_FILE_LENGTH
        && image.getWidth() > DownscaleImageCommand.MAX_WIDTH
        && ("image/png".equals(image.getFileType()) || "image/jpeg".equals(image.getFileType()));
  }

  /** Every oversized image, largest file first, with where each one is used. */
  public static List<Candidate> findCandidates() {
    List<Candidate> candidates = new ArrayList<>();
    for (Image image : ImageRepository.findAllLargerThan(MIN_FILE_LENGTH)) {
      if (isOversized(image)) {
        candidates.add(describe(image));
      }
    }
    return candidates;
  }

  /**
   * Images over {@link #MIN_FILE_LENGTH} that are not candidates -- not wider than the limit, or a
   * format the downscale leaves alone -- so the report can say why they stay.
   */
  public static List<Image> findLargeButNotOversized() {
    List<Image> images = new ArrayList<>();
    for (Image image : ImageRepository.findAllLargerThan(MIN_FILE_LENGTH)) {
      if (!isOversized(image)) {
        images.add(image);
      }
    }
    return images;
  }

  /** Where one image is used, split into what the apply rewrites and what keeps the original. */
  public static Candidate describe(Image image) {
    List<Reference> rewritable = findRewritableReferences(image);
    List<ImageUsageCommand.UsageReference> elsewhere = new ArrayList<>();
    for (ImageUsageCommand.UsageReference usage : ImageUsageCommand.findUsages(image)) {
      if (!isRewritable(usage)) {
        elsewhere.add(usage);
      }
    }
    String skipReason = null;
    if (rewritable.isEmpty()) {
      skipReason = elsewhere.isEmpty()
          ? "Not used on any page, so there is nothing to switch"
          : "Used only in places this does not update";
    }
    return new Candidate(image, rewritable, elsewhere, skipReason);
  }

  private static boolean isRewritable(ImageUsageCommand.UsageReference usage) {
    for (Source source : Source.values()) {
      if (source.matches(usage.getTable(), usage.getColumn())) {
        return true;
      }
    }
    return false;
  }

  /**
   * What the resized copy would be, computed in memory without writing anything.
   *
   * @return the resized image, or null when the downscale would keep the original as it is
   */
  public static DownscaleImageCommand.Downscaled preview(Image image) {
    File file = FileSystemCommand.resolveWithinRoot(FileSystemCommand.getFileServerRootPath(), image.getFileServerPath());
    if (file == null || !file.isFile()) {
      return null;
    }
    return DownscaleImageCommand.downscaleIfTooWide(file);
  }

  /** The URL prefix that identifies this image wherever it is referenced. */
  static String urlPrefix(Image image) {
    return IMAGE_PATH + image.getWebPath() + "-" + image.getId() + "/";
  }

  /**
   * The ways an image's filename is written after its prefix, longest first so a shorter spelling
   * can never claim part of a longer one.
   */
  static List<String> filenameSpellings(String filename) {
    Set<String> spellings = new LinkedHashSet<>();
    spellings.add(UrlCommand.encodeUri(filename));
    // Markup escapes "&" in a literal filename as "&amp;"
    spellings.add(StringEscapeUtils.escapeXml11(filename));
    spellings.add(filename);
    List<String> sorted = new ArrayList<>(spellings);
    sorted.sort((a, b) -> b.length() - a.length());
    return sorted;
  }

  /** Counts how each reference to {@code image} in {@code text} spells its filename. */
  static Spellings countSpellings(String text, Image image) {
    int encoded = 0;
    int literal = 0;
    int other = 0;
    if (StringUtils.isEmpty(text)) {
      return new Spellings(0, 0, 0);
    }
    String prefix = urlPrefix(image);
    String encodedSpelling = UrlCommand.encodeUri(image.getFilename());
    List<String> spellings = filenameSpellings(image.getFilename());
    int from = 0;
    int found;
    while ((found = text.indexOf(prefix, from)) >= 0) {
      int after = found + prefix.length();
      String matched = matchSpelling(text, after, spellings);
      if (matched == null) {
        other++;
      } else if (matched.equals(encodedSpelling)) {
        encoded++;
      } else {
        literal++;
      }
      from = after;
    }
    return new Spellings(encoded, literal, other);
  }

  /**
   * Points every reference to {@code from} in {@code text} at {@code to}. A recognized filename is
   * replaced along with the prefix, written the way {@link Image#getUrl()} writes it, which has no
   * character that needs escaping in markup. Anything after the filename, such as
   * {@code ?variant=large}, is kept.
   *
   * @return the rewritten text, or the same text when there was nothing to rewrite
   */
  static String rewrite(String text, Image from, Image to) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }
    String prefix = urlPrefix(from);
    int found = text.indexOf(prefix);
    if (found < 0) {
      return text;
    }
    String newPrefix = urlPrefix(to);
    String newFilename = UrlCommand.encodeUri(to.getFilename());
    List<String> spellings = filenameSpellings(from.getFilename());
    StringBuilder rewritten = new StringBuilder(text.length());
    int copiedUpTo = 0;
    while (found >= 0) {
      int after = found + prefix.length();
      String matched = matchSpelling(text, after, spellings);
      rewritten.append(text, copiedUpTo, found).append(newPrefix);
      if (matched != null) {
        rewritten.append(newFilename);
        copiedUpTo = after + matched.length();
      } else {
        copiedUpTo = after;
      }
      found = text.indexOf(prefix, copiedUpTo);
    }
    rewritten.append(text, copiedUpTo, text.length());
    return rewritten.toString();
  }

  /**
   * The spelling that sits at {@code index}, provided it ends the filename: a character that can
   * follow a URL in markup, or the end of the text. Without that check "photo.png" would match the
   * start of a different, longer name such as "photo.png.bak".
   */
  private static String matchSpelling(String text, int index, List<String> spellings) {
    for (String spelling : spellings) {
      if (text.startsWith(spelling, index)) {
        int end = index + spelling.length();
        if (end == text.length() || "\"'?#)<>&;, \t\r\n]".indexOf(text.charAt(end)) >= 0) {
          return spelling;
        }
      }
    }
    return null;
  }

  /** Every row in the rewritten columns that references the image, with how each spells it. */
  static List<Reference> findRewritableReferences(Image image) {
    List<Reference> references = new ArrayList<>();
    String likeValue = "%" + urlPrefix(image) + "%";
    for (Source source : Source.values()) {
      String sql = "SELECT " + source.idColumn + ", " + source.labelColumn + ", " + source.column
          + " FROM " + source.table + " WHERE " + source.column + " LIKE ? ORDER BY " + source.idColumn;
      try (Connection connection = DB.getConnection();
          PreparedStatement pst = connection.prepareStatement(sql)) {
        pst.setString(1, likeValue);
        try (ResultSet rs = pst.executeQuery()) {
          while (rs.next()) {
            Spellings spellings = countSpellings(rs.getString(3), image);
            if (spellings.getTotal() > 0) {
              references.add(new Reference(source, rs.getLong(1), rs.getString(2), spellings));
            }
          }
        }
      } catch (SQLException se) {
        LOG.warn("Reference scan failed for " + source.table + "." + source.column + ": " + se.getMessage());
      }
    }
    return references;
  }

  /**
   * Replaces one oversized image: adds the resized copy to the library and points every rewritable
   * reference at it. Runs from {@code DownscaleLibraryImageJob}. Everything is re-checked here, since
   * the library and the pages can change between the report and the job.
   *
   * @return a one-line outcome for the job log
   */
  public static String apply(long imageId, long userId) {
    Image original = ImageRepository.findById(imageId);
    if (original == null) {
      return "Image " + imageId + " no longer exists";
    }
    if (!isOversized(original)) {
      return "Image " + imageId + " is no longer oversized";
    }
    List<Reference> references = findRewritableReferences(original);
    if (references.isEmpty()) {
      // Also what makes a second run harmless: a replaced image has no references left to switch
      return "Image " + imageId + " has no references to switch";
    }
    DownscaleImageCommand.Downscaled downscaled = preview(original);
    if (downscaled == null) {
      return "Image " + imageId + " would not come out smaller; kept as it is";
    }

    Image copy;
    Path temporary = null;
    try {
      temporary = Files.createTempFile("downscale-" + imageId + "-", "." + downscaled.extension());
      Files.write(temporary, downscaled.bytes());
      String filename = original.getFilename();
      if (!downscaled.fileType().equals(original.getFileType())) {
        filename = FilenameUtils.removeExtension(filename) + "." + downscaled.extension();
      }
      copy = AddImageToLibraryCommand.addFromFile(temporary.toFile(), filename, userId, original);
    } catch (Exception e) {
      LOG.error("Could not add the resized copy of image " + imageId, e);
      return "Image " + imageId + " could not be copied: " + e.getMessage();
    } finally {
      deleteTemporary(temporary);
    }
    copyTags(original, copy);

    List<String> rewritten = new ArrayList<>();
    List<String> changedMeanwhile = new ArrayList<>();
    for (Reference reference : references) {
      String place = reference.getSourceType() + " " + reference.getLabel();
      if (rewriteRow(reference, original, copy)) {
        rewritten.add(place);
        LOG.info("Image " + original.getId() + " -> " + copy.getId() + ": updated " + place);
      } else {
        changedMeanwhile.add(place);
      }
    }

    String details = "Replaced image " + original.getId() + " (" + original.getFileLength() + " bytes, "
        + original.getWidth() + "x" + original.getHeight() + ") with image " + copy.getId() + " ("
        + copy.getFileLength() + " bytes, " + copy.getWidth() + "x" + copy.getHeight() + ") in: "
        + String.join("; ", rewritten)
        + (changedMeanwhile.isEmpty() ? "" : ". Not updated, edited since the scan: " + String.join("; ", changedMeanwhile));
    // Literal outcome values: the presentation layer's AuditEventCommand constants are not visible here
    SaveAuditEventCommand.recordAdminEvent("content", "image.downscaleLibrary", rewritten.isEmpty() ? "failure" : "success",
        userId, null, null, null, "image", String.valueOf(original.getId()), original.getFilename(), details);
    return details;
  }

  private static void copyTags(Image original, Image copy) {
    Map<Long, List<ImageTag>> tags = ImageTagRepository.findByImageIds(List.of(original.getId()));
    List<ImageTag> originalTags = tags.get(original.getId());
    if (originalTags == null || originalTags.isEmpty()) {
      return;
    }
    try (Connection connection = DB.getConnection()) {
      for (ImageTag tag : originalTags) {
        ImageTagMapRepository.insertImageTagId(connection, copy, tag.getId());
      }
    } catch (SQLException se) {
      // The copy is still correct without its tags; they are a library filter, not page content
      LOG.warn("Could not copy the tags of image " + original.getId() + " to " + copy.getId() + ": " + se.getMessage());
    }
  }

  /**
   * Rewrites one row, only if it still holds what was just read: a row edited between the read and
   * the write is left as the editor saved it rather than overwritten.
   */
  private static boolean rewriteRow(Reference reference, Image original, Image copy) {
    Source source = reference.source();
    String select = "SELECT " + source.column + " FROM " + source.table + " WHERE " + source.idColumn + " = ?";
    String update = "UPDATE " + source.table + " SET " + source.column + " = ? WHERE " + source.idColumn
        + " = ? AND " + source.column + " = ?";
    try (Connection connection = DB.getConnection()) {
      String current;
      try (PreparedStatement pst = connection.prepareStatement(select)) {
        pst.setLong(1, reference.rowId());
        try (ResultSet rs = pst.executeQuery()) {
          if (!rs.next()) {
            return false;
          }
          current = rs.getString(1);
        }
      }
      String replacement = rewrite(current, original, copy);
      if (replacement == null || replacement.equals(current)) {
        return false;
      }
      int updated;
      try (PreparedStatement pst = connection.prepareStatement(update)) {
        pst.setString(1, replacement);
        pst.setLong(2, reference.rowId());
        pst.setString(3, current);
        updated = pst.executeUpdate();
      }
      if (updated != 1) {
        return false;
      }
    } catch (SQLException se) {
      LOG.error("Could not update " + source.table + "." + source.column + " row " + reference.rowId(), se);
      return false;
    }
    // The modified timestamp is left alone on purpose: the page reads the same, and a bumped date
    // would tell search engines (through the sitemap) that hundreds of pages changed
    switch (source) {
      case CONTENT:
      case CONTENT_DRAFT:
        CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, reference.getLabel());
        break;
      case PAGE_XML:
      case PAGE_XML_DRAFT:
        WebPageRepository.invalidateCachedPage(reference.getLabel());
        break;
      default:
        // Blog posts are read from the database on every request
        break;
    }
    return true;
  }

  private static void deleteTemporary(Path temporary) {
    if (temporary == null) {
      return;
    }
    try {
      Files.deleteIfExists(temporary);
    } catch (Exception e) {
      LOG.warn("Could not remove " + temporary + ": " + e.getMessage());
    }
  }
}
