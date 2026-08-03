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

package com.simisinc.platform.presentation.widgets.admin.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class FolderListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"folderList\">\n" +
        "  <title>Folders</title>\n" +
        "</widget>");

    List<Folder> folderList = new ArrayList<>();
    Folder folder = new Folder();
    folder.setId(1L);
    folderList.add(folder);

    try (MockedStatic<FolderRepository> folderRepositoryMockedStatic = mockStatic(FolderRepository.class)) {
      folderRepositoryMockedStatic.when(FolderRepository::findAll).thenReturn(folderList);

      // Use admin
      setRoles(widgetContext, ADMIN);

      // Execute the widget
      FolderListWidget widget = new FolderListWidget();
      widget.execute(widgetContext);
    }

    // Verify
    Assertions.assertEquals(FolderListWidget.JSP, widgetContext.getJsp());
    Assertions.assertEquals("Folders", request.getAttribute("title"));
    List<Folder> folderListRequest = (List) request.getAttribute("folderList");
    Assertions.assertEquals(folder.getId(), folderListRequest.get(0).getId());
  }

  @Test
  void executeFiltersByQueryCaseInsensitively() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"folderList\">\n" +
        "  <title>Folders</title>\n" +
        "</widget>");

    Folder hrForms = new Folder();
    hrForms.setId(1L);
    hrForms.setName("HR Forms");
    Folder laborLaws = new Folder();
    laborLaws.setId(2L);
    laborLaws.setName("Alabama Labor Laws");
    List<Folder> folderList = new ArrayList<>();
    folderList.add(hrForms);
    folderList.add(laborLaws);

    addQueryParameter(widgetContext, "query", "labor");

    try (MockedStatic<FolderRepository> folderRepositoryMockedStatic = mockStatic(FolderRepository.class)) {
      folderRepositoryMockedStatic.when(FolderRepository::findAll).thenReturn(folderList);
      setRoles(widgetContext, ADMIN);
      new FolderListWidget().execute(widgetContext);
    }

    List<Folder> folderListRequest = (List) request.getAttribute("folderList");
    Assertions.assertEquals(1, folderListRequest.size());
    Assertions.assertEquals(laborLaws.getId(), folderListRequest.get(0).getId());
  }

  @Test
  void executeReturnsAllFoldersWhenQueryIsBlank() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"folderList\">\n" +
        "  <title>Folders</title>\n" +
        "</widget>");

    List<Folder> folderList = new ArrayList<>();
    Folder folder = new Folder();
    folder.setId(1L);
    folder.setName("HR Forms");
    folderList.add(folder);

    try (MockedStatic<FolderRepository> folderRepositoryMockedStatic = mockStatic(FolderRepository.class)) {
      folderRepositoryMockedStatic.when(FolderRepository::findAll).thenReturn(folderList);
      setRoles(widgetContext, ADMIN);
      new FolderListWidget().execute(widgetContext);
    }

    List<Folder> folderListRequest = (List) request.getAttribute("folderList");
    Assertions.assertEquals(1, folderListRequest.size());
  }

  @Test
  void executeDefaultsToNameAscendingSort() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"folderList\">\n" +
        "  <title>Folders</title>\n" +
        "</widget>");

    Folder zFolder = new Folder();
    zFolder.setId(1L);
    zFolder.setName("Zebra Forms");
    Folder aFolder = new Folder();
    aFolder.setId(2L);
    aFolder.setName("Alabama Labor Laws");
    List<Folder> folderList = new ArrayList<>();
    folderList.add(zFolder);
    folderList.add(aFolder);

    try (MockedStatic<FolderRepository> folderRepositoryMockedStatic = mockStatic(FolderRepository.class)) {
      folderRepositoryMockedStatic.when(FolderRepository::findAll).thenReturn(folderList);
      setRoles(widgetContext, ADMIN);
      new FolderListWidget().execute(widgetContext);
    }

    List<Folder> folderListRequest = (List) request.getAttribute("folderList");
    Assertions.assertEquals("Alabama Labor Laws", folderListRequest.get(0).getName());
    Assertions.assertEquals("Zebra Forms", folderListRequest.get(1).getName());
    Assertions.assertEquals("name", request.getAttribute("sort"));
  }

  @Test
  void executeSortsByNameDescendingWhenRequested() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"folderList\">\n" +
        "  <title>Folders</title>\n" +
        "</widget>");

    Folder aFolder = new Folder();
    aFolder.setId(1L);
    aFolder.setName("Alabama Labor Laws");
    Folder zFolder = new Folder();
    zFolder.setId(2L);
    zFolder.setName("Zebra Forms");
    List<Folder> folderList = new ArrayList<>();
    folderList.add(aFolder);
    folderList.add(zFolder);

    addQueryParameter(widgetContext, "sort", "name_desc");

    try (MockedStatic<FolderRepository> folderRepositoryMockedStatic = mockStatic(FolderRepository.class)) {
      folderRepositoryMockedStatic.when(FolderRepository::findAll).thenReturn(folderList);
      setRoles(widgetContext, ADMIN);
      new FolderListWidget().execute(widgetContext);
    }

    List<Folder> folderListRequest = (List) request.getAttribute("folderList");
    Assertions.assertEquals("Zebra Forms", folderListRequest.get(0).getName());
    Assertions.assertEquals("Alabama Labor Laws", folderListRequest.get(1).getName());
  }

  @Test
  void executeSortsByFileCountDescendingWhenRequested() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"folderList\">\n" +
        "  <title>Folders</title>\n" +
        "</widget>");

    Folder fewFiles = new Folder();
    fewFiles.setId(1L);
    fewFiles.setName("A Folder");
    fewFiles.setFileCount(2);
    Folder manyFiles = new Folder();
    manyFiles.setId(2L);
    manyFiles.setName("B Folder");
    manyFiles.setFileCount(50);
    List<Folder> folderList = new ArrayList<>();
    folderList.add(fewFiles);
    folderList.add(manyFiles);

    addQueryParameter(widgetContext, "sort", "files_desc");

    try (MockedStatic<FolderRepository> folderRepositoryMockedStatic = mockStatic(FolderRepository.class)) {
      folderRepositoryMockedStatic.when(FolderRepository::findAll).thenReturn(folderList);
      setRoles(widgetContext, ADMIN);
      new FolderListWidget().execute(widgetContext);
    }

    List<Folder> folderListRequest = (List) request.getAttribute("folderList");
    Assertions.assertEquals(50, folderListRequest.get(0).getFileCount());
    Assertions.assertEquals(2, folderListRequest.get(1).getFileCount());
  }

  @Test
  void executeSortWorksTogetherWithQueryFilter() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"folderList\">\n" +
        "  <title>Folders</title>\n" +
        "</widget>");

    Folder hrFormsSmall = new Folder();
    hrFormsSmall.setId(1L);
    hrFormsSmall.setName("HR Forms Small");
    hrFormsSmall.setFileCount(1);
    Folder hrFormsBig = new Folder();
    hrFormsBig.setId(2L);
    hrFormsBig.setName("HR Forms Big");
    hrFormsBig.setFileCount(10);
    Folder laborLaws = new Folder();
    laborLaws.setId(3L);
    laborLaws.setName("Alabama Labor Laws");
    laborLaws.setFileCount(100);
    List<Folder> folderList = new ArrayList<>();
    folderList.add(hrFormsSmall);
    folderList.add(hrFormsBig);
    folderList.add(laborLaws);

    addQueryParameter(widgetContext, "query", "HR");
    addQueryParameter(widgetContext, "sort", "files_desc");

    try (MockedStatic<FolderRepository> folderRepositoryMockedStatic = mockStatic(FolderRepository.class)) {
      folderRepositoryMockedStatic.when(FolderRepository::findAll).thenReturn(folderList);
      setRoles(widgetContext, ADMIN);
      new FolderListWidget().execute(widgetContext);
    }

    List<Folder> folderListRequest = (List) request.getAttribute("folderList");
    Assertions.assertEquals(2, folderListRequest.size());
    Assertions.assertEquals("HR Forms Big", folderListRequest.get(0).getName());
    Assertions.assertEquals("HR Forms Small", folderListRequest.get(1).getName());
  }
}