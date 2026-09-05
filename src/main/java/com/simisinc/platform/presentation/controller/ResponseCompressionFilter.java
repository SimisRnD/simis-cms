/*
 * Copyright 2022 SimIS Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.presentation.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Compresses generated responses -- the HTML from PageServlet above all -- before they leave the
 * application.
 *
 * <p><strong>Why this is a filter and not a connector setting.</strong> Compression was first tried
 * on the Tomcat connector in #1832 and had to be reverted in #1833: every stylesheet went out with
 * {@code Content-Encoding: gzip} beside a {@code Content-Length} that still described the
 * uncompressed file, so HTTP/1.1 clients waited forever for bytes that were never coming and the
 * site lost its theme. The lesson is not "compression is dangerous" but "whatever compresses the
 * body must also own the length header". This filter owns both: {@link CompressingResponse}
 * intercepts every route to Content-Length and drops the value the moment it decides to compress,
 * so the mismatch that broke #1832 cannot be constructed here.
 *
 * <p><strong>Why not let Front Door do it.</strong> Front Door only compresses what it caches --
 * "compression is part of Enable Caching in Route" -- and it strips {@code Set-Cookie} from
 * anything it caches. Every page here mints a JSESSIONID, so making pages cacheable would hand
 * visitors a session-less page and break form posts. Compressing at the origin needs none of that;
 * Front Door passes an already-compressed body straight through.
 *
 * <p>File transfers are left alone too: MultipartFileSender sets Content-Range and
 * Content-Length itself and streams the bytes, so compressing underneath it would leave those
 * headers describing a body that is no longer there -- the #1832 shape reached by another route.
 *
 * <p><strong>Requires {@code suspendWrappedResponseAfterForward="false"}</strong> in
 * META-INF/context.xml. Every page is rendered through RequestDispatcher.forward(), and by
 * default Tomcat suspends the response once a forward returns, silently discarding whatever
 * this filter writes afterwards -- the trailer among it. Without that setting the body is
 * truncated with no error on either side: measured on the home page, 4,770 bytes handed to
 * Tomcat and 2,551 delivered. A mocked response cannot show this; only a real container can.
 *
 * <p><strong>Scope.</strong> Static files under /css, /fonts, /images and friends are skipped
 * outright. They are already compressed at the edge, where they are cacheable and the work is done
 * once rather than per request, so compressing them here would buy nothing -- and skipping them
 * keeps this filter entirely off Tomcat's sendfile path, which is the path #1832 corrupted.
 */
public class ResponseCompressionFilter implements Filter {

  /**
   * Below this many bytes a response is sent as-is. gzip has ~20 bytes of framing and a floor on
   * what it can achieve, so compressing a short body spends CPU to save nothing and can add bytes.
   * Front Door uses the same 1 KB floor.
   */
  static final int COMPRESS_THRESHOLD_BYTES = 1024;

  /**
   * Content types worth compressing: text, and the text-shaped application types. Everything absent
   * from this list is either already compressed (images, fonts, video, zip) or unknown, and both
   * are better left alone. An allowlist rather than a denylist, so a content type nobody thought
   * about is passed through untouched instead of being re-compressed by accident.
   */
  static boolean isCompressibleType(String contentType) {
    if (contentType == null) {
      return false;
    }
    // "text/html;charset=UTF-8" -- the parameters are not part of the decision
    int semicolon = contentType.indexOf(';');
    String type = (semicolon == -1 ? contentType : contentType.substring(0, semicolon))
        .trim().toLowerCase(Locale.ROOT);
    return "text/html".equals(type)
        || "text/plain".equals(type)
        || "text/css".equals(type)
        || "text/xml".equals(type)
        || "text/javascript".equals(type)
        || "application/json".equals(type)
        || "application/javascript".equals(type)
        || "application/xml".equals(type)
        || "application/xhtml+xml".equals(type)
        || "image/svg+xml".equals(type);
  }

  /**
   * True when the client actually asked for gzip.
   *
   * <p>Checks the quality value rather than searching for the substring: "gzip;q=0" is a client
   * saying it does NOT want gzip, and treating that as consent sends it a body it will not decode.
   * Identity is never assumed from a missing header either -- no Accept-Encoding means no
   * compression.
   */
  static boolean acceptsGzip(String acceptEncoding) {
    if (acceptEncoding == null) {
      return false;
    }
    for (String part : acceptEncoding.split(",")) {
      String[] pieces = part.trim().split(";");
      String coding = pieces[0].trim().toLowerCase(Locale.ROOT);
      if (!"gzip".equals(coding) && !"*".equals(coding)) {
        continue;
      }
      for (int i = 1; i < pieces.length; i++) {
        String parameter = pieces[i].trim().toLowerCase(Locale.ROOT);
        if (parameter.startsWith("q=")) {
          try {
            if (Double.parseDouble(parameter.substring(2)) == 0d) {
              return false;
            }
          } catch (NumberFormatException e) {
            // A malformed q-value is not consent
            return false;
          }
        }
      }
      return true;
    }
    return false;
  }

  public void init(FilterConfig config) throws ServletException {
    // Nothing to configure
  }

  public void destroy() {
    // Nothing to release
  }

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      chain.doFilter(request, response);
      return;
    }
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    String contextPath = request.getServletContext().getContextPath();
    String resource = httpRequest.getRequestURI().substring(contextPath.length());

    if (!acceptsGzip(httpRequest.getHeader("Accept-Encoding"))
        || httpRequest.getHeader("Range") != null
        || WebRequestFilter.isBrowserResourcePath(resource)) {
      // A Range request is excluded because a ranged body and a compressed body describe different
      // byte counts for the same URL; Front Door answers that disagreement with a 503.
      chain.doFilter(request, response);
      return;
    }

    // Set before anything downstream can commit the response. The body now depends on a request
    // header, so any cache between here and the browser has to key on it; without this a shared
    // cache can hand a gzip body to a client that never asked for one.
    httpResponse.addHeader("Vary", "Accept-Encoding");

    CompressingResponse compressingResponse = new CompressingResponse(httpResponse);
    try {
      chain.doFilter(request, compressingResponse);
    } finally {
      // In a finally so a thrown exception still terminates the gzip stream. Leaving a
      // GZIPOutputStream unfinished truncates the body without any error the client can see.
      compressingResponse.finishResponse();
    }
  }

  /**
   * A response that decides, once, whether to gzip -- and owns Content-Length either way.
   *
   * <p>The decision cannot be made when the wrapper is built: the content type is not known until
   * the servlet sets it, and the size is not known until the body is written. So writes accumulate
   * in a small buffer until there is enough to judge. That deferral is what makes the length header
   * safe to intercept: nothing has been committed yet, so whichever way the decision falls, the
   * right header can still be sent.
   */
  static final class CompressingResponse extends HttpServletResponseWrapper {

    private CompressingOutputStream outputStream;
    private PrintWriter writer;

    /**
     * The length the application asked for, held back rather than passed straight through. If the
     * body ends up compressed this value describes the wrong bytes and is discarded; if it does
     * not, it is applied unchanged. #1832 shipped because nothing sat in this position.
     */
    private long declaredContentLength = -1;

    CompressingResponse(HttpServletResponse response) {
      super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
      if (writer != null) {
        throw new IllegalStateException("getWriter() has already been called on this response");
      }
      if (outputStream == null) {
        outputStream = new CompressingOutputStream(this);
      }
      return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
      if (writer == null) {
        if (outputStream != null) {
          throw new IllegalStateException("getOutputStream() has already been called");
        }
        outputStream = new CompressingOutputStream(this);
        writer = new PrintWriter(new OutputStreamWriter(outputStream, getCharacterEncoding()));
      }
      return writer;
    }

    @Override
    public void setContentLength(int len) {
      declaredContentLength = len;
    }

    @Override
    public void setContentLengthLong(long len) {
      declaredContentLength = len;
    }

    @Override
    public void setHeader(String name, String value) {
      if (isContentLength(name)) {
        declaredContentLength = parseLength(value);
        return;
      }
      super.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
      if (isContentLength(name)) {
        declaredContentLength = parseLength(value);
        return;
      }
      super.addHeader(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
      if (isContentLength(name)) {
        declaredContentLength = value;
        return;
      }
      super.setIntHeader(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
      if (isContentLength(name)) {
        declaredContentLength = value;
        return;
      }
      super.addIntHeader(name, value);
    }

    private static boolean isContentLength(String name) {
      return "content-length".equalsIgnoreCase(name);
    }

    private static long parseLength(String value) {
      try {
        return Long.parseLong(value.trim());
      } catch (RuntimeException e) {
        return -1;
      }
    }

    @Override
    public void flushBuffer() throws IOException {
      if (writer != null) {
        writer.flush();
      }
      if (outputStream != null) {
        outputStream.flush();
      }
      super.flushBuffer();
    }

    @Override
    public void reset() {
      super.reset();
      declaredContentLength = -1;
      if (outputStream != null) {
        outputStream.reset();
      }
    }

    @Override
    public void resetBuffer() {
      super.resetBuffer();
      if (outputStream != null) {
        outputStream.reset();
      }
    }

    /**
     * An error page is generated by the container, after this wrapper has been bypassed, so a
     * compression decision taken for the original body must not be left standing over it.
     */
    @Override
    public void sendError(int sc) throws IOException {
      abandonCompression();
      super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      abandonCompression();
      super.sendError(sc, msg);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
      abandonCompression();
      super.sendRedirect(location);
    }

    private void abandonCompression() {
      if (outputStream != null) {
        outputStream.reset();
      }
      if (!isCommitted()) {
        // Tomcat 11 maps a null value to removeHeader() (verified against the runtime image), so
        // this takes the header off rather than emitting an empty one.
        super.setHeader("Content-Encoding", null);
      }
    }

    /**
     * Applies whatever the application asked for, now that no compression is going to happen.
     */
    void restoreDeclaredContentLength() {
      if (declaredContentLength >= 0 && !isCommitted()) {
        super.setContentLengthLong(declaredContentLength);
      }
    }

    boolean hasDeclaredContentLength() {
      return declaredContentLength >= 0;
    }

    /**
     * Writes a header past this wrapper's own interception.
     *
     * <p>{@link #setHeader} swallows Content-Length by design, and the compressing stream is the
     * one caller that has to reach the real response -- it is setting the headers it has just
     * taken responsibility for, not the application's.
     */
    void setHeaderDirect(String name, String value) {
      super.setHeader(name, value);
    }

    void setContentLengthDirect(int len) {
      super.setContentLength(len);
    }

    ServletOutputStream underlyingOutputStream() throws IOException {
      return ((HttpServletResponse) getResponse()).getOutputStream();
    }

    /**
     * Flushes the writer before the stream: a PrintWriter holds characters this stream has never
     * seen, and finishing the gzip member first would drop them.
     */
    void finishResponse() throws IOException {
      if (writer != null) {
        writer.flush();
      }
      if (outputStream != null) {
        outputStream.finish();
      } else {
        // Nothing was ever written -- a 204, a redirect, a response built from headers alone. The
        // length was still taken on the way past, so it has to be handed back; there is no
        // compression decision left to make that could invalidate it.
        restoreDeclaredContentLength();
      }
    }
  }

  /**
   * The stream that does the deciding. Writes land in {@link #pending} until there is enough to
   * judge, then go either into a {@link GZIPOutputStream} or straight through, and the buffer is
   * replayed into whichever was chosen.
   */
  static final class CompressingOutputStream extends ServletOutputStream {

    private final CompressingResponse response;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    private ServletOutputStream passthrough;
    private ServletOutputStream underlying;
    private GZIPOutputStream gzip;
    private boolean decided;
    private boolean finished;

    CompressingOutputStream(CompressingResponse response) {
      this.response = response;
    }

    @Override
    public void write(int b) throws IOException {
      write(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (finished) {
        throw new IOException("Write after the response was completed");
      }
      if (!decided) {
        pending.write(b, off, len);
        if (pending.size() > COMPRESS_THRESHOLD_BYTES) {
          decide();
        }
        return;
      }
      target().write(b, off, len);
    }

    private java.io.OutputStream target() {
      return gzip != null ? gzip : passthrough;
    }

    /**
     * Commits to one path or the other and replays what was buffered.
     *
     * <p>The content type is read here rather than at construction because a servlet sets it on the
     * way to writing, not before; asking too early reads null and never compresses anything.
     */
    private void decide() throws IOException {
      decided = true;
      boolean compressible = isCompressibleType(response.getContentType())
          && response.getHeader("Content-Encoding") == null
          && response.getHeader("Content-Range") == null
          && response.getHeader("Content-Disposition") == null;
      if (compressible) {
        // Order matters: the header has to be on the response before the first byte reaches the
        // container, or it goes out describing a body that is already on its way.
        response.setHeaderDirect("Content-Encoding", "gzip");
        if (response.getHeader("Vary") == null) {
          // Normally the filter already set this. A reset() in between clears every header, so
          // re-assert it rather than let a compressed body go out without it -- guarded, or the
          // ordinary path would carry the header twice.
          response.addHeader("Vary", "Accept-Encoding");
        }
        underlying = response.underlyingOutputStream();
        gzip = new GZIPOutputStream(underlying, true);
      } else {
        response.restoreDeclaredContentLength();
        passthrough = response.underlyingOutputStream();
      }
      pending.writeTo(target());
      pending.reset();
    }

    @Override
    public void flush() throws IOException {
      if (finished) {
        return;
      }
      if (!decided) {
        // A flush is the application saying "send this now", so the decision can no longer wait.
        decide();
      }
      target().flush();
    }

    /**
     * Discards everything written so far. Only legal while the response is uncommitted, which is
     * exactly when the container allows reset() and resetBuffer().
     */
    void reset() {
      if (gzip == null && passthrough == null) {
        pending.reset();
        decided = false;
      }
    }

    /**
     * Ends the response.
     *
     * <p>A body that never crossed the threshold is written out uncompressed here, with its exact
     * length -- known now because all of it is in hand -- so a short response is sent whole rather
     * than chunked.
     */
    void finish() throws IOException {
      if (finished) {
        return;
      }
      finished = true;
      if (!decided) {
        decided = true;
        passthrough = response.underlyingOutputStream();
        if (response.hasDeclaredContentLength()) {
          response.restoreDeclaredContentLength();
        } else if (!response.isCommitted()) {
          response.setContentLengthDirect(pending.size());
        }
        pending.writeTo(passthrough);
        pending.reset();
        return;
      }
      if (gzip != null) {
        // finish() writes the gzip trailer. Without it the client sees a truncated member and
        // discards the whole body.
        gzip.finish();
        underlying.flush();
      }
    }

    /**
     * Terminates the gzip member if the container closes the stream.
     *
     * <p>Tomcat does not take this path today: at the end of a forward it unwraps to its own
     * response facade rather than closing this stream, which is why the trailer is written from the
     * filter's post-chain cleanup instead. Kept because finishing on close is what the contract
     * implies, and a container that does close the stream would otherwise truncate the body.
     */
    @Override
    public void close() throws IOException {
      finish();
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
      // The application writes synchronously throughout; nothing in the request path registers a
      // write listener, and honouring one here would mean handing out the uncompressed stream.
      throw new UnsupportedOperationException("Asynchronous writes are not supported");
    }
  }
}
