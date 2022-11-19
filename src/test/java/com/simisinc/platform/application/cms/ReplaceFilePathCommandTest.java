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

package com.simisinc.platform.application.cms;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;

/**
 * @author matt rajkowski
 * @created 11/19/2022 9:53 AM
 */
class ReplaceFilePathCommandTest {

  @Test
  void updateFileReferences() {

    FileItem fileItem = new FileItem();
    fileItem.setId(351L);
    fileItem.setWebPath("12345");

    // Mock directory path
    try (MockedStatic<FileItemRepository> staticFileItemRepository = mockStatic(FileItemRepository.class)) {

      staticFileItemRepository.when(() -> FileItemRepository.findById(anyLong())).thenReturn(fileItem);

      String nullContent = ReplaceFilePathCommand.updateFileReferences(null);
      Assertions.assertNull(nullContent);

      String blankContent = ReplaceFilePathCommand.updateFileReferences("");
      Assertions.assertEquals("", blankContent);

      String fileContent = ReplaceFilePathCommand
          .updateFileReferences("\"/assets/view/20191217132132-351/document.pdf\" and 111-444 document");
      Assertions.assertEquals("\"/assets/view/12345-351/document.pdf\" and 111-444 document", fileContent);

      String urlContent = ReplaceFilePathCommand.updateFileReferences(
          " href=\"/assets/view/20191120110455-351?ref=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dvideo%3D1s\" and 111-444 document");
      Assertions.assertEquals(" href=\"/assets/view/12345-351?ref=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dvideo%3D1s\" and 111-444 document",
          urlContent);

    }
  }

}