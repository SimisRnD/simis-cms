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

package com.simisinc.platform.application.admin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

/**
 * Writes text to a file
 *
 * @author matt rajkowski
 * @created 7/14/22 3:50 PM
 */
public class SaveTextFileCommand {

  private static Log LOG = LogFactory.getLog(SaveTextFileCommand.class);

  public static File save(String content, File file) {
    if (content == null) {
      return null;
    }
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write(content);
    } catch (Exception e) {
      LOG.warn("Could not write file", e);
      return null;
    }
    return file;
  }
}
