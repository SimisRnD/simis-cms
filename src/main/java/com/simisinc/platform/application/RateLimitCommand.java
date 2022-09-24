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

package com.simisinc.platform.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.Duration;

/**
 * Methods for rate limiting
 *
 * @author matt rajkowski
 * @created 9/20/2022 9:05 PM
 */
public class RateLimitCommand {

  public static final String INVALID_ATTEMPTS = "Too many attempts. Please try again later.";
  private static Log LOG = LogFactory.getLog(RateLimitCommand.class);

  /**
   * Rate limiting on a login page can be applied according to the user's username.
   *
   * @param username
   * @param startWatching
   * @return
   */
  public static boolean isUsernameAllowedRightNow(String username, boolean startWatching) {
    Cache cache = CacheManager.getCache(CacheManager.RATE_LIMIT_LOGIN_ATTEMPT_BY_USERNAME_CACHE);
    Bucket bucket;
    synchronized (RateLimitCommand.class) {
      bucket = (Bucket) cache.getIfPresent(username);
      if (bucket == null) {
        if (startWatching) {
          Bandwidth limit = Bandwidth.simple(5, Duration.ofMinutes(30));
          bucket = Bucket.builder().addLimit(limit).build();
          cache.put(username, bucket);
        }
        return true;
      }
    }
    return bucket.tryConsume(1);
  }

  /**
   * Rate limiting can be applied according to the IP address trying to log in, or access the site
   *
   * @param ipAddress
   * @param startWatching
   * @return
   */
  public static boolean isIpAllowedRightNow(String ipAddress, boolean startWatching) {
    Cache cache = CacheManager.getCache(CacheManager.RATE_LIMIT_ATTEMPT_BY_IP_CACHE);
    Bucket bucket;
    synchronized (RateLimitCommand.class) {
      bucket = (Bucket) cache.getIfPresent(ipAddress);
      if (bucket == null) {
        if (startWatching) {
          Bandwidth limit = Bandwidth.simple(10, Duration.ofMinutes(30));
          bucket = Bucket.builder().addLimit(limit).build();
          cache.put(ipAddress, bucket);
        }
        return true;
      }
    }
    return bucket.tryConsume(1);
  }

  /**
   * Rate limiting can be applied according to the API client trying to access the site
   *
   * @param thisApp
   * @return
   */
  public static boolean isAppAllowedRightNow(App thisApp) {
    Cache cache = CacheManager.getCache(CacheManager.RATE_LIMIT_BY_APP_CACHE);
    Bucket bucket;
    synchronized (RateLimitCommand.class) {
      bucket = (Bucket) cache.getIfPresent(thisApp.getId());
      if (bucket == null) {
        Bandwidth limit = Bandwidth.simple(1500, Duration.ofMinutes(15));
        bucket = Bucket.builder().addLimit(limit).build();
        cache.put(thisApp.getId(), bucket);
      }
    }
    return bucket.tryConsume(1);
  }

  /**
   * Rate limiting can be applied according to the API client trying to access the site
   *
   * @param thisApp
   * @param userId
   * @return
   */
  public static boolean isAppUserAllowedRightNow(App thisApp, long userId) {
    Cache cache = CacheManager.getCache(CacheManager.RATE_LIMIT_BY_APP_USER_CACHE);
    Bucket bucket;
    synchronized (RateLimitCommand.class) {
      bucket = (Bucket) cache.getIfPresent(thisApp.getId() + "-" + userId);
      if (bucket == null) {
        Bandwidth limit = Bandwidth.simple(200, Duration.ofMinutes(15));
        bucket = Bucket.builder().addLimit(limit).build();
        cache.put(thisApp.getId() + "-" + userId, bucket);
      }
    }
    return bucket.tryConsume(1);
  }

}
