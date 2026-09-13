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

package com.simisinc.platform.presentation.widgets.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every internal link to a calendar event must point at the event's canonical URL, with no query
 * string appended (issue #2019).
 *
 * <p>The listings used to append {@code ?returnPage=${widgetContext.uri}} to every event link. The
 * event page canonicalizes to the bare path, so each of those links pointed at a URL that
 * canonicalizes away, and the canonical URL itself ended up with no incoming internal links at
 * all -- which is exactly what a site audit of www.simisinc.com reported against both published
 * events: two canonical inlinks each (the two listing pages' variants) and zero href inlinks. It
 * also multiplied the crawled URL count by one per linking page per event.
 *
 * <p>The parameter bought nothing for that cost. Its value was never read: the event page passed
 * it to {@code goBack()}, which declares no argument and calls {@code window.history.back()}. Its
 * only effect was gating whether the "Return to previous page" link rendered, and that gate now
 * reads a same-origin {@code document.referrer} instead, which costs no URL.
 *
 * <p>These assertions read the JSP sources rather than a rendered page because the defect is in
 * the link markup itself, and because a rendered check would need a populated calendar to have
 * any event links to inspect.
 *
 * @author elizabeth houser
 */
class CalendarEventLinkCanonicalTest {

  private static final File CALENDAR_JSP_DIR = new File("src/main/webapp/WEB-INF/jsp/calendar");

  /**
   * JSP comments never reach the response. They are stripped before scanning because the comments
   * in this directory necessarily name the very parameter being asserted against -- special-casing
   * them by wording would be far more brittle than removing them wholesale.
   */
  private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);

  /** A server-rendered event link, capturing whatever trails the unique id inside the href. */
  private static final Pattern EVENT_HREF = Pattern
      .compile("/calendar-event/\\$\\{calendarEvent\\.uniqueId\\}([^\"]*)\"");

  private static List<File> jspFiles;

  @BeforeAll
  static void collectJspFiles() {
    assertTrue(CALENDAR_JSP_DIR.isDirectory(),
        "calendar JSP directory not found (run from the project root): " + CALENDAR_JSP_DIR.getAbsolutePath());
    File[] found = CALENDAR_JSP_DIR.listFiles((dir, name) -> name.endsWith(".jsp"));
    assertTrue(found != null && found.length > 0, "no calendar JSPs found to scan");
    jspFiles = List.of(found);
  }

  private static String sourceWithoutComments(File jsp) throws IOException {
    return JSP_COMMENT.matcher(Files.readString(jsp.toPath(), StandardCharsets.UTF_8)).replaceAll("");
  }

  @Test
  void noCalendarJspAppendsAReturnPageParameterToAnEventUrl() throws IOException {
    List<String> offenders = new ArrayList<>();
    for (File jsp : jspFiles) {
      if (sourceWithoutComments(jsp).contains("returnPage")) {
        offenders.add(jsp.getName());
      }
    }
    assertEquals(List.of(), offenders,
        "returnPage is dead -- goBack() ignores its value -- and appending it points internal links "
            + "at non-canonical URLs. The back link reads document.referrer instead.");
  }

  @Test
  void everyServerRenderedEventLinkIsTheBareCanonicalPath() throws IOException {
    List<String> offenders = new ArrayList<>();
    int linksChecked = 0;
    for (File jsp : jspFiles) {
      Matcher matcher = EVENT_HREF.matcher(sourceWithoutComments(jsp));
      while (matcher.find()) {
        linksChecked++;
        if (!matcher.group(1).isEmpty()) {
          offenders.add(jsp.getName() + ": /calendar-event/${calendarEvent.uniqueId}" + matcher.group(1));
        }
      }
    }
    assertEquals(List.of(), offenders, "event links must be the canonical path, with nothing appended");
    // Guard against a vacuous pass: if the listings stop linking to events at all, the loop above
    // finds nothing and every assertion in it holds trivially.
    assertTrue(linksChecked >= 5,
        "expected at least the 5 known event links across the calendar listings, found " + linksChecked);
  }
}
