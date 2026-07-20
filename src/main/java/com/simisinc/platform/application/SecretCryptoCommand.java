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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Application-layer encryption for secrets that must be stored recoverably (unlike passwords, which are hashed) --
 * currently the per-user TOTP seed, and available for integration/payment credentials. Values are wrapped with
 * AES-256-GCM (authenticated encryption) under a key-encryption key (KEK) read from the environment, so a database
 * dump alone does not yield usable secrets.
 *
 * <p>The KEK is a base64-encoded 256-bit key supplied out-of-band via the {@code CMS_SECRET_KEY} environment variable
 * (or the {@code cms.secret.key} system property). If no key is configured, {@link #encrypt} returns the value
 * unchanged so existing deployments keep working -- set the key to enable encryption; values are then encrypted on
 * their next save. Encrypted values are prefixed with {@code enc:} so {@link #decrypt} can distinguish them from
 * legacy plaintext. Key rotation: decrypt with the old key, re-save to encrypt under the new key.
 *
 * @author SimIS Inc.
 * @created 2026-07-19
 */
public class SecretCryptoCommand {

  private static final Log LOG = LogFactory.getLog(SecretCryptoCommand.class);

  private static final String PREFIX = "enc:";
  private static final String ENV_KEY = "CMS_SECRET_KEY";
  private static final String PROP_KEY = "cms.secret.key";
  private static final int KEY_BYTES = 32; // AES-256
  private static final int IV_BYTES = 12;  // GCM standard nonce length
  private static final int GCM_TAG_BITS = 128;
  private static final SecureRandom RANDOM = new SecureRandom();

  private SecretCryptoCommand() {
    // Static utility, not instantiated
  }

  /**
   * @return the configured AES key, or null when none is set or the value is invalid (env var wins over the property)
   */
  private static SecretKeySpec key() {
    String raw = System.getenv(ENV_KEY);
    if (StringUtils.isBlank(raw)) {
      raw = System.getProperty(PROP_KEY);
    }
    if (StringUtils.isBlank(raw)) {
      return null;
    }
    try {
      byte[] bytes = Base64.getDecoder().decode(raw.trim());
      if (bytes.length != KEY_BYTES) {
        LOG.error(ENV_KEY + " must be a base64-encoded 256-bit (32-byte) key; secret encryption is disabled");
        return null;
      }
      return new SecretKeySpec(bytes, "AES");
    } catch (IllegalArgumentException e) {
      LOG.error(ENV_KEY + " is not valid base64; secret encryption is disabled");
      return null;
    }
  }

  /**
   * @return true when a valid encryption key is configured
   */
  public static boolean isEnabled() {
    return key() != null;
  }

  /**
   * @return true when the value is already an {@code enc:}-prefixed ciphertext
   */
  public static boolean isEncrypted(String value) {
    return value != null && value.startsWith(PREFIX);
  }

  /**
   * Encrypts a secret for storage. Blank input is returned unchanged, and when no key is configured the value is
   * returned as-is (legacy plaintext) so an unconfigured deployment keeps working.
   *
   * @param plaintext the secret to protect
   * @return an {@code enc:}-prefixed ciphertext, or the input unchanged when blank / no key
   */
  public static String encrypt(String plaintext) {
    // Idempotent: a blank value or one that is already encrypted is returned unchanged, so re-encrypting
    // a stored secret cannot double-wrap it
    if (StringUtils.isBlank(plaintext) || isEncrypted(plaintext)) {
      return plaintext;
    }
    SecretKeySpec k = key();
    if (k == null) {
      return plaintext;
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, k, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
      return PREFIX + Base64.getEncoder().encodeToString(combined);
    } catch (GeneralSecurityException e) {
      LOG.error("Secret encryption failed", e);
      throw new IllegalStateException("Secret encryption failed", e);
    }
  }

  /**
   * Decrypts a stored secret. A value that is not {@code enc:}-prefixed is returned unchanged (legacy plaintext).
   * On failure (missing key, wrong key, or a corrupt value) this returns null so the caller fails safe -- treating
   * the account as having no usable secret rather than exposing a broken one.
   *
   * @param stored the stored value
   * @return the plaintext secret, the input unchanged if it was legacy plaintext, or null on failure
   */
  public static String decrypt(String stored) {
    if (StringUtils.isBlank(stored) || !stored.startsWith(PREFIX)) {
      return stored;
    }
    SecretKeySpec k = key();
    if (k == null) {
      LOG.error("An encrypted secret was found but " + ENV_KEY + " is not configured; cannot decrypt");
      return null;
    }
    try {
      byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
      byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
      byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      LOG.error("Secret decryption failed (wrong key or corrupt value)", e);
      return null;
    }
  }
}
