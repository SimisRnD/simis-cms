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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.application.cms.ImageCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

/**
 * A general-purpose drop-on-a-page widget whose {@code imageUrl} preference is the sole source
 * of what it renders -- unlike the ecommerce/collection/blog widgets, which always resolve an
 * &lt;img&gt; through a domain-entity reference (product, item, post) rather than a directly
 * editable preference (issue #772).
 *
 * <p>{@code imageUrl} is attacker-reachable content: it is written by
 * {@link com.simisinc.platform.application.cms.MutateLayoutCommand#setWidgetPreferences} /
 * {@code #addWidget} (the visual editor's free-form preference-save path, and the Media Library's
 * click-to-replace, both reached from PageServlet/MediaApiController) and persisted into the
 * page's draft XML. {@link #isValidImageUrl(String)} is the save-path gate -- it must reject an
 * unsafe value there, before it is ever written. The {@link UrlCommand#sanitizeUrl} call in
 * {@link #execute} below is defense in depth only, in case bad data reaches render some other way.
 *
 * @author elizabeth houser
 * @created 7/31/2026
 */
public class ImageWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/image.jsp";

  /**
   * This widget's registered name in widget-library.xml. Used by MutateLayoutCommand to know when
   * an "imageUrl" preference value belongs to this widget and needs {@link #isValidImageUrl}, and by
   * MediaApiController to confirm a widget-update request's target is really an image widget before
   * honoring the client-supplied prefKey (issue #772 follow-up).
   */
  public static final String WIDGET_NAME = "image";

  /**
   * The name of this widget's sole render-source preference. MediaApiController's widget-update
   * endpoint (the Media Library's click-to-replace) exists only to set this preference on a widget
   * confirmed to be {@link #WIDGET_NAME} -- it rejects any other prefKey, since nothing else this
   * widget owns should ever be reachable through that path.
   */
  public static final String IMAGE_URL_PREF_KEY = "imageUrl";

  /**
   * {@code imageUrl} is rendered straight into an &lt;img src&gt; attribute (see {@link #execute}),
   * so a value that would break out of that attribute or introduce an active scheme (javascript:/
   * data:) must never be persisted -- not merely dropped at render time. An unset/blank value is
   * valid: it simply means no image has been chosen yet, and the widget renders its placeholder.
   *
   * @return true if {@code url} is blank, or is a safe site-relative path / http(s)/mailto/tel
   *     absolute url per {@link UrlCommand#sanitizeUrl(String)}
   */
  public static boolean isValidImageUrl(String url) {
    return StringUtils.isBlank(url) || UrlCommand.sanitizeUrl(url) != null;
  }

  public WidgetContext execute(WidgetContext context) {

    // The value is rendered straight into a src attribute, so sanitize it the same way the other
    // widgets that render an editor-supplied url into src/href do (ButtonWidget's link,
    // RemoteContentWidget's image url) -- this rejects attribute-breakout characters and any
    // scheme other than http(s)/mailto/tel, while still allowing an ordinary site-relative path
    // (e.g. a Media Library asset's storage path). An unset or unsafe value simply falls through
    // to the placeholder rendering in the JSP -- never a broken <img> tag.
    String imageUrl = UrlCommand.sanitizeUrl(context.getPreferences().get(IMAGE_URL_PREF_KEY));
    context.getRequest().setAttribute("imageUrl", imageUrl);

    // Always provide a String (never null) so the placeholder branch can tell an intentionally
    // decorative image (alt="") apart from one an author simply hasn't described yet, per this
    // codebase's existing accessibility convention (see ContentAccessibilityCommand) of only
    // flagging a genuinely *missing* alt attribute, not an empty one.
    String altText = StringUtils.defaultString(context.getPreferences().get("altText"));
    // Fall back to the description the author saved against the image itself in the media library.
    // Without this the stored alt_text is written and never read anywhere -- an author who
    // describes an image once, expecting the description to travel with it, gets nothing
    // (issue #1367). The preference still wins when set, since a per-placement description beats a
    // global one, and an image with neither still renders alt="" -- which keeps the decorative
    // convention above intact: leaving both blank is how an author marks an image decorative.
    if (StringUtils.isBlank(altText)) {
      altText = StringUtils.defaultString(lookupStoredAltText(imageUrl));
    }
    context.getRequest().setAttribute("altText", altText);

    context.setJsp(JSP);
    return context;
  }

  /**
   * The alt text saved against an internal image record, or null when the url is not an internal
   * image, the record is gone, or no description was ever set.
   */
  private static String lookupStoredAltText(String imageUrl) {
    Long imageId = ImageCommand.parseImageId(imageUrl);
    if (imageId == null) {
      return null;
    }
    Image image = ImageRepository.findById(imageId);
    return image != null ? image.getAltText() : null;
  }
}
