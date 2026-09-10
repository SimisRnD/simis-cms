package com.simisinc.platform.infrastructure.scheduler;

import org.jobrunr.utils.mapper.JsonMapper;
import org.jobrunr.utils.mapper.jackson3.Jackson3JsonMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.events.cms.FormSubmittedEvent;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberConfirmationRequestedEvent;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberCreatedEvent;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberDeletedEvent;
import com.simisinc.platform.domain.events.mailinglists.MailingListMemberUpdatedEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.domain.model.mailinglists.MailingListMember;

/** Round-trips a WorkflowEngineJob through the exact mapper SchedulerManager configures. */
class WorkflowEngineJobSerializationTest {

  private JsonMapper mapper() {
    return new Jackson3JsonMapper(BasicPolymorphicTypeValidator.builder().allowIfSubType(Event.class));
  }

  private void roundTrip(Event event) {
    JsonMapper mapper = mapper();
    String json = mapper.serialize(new WorkflowEngineJob(event));
    WorkflowEngineJob back = mapper.deserialize(json, WorkflowEngineJob.class);
    Assertions.assertNotNull(back, "job did not deserialize");
    Assertions.assertNotNull(back.getEvent(), "event did not deserialize for " + event.getClass().getSimpleName());
    Assertions.assertEquals(event.getDomainEventType(), back.getEvent().getDomainEventType());
  }

  @Test
  void formSubmittedEventRoundTrips() {
    roundTrip(new FormSubmittedEvent(new FormData(), "someone@example.com"));
  }

  @Test
  void mailingListConfirmationRequestedEventRoundTrips() {
    roundTrip(new MailingListMemberConfirmationRequestedEvent(member(), list(), "https://example.com/confirm"));
  }

  @Test
  void mailingListMemberCreatedEventRoundTrips() {
    roundTrip(new MailingListMemberCreatedEvent(member(), list(), new User()));
  }

  @Test
  void mailingListMemberUpdatedEventRoundTrips() {
    roundTrip(new MailingListMemberUpdatedEvent(member(), list(), new User(), "subscribed", false));
  }

  @Test
  void mailingListMemberDeletedEventRoundTrips() {
    roundTrip(new MailingListMemberDeletedEvent(member(), list(), new User()));
  }

  private MailingListMember member() {
    MailingListMember m = new MailingListMember();
    m.setEmailAddress("someone@example.com");
    return m;
  }

  private MailingList list() {
    MailingList l = new MailingList();
    l.setName("Newsletter");
    l.setTitle("Newsletter");
    return l;
  }
}
