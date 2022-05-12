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

package com.simisinc.platform.domain.events.cms;

import com.simisinc.platform.domain.model.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author matt rajkowski
 * @created 5/11/2022 10:30 PM
 */
class UserSignedUpEventTest {

  @Test
  void checkEvent() {
    User user = new User();
    user.setId(1L);

    UserSignedUpEvent event = new UserSignedUpEvent(user);
    Assertions.assertEquals(user.getId(), event.getUser().getId());
    Assertions.assertTrue(event.getOccurred() <= System.currentTimeMillis());
    Assertions.assertEquals(UserSignedUpEvent.ID, event.getDomainEventType());
  }
}