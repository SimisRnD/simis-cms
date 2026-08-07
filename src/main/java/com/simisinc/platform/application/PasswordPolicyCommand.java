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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * The single source of truth for "is this a policy-compliant new password?" -- used everywhere a
 * plaintext password is accepted from a user and about to be hashed (self-registration, the
 * account-activation/password-reset completion flow, and e-commerce guest checkout account
 * creation). Before this existed, those three call sites each hardcoded their own length check
 * (6, 8, and 6 characters respectively) with no complexity requirement anywhere -- easy to miss
 * one when the policy changes, which is exactly what happened.
 *
 * <p>
 * Configurable via the {@code security.password.minLength} and {@code
 * security.password.requireComplexity} site properties (Security Settings) so an admin can tune
 * the policy without a code change; both fall back to a strong default if unset or invalid.
 * </p>
 *
 * <p>
 * Deliberately does not apply retroactively: an existing user's already-hashed password is never
 * re-validated against a tightened policy (there is no way to check length/complexity against a
 * hash without the plaintext). A stricter policy only takes effect the next time that user's
 * password is actually set again -- self-service reset, admin-forced reset, or registration.
 * </p>
 */
public class PasswordPolicyCommand {

  /** Used when {@code security.password.minLength} is unset or not a valid positive integer. */
  public static final int DEFAULT_MIN_LENGTH = 15;

  /** No admin-configured minimum is ever honored below this, to prevent an accidental weakening. */
  public static final int ABSOLUTE_MIN_LENGTH = 8;

  private PasswordPolicyCommand() {
    // Static utility, not instantiated
  }

  /**
   * @param password the plaintext candidate password
   * @return null when the password satisfies the current policy; otherwise a user-facing message
   *         describing exactly what's missing
   */
  public static String validate(String password) {
    if (StringUtils.isBlank(password)) {
      return "A password is required";
    }
    int minLength = minLength();
    if (password.length() < minLength) {
      return "Passwords must be at least " + minLength + " characters";
    }
    if (!requireComplexity()) {
      return null;
    }
    List<String> missing = new ArrayList<>();
    if (password.chars().noneMatch(Character::isUpperCase)) {
      missing.add("an uppercase letter");
    }
    if (password.chars().noneMatch(Character::isLowerCase)) {
      missing.add("a lowercase letter");
    }
    if (password.chars().noneMatch(Character::isDigit)) {
      missing.add("a number");
    }
    if (password.chars().noneMatch(PasswordPolicyCommand::isSpecialCharacter)) {
      missing.add("a special character");
    }
    if (missing.isEmpty()) {
      return null;
    }
    return "Passwords must include " + String.join(", ", missing);
  }

  /** @return the configured minimum length, clamped to never go below {@link #ABSOLUTE_MIN_LENGTH} */
  public static int minLength() {
    String configured = LoadSitePropertyCommand.loadByName("security.password.minLength");
    int parsed = DEFAULT_MIN_LENGTH;
    if (StringUtils.isNotBlank(configured)) {
      try {
        parsed = Integer.parseInt(configured.trim());
      } catch (NumberFormatException e) {
        parsed = DEFAULT_MIN_LENGTH;
      }
    }
    return Math.max(parsed, ABSOLUTE_MIN_LENGTH);
  }

  /** @return whether uppercase/lowercase/digit/special-character composition is required (default true) */
  public static boolean requireComplexity() {
    return !"false".equals(LoadSitePropertyCommand.loadByName("security.password.requireComplexity", "true"));
  }

  /** A character that is neither a letter nor a digit nor whitespace, e.g. {@code ! @ # $ % ^ & *}. */
  private static boolean isSpecialCharacter(int c) {
    return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
  }
}
