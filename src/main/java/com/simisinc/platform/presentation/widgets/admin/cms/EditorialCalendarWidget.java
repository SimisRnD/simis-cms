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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.UserSpecification;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The /admin/editorial-calendar admin page (issue #426): renders the page shell for a
 * FullCalendar month/week view of upcoming editorial activity -- web page publish/expire dates,
 * blog post start/end dates, and calendar events. Mirrors {@code CalendarWidget}'s division of
 * labor exactly: this widget prepares the page shell only (here, the Author filter dropdown's
 * user list), while editorial-calendar.jsp's FullCalendar instance fetches the calendar entries
 * themselves client-side from {@link EditorialCalendarAjax}'s /json/editorialCalendar feed, the
 * same eventSources pattern {@code full-calendar.jsp} uses for /json/calendar. No new
 * domain/repository logic is added here -- see {@code EditorialCalendarAjax} for the aggregation
 * across web pages/blog posts/calendar events.
 *
 * @author SimIS Inc.
 */
public class EditorialCalendarWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/editorial-calendar.jsp";

  public WidgetContext execute(WidgetContext context) {

    // The Author filter dropdown -- every enabled user is a candidate content author, mirroring
    // CalendarWidget's calendarList lookup for its own filter dropdown. Sorted here in Java
    // (rather than via an ORDER BY column passed through from the request) since this is just a
    // small dropdown's option list, not a paginated/sortable table.
    UserSpecification specification = new UserSpecification();
    specification.setIsEnabled(true);
    List<User> authorList = new ArrayList<>(UserRepository.findAll(specification, null));
    authorList.sort(Comparator.comparing(EditorialCalendarWidget::displayName, String.CASE_INSENSITIVE_ORDER));
    context.getRequest().setAttribute("authorList", authorList);

    // Standard request items -- matches CalendarEventListWidget's icon/title preference handling
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Bug B (#426 research follow-up): /admin/editorial-calendar and its /json/editorialCalendar
    // feed are reachable by community-manager too (see admin-layout.xml/json-services.xml), so a
    // pure community-manager (no admin/content-manager) sees every Page/Post entry here, but their
    // edit targets -- /admin/web-page and /blog-editor -- are gated "admin,content-manager" only
    // and 404 for that viewer (deliberately not changed by this fix; that's a separate
    // permission-expansion decision). editorial-calendar.jsp's script reads this flag to decide,
    // per rendered entry, whether a Page/Post is a clickable edit link or plain non-interactive
    // text -- an Event entry is unaffected either way, since /admin/calendar-event already
    // includes community-manager in its own role gate. A String "true"/"false" (not a raw EL
    // boolean expression) is used for the same reason SupersetWidget.java sets
    // hideChartTitle/hideChartControls this way: it's what's embedded directly into inline JS
    // below, without a ternary in the JSP itself.
    boolean canEditPagesAndPosts = context.hasRole("admin") || context.hasRole("content-manager");
    context.getRequest().setAttribute("canEditPagesAndPosts", canEditPagesAndPosts ? "true" : "false");

    context.setJsp(JSP);
    return context;
  }

  /** User#getFullName() can return null (no first/last name on file); fall back to username so
   * the sort comparator and the JSP's rendered option text never show a blank entry. */
  private static String displayName(User user) {
    return StringUtils.defaultIfBlank(user.getFullName(), user.getUsername());
  }
}
