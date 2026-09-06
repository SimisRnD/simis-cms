<%--
  ~ Copyright 2022 SimIS Inc.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="image" uri="/WEB-INF/tlds/image-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="calendar" class="com.simisinc.platform.domain.model.cms.Calendar" scope="request"/>
<jsp:useBean id="calendarEvent" class="com.simisinc.platform.domain.model.cms.CalendarEvent" scope="request"/>
<%@include file="../page_messages.jspf" %>
<div class="platform-calendar-details-container">
<c:if test="${!empty title}">
  <div class="platform-calendar-title text-center">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  </div>
</c:if>
  <%-- Date Formatting --%>
  <c:set var="startDateTime" scope="request">${date:format(calendarEvent.startDate, "MMMM d, yyyy")}</c:set>
  <c:set var="endDateTime" scope="request">${date:format(calendarEvent.endDate, "MMMM d, yyyy")}</c:set>
  <c:set var="startDate" scope="request">${date:format(calendarEvent.startDate, "MMMM d")}</c:set>
  <c:set var="endDate" scope="request">${date:format(calendarEvent.endDate, "MMMM d")}</c:set>
  <c:set var="startYear" scope="request">${date:format(calendarEvent.startDate, "yyyy")}</c:set>
  <c:set var="endYear" scope="request">${date:format(calendarEvent.endDate, "yyyy")}</c:set>
  <c:set var="startTime" scope="request">${date:format(calendarEvent.startDate, "h:mm a")}</c:set>
  <c:set var="endTime" scope="request">${date:format(calendarEvent.endDate, "h:mm a")}</c:set>
  <c:set var="thisMonth" scope="request">${date:format(calendarEvent.startDate, "MMMM yyyy")}</c:set>
  <c:set var="thisDay" scope="request">${date:format(calendarEvent.startDate, "MMMM d, yyyy")}</c:set>
  <%-- No month header or day separator here. Both belong to the calendar LIST, where they group
       many events under a heading; on a page showing one event there is nothing to group, and they
       printed the same date twice more directly above the event's own date line. The list views
       (upcoming-events.jsp, calendar-search-results.jsp) still use them. --%>
  <div class="platform-calendar-event-block">
    <h1><c:out value="${calendarEvent.title}" /></h1>
    <c:choose>
      <c:when test="${calendarEvent.allDay}">
        <%-- A single-day all-day event still has a date worth showing; only the range
             is conditional, which the inner test below handles. --%>
        <p class="platform-calendar-event-date">
          <i class="fa fa-calendar-o fa-fw"></i>
          ${date:format(calendarEvent.startDate, "MMMM d, yyyy")}
          <c:if test="${startDateTime ne endDateTime}">
            -
            ${date:format(calendarEvent.endDate, "MMMM d, yyyy")}
          </c:if>
        </p>
      </c:when>
      <c:otherwise>
        <p class="platform-calendar-event-date">
          <c:choose>
            <c:when test="${startDateTime eq endDateTime}">
              <i class="fa fa-clock-o fa-fw"></i>
              ${date:format(calendarEvent.startDate, "h:mm a")}
              <c:if test="${startTime ne endTime}">
                - ${date:format(calendarEvent.endDate, "h:mm a")}
              </c:if>
            </c:when>
            <c:otherwise>
              <i class="fa fa-calendar-o fa-fw"></i>
              ${date:format(calendarEvent.startDate, "MMMM d, h:mm a")}
              -
              <c:choose>
                <c:when test="${startYear ne endYear}">
                  ${date:format(calendarEvent.endDate, "MMMM d, yyyy h:mm a")}
                </c:when>
                <c:otherwise>
                  ${date:format(calendarEvent.endDate, "MMMM d, h:mm a")}
                </c:otherwise>
              </c:choose>
            </c:otherwise>
          </c:choose>
        </p>
      </c:otherwise>
    </c:choose>
    <c:if test="${!empty calendarEvent.imageUrl}">
      <%-- sizes is stated rather than left as "auto". sizes="auto" resolves against the image's own
           laid-out width, and this image has no CSS-determined width -- platform.css gives it
           max-width and max-height and leaves width/height auto, so the width follows the intrinsic
           size. That is circular, and the browser resolves it by falling back to the default
           replaced-element box: a 1279x1279 square rendered 300x150, a 100% aspect error, squashed
           to half its height. Measured at 1280px and at 375px; both wrong, both correct once sizes
           is explicit.
           Issue #1349 is the reason this was reached for, but its case was a content image inside a
           container with a determinate width, where "auto" does resolve. The precondition does not
           hold here.
           720px is the widest this is ever displayed: max-width: 100% inside a ~707px column caps
           it, and a tall image is capped earlier still by max-height. Below the 767px breakpoint
           the column is the viewport, so 100vw is right there. --%>
      <c:set var="eventImageSrcset" value="${image:srcset(calendarEvent.imageUrl)}"/>
      <p class="platform-calendar-event-image">
        <img src="<c:out value="${calendarEvent.imageUrl}"/>" alt="<c:out value="${calendarEvent.title}"/>"
          <c:if test="${not empty eventImageSrcset}"> srcset="<c:out value="${eventImageSrcset}"/>" sizes="(max-width: 767px) 100vw, 720px"</c:if>
          loading="lazy" decoding="async" />
      </p>
    </c:if>
    <c:if test="${!empty calendarEvent.location}">
      <p class="platform-calendar-event-location"><i class="fa fa-map-marker fa-fw"></i> <c:out value="${calendarEvent.location}" /></p>
    </c:if>
    <%-- Tags are not shown to visitors. They render as plain <span>s, not links, and no
         tag-filtered calendar view exists to link to -- so "tradeshow" and "2026" were editorial
         metadata on display with nothing to do, and "2026" repeated the date directly above it.
         They remain on the event in the admin, where they organise the calendar. If a filtered
         view is ever built, this is the place to bring them back as links. --%>
    <c:if test="${!empty calendarEvent.summary}">
      <p class="platform-calendar-event-summary"><c:out value="${calendarEvent.summary}" /></p>
    </c:if>
    <%-- Actions grouped in one row. The Add-to-Calendar control and the link buttons were
         separate blocks with the summary between them, so they stacked down the page and the
         first carried an inline margin to fake alignment. Flexed here instead, which also
         wraps them cleanly on a narrow screen. --%>
    <div class="platform-calendar-event-actions">
      <%-- The Add-to-Calendar control was removed, not restyled. The vendored library builds its
           own button with innerHTML and puts an inline onclick on it:
             result.innerHTML = '<button ... onclick="return doAddToCalenderClick(...)">'
           PageServlet sends script-src 'self' 'nonce-...' with no 'unsafe-inline', so the browser
           refuses to run that attribute and the button did nothing on any deployment. Verified on
           the live site: doAddToCalenderClick is defined, the dropdown markup is present with a
           valid .ics data URL inside it, and clicking the button leaves the dropdown display:none.
           This is the issue #1188 class of dead control, and tools/check-inline-handlers.py cannot
           see it -- that gate reads JSPs, and this handler is injected from JavaScript at runtime,
           which its own docstring records as out of scope.
           An optional action link takes its place, so a site can point visitors somewhere useful
           from the event page. Unset by default: no deployment gains a button it did not ask for. --%>
      <c:if test="${!empty actionUrl}">
        <c:choose>
          <c:when test="${fn:startsWith(actionUrl, 'http://') || fn:startsWith(actionUrl, 'https://')}">
            <a class="button primary" target="_blank" rel="noopener" href="<c:out value="${actionUrl}"/>"><c:out value="${empty actionLabel ? 'View all events' : actionLabel}"/></a>
          </c:when>
          <c:otherwise>
            <a class="button primary" href="<c:out value="${ctx}${actionUrl}"/>"><c:out value="${empty actionLabel ? 'View all events' : actionLabel}"/></a>
          </c:otherwise>
        </c:choose>
      </c:if>
      <c:if test="${!empty calendarEvent.detailsUrl || !empty calendarEvent.signUpUrl || !empty calendarEvent.videoUrl}">
        <p class="platform-calendar-event-buttons">
          <i class="fa fa-fw"></i>
          <c:if test="${!empty calendarEvent.videoUrl}">
            <c:choose>
              <c:when test="${fn:startsWith(calendarEvent.videoUrl, 'http://') || fn:startsWith(calendarEvent.videoUrl, 'https://')}">
                <a class="button primary" target="_blank" href="<c:out value="${calendarEvent.videoUrl}" />">Join Meeting</a>
              </c:when>
              <c:otherwise>
                <a class="button primary" href="<c:out value="${ctx}${calendarEvent.videoUrl}" />">Join Meeting</a>
              </c:otherwise>
            </c:choose>
          </c:if>
          <c:if test="${!empty calendarEvent.detailsUrl}">
            <c:choose>
              <c:when test="${fn:startsWith(calendarEvent.detailsUrl, 'http://') || fn:startsWith(calendarEvent.detailsUrl, 'https://')}">
                <a class="button primary" target="_blank" href="<c:out value="${calendarEvent.detailsUrl}" />">Learn More</a>
              </c:when>
              <c:otherwise>
                <a class="button primary" href="<c:out value="${ctx}${calendarEvent.detailsUrl}" />">View Details</a>
              </c:otherwise>
            </c:choose>
          </c:if>
          <c:if test="${!empty calendarEvent.signUpUrl}">
            <c:choose>
              <c:when test="${fn:startsWith(calendarEvent.signUpUrl, 'http://') || fn:startsWith(calendarEvent.signUpUrl, 'https://')}">
                <a class="button primary" target="_blank" href="<c:out value="${calendarEvent.signUpUrl}" />">Sign Up Page</a>
              </c:when>
              <c:otherwise>
                <a class="button primary" href="<c:out value="${ctx}${calendarEvent.signUpUrl}" />">Sign Up Page</a>
              </c:otherwise>
            </c:choose>
          </c:if>
        </p>
      </c:if>
    </div>
    <c:choose>
      <c:when test="${!empty returnPage}">
        <p class="platform-calendar-event-return">
          <i class="fa fa-fw"></i> <a href="#" data-js-call="goBack" data-js-arg1="<c:out value="${returnPage}"/>"><i class="${font:fal()} fa-arrow-left"></i> Return to previous page</a>
        </p>
      </c:when>
      <c:otherwise>
        <p class="platform-calendar-event-return">
          <i class="fa fa-fw"></i> <a href="${ctx}/calendar"><i class="${font:fal()} fa-arrow-left"></i> View the calendar</a>
        </p>
      </c:otherwise>
    </c:choose>
  </div>
</div>
<script nonce="${cspNonce}">
  function goBack() {
    window.history.back();
  }
</script>
