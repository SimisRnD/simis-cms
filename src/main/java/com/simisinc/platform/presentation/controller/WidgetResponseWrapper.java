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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/6/18 10:50 AM
 */
public class WidgetResponseWrapper extends HttpServletResponseWrapper {

  private static Log LOG = LogFactory.getLog(WidgetResponseWrapper.class);

  private final CharArrayWriter charArray = new CharArrayWriter();
  private ServletOutputStream servletOutputStream;
  private PrintWriter writer;
  private WidgetOutputStream copier;

  public WidgetResponseWrapper(HttpServletResponse response) {
    super(response);
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    if (servletOutputStream != null) {
      throw new IllegalStateException("getOutputStream() has already been called on this response.");
    }

//    LOG.info("Returning writer...");
    if (writer == null) {
      copier = new WidgetOutputStream(this.charArray);
      writer = new PrintWriter(new OutputStreamWriter(copier, getResponse().getCharacterEncoding()), true);
    }

    return writer;
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    if (servletOutputStream == null) {
//      LOG.info("Creating servletOutputStream...");
      servletOutputStream = new WidgetOutputStream(this.charArray);
    }
//    LOG.info("Returning servletOutputStream");
    return servletOutputStream;
  }

  public String getOutputAndClose() {
    if (this.servletOutputStream != null) {
      try {
//        LOG.info("Returning servletOutputStream...");
        // flush() is important to get complete cms and not last "buffered" part missing
        this.servletOutputStream.flush();
        return this.charArray.toString();
      } catch (Exception e) {
        LOG.error("output and close: " + e.getMessage());
      } finally {
        try {
          this.servletOutputStream.close();
        } catch (Exception e) {
          LOG.error("output and close: " + e.getMessage());
        }
      }
    } else if (copier != null) {
      try {
//        LOG.info("Returning writer output...");
        // flush() is important to get complete cms and not last "buffered" part missing
        this.writer.flush();
        this.copier.flush();
        return this.charArray.toString();
      } catch (Exception e) {
        LOG.error("output and close: " + e.getMessage());
      } finally {
        try {
          this.writer.close();
          this.copier.close();
        } catch (Exception e) {
          LOG.error("output and close: " + e.getMessage());
        }
      }
    }
    throw new IllegalStateException("Empty (null) servletOutputStream not allowed");
  }

  // not necessary to override getWriter() if getOutputStream() is used by the "application server".

}
