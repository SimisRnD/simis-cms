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

import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Signs an outbound webhook payload with a subscription's HMAC-SHA256 secret (issue #418), for
 * the {@code X-Simis-Signature: sha256=<hex>} header the receiver uses to verify the request
 * actually came from this application. Sender-side only -- nothing in this codebase verifies an
 * inbound signature yet, but a future verifier must compare with
 * {@link java.security.MessageDigest#isEqual(byte[], byte[])} (or equivalent constant-time
 * compare), not {@code String.equals}, to avoid a timing side-channel.
 *
 * @author SimIS Inc.
 */
public class SignWebhookPayloadCommand {

  private static final Log LOG = LogFactory.getLog(SignWebhookPayloadCommand.class);

  private static final String ALGORITHM = "HmacSHA256";

  /** The full header value, e.g. {@code "sha256=<hex>"} -- ready to set on X-Simis-Signature. */
  public static final String HEADER_NAME = "X-Simis-Signature";

  /**
   * Computes the hex-encoded HMAC-SHA256 of {@code payload} using {@code secret}. Returns null
   * (and logs an error that never includes the secret itself) if signing fails for any reason.
   */
  public static String sign(String payload, String secret) {
    if (payload == null || StringUtils.isBlank(secret)) {
      LOG.error("Cannot sign a webhook payload without a payload and a subscription secret");
      return null;
    }
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return Hex.encodeHexString(hash);
    } catch (Exception e) {
      // Deliberately do not include the secret in this log line
      LOG.error("Could not sign webhook payload", e);
      return null;
    }
  }

  /** The ready-to-send header value: {@code "sha256=" + sign(payload, secret)}. */
  public static String signatureHeaderValue(String payload, String secret) {
    String signature = sign(payload, secret);
    if (signature == null) {
      return null;
    }
    return "sha256=" + signature;
  }
}
