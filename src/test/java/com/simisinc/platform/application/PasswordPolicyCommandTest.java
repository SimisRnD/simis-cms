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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Covers {@link PasswordPolicyCommand}, the single place every password-set entry point
 * (self-registration, account activation/reset completion, and e-commerce guest checkout) is
 * meant to route through. Before this existed, those three call sites each hardcoded their own
 * length check (6, 8, and 6 characters, with zero complexity requirement anywhere) and had
 * silently drifted out of sync.
 *
 * @author SimIS Inc.
 */
class PasswordPolicyCommandTest {

  /** Defaults: 15-character minimum, complexity required. Matches the seeded site properties. */
  private MockedStatic<LoadSitePropertyCommand> defaultPolicy() {
    return properties(null, null);
  }

  private MockedStatic<LoadSitePropertyCommand> properties(String minLength, String requireComplexity) {
    MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class);
    m.when(() -> LoadSitePropertyCommand.loadByName(eq("security.password.minLength"))).thenReturn(minLength);
    m.when(() -> LoadSitePropertyCommand.loadByName(eq("security.password.requireComplexity"), eq("true")))
        .thenAnswer((Answer<String>) invocation -> requireComplexity != null ? requireComplexity : "true");
    return m;
  }

  @Test
  void validateAcceptsAPasswordMeetingLengthAndEveryCharacterClass() {
    try (MockedStatic<LoadSitePropertyCommand> m = defaultPolicy()) {
      assertNull(PasswordPolicyCommand.validate("Correct-Horse-B4ttery!"));
    }
  }

  @Test
  void validateRejectsBlank() {
    try (MockedStatic<LoadSitePropertyCommand> m = defaultPolicy()) {
      assertEquals("A password is required", PasswordPolicyCommand.validate(""));
      assertEquals("A password is required", PasswordPolicyCommand.validate(null));
    }
  }

  @Test
  void validateRejectsAPasswordShorterThanTheConfiguredMinimum() {
    try (MockedStatic<LoadSitePropertyCommand> m = defaultPolicy()) {
      // 14 characters, one short of the default 15-character minimum
      String password = "Sh0rt-Pw!Aaaaa";
      assertEquals(14, password.length());
      assertEquals("Passwords must be at least 15 characters", PasswordPolicyCommand.validate(password));
    }
  }

  @Test
  void validateAcceptsAPasswordExactlyAtTheConfiguredMinimum() {
    try (MockedStatic<LoadSitePropertyCommand> m = defaultPolicy()) {
      // Exactly 15 characters, all four classes present
      String password = "Aa1!Aa1!Aa1!Aaa";
      assertEquals(15, password.length());
      assertNull(PasswordPolicyCommand.validate(password));
    }
  }

  @Test
  void validateReportsEveryMissingCharacterClassWhenComplexityIsRequired() {
    try (MockedStatic<LoadSitePropertyCommand> m = defaultPolicy()) {
      // 20 lowercase letters: long enough, but missing uppercase, digit, and special character
      String message = PasswordPolicyCommand.validate("aaaaaaaaaaaaaaaaaaaa");
      assertTrue(message.contains("an uppercase letter"), message);
      assertFalse(message.contains("a lowercase letter"), message);
      assertTrue(message.contains("a number"), message);
      assertTrue(message.contains("a special character"), message);
    }
  }

  @Test
  void validateReportsOnlyTheMissingClassesNotAllFour() {
    try (MockedStatic<LoadSitePropertyCommand> m = defaultPolicy()) {
      // Long enough, has uppercase/lowercase/digit, missing only a special character
      String message = PasswordPolicyCommand.validate("AaaaaaaaaaaaaB1");
      assertEquals("Passwords must include a special character", message);
    }
  }

  @Test
  void validateSkipsComplexityWhenDisabledBySiteProperty() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties(null, "false")) {
      // 20 lowercase letters -- would fail every complexity class, but complexity is off
      assertNull(PasswordPolicyCommand.validate("aaaaaaaaaaaaaaaaaaaa"));
    }
  }

  @Test
  void validateHonorsAConfiguredMinLengthAboveTheDefault() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties("20", "false")) {
      String message = PasswordPolicyCommand.validate("aaaaaaaaaaaaaaaaaaa"); // 19 chars
      assertEquals("Passwords must be at least 20 characters", message);
    }
  }

  @Test
  void minLengthNeverGoesBelowTheAbsoluteFloorEvenIfConfiguredLower() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties("4", null)) {
      assertEquals(PasswordPolicyCommand.ABSOLUTE_MIN_LENGTH, PasswordPolicyCommand.minLength());
    }
  }

  @Test
  void minLengthFallsBackToTheDefaultWhenTheConfiguredValueIsNotANumber() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties("not-a-number", null)) {
      assertEquals(PasswordPolicyCommand.DEFAULT_MIN_LENGTH, PasswordPolicyCommand.minLength());
    }
  }

  @Test
  void requireComplexityDefaultsToTrueWhenUnset() {
    try (MockedStatic<LoadSitePropertyCommand> m = defaultPolicy()) {
      assertTrue(PasswordPolicyCommand.requireComplexity());
    }
  }
}
