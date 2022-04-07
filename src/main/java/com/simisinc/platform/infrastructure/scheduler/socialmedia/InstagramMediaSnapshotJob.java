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

package com.simisinc.platform.infrastructure.scheduler.socialmedia;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.socialmedia.InstagramCommand;
import com.simisinc.platform.domain.model.socialmedia.InstagramMedia;
import com.simisinc.platform.infrastructure.persistence.socialmedia.InstagramMediaRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.jobs.annotations.Job;

import java.util.ArrayList;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/15/19 4:50 PM
 */
public class InstagramMediaSnapshotJob {

  private static Log LOG = LogFactory.getLog(InstagramMediaSnapshotJob.class);

  @Job(name = "Retrieve the latest instagram posts")
  public static void execute() {

    // See if Instagram is configured
    String apiKey = LoadSitePropertyCommand.loadByName("social.instagram.accessToken");
    String facebookPageValue = LoadSitePropertyCommand.loadByName("social.instagram.facebookPageValue");
    if (StringUtils.isBlank(apiKey) || StringUtils.isBlank(facebookPageValue)) {
      LOG.debug("Instagram is not configured");
      return;
    }

    // Get the Facebook Page Id and Token
    String[] facebookPageIdAndToken = InstagramCommand.retrieveFacebookPageIdAndToken(facebookPageValue);
    if (facebookPageIdAndToken == null) {
      LOG.warn("No facebookPageIdAndToken found.");
      return;
    }

    // Determine the instagramUserId
    String instagramUserId = InstagramCommand.retrieveInstagramUserIdForFacebookPageId(facebookPageIdAndToken[0]);
    LOG.debug("Found instagramUserId? " + instagramUserId);
    if (StringUtils.isBlank(instagramUserId)) {
      LOG.warn("No instagramUserId found.");
      return;
    }

    // Check for new media
    ArrayList<String> mediaList = InstagramCommand.retrieveMediaListForInstagramUserId(instagramUserId);
    if (mediaList == null) {
      LOG.warn("No media found.");
      return;
    }
    LOG.debug("Media List? " + mediaList.size());

    // Compare the media with the database and retrieve new images
    int addCount = 0;
    int updateCount = 0;
    for (String graphId : mediaList) {
      // Retrieve the remote record
      InstagramMedia remoteInstagramMedia = InstagramCommand.retrieveInstagramMediaForGraphId(graphId);
      if (remoteInstagramMedia != null) {
        LOG.debug("Saving instagram media from graphId: " + graphId);
        if (StringUtils.isNotBlank(remoteInstagramMedia.getMediaUrl())) {
          // Check for a previously saved graphId
          InstagramMedia instagramMedia = InstagramMediaRepository.findByGraphId(graphId);
          if (instagramMedia != null) {
            // Existing record will be updated
            remoteInstagramMedia.setId(instagramMedia.getId());
            if (!remoteInstagramMedia.getMediaUrl().equals(instagramMedia.getMediaUrl())) {
              ++updateCount;
            }
          } else {
            ++addCount;
          }
          InstagramMediaRepository.save(remoteInstagramMedia);
        }
      }

    }
    if (addCount > 0 || updateCount > 0) {
      LOG.info("Added instagram media: " + addCount + "; updated: " + updateCount);
    }
  }
}
