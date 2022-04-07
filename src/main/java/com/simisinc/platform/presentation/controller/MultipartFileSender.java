/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.presentation.controller;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class MultipartFileSender {

  private static final int DEFAULT_BUFFER_SIZE = 10240;
  private static final long DEFAULT_EXPIRE_TIME = TimeUnit.DAYS.toSeconds(30);
  private static final String MULTIPART_BOUNDARY = UUID.randomUUID().toString();
  private static Log LOG = LogFactory.getLog(MultipartFileSender.class);

  Path filepath;
  HttpServletRequest request;
  HttpServletResponse response;
  String contentType = null;
  String filename = null;

  public MultipartFileSender() {
  }

  public static MultipartFileSender fromPath(Path path) {
    return new MultipartFileSender().setFilepath(path);
  }

  public static MultipartFileSender fromFile(File file) {
    return new MultipartFileSender().setFilepath(file.toPath());
  }

  public static MultipartFileSender fromURIString(String uri) {
    return new MultipartFileSender().setFilepath(Paths.get(uri));
  }

  //** internal setter **//
  private MultipartFileSender setFilepath(Path filepath) {
    this.filepath = filepath;
    return this;
  }

  public MultipartFileSender with(HttpServletRequest httpRequest) {
    request = httpRequest;
    return this;
  }

  public MultipartFileSender with(HttpServletResponse httpResponse) {
    response = httpResponse;
    return this;
  }

  public MultipartFileSender withMimeType(String mimeType) {
    this.contentType = mimeType;
    return this;
  }

  public MultipartFileSender withFilename(String filename) {
    this.filename = filename;
    return this;
  }

  public void serveResource() throws Exception {
    if (response == null || request == null) {
      return;
    }

    if (!Files.exists(filepath)) {
      LOG.error("File doesn't exist at URI: " + filepath.toAbsolutePath().toString());
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    long length = Files.size(filepath);
    if (filename == null) {
      filename = filepath.getFileName().toString();
    }
    FileTime lastModifiedObj = Files.getLastModifiedTime(filepath);

    if (StringUtils.isEmpty(filename) || lastModifiedObj == null) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    long lastModified = LocalDateTime.ofInstant(lastModifiedObj.toInstant(), ZoneId.of(ZoneOffset.systemDefault().getId())).toEpochSecond(ZoneOffset.UTC);

    // Validate request headers for caching ---------------------------------------------------

    // If-None-Match header should contain "*" or ETag. If so, then return 304.
    String ifNoneMatch = request.getHeader("If-None-Match");
    if (ifNoneMatch != null && HttpUtils.matches(ifNoneMatch, filename)) {
      response.setHeader("ETag", filename); // Required in 304.
      response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
      return;
    }

    // If-Modified-Since header should be greater than LastModified. If so, then return 304.
    // This header is ignored if any If-None-Match header is specified.
    long ifModifiedSince = request.getDateHeader("If-Modified-Since");
    if (ifNoneMatch == null && ifModifiedSince != -1 && ifModifiedSince + 1000 > lastModified) {
      response.setHeader("ETag", filename); // Required in 304.
      response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
      return;
    }

    // Validate request headers for resume ----------------------------------------------------

    // If-Match header should contain "*" or ETag. If not, then return 412.
    String ifMatch = request.getHeader("If-Match");
    if (ifMatch != null && !HttpUtils.matches(ifMatch, filename)) {
      response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
      return;
    }

    // If-Unmodified-Since header should be greater than LastModified. If not, then return 412.
    long ifUnmodifiedSince = request.getDateHeader("If-Unmodified-Since");
    if (ifUnmodifiedSince != -1 && ifUnmodifiedSince + 1000 <= lastModified) {
      response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
      return;
    }

    // Validate and process range -------------------------------------------------------------

    // Prepare some variables. The full Range represents the complete file.
    Range full = new Range(0, length - 1, length);
    List<Range> ranges = new ArrayList<>();

    // Validate and process Range and If-Range headers.
    String range = request.getHeader("Range");
    if (range != null) {

      // Range header should match format "bytes=n-n,n-n,n-n...". If not, then return 416.
      if (!range.matches("^bytes=\\d*-\\d*(,\\d*-\\d*)*$")) {
        response.setHeader("Content-Range", "bytes */" + length); // Required in 416.
        response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        return;
      }

      String ifRange = request.getHeader("If-Range");
      if (ifRange != null && !ifRange.equals(filename)) {
        try {
          long ifRangeTime = request.getDateHeader("If-Range"); // Throws IAE if invalid.
          if (ifRangeTime != -1) {
            ranges.add(full);
          }
        } catch (IllegalArgumentException ignore) {
          ranges.add(full);
        }
      }

      // If any valid If-Range header, then process each part of byte range.
      if (ranges.isEmpty()) {
        for (String part : range.substring(6).split(",")) {
          // Assuming a file with length of 100, the following examples returns bytes at:
          // 50-80 (50 to 80), 40- (40 to length=100), -20 (length-20=80 to length=100).
          long start = Range.sublong(part, 0, part.indexOf("-"));
          long end = Range.sublong(part, part.indexOf("-") + 1, part.length());

          if (start == -1) {
            start = length - end;
            end = length - 1;
          } else if (end == -1 || end > length - 1) {
            end = length - 1;
          }

          // Check if Range is syntactically valid. If not, then return 416.
          if (start > end) {
            response.setHeader("Content-Range", "bytes */" + length); // Required in 416.
            response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            return;
          }

          // Add range.
          ranges.add(new Range(start, end, length));
        }
      }
    }

    // Prepare and initialize response --------------------------------------------------------

    // Get content type by file name and set content disposition.
    String disposition = "inline";

    // If content type is unknown, then set the default value.
    // For all content types, see: http://www.w3schools.com/media/media_mimeref.asp
    // To add new content types, add new mime-mapping entry in web.xml.
    if (contentType == null) {
      contentType = "application/octet-stream";
    } else if (contentType.equals("text/csv")) {
      disposition = "attachment";
    } else if (!contentType.startsWith("text") && !contentType.startsWith("image")) {
      // Else, except for images, determine content disposition. If content type is supported by
      // the browser, then set to inline, else attachment which will pop a 'save as' dialogue.
      String accept = request.getHeader("Accept");
      if (LOG.isDebugEnabled()) {
        LOG.debug("Browser accepts: " + accept);
      }
      disposition = accept != null && HttpUtils.accepts(accept, contentType) ? "inline" : "attachment";
    }
    LOG.debug("Content-Type: " + contentType + ";" + disposition);

    // Initialize response.
    response.reset();
    response.setBufferSize(DEFAULT_BUFFER_SIZE);
    response.setHeader("Content-Type", contentType);
    response.setHeader("Content-Disposition", disposition + ";filename=\"" + filename + "\"");
    response.setHeader("Accept-Ranges", "bytes");
    response.setHeader("ETag", filename);
    response.setDateHeader("Last-Modified", lastModified);
    response.setDateHeader("Expires", System.currentTimeMillis() + DEFAULT_EXPIRE_TIME);

    // Send requested file (part(s)) to client ------------------------------------------------

    // Prepare streams.
    if (ranges.isEmpty() || ranges.get(0) == full) {
      // Return full file
      LOG.debug("Return full file");
      response.setContentType(contentType);
      response.setHeader("Content-Range", "bytes " + full.start + "-" + full.end + "/" + full.total);
      response.setHeader("Content-Length", String.valueOf(full.length));
      Range.stream(filepath.toFile(), response.getOutputStream(), full.start, full.length);

    } else if (ranges.size() == 1) {

      // Return single part of file
      Range r = ranges.get(0);
      LOG.debug("Return 1 part of file: from (" + r.start + ") to (" + r.end + ")");
      response.setContentType(contentType);
      response.setHeader("Content-Range", "bytes " + r.start + "-" + r.end + "/" + r.total);
      response.setHeader("Content-Length", String.valueOf(r.length));
      response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT); // 206.
      Range.stream(filepath.toFile(), response.getOutputStream(), r.start, r.length);

    } else {

      // Return multiple parts of file
      response.setContentType("multipart/byteranges; boundary=" + MULTIPART_BOUNDARY);
      response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT); // 206.

      // Send each multi part range
      for (Range r : ranges) {
        LOG.debug("Return multi part of file: from (" + r.start + ") to (" + r.end + ")");
        // Add multipart boundary and header fields for every range.
        response.getOutputStream().println();
        response.getOutputStream().println("--" + MULTIPART_BOUNDARY);
        response.getOutputStream().println("Content-Type: " + contentType);
        response.getOutputStream().println("Content-Range: bytes " + r.start + "-" + r.end + "/" + r.total);
        Range.stream(filepath.toFile(), response.getOutputStream(), r.start, r.length);
      }

      // End with multipart boundary
      response.getOutputStream().println();
      response.getOutputStream().println("--" + MULTIPART_BOUNDARY + "--");
    }
  }

  private static class Range {
    long start;
    long end;
    long length;
    long total;

    /**
     * Construct a byte range.
     *
     * @param start Start of the byte range.
     * @param end   End of the byte range.
     * @param total Total length of the byte source.
     */
    public Range(long start, long end, long total) {
      this.start = start;
      this.end = end;
      this.length = end - start + 1;
      this.total = total;
    }

    public static long sublong(String value, int beginIndex, int endIndex) {
      String substring = value.substring(beginIndex, endIndex);
      return (substring.length() > 0) ? Long.parseLong(substring) : -1;
    }

    /**
     * Stream the given input to the given output via NIO {@link Channels} and a directly allocated NIO
     * {@link ByteBuffer}. Both the input and output streams will implicitly be closed after streaming,
     * regardless of whether an exception is been thrown or not.
     *
     * @param input  The input stream.
     * @param output The output stream.
     * @return The length of the written bytes.
     * @throws IOException When an I/O error occurs.
     */
    public static long stream(InputStream input, OutputStream output) throws IOException {
      try (ReadableByteChannel inputChannel = Channels.newChannel(input);
           WritableByteChannel outputChannel = Channels.newChannel(output)) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE);
        long size = 0;

        while (inputChannel.read(buffer) != -1) {
          buffer.flip();
          size += outputChannel.write(buffer);
          buffer.clear();
        }

        return size;
      }
    }

    /**
     * Stream a specified range of the given file to the given output via NIO {@link Channels} and a directly allocated
     * NIO {@link ByteBuffer}. The output stream will only implicitly be closed after streaming when the specified range
     * represents the whole file, regardless of whether an exception is been thrown or not.
     *
     * @param file   The file.
     * @param output The output stream.
     * @param start  The start position (offset).
     * @param length The (intented) length of written bytes.
     * @return The (actual) length of the written bytes. This may be smaller when the given length is too large.
     * @throws IOException When an I/O error occurs.
     * @since 2.2
     */
    public static long stream(File file, OutputStream output, long start, long length) throws IOException {
      if (start == 0 && length >= file.length()) {
        return stream(new FileInputStream(file), output);
      }

      try (FileChannel fileChannel = (FileChannel) Files.newByteChannel(file.toPath(), StandardOpenOption.READ)) {
        WritableByteChannel outputChannel = Channels.newChannel(output);
        ByteBuffer buffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE);
        long size = 0;

        while (fileChannel.read(buffer, start + size) != -1) {
          buffer.flip();

          if (size + buffer.limit() > length) {
            buffer.limit((int) (length - size));
          }

          size += outputChannel.write(buffer);

          if (size >= length) {
            break;
          }

          buffer.clear();
        }

        return size;
      }
    }
  }

  private static class HttpUtils {

    /**
     * Returns true if the given accept header accepts the given value.
     *
     * @param acceptHeader The accept header.
     * @param toAccept     The value to be accepted.
     * @return True if the given accept header accepts the given value.
     */
    public static boolean accepts(String acceptHeader, String toAccept) {
      String[] acceptValues = acceptHeader.split("\\s*(,|;)\\s*");
      Arrays.sort(acceptValues);

      return Arrays.binarySearch(acceptValues, toAccept) > -1
          || Arrays.binarySearch(acceptValues, toAccept.replaceAll("/.*$", "/*")) > -1
          || Arrays.binarySearch(acceptValues, "*/*") > -1;
    }

    /**
     * Returns true if the given match header matches the given value.
     *
     * @param matchHeader The match header.
     * @param toMatch     The value to be matched.
     * @return True if the given match header matches the given value.
     */
    public static boolean matches(String matchHeader, String toMatch) {
      String[] matchValues = matchHeader.split("\\s*,\\s*");
      Arrays.sort(matchValues);
      return Arrays.binarySearch(matchValues, toMatch) > -1
          || Arrays.binarySearch(matchValues, "*") > -1;
    }
  }
}
