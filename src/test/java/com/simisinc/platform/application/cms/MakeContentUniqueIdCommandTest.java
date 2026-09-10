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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class MakeContentUniqueIdCommandTest {

  @Test
  void parseToValidValue() {
    Assertions.assertEquals("this-is-a-test", MakeContentUniqueIdCommand.parseToValidValue("This is a test"));
    Assertions.assertEquals("this-is-a-test-example", MakeContentUniqueIdCommand.parseToValidValue("This is a test/example"));
    Assertions.assertEquals("test-and-example", MakeContentUniqueIdCommand.parseToValidValue("Test & Example"));
    Assertions.assertEquals("this-is-a-test", MakeContentUniqueIdCommand.parseToValidValue("This is a test?"));
    Assertions.assertEquals("this-is-a-test-1", MakeContentUniqueIdCommand.parseToValidValue("this-is-a-test-1"));
    Assertions.assertEquals("this-is-a-test", MakeContentUniqueIdCommand.parseToValidValue("This is a test-"));
  }

  /**
   * The control for the entity cases below: a title with no entities and no typographic
   * punctuation must slugify exactly as it always has.
   */
  @Test
  void plainAsciiTitleIsUnaffected() {
    Assertions.assertEquals("quarterly-report-2026",
        MakeContentUniqueIdCommand.parseToValidValue("Quarterly Report 2026"));
    Assertions.assertEquals("this-is-a-test", MakeContentUniqueIdCommand.parseToValidValue("This is a test"));
  }

  /**
   * The reported defect: an entity-encoded smart quote used to leave its entity name in the URL,
   * because "&" became "and", ";" was dropped, and "rsquo" was slugified as ordinary letters.
   */
  @Test
  void smartQuoteEntitiesDoNotLeakIntoTheSlug() {
    Assertions.assertEquals("whats-in-americas-code",
        MakeContentUniqueIdCommand.parseToValidValue("What&rsquo;s in America&rsquo;s Code"));
    Assertions.assertEquals("the-teams-plan",
        MakeContentUniqueIdCommand.parseToValidValue("The Team&#8217;s Plan"));
    Assertions.assertEquals("a-quoted-headline",
        MakeContentUniqueIdCommand.parseToValidValue("&ldquo;A Quoted Headline&rdquo;"));

    // The already-correct case, kept as a control: the character itself is simply dropped
    Assertions.assertEquals("whats-in-americas-code",
        MakeContentUniqueIdCommand.parseToValidValue("What\u2019s in America\u2019s Code"));
  }

  /**
   * "&amp;" must reach the same slug as a typed "&", rather than contributing "andamp".
   */
  @Test
  void encodedAmpersandMatchesATypedAmpersand() {
    Assertions.assertEquals("research-and-development",
        MakeContentUniqueIdCommand.parseToValidValue("Research &amp; Development"));
    Assertions.assertEquals("research-and-development",
        MakeContentUniqueIdCommand.parseToValidValue("Research & Development"));

    // A bare "&" that is not an entity must still be left alone by the decode step
    Assertions.assertEquals("atandt", MakeContentUniqueIdCommand.parseToValidValue("AT&T"));
  }

  /**
   * A decoded dash separates words, so the words on either side do not run together.
   */
  @Test
  void dashEntitiesSeparateWordsInsteadOfLeakingOrJoining() {
    Assertions.assertEquals("design-build-contracts",
        MakeContentUniqueIdCommand.parseToValidValue("Design&mdash;Build Contracts"));
    Assertions.assertEquals("design-build",
        MakeContentUniqueIdCommand.parseToValidValue("Design &mdash; Build"));
    Assertions.assertEquals("pages-10-20",
        MakeContentUniqueIdCommand.parseToValidValue("Pages 10&ndash;20"));
    Assertions.assertEquals("section-a",
        MakeContentUniqueIdCommand.parseToValidValue("Section&nbsp;A"));
  }
}