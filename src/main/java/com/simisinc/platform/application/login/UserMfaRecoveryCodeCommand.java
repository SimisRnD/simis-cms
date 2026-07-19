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

package com.simisinc.platform.application.login;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.login.UserMfaRecoveryCode;
import com.simisinc.platform.infrastructure.persistence.login.UserMfaRecoveryCodeRepository;

/**
 * Generates and verifies multi-factor authentication recovery codes -- single-use backup codes a user falls back on
 * when they can't produce a TOTP code (a lost or reset authenticator). Codes are high-entropy random strings stored
 * only as SHA-256 hashes; the plaintext is returned once at generation and shown to the user, never persisted.
 *
 * @author SimIS Inc.
 * @created 2026-07-17
 */
public class UserMfaRecoveryCodeCommand {

  private static final int CODE_COUNT = 10;
  private static final int CODE_LENGTH = 10;
  // Unambiguous alphabet (no 0/O, 1/l/I) to avoid transcription errors; 31^10 is roughly 50 bits of entropy per code
  private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
  private static final SecureRandom RANDOM = new SecureRandom();

  private UserMfaRecoveryCodeCommand() {
    // Static utility, not instantiated
  }

  /**
   * Replaces any existing codes with a fresh set, stores their hashes, and returns the formatted plaintext codes to
   * show the user once.
   *
   * @param user the user to generate codes for
   * @return the plaintext codes (formatted for display); the caller must show these immediately and not store them
   */
  public static List<String> generate(User user) {
    UserMfaRecoveryCodeRepository.removeAll(user.getId());
    List<String> plaintext = new ArrayList<>();
    for (int i = 0; i < CODE_COUNT; i++) {
      String raw = randomCode();
      plaintext.add(format(raw));
      UserMfaRecoveryCode record = new UserMfaRecoveryCode();
      record.setUserId(user.getId());
      record.setCodeHash(hash(raw));
      UserMfaRecoveryCodeRepository.add(record);
    }
    return plaintext;
  }

  /**
   * Verifies a user-supplied recovery code and, if it matches an unused code, consumes it (marks it used).
   *
   * @param user the user
   * @param input the code entered by the user (formatting and case are ignored)
   * @return true when a matching unused code was found and consumed
   */
  public static boolean consume(User user, String input) {
    if (user == null || StringUtils.isBlank(input)) {
      return false;
    }
    String normalized = normalize(input);
    if (normalized.length() != CODE_LENGTH) {
      return false;
    }
    UserMfaRecoveryCode match = UserMfaRecoveryCodeRepository.findUnusedByUserIdAndHash(user.getId(), hash(normalized));
    if (match == null) {
      return false;
    }
    // Consume atomically: markUsed returns true only when THIS call flips the row from unused,
    // so a concurrent request racing on the same code cannot also succeed (single-use guarantee).
    return UserMfaRecoveryCodeRepository.markUsed(match);
  }

  /**
   * @param user the user
   * @return how many unused recovery codes the user has left
   */
  public static long countRemaining(User user) {
    if (user == null) {
      return 0;
    }
    return UserMfaRecoveryCodeRepository.countUnusedByUserId(user.getId());
  }

  /**
   * Removes all of a user's recovery codes (for example when MFA is turned off).
   *
   * @param user the user
   */
  public static void clear(User user) {
    if (user == null) {
      return;
    }
    UserMfaRecoveryCodeRepository.removeAll(user.getId());
  }

  private static String randomCode() {
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }

  private static String format(String raw) {
    // Split into two halves for readability, e.g. abcde-fghij
    int mid = raw.length() / 2;
    return raw.substring(0, mid) + "-" + raw.substring(mid);
  }

  private static String normalize(String input) {
    return input.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
  }

  private static String hash(String normalizedCode) {
    return DigestUtils.sha256Hex(normalizedCode);
  }
}
