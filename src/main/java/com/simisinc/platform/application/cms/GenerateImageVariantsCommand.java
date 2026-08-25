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

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ImageVariantRepository;

/**
 * Generates resized renditions of an uploaded image using ImageMagick (via im4java), so a page
 * can serve a file sized close to how it's actually displayed instead of the full-resolution
 * original (issue #411).
 *
 * <p>
 * Runs from {@code ImageVariantJob} (a JobRunr background job), not inline with the upload
 * request -- shelling out to an external process on every upload would make upload response time
 * depend on ImageMagick's speed. Requires the {@code imagemagick} package's {@code convert} binary
 * to be on the container's PATH (see {@code docker/app/Dockerfile}); im4java is only a thin Java
 * wrapper around that external process.
 * </p>
 *
 * @author SimIS Inc.
 */
public class GenerateImageVariantsCommand {

  private static final Log LOG = LogFactory.getLog(GenerateImageVariantsCommand.class);

  public static final String THUMBNAIL = "thumbnail";
  public static final String SMALL = "small";
  public static final String MEDIUM = "medium";
  public static final String LARGE = "large";
  public static final String SQUARE = "square";

  // A card-thumbnail-ish size; nothing consumes this variant at a specific size yet, so it's an
  // easy constant to retune once more callers exist.
  /** Exposed so the #1422 backfill can ask for exactly the rung it is filling. */
  public static final int SMALL_MAX_DIMENSION = 400;

  private static final int SQUARE_DIMENSION = 400;

  /** Max dimension (the longer side) each variant is resized to fit within, aspect-preserved. */
  private static final Map<String, Integer> VARIANT_MAX_DIMENSION = new LinkedHashMap<>();
  static {
    VARIANT_MAX_DIMENSION.put(THUMBNAIL, 200);
    // 400 closes a 4x hole between the thumbnail and the original (issue #1422). Because variants
    // are shrink-only, an original narrower than 800px produced no medium and no large, leaving a
    // srcset of just "200w, original". A 251px slot cannot use the 200w thumbnail, so the browser
    // correctly fell through to the full original -- the candidate list was the problem, not the
    // picker. On the SimIS library 98 of 305 images (32%) sat in that 201-799px band.
    VARIANT_MAX_DIMENSION.put(SMALL, SMALL_MAX_DIMENSION);
    VARIANT_MAX_DIMENSION.put(MEDIUM, 800);
    VARIANT_MAX_DIMENSION.put(LARGE, 1600);
  }

  // ImageMagick can technically decode many more formats, but an uploaded file only reaches here
  // after ValidateImageCommand's MIME sniff -- restricting to the formats a browser actually
  // displays keeps the external-process attack surface to exactly what's needed. SVG is
  // deliberately excluded: it is already resolution-independent and gains nothing from a raster
  // resize.
  private static final Set<String> SUPPORTED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

  private GenerateImageVariantsCommand() {
    // Static utility, not instantiated
  }

  /**
   * Generates every variant that makes sense for this image (skipping any whose target size is
   * not smaller than the original, so a variant is never an upscaled or byte-identical duplicate)
   * and persists a row for each one that was actually written.
   *
   * @param image a previously-saved image record
   * @return the variants that were generated and saved; empty when the image's format is
   *         unsupported or every variant would be a no-op resize
   */
  public static List<ImageVariant> generateVariants(Image image) {
    List<ImageVariant> generated = new ArrayList<>();
    if (image == null || image.getId() == null || image.getId() == -1) {
      return generated;
    }
    if (!SUPPORTED_MIME_TYPES.contains(image.getFileType())) {
      LOG.debug("Skipping variant generation for unsupported type: " + image.getFileType());
      return generated;
    }

    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File originalFile = FileSystemCommand.resolveWithinRoot(serverRootPath, image.getFileServerPath());
    if (originalFile == null || !originalFile.isFile()) {
      LOG.warn("Original image file not found for variant generation: " + image.getId());
      return generated;
    }

    for (Map.Entry<String, Integer> entry : VARIANT_MAX_DIMENSION.entrySet()) {
      String variantType = entry.getKey();
      int maxDimension = entry.getValue();
      if (image.getWidth() <= maxDimension && image.getHeight() <= maxDimension) {
        // The original is already at or below this variant's target size -- resizing would
        // either upscale (im4java's ">" flag already refuses this) or duplicate the original.
        continue;
      }
      try {
        ImageVariant variant = generateOneVariant(image, originalFile, variantType, maxDimension);
        if (variant != null) {
          generated.add(variant);
        }
      } catch (Exception e) {
        // One variant failing (e.g. a policy.xml restriction) must not stop the others.
        LOG.error("Could not generate the " + variantType + " variant for image " + image.getId(), e);
      }
    }
    return generated;
  }

  private static ImageVariant generateOneVariant(Image image, File originalFile, String variantType, int maxDimension)
      throws Exception {
    VariantPath variantPath = resolveVariantPath(image, variantType);
    if (variantPath == null) {
      return null;
    }
    if (!imageStillExists(image, variantType)) {
      return null;
    }

    ConvertCmd convert = new ConvertCmd();
    IMOperation op = new IMOperation();
    op.addImage(originalFile.getAbsolutePath());
    if ("image/gif".equals(image.getFileType())) {
      // Animated GIF frames are stored as deltas against the previous frame's canvas, each
      // potentially smaller than and offset within the logical screen size. Resizing without
      // coalescing first resizes each frame's sub-rectangle independently of the others'
      // offsets, producing ghosting/misaligned playback -- coalesce expands every frame to a
      // full canvas first, and re-optimizing after keeps the output from ballooning in size.
      op.coalesce();
    }
    // ">" -- shrink only, never enlarge; aspect ratio is preserved automatically since only one
    // bound needs to be hit.
    op.resize(maxDimension, maxDimension, ">");
    if ("image/gif".equals(image.getFileType())) {
      op.layers("optimize");
    }
    op.addImage(variantPath.file().getAbsolutePath());
    convert.run(op);

    return finalizeVariant(image, variantType, variantPath);
  }

  /**
   * Generates a square variant cropped around the image's stored focal point (issue #411 PR3),
   * instead of the aspect-preserving resize {@link #generateVariants} produces. Meant to be called
   * on demand when an admin sets or changes a focal point ({@code FocalPointVariantJob}), not as
   * part of the upload-time {@link #generateVariants} sweep -- thumbnail/medium/large are
   * unaffected by focal point, so regenerating them on a focal-point-only change would be wasted
   * work.
   *
   * @param image a previously-saved image record with its focal point already persisted
   * @return the generated variant, or {@code null} when the image's format is unsupported, the
   *         original file is missing, or generation failed
   */
  public static ImageVariant generateSquareVariant(Image image) {
    if (image == null || image.getId() == null || image.getId() == -1) {
      return null;
    }
    if (!SUPPORTED_MIME_TYPES.contains(image.getFileType())) {
      LOG.debug("Skipping square variant generation for unsupported type: " + image.getFileType());
      return null;
    }

    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File originalFile = FileSystemCommand.resolveWithinRoot(serverRootPath, image.getFileServerPath());
    if (originalFile == null || !originalFile.isFile()) {
      LOG.warn("Original image file not found for square variant generation: " + image.getId());
      return null;
    }

    try {
      return generateOneFocalPointVariant(image, originalFile, SQUARE, 1, 1, SQUARE_DIMENSION);
    } catch (Exception e) {
      LOG.error("Could not generate the square variant for image " + image.getId(), e);
      return null;
    }
  }

  private static ImageVariant generateOneFocalPointVariant(Image image, File originalFile, String variantType,
      int targetAspectWidth, int targetAspectHeight, int maxOutputDimension) throws Exception {
    VariantPath variantPath = resolveVariantPath(image, variantType);
    if (variantPath == null) {
      return null;
    }
    if (!imageStillExists(image, variantType)) {
      return null;
    }

    Rectangle cropRect = FocalPointCropCommand.computeCropRect(
        image.getWidth(), image.getHeight(), image.getFocalX(), image.getFocalY(),
        targetAspectWidth, targetAspectHeight);

    ConvertCmd convert = new ConvertCmd();
    IMOperation op = new IMOperation();
    op.addImage(originalFile.getAbsolutePath());
    if ("image/gif".equals(image.getFileType())) {
      op.coalesce();
    }
    // Crop first -- it's the only operation of the two that changes aspect ratio. Never call
    // .gravity(...) with a non-default value here: it would change the coordinate origin this
    // crop's (x,y) is measured from, silently shifting the rectangle FocalPointCropCommand just
    // computed.
    op.crop(cropRect.width, cropRect.height, cropRect.x, cropRect.y);
    // "!" forces the exact final pixel size (removing any rounding slop from the crop step), never
    // upscaling past what the crop actually contains.
    int outputDimension = Math.min(maxOutputDimension, cropRect.width);
    op.resize(outputDimension, outputDimension, "!");
    if ("image/gif".equals(image.getFileType())) {
      op.layers("optimize");
    }
    op.addImage(variantPath.file().getAbsolutePath());
    convert.run(op);

    return finalizeVariant(image, variantType, variantPath);
  }

  private static VariantPath resolveVariantPath(Image image, String variantType) {
    String extension = FileSystemCommand.cleanExtension(FilenameUtils.getExtension(image.getFilename()));
    String relativeDir = FilenameUtils.getFullPath(image.getFileServerPath());
    String baseName = FilenameUtils.getBaseName(image.getFileServerPath());
    String relativeVariantPath = relativeDir + baseName + "-" + variantType
        + (extension.isEmpty() ? "" : "." + extension);

    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File variantFile = FileSystemCommand.resolveWithinRoot(serverRootPath, relativeVariantPath);
    if (variantFile == null) {
      LOG.warn("Could not resolve a safe variant path for image " + image.getId() + " (" + variantType + ")");
      return null;
    }
    return new VariantPath(variantFile, relativeVariantPath);
  }

  private static boolean imageStillExists(Image image, String variantType) {
    // Re-check the image row still exists immediately before writing: this job runs
    // asynchronously off the upload's critical path, so an admin can delete the image while a
    // variant is still being generated. DeleteImageCommand only knows to clean up variant files
    // that already had a row at the moment it ran -- writing a file for an image that's already
    // gone would orphan it with no cleanup path.
    if (ImageRepository.findById(image.getId()) == null) {
      LOG.warn("Image was deleted before variant generation completed: " + image.getId() + " (" + variantType + ")");
      return false;
    }
    return true;
  }

  private static ImageVariant finalizeVariant(Image image, String variantType, VariantPath variantPath)
      throws IOException {
    if (!variantPath.file().isFile() || variantPath.file().length() <= 0) {
      LOG.warn("Variant file was not written for image " + image.getId() + " (" + variantType + ")");
      return null;
    }

    Dimension dimension = readDimension(variantPath.file());

    ImageVariant record = new ImageVariant();
    record.setImageId(image.getId());
    record.setVariantType(variantType);
    record.setFileServerPath(variantPath.relativePath());
    record.setFileLength(variantPath.file().length());
    record.setFileType(image.getFileType());
    record.setWidth(dimension.width);
    record.setHeight(dimension.height);
    return ImageVariantRepository.save(record);
  }

  private static Dimension readDimension(File imageFile) throws IOException {
    return ImageDimensionCommand.readDimension(imageFile);
  }

  private record VariantPath(File file, String relativePath) {
  }
}
