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

package com.simisinc.platform.application.webhooks;

import java.security.SecureRandom;

import org.apache.commons.codec.binary.Hex;

/**
 * Generates the shared secret a {@code webhook_subscription} uses to HMAC-sign outbound
 * deliveries (see {@code SignWebhookPayloadCommand}) -- issue #453. Same style as {@code
 * TotpCommand#generateSecret()}: a fixed number of {@link SecureRandom} bytes encoded to a
 * printable string. Hex rather than TOTP's Base32 -- this secret is never hand-typed by a user
 * (unlike a TOTP setup key), only copy-pasted into a receiver's webhook-verification config, and
 * hex keeps it consistent with {@code SignWebhookPayloadCommand}'s own hex-encoded HMAC output.
 *
 * <p>
 * The returned value is plaintext -- callers must show it to the admin at most once (it is not
 * recoverable in plaintext form after this call returns unless the caller holds onto it) and
 * persist it only via {@code WebhookSubscriptionRepository}, which encrypts it at rest.
 * </p>
 *
 * @author SimIS Inc.
 */
public class GenerateWebhookSecretCommand {

  private static final int SECRET_BYTES = 32; // 256 bits, encodes to 64 hex characters
  private static final SecureRandom RANDOM = new SecureRandom();

  private GenerateWebhookSecretCommand() {
    // Static utility, not instantiated
  }

  /**
   * @return a new random, hex-encoded 256-bit secret
   */
  public static String generate() {
    byte[] bytes = new byte[SECRET_BYTES];
    RANDOM.nextBytes(bytes);
    return Hex.encodeHexString(bytes);
  }
}
