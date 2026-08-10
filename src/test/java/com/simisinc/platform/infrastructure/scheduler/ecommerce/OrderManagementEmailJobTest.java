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

package com.simisinc.platform.infrastructure.scheduler.ecommerce;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.ecommerce.Order;
import com.simisinc.platform.infrastructure.scheduler.ecommerce.OrderManagementEmailJob.OrderManagementEmailJobRequestHandler;

/**
 * Verifies {@link OrderManagementEmailJobRequestHandler#run} propagates a send failure instead of
 * swallowing it (issue #1124) -- before this fix, the {@code catch} block only logged, so this
 * job's {@code @Job(retries = 1)} never saw a failure to retry against. No SMTP/servlet
 * infrastructure is set up for this test, so {@code sendConfirmationToUser} fails naturally
 * (real, not mocked) at its Thymeleaf/servlet-context bootstrap step -- the point being tested is
 * that whatever failure occurs now reaches the caller, not the specific failure itself.
 *
 * @author SimIS Inc.
 */
class OrderManagementEmailJobTest {

  @Test
  void runPropagatesAFailureFromSendConfirmationToUser() {
    OrderManagementEmailJob jobRequest = new OrderManagementEmailJob();
    jobRequest.setOrder(new Order());
    jobRequest.setEmailType(OrderManagementEmailJob.EMAIL_TYPE_SHIPPING_CONFIRMATION);
    jobRequest.setResend(true);

    OrderManagementEmailJobRequestHandler handler = new OrderManagementEmailJobRequestHandler();
    assertThrows(RuntimeException.class, () -> handler.run(jobRequest));
  }
}
