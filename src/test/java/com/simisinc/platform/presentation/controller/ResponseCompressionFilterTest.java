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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the filter that compresses generated responses.
 *
 * <p>The test that matters most is {@code neverSendsTheUncompressedLengthBesideACompressedBody} --
 * it is the shape of the #1832 outage, where a body was gzipped while Content-Length still
 * described the original file.
 */
class ResponseCompressionFilterTest {

  /** Big enough to clear COMPRESS_THRESHOLD_BYTES, and repetitive so gzip visibly shrinks it. */
  private static final String LARGE_HTML =
      "<html><body>" + "the quick brown fox jumps over the lazy dog ".repeat(200) + "</body></html>";

  // ---------------------------------------------------------------- test doubles

  /** Records what the container would have received. */
  private static class Recorder {
    final Map<String, String> headers = new HashMap<>();
    final List<String> addedHeaders = new ArrayList<>();
    final ByteArrayOutputStream body = new ByteArrayOutputStream();
    String contentType;
    long contentLength = -1;
    boolean committed = false;
  }

  private static HttpServletResponse recordingResponse(Recorder recorder) throws IOException {
    HttpServletResponse response = mock(HttpServletResponse.class);

    ServletOutputStream out = new ServletOutputStream() {
      @Override
      public void write(int b) {
        recorder.body.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) {
        recorder.body.write(b, off, len);
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setWriteListener(WriteListener writeListener) {
        // not used
      }
    };
    when(response.getOutputStream()).thenReturn(out);
    when(response.getCharacterEncoding()).thenReturn("UTF-8");
    when(response.isCommitted()).thenAnswer(i -> recorder.committed);

    doAnswer(i -> {
      String value = i.getArgument(1);
      if (value == null) {
        recorder.headers.remove(i.getArgument(0));
      } else {
        recorder.headers.put(i.getArgument(0), value);
      }
      return null;
    }).when(response).setHeader(anyString(), any());
    doAnswer(i -> {
      String name = i.getArgument(0);
      recorder.addedHeaders.add(name + ": " + i.getArgument(1));
      // a real response reports an added header from getHeader() too; without this the filter
      // cannot tell whether it already set Vary
      recorder.headers.putIfAbsent(name, String.valueOf((Object) i.getArgument(1)));
      return null;
    }).when(response).addHeader(anyString(), any());
    when(response.getHeader(anyString())).thenAnswer(i -> recorder.headers.get(i.getArgument(0)));

    // setIntHeader has to be recorded too: without it a Content-Length arriving by that route
    // disappears into the mock, and a test asserting it was intercepted passes either way.
    doAnswer(i -> {
      String name = i.getArgument(0);
      Integer value = i.getArgument(1);
      if ("content-length".equalsIgnoreCase(name)) {
        recorder.contentLength = value.longValue();
      } else {
        recorder.headers.put(name, String.valueOf(value));
      }
      return null;
    }).when(response).setIntHeader(anyString(), anyInt());

    doAnswer(i -> {
      recorder.contentType = i.getArgument(0);
      return null;
    }).when(response).setContentType(anyString());
    when(response.getContentType()).thenAnswer(i -> recorder.contentType);

    doAnswer(i -> {
      recorder.contentLength = ((Integer) i.getArgument(0)).longValue();
      return null;
    }).when(response).setContentLength(anyInt());
    doAnswer(i -> {
      recorder.contentLength = i.getArgument(0);
      return null;
    }).when(response).setContentLengthLong(anyLong());

    return response;
  }

  private static HttpServletRequest request(String uri, String acceptEncoding, String range) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletContext context = mock(ServletContext.class);
    when(context.getContextPath()).thenReturn("");
    when(request.getServletContext()).thenReturn(context);
    when(request.getRequestURI()).thenReturn(uri);
    when(request.getHeader("Accept-Encoding")).thenReturn(acceptEncoding);
    when(request.getHeader("Range")).thenReturn(range);
    return request;
  }

  private static HttpServletRequest gzipRequest(String uri) {
    return request(uri, "gzip, deflate, br", null);
  }

  /** A servlet that sets a content type and writes a body through the output stream. */
  private static FilterChain servletWriting(String contentType, String body, Integer declaredLength) {
    return (req, res) -> {
      HttpServletResponse response = (HttpServletResponse) res;
      response.setContentType(contentType);
      if (declaredLength != null) {
        response.setContentLength(declaredLength);
      }
      response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    };
  }

  private static String gunzip(byte[] compressed) throws IOException {
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Recorder run(HttpServletRequest request, FilterChain chain) throws Exception {
    Recorder recorder = new Recorder();
    HttpServletResponse response = recordingResponse(recorder);
    new ResponseCompressionFilter().doFilter(request, response, chain);
    return recorder;
  }

  // ---------------------------------------------------------------- the #1832 shape

  @Test
  void neverSendsTheUncompressedLengthBesideACompressedBody() throws Exception {
    int uncompressedLength = LARGE_HTML.getBytes(StandardCharsets.UTF_8).length;
    Recorder recorder = run(gzipRequest("/contact-us"),
        servletWriting("text/html;charset=UTF-8", LARGE_HTML, uncompressedLength));

    Assertions.assertEquals("gzip", recorder.headers.get("Content-Encoding"),
        "the body was compressed");
    Assertions.assertNotEquals(uncompressedLength, recorder.contentLength,
        "the uncompressed length must not survive onto a compressed body -- this is #1832");
    Assertions.assertTrue(recorder.body.size() < uncompressedLength,
        "fewer bytes on the wire than the original");
    Assertions.assertEquals(LARGE_HTML, gunzip(recorder.body.toByteArray()),
        "and the client can still read it back");
  }

  @Test
  void aDeclaredLengthSurvivesWhenNothingIsCompressed() throws Exception {
    String body = "x".repeat(4000);
    Recorder recorder = run(gzipRequest("/assets/img/logo.png"),
        servletWriting("image/png", body, body.length()));

    Assertions.assertNull(recorder.headers.get("Content-Encoding"),
        "an image is already compressed");
    Assertions.assertEquals(body.length(), recorder.contentLength,
        "so the application's own length has to be passed through untouched");
    Assertions.assertEquals(body, recorder.body.toString(StandardCharsets.UTF_8));
  }

  // ---------------------------------------------------------------- what gets compressed

  @Nested
  class Compresses {

    @Test
    void htmlWrittenThroughTheOutputStream() throws Exception {
      Recorder recorder = run(gzipRequest("/"), servletWriting("text/html", LARGE_HTML, null));
      Assertions.assertEquals("gzip", recorder.headers.get("Content-Encoding"));
      Assertions.assertEquals(LARGE_HTML, gunzip(recorder.body.toByteArray()));
    }

    @Test
    void htmlWrittenThroughTheWriter() throws Exception {
      Recorder recorder = run(gzipRequest("/"), (req, res) -> {
        HttpServletResponse response = (HttpServletResponse) res;
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write(LARGE_HTML);
      });
      Assertions.assertEquals("gzip", recorder.headers.get("Content-Encoding"),
          "JSPs write through the writer, so this is the path that actually matters");
      Assertions.assertEquals(LARGE_HTML, gunzip(recorder.body.toByteArray()));
    }

    @Test
    void andAlwaysSaysTheBodyVariesOnTheRequestEncoding() throws Exception {
      Recorder recorder = run(gzipRequest("/"), servletWriting("text/html", LARGE_HTML, null));
      long varyHeaders = recorder.addedHeaders.stream()
          .filter("Vary: Accept-Encoding"::equals).count();
      Assertions.assertEquals(1, varyHeaders,
          "exactly once -- a shared cache must not serve a gzip body to a client that did not ask "
              + "for one, and the header must not be repeated on the way to saying so");
    }
  }

  // ---------------------------------------------------------------- what does not

  @Nested
  class LeavesAlone {

    @Test
    void aBodyTooSmallToBeWorthIt() throws Exception {
      String small = "<html>ok</html>";
      Recorder recorder = run(gzipRequest("/"), servletWriting("text/html", small, null));

      Assertions.assertNull(recorder.headers.get("Content-Encoding"),
          "gzip framing would cost more than it saves");
      Assertions.assertEquals(small, recorder.body.toString(StandardCharsets.UTF_8));
      Assertions.assertEquals(small.length(), recorder.contentLength,
          "and the whole body is in hand, so it can be sent with an exact length");
    }

    @Test
    void aClientThatDidNotAskForGzip() throws Exception {
      Recorder recorder = run(request("/", null, null),
          servletWriting("text/html", LARGE_HTML, null));
      Assertions.assertNull(recorder.headers.get("Content-Encoding"));
      Assertions.assertEquals(LARGE_HTML, recorder.body.toString(StandardCharsets.UTF_8));
    }

    @Test
    void aClientThatRefusedGzip() throws Exception {
      Recorder recorder = run(request("/", "gzip;q=0, deflate", null),
          servletWriting("text/html", LARGE_HTML, null));
      Assertions.assertNull(recorder.headers.get("Content-Encoding"),
          "q=0 is a refusal, not a request");
    }

    @Test
    void theStaticFilesTheDefaultServletServes() throws Exception {
      Recorder recorder = run(gzipRequest("/css/platform.css"),
          servletWriting("text/css", LARGE_HTML, null));

      Assertions.assertNull(recorder.headers.get("Content-Encoding"),
          "CSS is compressed at the edge where it is cacheable; skipping it here also keeps this "
              + "filter off the sendfile path that #1832 corrupted");
      Assertions.assertEquals(LARGE_HTML, recorder.body.toString(StandardCharsets.UTF_8));
    }

    @Test
    void aRangeRequest() throws Exception {
      Recorder recorder = run(request("/", "gzip", "bytes=0-1023"),
          servletWriting("text/html", LARGE_HTML, null));
      Assertions.assertNull(recorder.headers.get("Content-Encoding"),
          "a ranged body and a compressed body disagree on the byte count for one URL");
    }

    @Test
    void aBodyTheApplicationHasAlreadyEncoded() throws Exception {
      Recorder recorder = run(gzipRequest("/export"), (req, res) -> {
        HttpServletResponse response = (HttpServletResponse) res;
        response.setContentType("application/json");
        response.setHeader("Content-Encoding", "gzip");
        response.getOutputStream().write(LARGE_HTML.getBytes(StandardCharsets.UTF_8));
      });
      Assertions.assertEquals(LARGE_HTML, recorder.body.toString(StandardCharsets.UTF_8),
          "compressing an already-encoded body would double-encode it");
    }

    @Test
    void aFileDownloadStreamedByMultipartFileSender() throws Exception {
      // The shape MultipartFileSender produces: it resets, writes its own Content-Length and
      // Content-Range, then streams the bytes itself.
      Recorder recorder = run(gzipRequest("/assets/file/42"), (req, res) -> {
        HttpServletResponse response = (HttpServletResponse) res;
        response.reset();
        response.setContentType("text/plain");
        response.setHeader("Content-Disposition", "attachment;filename=\"notes.txt\"");
        response.setHeader("Content-Range", "bytes 0-" + (LARGE_HTML.length() - 1)
            + "/" + LARGE_HTML.length());
        response.setHeader("Content-Length", String.valueOf(LARGE_HTML.length()));
        response.getOutputStream().write(LARGE_HTML.getBytes(StandardCharsets.UTF_8));
      });

      Assertions.assertNull(recorder.headers.get("Content-Encoding"),
          "compressing under a hand-written Content-Range leaves it describing bytes that are gone");
      Assertions.assertEquals(LARGE_HTML.length(), recorder.contentLength,
          "and its own length has to reach the client intact");
      Assertions.assertEquals(LARGE_HTML, recorder.body.toString(StandardCharsets.UTF_8));
    }

    @Test
    void aLengthDeclaredThroughSetIntHeader() throws Exception {
      // A third route to the same header, and the one an override is easiest to forget
      Recorder recorder = run(gzipRequest("/"), (req, res) -> {
        HttpServletResponse response = (HttpServletResponse) res;
        response.setContentType("text/html");
        response.setIntHeader("Content-Length", LARGE_HTML.length());
        response.getOutputStream().write(LARGE_HTML.getBytes(StandardCharsets.UTF_8));
      });

      Assertions.assertEquals("gzip", recorder.headers.get("Content-Encoding"));
      Assertions.assertNull(recorder.headers.get("Content-Length"),
          "setIntHeader must not slip an uncompressed length past the interception");
      Assertions.assertNotEquals(LARGE_HTML.length(), recorder.contentLength);
      Assertions.assertEquals(LARGE_HTML, gunzip(recorder.body.toByteArray()));
    }

    @Test
    void aResponseWithNoContentTypeAtAll() throws Exception {
      Recorder recorder = run(gzipRequest("/"), (req, res) ->
          res.getOutputStream().write(LARGE_HTML.getBytes(StandardCharsets.UTF_8)));
      Assertions.assertNull(recorder.headers.get("Content-Encoding"),
          "an allowlist means the unknown case is passed through, not guessed at");
    }
  }

  // ---------------------------------------------------------------- redirects and errors

  @Test
  void aRedirectIsNotGivenAContentEncoding() throws Exception {
    Recorder recorder = run(gzipRequest("/old-page"), (req, res) ->
        ((HttpServletResponse) res).sendRedirect("/new-page"));
    Assertions.assertNull(recorder.headers.get("Content-Encoding"));
  }

  @Test
  void anErrorPageIsNotGivenAContentEncoding() throws Exception {
    Recorder recorder = run(gzipRequest("/missing"), (req, res) -> {
      HttpServletResponse response = (HttpServletResponse) res;
      response.setContentType("text/html");
      response.sendError(404);
    });
    Assertions.assertNull(recorder.headers.get("Content-Encoding"),
        "the container generates the error body after this wrapper is out of the picture");
  }

  @Test
  void aFailingServletStillTerminatesTheStream() throws Exception {
    Recorder recorder = new Recorder();
    HttpServletResponse response = recordingResponse(recorder);
    FilterChain chain = (req, res) -> {
      HttpServletResponse r = (HttpServletResponse) res;
      r.setContentType("text/html");
      r.getOutputStream().write(LARGE_HTML.getBytes(StandardCharsets.UTF_8));
      throw new IOException("rendering blew up");
    };

    Assertions.assertThrows(IOException.class,
        () -> new ResponseCompressionFilter().doFilter(gzipRequest("/"), response, chain));
    Assertions.assertEquals(LARGE_HTML, gunzip(recorder.body.toByteArray()),
        "an unfinished gzip member truncates the body with no error the client can see");
  }

  @Test
  void aResponseWithHeadersButNoBodyKeepsItsDeclaredLength() throws Exception {
    // Nothing is ever written, so the stream never decides -- but the length was still taken on
    // the way past and has to be given back
    Recorder recorder = run(gzipRequest("/no-content"), (req, res) -> {
      HttpServletResponse response = (HttpServletResponse) res;
      response.setContentType("text/html");
      response.setContentLength(0);
    });
    Assertions.assertEquals(0, recorder.contentLength,
        "a swallowed Content-Length that is never restored is a header silently dropped");
    Assertions.assertNull(recorder.headers.get("Content-Encoding"));
  }

  @Test
  void aServletThatClosesTheStreamStillGetsACompleteBody() throws Exception {
    // Tomcat does not close this stream today, but a container that did must not truncate the
    // body -- and closing then finishing again must not write a second trailer
    Recorder recorder = run(gzipRequest("/"), (req, res) -> {
      HttpServletResponse response = (HttpServletResponse) res;
      response.setContentType("text/html");
      response.getOutputStream().write(LARGE_HTML.getBytes(StandardCharsets.UTF_8));
      response.getOutputStream().close();
    });
    Assertions.assertEquals("gzip", recorder.headers.get("Content-Encoding"));
    Assertions.assertEquals(LARGE_HTML, gunzip(recorder.body.toByteArray()),
        "closing finishes the gzip member, and the filter's own cleanup must not double-finish it");
  }

  // ---------------------------------------------------------------- header parsing

  @Nested
  class AcceptsGzip {

    @Test
    void readsTheQualityValueRatherThanSearchingForTheWord() {
      Assertions.assertTrue(ResponseCompressionFilter.acceptsGzip("gzip"));
      Assertions.assertTrue(ResponseCompressionFilter.acceptsGzip("deflate, gzip;q=0.9"));
      Assertions.assertTrue(ResponseCompressionFilter.acceptsGzip("GZIP"));
      Assertions.assertTrue(ResponseCompressionFilter.acceptsGzip("*"));

      Assertions.assertFalse(ResponseCompressionFilter.acceptsGzip("gzip;q=0"));
      Assertions.assertFalse(ResponseCompressionFilter.acceptsGzip("gzip;q=0.0"));
      Assertions.assertFalse(ResponseCompressionFilter.acceptsGzip("br, deflate"));
      Assertions.assertFalse(ResponseCompressionFilter.acceptsGzip(""));
      Assertions.assertFalse(ResponseCompressionFilter.acceptsGzip(null));
    }
  }

  @Nested
  class CompressibleTypes {

    @Test
    void ignoreTheCharsetParameterAndTheCase() {
      Assertions.assertTrue(ResponseCompressionFilter.isCompressibleType("text/html;charset=UTF-8"));
      Assertions.assertTrue(ResponseCompressionFilter.isCompressibleType("TEXT/HTML"));
      Assertions.assertTrue(ResponseCompressionFilter.isCompressibleType("application/json"));
      Assertions.assertTrue(ResponseCompressionFilter.isCompressibleType("image/svg+xml"));
    }

    @Test
    void excludeWhatIsAlreadyCompressed() {
      Assertions.assertFalse(ResponseCompressionFilter.isCompressibleType("image/png"));
      Assertions.assertFalse(ResponseCompressionFilter.isCompressibleType("font/woff2"));
      Assertions.assertFalse(ResponseCompressionFilter.isCompressibleType("application/zip"));
      Assertions.assertFalse(ResponseCompressionFilter.isCompressibleType("video/mp4"));
      Assertions.assertFalse(ResponseCompressionFilter.isCompressibleType(null));
    }
  }
}
