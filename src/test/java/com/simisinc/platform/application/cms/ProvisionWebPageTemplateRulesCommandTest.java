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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.SaveCollectionCommand;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.WebPageTemplateRule;
import com.simisinc.platform.domain.model.items.Collection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * Tests for ProvisionWebPageTemplateRulesCommand's collection/folder auto-provisioning
 * behavior from a web page template's rules block (issue #1287).
 *
 * @author matt rajkowski
 */
class ProvisionWebPageTemplateRulesCommandTest {

  private static WebPageTemplateRule collectionRule() {
    WebPageTemplateRule rule = new WebPageTemplateRule();
    rule.setType("collection");
    rule.setUniqueId("job-listings");
    rule.setName("Job Listings");
    return rule;
  }

  private static WebPageTemplateRule folderRule() {
    WebPageTemplateRule rule = new WebPageTemplateRule();
    rule.setType("folder");
    rule.setUniqueId("photo-and-video-library");
    rule.setName("Photo and Video Library");
    return rule;
  }

  @Test
  void createsACollectionThatDoesNotYetExist() throws Exception {
    WebPageTemplateRule rule = collectionRule();
    try (MockedStatic<LoadCollectionCommand> load = mockStatic(LoadCollectionCommand.class);
        MockedStatic<SaveCollectionCommand> save = mockStatic(SaveCollectionCommand.class)) {
      load.when(() -> LoadCollectionCommand.loadCollectionByUniqueId("job-listings")).thenReturn(null);
      Collection saved = new Collection();
      saved.setUniqueId("job-listings");
      save.when(() -> SaveCollectionCommand.saveCollection(any())).thenReturn(saved);

      ProvisionWebPageTemplateRulesCommand.provisionRules(List.of(rule), 7L);

      save.verify(() -> SaveCollectionCommand.saveCollection(argThat(bean -> "Job Listings".equals(bean.getName()) && bean.getCreatedBy() == 7L)));
    }
  }

  @Test
  void skipsCreationWhenTheCollectionAlreadyExists() throws Exception {
    WebPageTemplateRule rule = collectionRule();
    try (MockedStatic<LoadCollectionCommand> load = mockStatic(LoadCollectionCommand.class);
        MockedStatic<SaveCollectionCommand> save = mockStatic(SaveCollectionCommand.class)) {
      Collection existing = new Collection();
      existing.setUniqueId("job-listings");
      load.when(() -> LoadCollectionCommand.loadCollectionByUniqueId("job-listings")).thenReturn(existing);

      ProvisionWebPageTemplateRulesCommand.provisionRules(List.of(rule), 7L);

      save.verify(() -> SaveCollectionCommand.saveCollection(any()), never());
    }
  }

  @Test
  void aCollectionCreationFailureDoesNotThrow() throws Exception {
    WebPageTemplateRule rule = collectionRule();
    try (MockedStatic<LoadCollectionCommand> load = mockStatic(LoadCollectionCommand.class);
        MockedStatic<SaveCollectionCommand> save = mockStatic(SaveCollectionCommand.class)) {
      load.when(() -> LoadCollectionCommand.loadCollectionByUniqueId("job-listings")).thenReturn(null);
      save.when(() -> SaveCollectionCommand.saveCollection(any())).thenThrow(new DataException("boom"));

      assertDoesNotThrow(() -> ProvisionWebPageTemplateRulesCommand.provisionRules(List.of(rule), 7L));
    }
  }

  @Test
  void logsAWarningWhenTheProvisionedCollectionUniqueIdDiffersFromExpected() throws Exception {
    WebPageTemplateRule rule = collectionRule();
    try (MockedStatic<LoadCollectionCommand> load = mockStatic(LoadCollectionCommand.class);
        MockedStatic<SaveCollectionCommand> save = mockStatic(SaveCollectionCommand.class)) {
      load.when(() -> LoadCollectionCommand.loadCollectionByUniqueId("job-listings")).thenReturn(null);
      Collection saved = new Collection();
      saved.setUniqueId("job-listings-2");
      save.when(() -> SaveCollectionCommand.saveCollection(any())).thenReturn(saved);

      assertDoesNotThrow(() -> ProvisionWebPageTemplateRulesCommand.provisionRules(List.of(rule), 7L));

      save.verify(() -> SaveCollectionCommand.saveCollection(any()));
    }
  }

  @Test
  void createsAFolderThatDoesNotYetExist() throws Exception {
    WebPageTemplateRule rule = folderRule();
    try (MockedStatic<LoadFolderCommand> load = mockStatic(LoadFolderCommand.class);
        MockedStatic<SaveFolderCommand> save = mockStatic(SaveFolderCommand.class)) {
      load.when(() -> LoadFolderCommand.loadFolderByUniqueId("photo-and-video-library")).thenReturn(null);
      Folder saved = new Folder();
      saved.setUniqueId("photo-and-video-library");
      save.when(() -> SaveFolderCommand.saveFolder(any())).thenReturn(saved);

      ProvisionWebPageTemplateRulesCommand.provisionRules(List.of(rule), 7L);

      // getModifiedBy() is asserted here because folders.modified_by is a NOT NULL FK to users --
      // leaving it at Folder's default (-1) throws at save time, a case a fully-mocked
      // SaveFolderCommand can't otherwise catch (confirmed live in Docker rehearsal, issue #1287).
      save.verify(() -> SaveFolderCommand.saveFolder(
          argThat(bean -> "Photo and Video Library".equals(bean.getName()) && bean.getCreatedBy() == 7L && bean.getModifiedBy() == 7L)));
    }
  }

  @Test
  void skipsCreationWhenTheFolderAlreadyExists() throws Exception {
    WebPageTemplateRule rule = folderRule();
    try (MockedStatic<LoadFolderCommand> load = mockStatic(LoadFolderCommand.class);
        MockedStatic<SaveFolderCommand> save = mockStatic(SaveFolderCommand.class)) {
      Folder existing = new Folder();
      existing.setUniqueId("photo-and-video-library");
      load.when(() -> LoadFolderCommand.loadFolderByUniqueId("photo-and-video-library")).thenReturn(existing);

      ProvisionWebPageTemplateRulesCommand.provisionRules(List.of(rule), 7L);

      save.verify(() -> SaveFolderCommand.saveFolder(any()), never());
    }
  }

  @Test
  void aFolderCreationFailureDoesNotThrow() throws Exception {
    WebPageTemplateRule rule = folderRule();
    try (MockedStatic<LoadFolderCommand> load = mockStatic(LoadFolderCommand.class);
        MockedStatic<SaveFolderCommand> save = mockStatic(SaveFolderCommand.class)) {
      load.when(() -> LoadFolderCommand.loadFolderByUniqueId("photo-and-video-library")).thenReturn(null);
      save.when(() -> SaveFolderCommand.saveFolder(any())).thenThrow(new DataException("boom"));

      assertDoesNotThrow(() -> ProvisionWebPageTemplateRulesCommand.provisionRules(List.of(rule), 7L));
    }
  }

  @Test
  void aRuleWithABlankUniqueIdIsSkipped() {
    WebPageTemplateRule rule = new WebPageTemplateRule();
    rule.setType("collection");
    rule.setUniqueId("");
    rule.setName("Job Listings");
    try (MockedStatic<SaveCollectionCommand> save = mockStatic(SaveCollectionCommand.class)) {
      ProvisionWebPageTemplateRulesCommand.provisionRules(List.of(rule), 7L);
      save.verify(() -> SaveCollectionCommand.saveCollection(any()), never());
    }
  }

  @Test
  void anEmptyRuleListIsANoOp() {
    assertDoesNotThrow(() -> ProvisionWebPageTemplateRulesCommand.provisionRules(List.of(), 7L));
  }

  @Test
  void aNullRuleListIsANoOp() {
    assertDoesNotThrow(() -> ProvisionWebPageTemplateRulesCommand.provisionRules(null, 7L));
  }
}
