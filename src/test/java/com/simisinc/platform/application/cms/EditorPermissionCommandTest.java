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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Tests the builder-vs-editor capability split, with emphasis on the closed-by-default posture:
 * access is denied unless a granting role is explicitly held.
 *
 * @author elizabeth houser
 */
class EditorPermissionCommandTest {

  private static UserSession sessionWithRoles(String... codes) {
    UserSession userSession = new UserSession();
    List<Role> roles = new ArrayList<>();
    for (String code : codes) {
      roles.add(new Role(code, code)); // Role(title, code)
    }
    userSession.setRoleList(roles);
    return userSession;
  }

  @Test
  void adminMayEditAndBuild() {
    UserSession admin = sessionWithRoles("admin");
    assertTrue(EditorPermissionCommand.canEditContent(admin));
    assertTrue(EditorPermissionCommand.canBuildLayout(admin));
  }

  @Test
  void contentManagerMayEditAndBuild() {
    UserSession cm = sessionWithRoles("content-manager");
    assertTrue(EditorPermissionCommand.canEditContent(cm));
    assertTrue(EditorPermissionCommand.canBuildLayout(cm));
  }

  @Test
  void contentEditorMayEditButNotBuild() {
    // The whole point of the split: authors edit content, they do not get the layout canvas.
    UserSession editor = sessionWithRoles("content-editor");
    assertTrue(EditorPermissionCommand.canEditContent(editor));
    assertFalse(EditorPermissionCommand.canBuildLayout(editor));
  }

  @Test
  void unrelatedRoleGrantsNeither() {
    // Holding some other role is not an implicit grant -- closed-by-default.
    UserSession other = sessionWithRoles("data-manager");
    assertFalse(EditorPermissionCommand.canEditContent(other));
    assertFalse(EditorPermissionCommand.canBuildLayout(other));
  }

  @Test
  void noRolesGrantsNeither() {
    UserSession none = sessionWithRoles();
    assertFalse(EditorPermissionCommand.canEditContent(none));
    assertFalse(EditorPermissionCommand.canBuildLayout(none));
  }

  @Test
  void nullSessionGrantsNeither() {
    assertFalse(EditorPermissionCommand.canEditContent(null));
    assertFalse(EditorPermissionCommand.canBuildLayout(null));
  }
}
