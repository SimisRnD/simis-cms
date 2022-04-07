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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/28/20 11:32 AM
 */
public class LoadTextFileCommand {

  private static Log LOG = LogFactory.getLog(LoadTextFileCommand.class);

  private static final int DEFAULT_BYTES = 32768;

  public static List<String[]> loadSomeBytes(Dataset dataset) throws DataException {
    return loadBytes(dataset, DEFAULT_BYTES);
  }

  public static List<String[]> loadBytes(Dataset dataset, int bytesToRead) throws DataException {

    // Get a file handle
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File serverFile = new File(serverRootPath + dataset.getFileServerPath());
    if (!serverFile.exists()) {
      return null;
    }

    // Read the file
    List<String[]> rows = new ArrayList<>();
    String result = null;
    InputStreamReader reader = null;
    try {
      CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
      decoder.onMalformedInput(CodingErrorAction.REPLACE);
      decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

      LOG.debug("Opening reader...");
      reader = new InputStreamReader(new FileInputStream(serverFile), decoder);

      LOG.debug("Reading bytes... " + bytesToRead);
      char[] bytes = new char[bytesToRead];
      BufferedReader bufferedReader = new BufferedReader(reader);
      int bytesRead = bufferedReader.read(bytes);
      bufferedReader.close();
      LOG.debug("Bytes read: " + bytesRead);

      result = new String(bytes);
    } catch (IOException e) {
      LOG.error(e);
      throw new DataException("File could not be read");
    } finally {
      if (reader != null)
        try {
          LOG.debug("Closing reader...");
          reader.close();
        } catch (IOException e) {
          LOG.error(e);
        }
    }
    rows.add(new String[]{result});
    return rows;
  }

  public static List<String[]> loadLines(Dataset dataset, int linesToReturn) throws DataException {

    // Get a file handle
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File serverFile = new File(serverRootPath + dataset.getFileServerPath());
    if (!serverFile.exists()) {
      return null;
    }

    // Read the file
    List<String[]> rows = new ArrayList<>();
    try (Stream<String> stream = Files.lines(serverFile.toPath())) {
      stream.limit(linesToReturn).forEach(line -> rows.add(new String[]{line}));
    } catch (Exception e) {
      LOG.error("File Error: " + e.getMessage());
      throw new DataException("File could not be read");
    }

    return rows;
  }
}
