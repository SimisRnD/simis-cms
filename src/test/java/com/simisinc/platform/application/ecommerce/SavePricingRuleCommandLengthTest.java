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

package com.simisinc.platform.application.ecommerce;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.ecommerce.PricingRule;
import com.simisinc.platform.infrastructure.persistence.ecommerce.PricingRuleRepository;

/**
 * pricing_rules.promo_code is VARCHAR(20) -- the narrowest column reachable from any admin form, so
 * the easiest of all of them to exceed by typing. Before issue #1740 that produced only "Your
 * information could not be saved due to a system error. Please try again."
 *
 * @author SimIS Inc.
 */
class SavePricingRuleCommandLengthTest {

  private static PricingRule rule(String name, String promoCode) {
    PricingRule bean = new PricingRule();
    bean.setName(name);
    bean.setPromoCode(promoCode);
    bean.setCreatedBy(1L);
    bean.setModifiedBy(1L);
    return bean;
  }

  @Test
  void anOverLongPromoCodeIsRefusedWithTheLimitInTheMessage() {
    PricingRule bean = rule("Spring sale", "x".repeat(21));

    try (MockedStatic<PricingRuleRepository> repository = mockStatic(PricingRuleRepository.class)) {
      DataException exception = assertThrows(DataException.class,
          () -> SavePricingRuleCommand.savePricingRule(bean));

      assertTrue(exception.getMessage().contains("A promo code can be up to 20 characters"),
          exception.getMessage());
      repository.verify(() -> PricingRuleRepository.save(any()), never());
    }
  }

  @Test
  void aPromoCodeExactlyAtTheLimitIsNotRefusedForLength() {
    // the column holds 20, so 20 must not be rejected on length grounds
    PricingRule bean = rule("Spring sale", "x".repeat(20));

    try (MockedStatic<PricingRuleRepository> repository = mockStatic(PricingRuleRepository.class)) {
      repository.when(() -> PricingRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      try {
        SavePricingRuleCommand.savePricingRule(bean);
      } catch (DataException e) {
        assertTrue(!e.getMessage().contains("promo code can be up to"),
            "a promo code at exactly the limit must not be refused for length: " + e.getMessage());
      }
    }
  }

  @Test
  void anOverLongNameIsRefusedToo() {
    PricingRule bean = rule("x".repeat(256), "SPRING");

    try (MockedStatic<PricingRuleRepository> repository = mockStatic(PricingRuleRepository.class)) {
      DataException exception = assertThrows(DataException.class,
          () -> SavePricingRuleCommand.savePricingRule(bean));

      assertTrue(exception.getMessage().contains("A name can be up to 255 characters"),
          exception.getMessage());
      repository.verify(() -> PricingRuleRepository.save(any()), never());
    }
  }

  @Test
  void aBlankNameStillReportsMissingRatherThanTooLong() {
    PricingRule bean = rule("", "SPRING");

    try (MockedStatic<PricingRuleRepository> repository = mockStatic(PricingRuleRepository.class)) {
      DataException exception = assertThrows(DataException.class,
          () -> SavePricingRuleCommand.savePricingRule(bean));

      assertTrue(exception.getMessage().contains("A name is required"), exception.getMessage());
      assertTrue(!exception.getMessage().contains("A name can be up to"), exception.getMessage());
    }
  }
}
