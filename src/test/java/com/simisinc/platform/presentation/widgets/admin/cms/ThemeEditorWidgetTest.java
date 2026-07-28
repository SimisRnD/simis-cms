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
import com.simisinc.platform.application.cms.ThemeCommand;
import com.simisinc.platform.domain.model.cms.Theme;
import com.simisinc.platform.infrastructure.persistence.cms.ThemeRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * restoreViaPostCallsCommandAndAudits guards a real regression: the theme history's restore link submits via
 * a real HTTP POST (issue #358 moved state-changing admin actions off GET query strings), so
 * WebContainerContext routes the request to post(), not action() below -- action()'s "restore" dispatch was
 * correct but unreachable, and post() never checked the action parameter, so it fell through to the
 * save-theme-snapshot logic instead. That logic requires a "name" parameter a restore request never sends,
 * so it rejected the click with "A name is required for saving a theme" -- the theme was never restored.
 * This test calls post() directly, the same method a real request now reaches, so it fails if that dispatch
 * gap reopens.
 */
class ThemeEditorWidgetTest extends WidgetBase {

  @Test
  void restoreViaPostCallsCommandAndAudits() {
    Theme theme = new Theme();
    theme.setId(3L);
    theme.setName("Q1 Snapshot");

    addQueryParameter(widgetContext, "id", "3");
    addQueryParameter(widgetContext, "action", "restore");

    try (MockedStatic<ThemeRepository> themeRepository = mockStatic(ThemeRepository.class);
        MockedStatic<ThemeCommand> themeCommand = mockStatic(ThemeCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      themeRepository.when(() -> ThemeRepository.findById(anyLong())).thenReturn(theme);

      new ThemeEditorWidget().post(widgetContext);

      themeCommand.verify(() -> ThemeCommand.restoreTheme(theme), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION), eq("theme.restore"),
          eq(AuditEventCommand.SUCCESS), eq("theme"), eq("3"), eq("Q1 Snapshot"), any()), times(1));
    }
  }

  @Test
  void restoreViaPostDoesNotFallThroughToSaveValidation() {
    // Guards against a narrower fix that only special-cased the missing "name" check: restore must not
    // reach the createSnapshotWithName save path at all.
    Theme theme = new Theme();
    theme.setId(3L);
    theme.setName("Q1 Snapshot");

    addQueryParameter(widgetContext, "id", "3");
    addQueryParameter(widgetContext, "action", "restore");

    try (MockedStatic<ThemeRepository> themeRepository = mockStatic(ThemeRepository.class);
        MockedStatic<ThemeCommand> themeCommand = mockStatic(ThemeCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      themeRepository.when(() -> ThemeRepository.findById(anyLong())).thenReturn(theme);

      WidgetContext result = new ThemeEditorWidget().post(widgetContext);

      themeCommand.verify(() -> ThemeCommand.createSnapshotWithName(any()), never());
      Assertions.assertNull(result.getErrorMessage());
    }
  }
}
