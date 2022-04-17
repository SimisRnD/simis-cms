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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.SiteProperty;
import com.simisinc.platform.domain.model.cms.Theme;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ThemeRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Methods for working with theme snapshots
 *
 * @author matt rajkowski
 * @created 7/12/19 1:31 PM
 */
public class ThemeCommand {

  private static final String prefix = "theme";
  private static Log LOG = LogFactory.getLog(ThemeCommand.class);

  /**
   * Saves the current site properties into a theme backup
   * @param name
   */
  public static void createSnapshotWithName(String name) {
    // Create a snapshot of the current properties
    List<SiteProperty> siteProperties = SitePropertyRepository.findAllByPrefix(prefix);
    if (siteProperties != null && !siteProperties.isEmpty()) {
      Theme theme = ThemeRepository.findByName(name);
      if (theme != null) {
        // Update the existing theme
        theme.setSiteProperties(siteProperties);
        ThemeRepository.save(theme);
      } else {
        // Save a new theme
        ThemeRepository.save(siteProperties, name);
      }
    }
  }

  /**
   * Reads the saved theme properties and sets them as the current theme
   * @param theme
   */
  public static void restoreTheme(Theme theme) {
    // Load the theme
    List<SiteProperty> themeProperties = theme.getSiteProperties();
    if (themeProperties == null || themeProperties.isEmpty()) {
      LOG.warn("Theme does not have any properties");
      return;
    }

    // Load the current values as a template
    List<SiteProperty> siteProperties = SitePropertyRepository.findAllByPrefix(prefix);
    if (siteProperties == null || siteProperties.isEmpty()) {
      LOG.error("Site properties were not found for prefix: " + prefix);
      return;
    }

    // Overwrite the current values when a theme value exists
    for (SiteProperty siteProperty : siteProperties) {
      for (SiteProperty themeProperty : themeProperties) {
        if (themeProperty.getName().equals(siteProperty.getName())) {
          siteProperty.setValue(themeProperty.getValue());
          break;
        }
      }
      // A value was not found, leave the current value
    }

    // Overwrite the current properties
    SitePropertyRepository.saveAll(prefix, siteProperties);
  }

}
