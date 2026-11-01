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
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Shrinks an image that is far wider than any page displays it, before it enters the image library.
 *
 * <p>Content managers upload infographics exported at around 3,700px wide, 2 to 4.5 MB as PNG, that
 * a page shows at about 1,000px. Anything wider than {@link #MAX_WIDTH} is scaled down to that width
 * with its aspect ratio kept. A PNG with no transparent pixel is re-encoded as JPEG, which is where
 * most of the saving is; a PNG that does use transparency stays PNG, since JPEG would turn every
 * transparent pixel solid. A JPEG is resized and re-encoded as JPEG.
 *
 * <p>Uses the JDK's own ImageIO, in-process, the same decoder upload validation already relies on.
 * WebP is deliberately not produced: ImageIO can neither write it nor, without a plugin, read it.
 * WebP, GIF and SVG uploads pass through untouched, and so does anything this cannot decode
 * confidently -- a CMYK JPEG, an animated PNG, a file too large to decode safely. Leaving an image
 * as it arrived is always an acceptable outcome here; mangling it is not.
 *
 * @author SimIS Inc.
 */
public class DownscaleImageCommand {

  private static final Log LOG = LogFactory.getLog(DownscaleImageCommand.class);

  /** Wide enough for a full-width slot on a 2x display at the site's content width; matches the largest srcset variant. */
  public static final int MAX_WIDTH = 1600;

  /** ImageIO's 0-1 scale; about what an image editor calls 85. */
  public static final float JPEG_QUALITY = 0.85f;

  /**
   * The largest image this will decode. A decoded image costs four bytes a pixel, the container runs
   * with -XX:+ExitOnOutOfMemoryError, and a small, well-compressed PNG can still declare enormous
   * dimensions, so the pixel count is checked from the header before anything is decoded. 50
   * megapixels is about 200 MB, enough for a 3,700px-wide infographic 13,500px tall.
   */
  static final long MAX_DECODE_PIXELS = 50_000_000L;

  private static final byte[] EXIF_HEADER = { 'E', 'x', 'i', 'f', 0, 0 };
  private static final int EXIF_ORIENTATION_TAG = 0x0112;

  private DownscaleImageCommand() {
    // Static utility, not instantiated
  }

  /**
   * The re-encoded image, held in memory for the caller to store wherever it belongs.
   *
   * @param bytes the encoded file
   * @param fileType {@code image/jpeg} or {@code image/png}
   * @param width the new pixel width
   * @param height the new pixel height
   */
  public record Downscaled(byte[] bytes, String fileType, int width, int height) {

    public String extension() {
      return DetectContentTypeCommand.imageExtensionFor(fileType);
    }
  }

  /**
   * @param file an image file on disk; it is only read, never changed
   * @return the smaller image, or {@code null} when the file should be kept exactly as it is: it is
   *         not wider than {@link #MAX_WIDTH}, is not a PNG or JPEG, cannot be decoded safely, or
   *         would not come out smaller than it already is
   */
  public static Downscaled downscaleIfTooWide(File file) {
    if (file == null || !file.isFile()) {
      return null;
    }
    String fileType = DetectContentTypeCommand.detect(file);
    boolean isJpeg = "image/jpeg".equals(fileType);
    if (!isJpeg && !"image/png".equals(fileType)) {
      return null;
    }
    try {
      int[] size = readSize(file);
      if (size == null) {
        return null;
      }
      // A camera JPEG is often stored sideways with an EXIF tag saying how to turn it. Browsers
      // honor the tag, so it is the turned width that matters, and re-encoding drops the tag, so
      // the pixels have to be turned for real or the photo would publish sideways.
      int orientation = isJpeg ? readExifOrientation(file) : 1;
      boolean swapsAxes = orientation >= 5;
      int displayWidth = swapsAxes ? size[1] : size[0];
      int displayHeight = swapsAxes ? size[0] : size[1];
      if (displayWidth <= MAX_WIDTH) {
        return null;
      }
      if ((long) size[0] * size[1] > MAX_DECODE_PIXELS) {
        LOG.warn("Not downscaling " + file.getName() + ": " + size[0] + "x" + size[1] + " is too large to decode safely");
        return null;
      }
      if (!isJpeg && isAnimatedPng(file)) {
        // ImageIO reads only the first frame, so the animation would be lost
        return null;
      }

      BufferedImage source = ImageIO.read(file);
      if (source == null) {
        return null;
      }
      boolean keepAsPng = !isJpeg && hasTransparentPixel(source);
      int targetDisplayHeight = Math.max(1, Math.round((float) displayHeight * MAX_WIDTH / displayWidth));
      int targetWidth = swapsAxes ? targetDisplayHeight : MAX_WIDTH;
      int targetHeight = swapsAxes ? MAX_WIDTH : targetDisplayHeight;
      BufferedImage resized = applyOrientation(resize(source, targetWidth, targetHeight, keepAsPng), orientation);
      source.flush();

      byte[] bytes = keepAsPng ? encodePng(resized) : encodeJpeg(resized);
      if (bytes == null || bytes.length >= file.length()) {
        // Possible for a palette PNG, which comes back as full-color PNG. A larger file is not an
        // improvement, so the original stands.
        LOG.info("Not downscaling " + file.getName() + ": the result would not be smaller");
        return null;
      }
      return new Downscaled(bytes, keepAsPng ? "image/png" : "image/jpeg", resized.getWidth(), resized.getHeight());
    } catch (Exception e) {
      // Thrown by ImageIO for a CMYK JPEG, a truncated file, an unsupported PNG variant. The upload
      // continues with the bytes as they arrived; validation decides whether they are an image.
      LOG.warn("Could not downscale " + file.getName() + ", keeping it as uploaded: " + e.getMessage());
      return null;
    }
  }

  /** The stored pixel dimensions from the file header, without decoding the pixels. */
  private static int[] readSize(File file) throws IOException {
    try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
      if (stream == null) {
        return null;
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) {
        return null;
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(stream);
        return new int[] { reader.getWidth(0), reader.getHeight(0) };
      } finally {
        reader.dispose();
      }
    }
  }

  /**
   * Any pixel less than fully opaque. An alpha channel alone is not enough to decide: design tools
   * routinely export a fully opaque infographic as RGBA, and that one should still become a JPEG.
   */
  static boolean hasTransparentPixel(BufferedImage image) {
    ColorModel colorModel = image.getColorModel();
    if (!colorModel.hasAlpha()) {
      return false;
    }
    int width = image.getWidth();
    int height = image.getHeight();
    WritableRaster alpha = image.getAlphaRaster();
    if (alpha != null) {
      int opaque = (1 << colorModel.getComponentSize(colorModel.getNumComponents() - 1)) - 1;
      int[] row = new int[width];
      for (int y = 0; y < height; y++) {
        alpha.getSamples(0, y, width, 1, 0, row);
        for (int value : row) {
          if (value < opaque) {
            return true;
          }
        }
      }
      return false;
    }
    // A palette image with a transparent entry has no separate alpha raster
    int[] row = new int[width];
    for (int y = 0; y < height; y++) {
      image.getRGB(0, y, width, 1, row, 0, width);
      for (int argb : row) {
        if ((argb >>> 24) != 0xFF) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Halves the image until it is within 2x of the target, then makes one last step. A single large
   * bilinear or bicubic step samples only a few source pixels per output pixel, which breaks up the
   * thin lines and small text these infographics are full of; each halving averages every pixel.
   */
  private static BufferedImage resize(BufferedImage source, int targetWidth, int targetHeight, boolean keepAlpha) {
    int type = keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    BufferedImage current = source;
    int width = source.getWidth();
    int height = source.getHeight();
    do {
      width = Math.max(targetWidth, width / 2);
      height = Math.max(targetHeight, height / 2);
      boolean lastStep = width == targetWidth && height == targetHeight;
      BufferedImage next = new BufferedImage(width, height, type);
      Graphics2D g = next.createGraphics();
      try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            lastStep ? RenderingHints.VALUE_INTERPOLATION_BICUBIC : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(current, 0, 0, width, height, null);
      } finally {
        g.dispose();
      }
      if (current != source) {
        current.flush();
      }
      current = next;
    } while (width != targetWidth || height != targetHeight);
    return current;
  }

  /**
   * Reads the EXIF orientation (1-8) from a JPEG's APP1 segment, or 1 when there is none. ImageIO
   * ignores this tag, so it is read directly: walk the JPEG markers to APP1, then the TIFF header and
   * the first IFD for tag 0x0112.
   */
  static int readExifOrientation(File file) {
    try (InputStream in = Files.newInputStream(file.toPath());
        DataInputStream data = new DataInputStream(in)) {
      if (data.readUnsignedShort() != 0xFFD8) {
        return 1;
      }
      while (true) {
        int marker = data.readUnsignedShort();
        if ((marker & 0xFF00) != 0xFF00 || marker == 0xFFDA || marker == 0xFFD9) {
          // Start of scan or end of image: the metadata segments are all before this
          return 1;
        }
        int length = data.readUnsignedShort() - 2;
        if (length < 0) {
          return 1;
        }
        if (marker != 0xFFE1) {
          data.skipNBytes(length);
          continue;
        }
        byte[] segment = data.readNBytes(length);
        int orientation = orientationFromExif(segment);
        if (orientation > 0) {
          return orientation;
        }
      }
    } catch (Exception e) {
      return 1;
    }
  }

  private static int orientationFromExif(byte[] segment) {
    if (segment.length < EXIF_HEADER.length + 8) {
      return -1;
    }
    for (int i = 0; i < EXIF_HEADER.length; i++) {
      if (segment[i] != EXIF_HEADER[i]) {
        // An APP1 that is XMP rather than EXIF; keep looking
        return -1;
      }
    }
    int tiff = EXIF_HEADER.length;
    ByteBuffer buffer = ByteBuffer.wrap(segment);
    String byteOrder = new String(segment, tiff, 2, StandardCharsets.US_ASCII);
    if ("II".equals(byteOrder)) {
      buffer.order(ByteOrder.LITTLE_ENDIAN);
    } else if (!"MM".equals(byteOrder)) {
      return 1;
    }
    int ifd = tiff + buffer.getInt(tiff + 4);
    if (ifd < tiff || ifd + 2 > segment.length) {
      return 1;
    }
    int entries = buffer.getShort(ifd) & 0xFFFF;
    for (int i = 0; i < entries; i++) {
      int entry = ifd + 2 + i * 12;
      if (entry + 12 > segment.length) {
        return 1;
      }
      if ((buffer.getShort(entry) & 0xFFFF) == EXIF_ORIENTATION_TAG) {
        int value = buffer.getShort(entry + 8) & 0xFFFF;
        return value >= 1 && value <= 8 ? value : 1;
      }
    }
    return 1;
  }

  /** Turns and mirrors the pixels the way the EXIF orientation tag tells a viewer to display them. */
  static BufferedImage applyOrientation(BufferedImage image, int orientation) {
    if (orientation <= 1 || orientation > 8) {
      return image;
    }
    int width = image.getWidth();
    int height = image.getHeight();
    boolean swapsAxes = orientation >= 5;
    AffineTransform transform = new AffineTransform();
    switch (orientation) {
      case 2: // mirrored left to right
        transform.translate(width, 0);
        transform.scale(-1, 1);
        break;
      case 3: // upside down
        transform.translate(width, height);
        transform.rotate(Math.PI);
        break;
      case 4: // mirrored top to bottom
        transform.translate(0, height);
        transform.scale(1, -1);
        break;
      case 5: // transposed
        transform.rotate(Math.PI / 2);
        transform.scale(1, -1);
        break;
      case 6: // turned a quarter clockwise to display
        transform.translate(height, 0);
        transform.rotate(Math.PI / 2);
        break;
      case 7: // transversed
        transform.translate(height, width);
        transform.rotate(Math.PI / 2);
        transform.scale(-1, 1);
        break;
      default: // 8: turned a quarter counterclockwise to display
        transform.translate(0, width);
        transform.rotate(-Math.PI / 2);
        break;
    }
    BufferedImage turned = new BufferedImage(swapsAxes ? height : width, swapsAxes ? width : height, image.getType());
    Graphics2D g = turned.createGraphics();
    try {
      g.drawImage(image, transform, null);
    } finally {
      g.dispose();
    }
    return turned;
  }

  private static byte[] encodeJpeg(BufferedImage image) throws IOException {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      return null;
    }
    ImageWriter writer = writers.next();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(JPEG_QUALITY);
      writer.setOutput(out);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }

  private static byte[] encodePng(BufferedImage image) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "png", bytes)) {
      return null;
    }
    return bytes.toByteArray();
  }

  /**
   * An APNG carries an {@code acTL} chunk before its first {@code IDAT}. Walks the chunk headers
   * only; the image data is skipped, not read.
   */
  static boolean isAnimatedPng(File file) {
    try (InputStream in = Files.newInputStream(file.toPath());
        DataInputStream data = new DataInputStream(in)) {
      data.skipNBytes(8);
      while (true) {
        int length = data.readInt();
        String type = new String(data.readNBytes(4), StandardCharsets.US_ASCII);
        if ("acTL".equals(type)) {
          return true;
        }
        if ("IDAT".equals(type) || "IEND".equals(type) || length < 0) {
          return false;
        }
        data.skipNBytes(length + 4L);
      }
    } catch (Exception e) {
      return false;
    }
  }
}
