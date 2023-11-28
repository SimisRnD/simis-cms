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

package com.simisinc.platform;

/**
 * The web application's name and reference URL; version for database upgrades
 *
 * @author matt rajkowski
 */
public class ApplicationInfo {

  // Display information
  public static final String PRODUCT_NAME = "SimIS CMS";
  public static final String PRODUCT_URL = "https://www.simiscms.com";

  // This version format drives Flyway migrations
  // The version number must be greater than the new_db script dates
  // Use: Change the date, increment the decimal on same day updates
  // then reset back to 10000
  //                         VERSION = "--------.10000";
  public static final String VERSION = "20231127.10000";

  /**
   * Outputs the version from the command line
   * @param args
   */
  public static void main(String args[]) {
    System.out.println("Version: " + VERSION);
    System.out.println("Product: " + PRODUCT_NAME);
    System.out.println("URL: " + PRODUCT_URL);
  }
}
