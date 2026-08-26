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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.domain.model.cms.Image;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;

/**
 * Builds an {@code <img>} {@code srcset} attribute value from an image's generated variants (issue
 * #411), so a page can serve a file close to how it's actually displayed instead of always shipping
 * the full-resolution original. Backs the {@code image:srcset}/{@code image:srcsetBatch} EL functions
 * (see {@code image-functions.tld}) used directly from JSPs.
 *
 * @author SimIS Inc.
 */
public class ImageCommand {

  private static Log LOG = LogFactory.getLog(ImageCommand.class);

  private static final String IMAGE_PATH_PREFIX = "/assets/img/";

  private ImageCommand() {
    // Static utility, not instantiated
  }

  /**
   * Parses the numeric image id out of the tail of a {@code /assets/img/{webPath}-{id}/{filename}}
   * path -- the same shape {@code Image.getUrl()} produces and {@code StreamImageWidget} already
   * parses back out of the request URI. Returns {@code null} for anything that isn't this exact
   * internal shape: an entity's {@code imageUrl} field is also allowed to hold an admin-typed
   * external {@code http(s)} URL (confirmed real in {@code SaveItemCommand}, site properties, and
   * {@code ProductBrowserWidget}'s image-map override), and that must simply produce no srcset, not
   * throw.
   *
   * @param imageUrl a value from an entity's imageUrl-style field, or a site property
   * @return the image id, or null if imageUrl isn't an internal /assets/img/ path
   */
  public static Long parseImageId(String imageUrl) {
    if (StringUtils.isBlank(imageUrl) || !imageUrl.startsWith(IMAGE_PATH_PREFIX)) {
      return null;
    }
    int startIdx = IMAGE_PATH_PREFIX.length();
    int slashIdx = imageUrl.indexOf('/', startIdx);
    int queryIdx = imageUrl.indexOf('?', startIdx);
    int endIdx = imageUrl.length();
    if (slashIdx > -1) {
      endIdx = slashIdx;
    }
    if (queryIdx > -1 && queryIdx < endIdx) {
      endIdx = queryIdx;
    }
    if (endIdx <= startIdx) {
      return null;
    }
    // resourceValue is "{webPath}-{id}" -- lastIndexOf, not indexOf, so a dash inside webPath
    // itself never confuses the split; the filename (which may itself contain dashes) was already
    // excluded above by cutting at the first '/' past the prefix.
    String resourceValue = imageUrl.substring(startIdx, endIdx);
    int dashIdx = resourceValue.lastIndexOf('-');
    if (dashIdx == -1 || dashIdx == resourceValue.length() - 1) {
      return null;
    }
    try {
      long imageId = Long.parseLong(resourceValue.substring(dashIdx + 1));
      return imageId > 0 ? imageId : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Builds the srcset value from whichever variants actually exist -- never assume thumbnail/medium/
   * large are all present (variant generation skips a size when the original is already smaller).
   * Deliberately does not add the original as an extra candidate: that would need a second DB call
   * just for its width, and "large" (PR1's ~1600px ceiling) is already an adequate upper bound for
   * every layout in this codebase.
   *
   * @param imageUrl the exact string every rendered &lt;img&gt; already uses for its src -- reused
   *                 as-is for each variant candidate, just with ?variant= appended
   * @param variants the image's existing variants, in any order
   * @return a ready-to-use srcset value, or "" if there's nothing usable
   */
  static String buildSrcset(String imageUrl, List<ImageVariant> variants) {
    return buildSrcset(imageUrl, variants, 0);
  }

  /**
   * As above, but also offers the original file as a candidate at {@code originalWidth} (issue
   * #1370).
   *
   * <p>Without it the list contains variants only, and GenerateImageVariantsCommand deliberately
   * skips any variant that would not be smaller than the original -- so a 626px upload yields a
   * 200w thumbnail and nothing else. A srcset carrying w descriptors is authoritative: the browser
   * chooses from those candidates and treats {@code src} only as a fallback for browsers that do
   * not understand srcset. The 200px thumbnail was therefore being stretched across far larger
   * slots while the full-size file sat unused on disk.
   *
   * @param originalWidth the original's pixel width, or 0 when it is not known -- in which case
   *                      this behaves exactly as before and offers variants only
   */
  static String buildSrcset(String imageUrl, List<ImageVariant> variants, int originalWidth) {
    boolean hasVariants = variants != null && !variants.isEmpty();
    if (!hasVariants && originalWidth <= 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (ImageVariant variant : hasVariants ? variants : java.util.Collections.<ImageVariant>emptyList()) {
      // width <= 0 shouldn't happen (NOT NULL column, always set by GenerateImageVariantsCommand)
      // but a malformed/partial row must never produce a bogus "0w" descriptor.
      if (variant == null || variant.getWidth() <= 0) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(imageUrl).append("?variant=").append(variant.getVariantType())
          .append(" ").append(variant.getWidth()).append("w");
    }
    // The original goes in last, with no ?variant= suffix -- it is the same url the <img> already
    // uses for src, so no new route is needed and browsers that ignore srcset are unaffected.
    if (originalWidth > 0) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(imageUrl).append(" ").append(originalWidth).append("w");
    }
    return sb.toString();
  }

  /**
   * Self-contained srcset lookup for one-off render sites (one DB call). For a list/grid widget
   * rendering many images per page, prefer {@link #srcsetBatch} with a pre-fetched map instead.
   *
   * @return a ready-to-use srcset value, or "" (never null) if imageUrl isn't an internal image
   *         path or no variants exist yet
   */
  /**
   * The image's focal point as a CSS {@code object-position} value, or null when it sits at the
   * default centre.
   *
   * The focal point is stored as a 0-100 percentage of width and height, which is exactly what
   * object-position takes, so a cropped image can be told which part to keep. Returning null for an
   * untouched image is deliberate: the caller then emits no style at all, so markup on a site where
   * nobody has set a focal point is byte-identical to before and nothing can shift.
   *
   * Values are clamped to 0-100. The column is NOT NULL with a 50.00 default, but a null is treated
   * as centre rather than trusted into a stylesheet.
   */
  public static String objectPositionFor(Image image) {
    if (image == null) {
      return null;
    }
    BigDecimal x = clampPercent(image.getFocalX());
    BigDecimal y = clampPercent(image.getFocalY());
    if (x.compareTo(FIFTY) == 0 && y.compareTo(FIFTY) == 0) {
      return null;
    }
    return trimPercent(x) + "% " + trimPercent(y) + "%";
  }

  private static final BigDecimal FIFTY = new BigDecimal("50.00");
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private static BigDecimal clampPercent(BigDecimal value) {
    if (value == null) {
      return FIFTY;
    }
    if (value.compareTo(BigDecimal.ZERO) < 0) {
      return BigDecimal.ZERO;
    }
    if (value.compareTo(HUNDRED) > 0) {
      return HUNDRED;
    }
    return value;
  }

  /** 50.00 -> "50", 33.50 -> "33.5" -- shorter markup, identical rendering. */
  private static String trimPercent(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  public static String srcset(String imageUrl) {
    Long imageId = parseImageId(imageUrl);
    if (imageId == null) {
      return "";
    }
    // Two queries rather than one: the variants, plus the original's width so it can be offered as
    // a candidate (issue #1370). Batch render sites should use srcsetBatch, which takes both maps
    // pre-fetched and issues none.
    // Defensive: the original's width is an enhancement, not a precondition. If this lookup fails
    // the srcset must still be built from the variants, exactly as before #1370 -- otherwise one
    // failing query silently removes srcset from the page, since ContentImageSrcsetCommand catches
    // and leaves content unchanged.
    int originalWidth = 0;
    try {
      Image image = ImageRepository.findById(imageId);
      if (image != null) {
        originalWidth = image.getWidth();
      }
    } catch (Exception e) {
      LOG.debug("Could not resolve original width for srcset: " + imageUrl, e);
    }
    return buildSrcset(imageUrl, ImageVariantRepository.findByImageId(imageId), originalWidth);
  }

  /**
   * Same as {@link #srcset}, but against a map a widget already batch-fetched via
   * {@link ImageVariantRepository#findByImageIds} -- zero DB calls per call, for use inside a JSP
   * loop rendering many images on one page.
   *
   * @return a ready-to-use srcset value, or "" (never null)
   */
  public static String srcsetBatch(String imageUrl, Map<Long, List<ImageVariant>> variantsByImageId) {
    return srcsetBatch(imageUrl, variantsByImageId, null);
  }

  /**
   * As above, but also offers the original as a candidate using a widths map the widget batch
   * fetched via {@link com.simisinc.platform.infrastructure.persistence.cms.ImageRepository#findWidthsByIds}
   * (issue #1370). Still zero DB calls per invocation.
   *
   * <p>The widths arrive as a separate map rather than being folded into the variants list on
   * purpose: {@code DeleteImageCommand} iterates those lists to delete files from disk, so a
   * synthetic entry standing for the original would make it delete the original itself.
   *
   * @param widthsByImageId imageId to original width, or null to offer variants only
   */
  public static String srcsetBatch(String imageUrl, Map<Long, List<ImageVariant>> variantsByImageId,
      Map<Long, Integer> widthsByImageId) {
    Long imageId = parseImageId(imageUrl);
    if (imageId == null) {
      return "";
    }
    List<ImageVariant> variants = variantsByImageId != null ? variantsByImageId.get(imageId) : null;
    Integer originalWidth = widthsByImageId != null ? widthsByImageId.get(imageId) : null;
    return buildSrcset(imageUrl, variants, originalWidth != null ? originalWidth : 0);
  }
}
