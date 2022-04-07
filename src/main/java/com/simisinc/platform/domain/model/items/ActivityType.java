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

package com.simisinc.platform.domain.model.items;

/**
 * The types of activity stream entries
 *
 * @author matt rajkowski
 * @created 8/20/18 11:49 AM
 */
public class ActivityType {

  public final static String ITEM_CREATED = "ITEM_CREATED";
  public final static String ITEM_RELATIONSHIP_CREATED = "ITEM_RELATIONSHIP_CREATED";
  public final static String CHAT = "CHAT";

  final static long serialVersionUID = 8345648404174283569L;

}
