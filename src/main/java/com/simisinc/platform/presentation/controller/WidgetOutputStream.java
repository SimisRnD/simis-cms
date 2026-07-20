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

import org.apache.commons.io.output.WriterOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.BufferedOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/6/18 10:51 AM
 */
public class WidgetOutputStream extends ServletOutputStream {

  private final BufferedOutputStream bufferedOut;

  public WidgetOutputStream(CharArrayWriter charArray) throws IOException {
    this.bufferedOut = new BufferedOutputStream(
        WriterOutputStream.builder()
            .setWriter(charArray)
            .setCharset(StandardCharsets.UTF_8)
            .get(),
        16384);
  }

  @Override
  public void write(int b) throws IOException {
    this.bufferedOut.write(b);
  }

  /**
   * This is needed to get correct full cms without anything missing
   */
  @Override
  public void flush() throws IOException {
    this.bufferedOut.flush();
    super.flush();
  }

  @Override
  public void close() throws IOException {
    this.bufferedOut.close();
    super.close();
  }

  @Override
  public boolean isReady() {
    return true;
  }

  @Override
  public void setWriteListener(WriteListener writeListener) {
  }
}
