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

package com.simisinc.platform.presentation.widgets.admin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.SiteProperty;

/**
 * A step-up re-auth prompt must not discard the admin's pending edit (issue #1816). The gate returns
 * before the save loop, so the redisplayed editor has to carry the submitted values forward -- and
 * must still never echo a secret back into the form.
 */
class SitePropertiesEditorStepUpTest {

  /** A real name from SecretSitePropertiesCommand's secret set. */
  private static final String SECRET_NAME = "ecommerce.stripe.test.secret";

  private static SiteProperty property(String name, String value) {
    SiteProperty siteProperty = new SiteProperty();
    siteProperty.setName(name);
    siteProperty.setValue(value);
    return siteProperty;
  }

  @Test
  void submittedEditSurvivesTheStepUpRoundTrip() {
    List<SiteProperty> properties = new ArrayList<>(List.of(
        property("security.csp.reportOnly", "img-src 'self'"),
        property("security.untouched", "keep-me")));
    Map<String, String> submitted = Map.of(
        "security.csp.reportOnly", "  img-src 'self' https://api.weather.gov  ",
        "security.untouched", "keep-me");

    SitePropertiesEditorWidget.applySubmittedValues(properties, submitted::get);

    // the pending edit is preserved (and trimmed), not reverted to the stored value
    assertEquals("img-src 'self' https://api.weather.gov", properties.get(0).getValue());
    assertEquals("keep-me", properties.get(1).getValue());
  }

  @Test
  void propertyAbsentFromTheSubmissionKeepsItsStoredValue() {
    List<SiteProperty> properties = new ArrayList<>(List.of(property("security.notOnForm", "stored")));

    SitePropertiesEditorWidget.applySubmittedValues(properties, name -> null);

    assertEquals("stored", properties.get(0).getValue());
  }

  @Test
  void clearedFieldIsHonoured() {
    List<SiteProperty> properties = new ArrayList<>(List.of(property("security.cleared", "was-set")));

    SitePropertiesEditorWidget.applySubmittedValues(properties, name -> "");

    // an admin deliberately emptying a field must not silently keep the old value
    assertEquals("", properties.get(0).getValue());
  }

  @Test
  void secretIsNeverOverlaid() {
    List<SiteProperty> properties = new ArrayList<>(List.of(property(SECRET_NAME, "stored-secret")));

    // even if a value was submitted, the stored secret must be left untouched: the JSP renders
    // secrets with an empty value and keys "not set" / "value hidden" off the stored value
    SitePropertiesEditorWidget.applySubmittedValues(properties, name -> "sk_live_should_not_be_echoed");
    assertEquals("stored-secret", properties.get(0).getValue());

    // and a blank submission must not make a configured secret look unset
    SitePropertiesEditorWidget.applySubmittedValues(properties, name -> "");
    assertEquals("stored-secret", properties.get(0).getValue());
  }

  @Test
  void nullListIsSafe() {
    assertDoesNotThrow(() -> SitePropertiesEditorWidget.applySubmittedValues(null, name -> "x"));
  }
}
