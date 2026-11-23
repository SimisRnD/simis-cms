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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;
import org.im4java.core.IdentifyCmd;
import org.im4java.process.ArrayListOutputConsumer;

import com.simisinc.platform.domain.model.cms.FormField;

/**
 * The second step for a file a visitor attaches to a form (issue #2130), after
 * {@link ValidateFormUploadCommand} accepted its size and type: the image is decoded and encoded
 * again from its pixels, so what gets stored is an image this platform drew, not the bytes the
 * visitor sent.
 *
 * <p>Re-encoding is what makes the type check's answer true. A file can start with a valid image
 * header and carry anything after it -- script appended past the end of a PNG, a second file hidden
 * in a JPEG comment, a camera's GPS position in EXIF. Only the pixels survive a decode and encode, so
 * all of that is dropped, and a file that will not decode is rejected rather than stored as it came.
 *
 * <ul>
 * <li>JPEG and PNG are decoded in-process with ImageIO, using DownscaleImageCommand's helpers. A JPEG
 * is turned the way its EXIF orientation says (the tag itself is dropped) and written as JPEG at
 * quality 0.85. A PNG stays PNG, keeping its transparency.</li>
 * <li>WebP, which ImageIO cannot read, goes through ImageMagick with {@code -auto-orient -strip}, the
 * same hardened {@code convert} the image library's variants use.</li>
 * <li>Every image also gets a thumbnail up to {@link #THUMBNAIL_WIDTH} pixels wide for the review
 * screen, so an admin never has to open the original to see what was sent.</li>
 * </ul>
 *
 * <p>Animated PNG and WebP keep only their first frame. Nothing here writes outside the file's own
 * directory: WebP's temporary outputs sit beside the input and are removed before returning.
 *
 * @author SimIS Inc.
 */
public class SanitizeFormUploadCommand {

  private static Log LOG = LogFactory.getLog(SanitizeFormUploadCommand.class);

  /** The review thumbnail's maximum width; a narrower image is not enlarged */
  public static final int THUMBNAIL_WIDTH = 320;

  /** WebP encode quality, on ImageMagick's 0-100 scale, matching the JPEG quality */
  static final double WEBP_QUALITY = 85d;

  private static final String WEBP_CODER = "webp:";

  /**
   * How long a submission waits for another image to finish decoding before giving up. Not final
   * only so a test can shorten it.
   */
  static long decodeWaitMillis = 30_000;

  public static final String REASON_UNDECODABLE = "undecodable";
  public static final String REASON_TOO_MANY_PIXELS = "too_many_pixels";
  public static final String REASON_BUSY = "busy";

  /**
   * A re-encoded image and its thumbnail, held in memory for the caller to store.
   *
   * @param bytes the re-encoded image
   * @param contentType {@code image/jpeg}, {@code image/png} or {@code image/webp}
   * @param thumbnail the review thumbnail
   * @param thumbnailContentType the thumbnail's type: WebP for a WebP image, PNG when the image has
   *     transparent pixels, otherwise JPEG
   */
  public record Sanitized(byte[] bytes, String contentType, int width, int height,
      byte[] thumbnail, String thumbnailContentType) {

    public String extension() {
      return DetectContentTypeCommand.imageExtensionFor(contentType);
    }

    public String thumbnailExtension() {
      return DetectContentTypeCommand.imageExtensionFor(thumbnailContentType);
    }
  }

  /**
   * The outcome of {@link #sanitize}.
   *
   * @param reason one of the {@code REASON_} constants when rejected, otherwise null
   * @param message a plain sentence for the visitor when rejected, otherwise null
   * @param image the re-encoded image when accepted, otherwise null
   */
  public record Result(boolean accepted, String reason, String message, Sanitized image) {

    static Result accept(Sanitized image) {
      return new Result(true, null, null, image);
    }

    static Result reject(String reason, String message) {
      return new Result(false, reason, message, null);
    }
  }

  private SanitizeFormUploadCommand() {
  }

  /**
   * Re-encodes a file that {@link ValidateFormUploadCommand} accepted. The file is only read.
   *
   * @param field the file field, for the visitor's message
   * @param file the uploaded file, already written to its quarantine location
   */
  public static Result sanitize(FormField field, File file) {
    String label = field != null && StringUtils.isNotBlank(field.getLabel()) ? field.getLabel().trim() : "The file";
    Result undecodable = Result.reject(REASON_UNDECODABLE,
        label + ": the image couldn't be opened. Try saving it again, or choose another image.");
    if (file == null || !file.isFile()) {
      return undecodable;
    }

    // The type comes from the bytes on disk, checked again here rather than taken from the caller
    String contentType = DetectContentTypeCommand.detect(file);
    if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType) && !"image/webp".equals(contentType)) {
      return undecodable;
    }

    boolean acquired = false;
    try {
      int[] size = "image/webp".equals(contentType) ? readWebpSize(file) : DownscaleImageCommand.readSize(file);
      if (size == null || size[0] <= 0 || size[1] <= 0) {
        return undecodable;
      }
      // Checked from the header, before decoding: a small file can declare enormous dimensions
      if ((long) size[0] * size[1] > DownscaleImageCommand.MAX_DECODE_PIXELS) {
        return Result.reject(REASON_TOO_MANY_PIXELS, label + ": the image has too many pixels. Choose a smaller image.");
      }

      acquired = DownscaleImageCommand.DECODE_PERMIT.tryAcquire(decodeWaitMillis, TimeUnit.MILLISECONDS);
      if (!acquired) {
        LOG.warn("A form upload waited " + decodeWaitMillis + "ms for an image decode and gave up");
        return Result.reject(REASON_BUSY, label + ": the image couldn't be processed right now. Try again in a minute.");
      }
      Sanitized sanitized = "image/webp".equals(contentType) ? reencodeWebp(file) : reencodeInProcess(file, contentType);
      return sanitized != null ? Result.accept(sanitized) : undecodable;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return undecodable;
    } catch (Exception e) {
      // ImageIO throws for a truncated file, a CMYK JPEG, an unsupported PNG variant; ImageMagick for
      // anything it cannot read. None of it is stored.
      LOG.warn("A form upload could not be re-encoded and was rejected: " + e.getMessage());
      return undecodable;
    } finally {
      if (acquired) {
        DownscaleImageCommand.DECODE_PERMIT.release();
      }
    }
  }

  /** JPEG and PNG, with ImageIO */
  private static Sanitized reencodeInProcess(File file, String contentType) throws IOException {
    BufferedImage decoded = ImageIO.read(file);
    if (decoded == null) {
      return null;
    }
    boolean isPng = "image/png".equals(contentType);
    boolean keepAlpha = isPng && DownscaleImageCommand.hasTransparentPixel(decoded);
    // Drawn onto a plain RGB or ARGB canvas: whatever color model and bit depth the file declared,
    // what is encoded is ordinary 8-bit pixels
    BufferedImage image = normalize(decoded, keepAlpha);
    decoded.flush();
    if (!isPng) {
      image = DownscaleImageCommand.applyOrientation(image, DownscaleImageCommand.readExifOrientation(file));
    }

    byte[] bytes = isPng ? DownscaleImageCommand.encodePng(image) : DownscaleImageCommand.encodeJpeg(image);
    BufferedImage thumbnailImage = thumbnail(image, keepAlpha);
    byte[] thumbnail = keepAlpha ? DownscaleImageCommand.encodePng(thumbnailImage) : DownscaleImageCommand.encodeJpeg(thumbnailImage);
    if (bytes == null || thumbnail == null) {
      return null;
    }
    Sanitized sanitized = new Sanitized(bytes, isPng ? "image/png" : "image/jpeg", image.getWidth(), image.getHeight(),
        thumbnail, keepAlpha ? "image/png" : "image/jpeg");
    if (thumbnailImage != image) {
      thumbnailImage.flush();
    }
    image.flush();
    return sanitized;
  }

  private static BufferedImage normalize(BufferedImage source, boolean keepAlpha) {
    BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(),
        keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      // An opaque canvas starts black; a PNG drawn onto it without transparency kept had none to lose
      g.drawImage(source, 0, 0, null);
    } finally {
      g.dispose();
    }
    return image;
  }

  private static BufferedImage thumbnail(BufferedImage image, boolean keepAlpha) {
    if (image.getWidth() <= THUMBNAIL_WIDTH) {
      return image;
    }
    int height = Math.max(1, Math.round((float) image.getHeight() * THUMBNAIL_WIDTH / image.getWidth()));
    return DownscaleImageCommand.resize(image, THUMBNAIL_WIDTH, height, keepAlpha);
  }

  /** WebP, with ImageMagick. Its outputs are written beside the input and removed once read. */
  private static Sanitized reencodeWebp(File file) throws Exception {
    File directory = file.getAbsoluteFile().getParentFile();
    File output = File.createTempFile("sanitized-", ".webp", directory);
    File thumbnailOutput = File.createTempFile("thumbnail-", ".webp", directory);
    try {
      // "webp:" names the decoder outright, so ImageMagick never guesses a format from the name or
      // content, and [0] is the first frame, so an animation becomes a still
      IMOperation op = new IMOperation();
      op.addImage(WEBP_CODER + file.getAbsolutePath() + "[0]");
      op.autoOrient();
      op.strip();
      op.quality(WEBP_QUALITY);
      op.addImage(WEBP_CODER + output.getAbsolutePath());
      new ConvertCmd().run(op);

      IMOperation thumbnailOp = new IMOperation();
      thumbnailOp.addImage(WEBP_CODER + output.getAbsolutePath());
      // ">" only ever shrinks
      thumbnailOp.addRawArgs("-thumbnail", THUMBNAIL_WIDTH + "x>");
      thumbnailOp.strip();
      thumbnailOp.quality(WEBP_QUALITY);
      thumbnailOp.addImage(WEBP_CODER + thumbnailOutput.getAbsolutePath());
      new ConvertCmd().run(thumbnailOp);

      byte[] bytes = Files.readAllBytes(output.toPath());
      byte[] thumbnail = Files.readAllBytes(thumbnailOutput.toPath());
      int[] size = readWebpSize(output);
      if (bytes.length == 0 || thumbnail.length == 0 || size == null
          || !"image/webp".equals(DetectContentTypeCommand.detect(output))) {
        return null;
      }
      return new Sanitized(bytes, "image/webp", size[0], size[1], thumbnail, "image/webp");
    } finally {
      Files.deleteIfExists(output.toPath());
      Files.deleteIfExists(thumbnailOutput.toPath());
    }
  }

  /**
   * A WebP's pixel size from its header, without decoding it. Asks for the first frame only, since
   * identify prints one size per frame of an animation.
   */
  static int[] readWebpSize(File file) {
    try {
      IdentifyCmd identify = new IdentifyCmd();
      ArrayListOutputConsumer output = new ArrayListOutputConsumer();
      identify.setOutputConsumer(output);
      IMOperation op = new IMOperation();
      op.ping();
      op.format("%w %h\n");
      op.addImage(WEBP_CODER + file.getAbsolutePath() + "[0]");
      identify.run(op);
      ArrayList<String> lines = output.getOutput();
      if (lines.isEmpty()) {
        return null;
      }
      String[] parts = lines.get(0).trim().split("\\s+");
      if (parts.length < 2) {
        return null;
      }
      return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
    } catch (Exception e) {
      LOG.warn("Could not read a WebP's size: " + e.getMessage());
      return null;
    }
  }
}
