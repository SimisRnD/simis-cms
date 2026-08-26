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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.im4java.core.IMOperation;
import org.im4java.core.IdentifyCmd;
import org.im4java.process.ArrayListOutputConsumer;

/**
 * Reads an image file's pixel dimensions without fully decoding it into memory.
 *
 * <p>
 * Tries {@code javax.imageio.ImageIO} first -- an in-process, no-subprocess read that covers
 * jpeg/png/gif/bmp, with the decoder chosen by sniffing the file's bytes rather than trusting its
 * extension (#1445). The JDK ships no WebP (or AVIF) decoder, so a WebP file falls through to
 * ImageMagick's {@code identify} instead, via the {@code im4java} dependency this app already
 * uses for variant generation ({@link GenerateImageVariantsCommand}). This reuses the same
 * hardened, resource-bounded ImageMagick invocation path already in place (see
 * {@code docker/app/imagemagick-policy.xml}, which permits exactly the coders
 * GenerateImageVariantsCommand resizes: jpeg/png/gif/webp) rather than adding a new third-party
 * ImageIO codec dependency. Issue #931.
 * </p>
 *
 * @author SimIS Inc.
 */
public class ImageDimensionCommand {

  private static final Log LOG = LogFactory.getLog(ImageDimensionCommand.class);

  private ImageDimensionCommand() {
    // Static utility, not instantiated
  }

  public static Dimension readDimension(File imageFile) throws IOException {
    // Choose the decoder from the file's own bytes, not from its name. ImageIO.getImageReaders(Object)
    // asks each candidate reader's canDecodeInput() to sniff the stream, so an upload whose extension
    // misdescribes its contents (PNG data named .jpg, issue #1445) still gets the reader that can
    // actually read it. Selecting by suffix instead handed such a file to the JPEG reader, which threw
    // "Not a JPEG file: starts with 0x89 0x50" and logged a stack trace before the ImageMagick
    // fallback below quietly recovered the correct dimensions -- a misleading error plus a subprocess
    // on every read of that file.
    try (ImageInputStream stream = new FileImageInputStream(imageFile)) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
      while (readers.hasNext()) {
        ImageReader reader = readers.next();
        try {
          // A reader that failed part-way leaves the stream mid-file; rewind so the next candidate
          // (and its canDecodeInput sniff) starts from the header again.
          stream.seek(0);
          reader.setInput(stream);
          return new Dimension(reader.getWidth(reader.getMinIndex()), reader.getHeight(reader.getMinIndex()));
        } catch (IOException e) {
          LOG.warn("Error reading image dimensions: " + imageFile.getAbsolutePath(), e);
        } finally {
          reader.dispose();
        }
      }
    } catch (IOException e) {
      LOG.warn("Error opening the image file: " + imageFile.getAbsolutePath(), e);
    }
    // No reader claimed these bytes -- WebP lands here, since the JDK ships no WebP decoder (#931)
    return readDimensionViaImageMagick(imageFile);
  }

  private static Dimension readDimensionViaImageMagick(File imageFile) throws IOException {
    try {
      IdentifyCmd identify = new IdentifyCmd();
      ArrayListOutputConsumer output = new ArrayListOutputConsumer();
      identify.setOutputConsumer(output);
      IMOperation op = new IMOperation();
      op.format("%w %h");
      op.addImage(imageFile.getAbsolutePath());
      identify.run(op);

      ArrayList<String> lines = output.getOutput();
      if (lines.isEmpty()) {
        throw new IOException("identify returned no output for: " + imageFile.getAbsolutePath());
      }
      String[] parts = lines.get(0).trim().split("\\s+");
      if (parts.length < 2) {
        throw new IOException("identify did not return width/height for: " + imageFile.getAbsolutePath());
      }
      return new Dimension(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      // im4java throws checked InterruptedException/IM4JavaException for a failed invocation
      // (e.g. a format the policy.xml denies, or a corrupt file) -- normalize to IOException so
      // callers have one exception type to handle, matching the ImageIO path above.
      throw new IOException("Not a known image file: " + imageFile.getAbsolutePath(), e);
    }
  }
}
