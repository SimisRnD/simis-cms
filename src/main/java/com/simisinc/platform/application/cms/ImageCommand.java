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

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.cms.ImageVariant;
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
    if (variants == null || variants.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (ImageVariant variant : variants) {
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
    return sb.toString();
  }

  /**
   * Self-contained srcset lookup for one-off render sites (one DB call). For a list/grid widget
   * rendering many images per page, prefer {@link #srcsetBatch} with a pre-fetched map instead.
   *
   * @return a ready-to-use srcset value, or "" (never null) if imageUrl isn't an internal image
   *         path or no variants exist yet
   */
  public static String srcset(String imageUrl) {
    Long imageId = parseImageId(imageUrl);
    if (imageId == null) {
      return "";
    }
    return buildSrcset(imageUrl, ImageVariantRepository.findByImageId(imageId));
  }

  /**
   * Same as {@link #srcset}, but against a map a widget already batch-fetched via
   * {@link ImageVariantRepository#findByImageIds} -- zero DB calls per call, for use inside a JSP
   * loop rendering many images on one page.
   *
   * @return a ready-to-use srcset value, or "" (never null)
   */
  public static String srcsetBatch(String imageUrl, Map<Long, List<ImageVariant>> variantsByImageId) {
    Long imageId = parseImageId(imageUrl);
    if (imageId == null || variantsByImageId == null) {
      return "";
    }
    return buildSrcset(imageUrl, variantsByImageId.get(imageId));
  }
}
