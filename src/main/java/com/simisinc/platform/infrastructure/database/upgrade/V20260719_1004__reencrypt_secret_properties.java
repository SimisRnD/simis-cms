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

package com.simisinc.platform.infrastructure.database.upgrade;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.simisinc.platform.application.SecretCryptoCommand;
import com.simisinc.platform.application.admin.SecretSitePropertiesCommand;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;

/**
 * One-time re-encryption of secret site properties that were stored before at-rest encryption was enabled.
 *
 * <p>At-rest encryption of secret {@code site_properties} values funnels through
 * {@link SitePropertyRepository#save} (encrypt on write when the name is a secret) and
 * {@link SitePropertyRepository#buildRecord} (decrypt on read). Values written before that funnel existed, or
 * written directly in SQL, stay as legacy plaintext and only gain encryption when they are next saved through Java.
 *
 * <p>That "next save" never happens for the production payment secrets
 * ({@code ecommerce.stripe.production.secret}, {@code ecommerce.square.production.secret},
 * {@code ecommerce.boxzooka.production.secret}): they are {@code property_type='disabled'}, so the admin editor
 * renders them read-only and {@link SitePropertyRepository#saveAll} skips them by design. They are provisioned
 * directly in the database via SQL and would therefore remain plaintext at rest forever.
 *
 * <p>This upgrade closes that gap. For every configured secret name it re-saves the current value through the
 * per-row {@link SitePropertyRepository#save} write funnel &mdash; never {@code saveAll}, because {@code saveAll}
 * skips the {@code disabled} rows that are the whole reason this step exists. It is idempotent: a value that is
 * blank, or that comes back already encrypted, is left untouched, and {@link SecretCryptoCommand#encrypt} refuses
 * to double-wrap an {@code enc:} value, so a re-run is safe.
 *
 * <p>When {@code CMS_SECRET_KEY} is not configured, {@link SecretCryptoCommand#encrypt} is a pass-through no-op:
 * this upgrade re-saves the values unchanged (still plaintext) and logs a warning. Because the {@code disabled}
 * rows have no other in-app write path, the key must be configured before this upgrade runs for the production
 * payment secrets to be encrypted.
 *
 * <p>Lives under {@code database/upgrade} (not {@code database/install}): existing deployments &mdash; where the
 * plaintext secrets actually live &mdash; are migrated through the upgrade path, whereas {@code install} classes
 * run only against a brand-new database.
 *
 * @author elizabeth houser
 * @created 2026-07-19
 */
public class V20260719_1004__reencrypt_secret_properties extends BaseJavaMigration {

  private static final Log LOG = LogFactory.getLog(V20260719_1004__reencrypt_secret_properties.class);

  @Override
  public void migrate(Context context) throws Exception {

    if (!SecretCryptoCommand.isEnabled()) {
      LOG.warn("CMS_SECRET_KEY is not configured; secret site properties will be re-saved unchanged and remain "
          + "plaintext at rest. Configure the key and re-run this upgrade to encrypt them.");
    }

    // Track the root prefix of every value we re-save so the property cache can be expired afterward, mirroring
    // SitePropertyRepository.saveAll -- a direct save() does not invalidate the cache on its own.
    Set<String> affectedRootPrefixes = new LinkedHashSet<>();
    int reEncryptedCount = 0;

    for (String name : SecretSitePropertiesCommand.getSecretPropertyNames()) {
      SiteProperty record = SitePropertyRepository.findByName(name);
      if (record == null) {
        // The property is not present in this deployment -- nothing to upgrade
        continue;
      }
      // findByName returns the value already run through buildRecord's decrypt(): legacy plaintext comes back
      // unchanged, an enc: value comes back decrypted (or null, fail-safe, if the key is missing/wrong). Skip when
      // there is nothing to protect (blank/unset) or the value is somehow already ciphertext.
      String value = record.getValue();
      if (StringUtils.isBlank(value) || SecretCryptoCommand.isEncrypted(value)) {
        continue;
      }
      // Re-save through the single encrypting write funnel. Unlike saveAll(), save() does NOT skip
      // property_type='disabled' rows -- which is the entire point: it catches the production payment secrets.
      if (SitePropertyRepository.save(record) != null) {
        reEncryptedCount++;
        affectedRootPrefixes.add(rootPrefixOf(name));
      } else {
        LOG.error("Failed to re-save secret site property through the encrypting funnel: " + name);
      }
    }

    // Expire the cached property lists for the affected root prefixes, as SitePropertyRepository.saveAll does
    for (String rootPrefix : affectedRootPrefixes) {
      CacheManager.invalidateKey(CacheManager.SYSTEM_PROPERTY_PREFIX_CACHE, rootPrefix);
    }

    LOG.info("Secret site property re-encryption upgrade complete; re-saved " + reEncryptedCount + " value(s)");
  }

  /**
   * @return the cache root of a property name -- the segment before the first dot (matching
   *     SitePropertyRepository.saveAll), or the whole name when it has no dot
   */
  private static String rootPrefixOf(String propertyName) {
    int dot = propertyName.indexOf('.');
    return dot > 0 ? propertyName.substring(0, dot) : propertyName;
  }
}
