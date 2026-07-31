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

package com.simisinc.platform.infrastructure.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.jobrunr.utils.mapper.jackson3.Jackson3JsonMapper;
import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.events.cms.UserRegisteredEvent;
import com.simisinc.platform.domain.model.User;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Covers the JobRunr {@code JsonMapper} configuration that {@link SchedulerManager#startup} wires
 * up for {@link WorkflowEngineJob}. Its {@code event} field is typed as the abstract {@link Event},
 * so JobRunr's Jackson 3 mapper writes a type id on serialize; deserialize only succeeds if the
 * mapper's {@code PolymorphicTypeValidator} was told to trust {@link Event} subtypes. Without that,
 * every job carrying a real event (e.g. {@link UserRegisteredEvent}) serializes fine at enqueue time
 * but then permanently fails at run time with {@code JobParameterNotDeserializableException}, wrapping
 * a denied {@code InvalidTypeIdException} -- silently, since JobRunr swallows the failure into its own
 * job-storage error state rather than surfacing it to the end user or admin.
 *
 * @author SimIS
 * @created 7/31/2026
 */
class WorkflowEngineJobTest {

  private static UserRegisteredEvent sampleEvent() {
    User user = new User();
    user.setId(42L);
    user.setEmail("test@example.com");
    user.setUsername("testuser");

    UserRegisteredEvent event = new UserRegisteredEvent();
    event.setUser(user);
    event.setIpAddress("203.0.113.7");
    event.setLocation("Testville, TS, Testland");
    return event;
  }

  @Test
  void mapperConfiguredWithEventAllowlistRoundTripsAWorkflowEngineJob() {
    Jackson3JsonMapper mapper = new Jackson3JsonMapper(
        BasicPolymorphicTypeValidator.builder().allowIfSubType(Event.class));

    WorkflowEngineJob job = new WorkflowEngineJob(sampleEvent());
    String json = mapper.serialize(job);
    WorkflowEngineJob deserialized = mapper.deserialize(json, WorkflowEngineJob.class);

    UserRegisteredEvent event = assertInstanceOf(UserRegisteredEvent.class, deserialized.getEvent());
    assertEquals(42L, event.getUser().getId());
    assertEquals("test@example.com", event.getUser().getEmail());
    assertEquals("203.0.113.7", event.getIpAddress());
    assertEquals("Testville, TS, Testland", event.getLocation());
  }

  @Test
  void mapperWithoutTheEventAllowlistReproducesTheOriginalDeserializationFailure() {
    // JobRunr's own default -- what SchedulerManager relied on implicitly before this fix, since it
    // never called useJsonMapper(...) at all -- trusts no application types by default.
    Jackson3JsonMapper defaultMapper = new Jackson3JsonMapper();

    WorkflowEngineJob job = new WorkflowEngineJob(sampleEvent());
    String json = defaultMapper.serialize(job);

    assertThrows(RuntimeException.class, () -> defaultMapper.deserialize(json, WorkflowEngineJob.class));
  }
}
