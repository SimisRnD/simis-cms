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
<%@ page import="static com.simisinc.platform.ApplicationInfo.VERSION" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="calendarList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="calendarEventList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="calendarEvent" class="com.simisinc.platform.domain.model.cms.CalendarEvent" scope="request"/>
<jsp:useBean id="calendarUniqueId" class="java.lang.String" scope="request"/>
<jsp:useBean id="defaultView" class="java.lang.String" scope="request"/>
<jsp:useBean id="height" class="java.lang.String" scope="request"/>
<%-- Full Calendar --%>
<link rel="stylesheet" href="${ctx}/javascript/fullcalendar-5.11.0/main.min.css" />
<link rel="stylesheet" href="${ctx}/css/platform-calendar.css?v=<%= VERSION %>" />
<script src="${ctx}/javascript/fullcalendar-5.11.0/moment-2.27.0.min.js"></script>
<script src="${ctx}/javascript/fullcalendar-5.11.0/main.min.js"></script>
<script src="${ctx}/javascript/fullcalendar-5.11.0/fc-plugin-moment-5.5.0.min.js"></script>
<%-- Render the widget --%>
<div id="calendar-small"></div>
<div id="tooltip" class="tooltip top align-center under-reveal" style="display:none"></div>
<script>
  function showTooltip(el, event) {
    let content = "<h5>" + event.title+"</h5>";
    if (event.allDay === undefined || !event.allDay) {
      content += "<p>";
      content += moment(event.start).format("LT") + " - " + moment(event.end).format("LT");
      content += "</p>";
    }
    if (event.extendedProps.location) {
      content += "<p><i class='fa fa-map-marker'></i> " + event.extendedProps.location + "</p>";
    }
    if (event.extendedProps.description || event.extendedProps.detailsUrl) {
      content += "<p class='no-gap'>(click for more details)</p>";
    }
    $("#tooltip").html(content);
    let ttHeight = $("#tooltip").outerHeight();
    let ttWidth = $("#tooltip").outerWidth();

    <%-- Center and show it --%>
    let parentLeft = Math.round($('#calendar-small').parent().offset().left);
    let calendarTop = $('#calendar-small').offset().top;
    let calendarLeft = $('#calendar-small').offset().left;
    let elTop = $(el).offset().top;
    let elLeft = Math.round($(el).offset().left);
    let tdLeft = Math.round($(el).closest('td').offset().left);
    let tdWidth = Math.round($(el).closest('td').outerWidth());
    let top = Math.round(elTop - calendarTop - ttHeight + 8);
    let zero = Math.round(calendarLeft - parentLeft);
    let left = zero + tdLeft - calendarLeft + (tdWidth/2) - (ttWidth/2);
    $('#tooltip').css({top: top, left: left});
    $('#tooltip').fadeIn(200);
    // $('#tooltip').show();
  }

  <c:choose>
    <c:when test="${defaultView eq 'list'}">
      <c:set var="initialView" scope="request" value="listWeek" />
      <c:set var="optionOrder" scope="request" value="listWeek,dayGridMonth" />
    </c:when>
    <c:when test="${defaultView eq 'day'}">
      <c:set var="initialView" scope="request" value="timeGrid" />
      <c:set var="optionOrder" scope="request" value="timeGrid,dayGridMonth" />
    </c:when>
    <c:otherwise>
      <c:set var="initialView" scope="request" value="dayGridMonth" />
      <c:set var="optionOrder" scope="request" value="dayGridMonth,listWeek" />
    </c:otherwise>
  </c:choose>

  document.addEventListener('DOMContentLoaded', function() {
    let calendarEl = document.getElementById('calendar-small');
    let calendar = new FullCalendar.Calendar(calendarEl, {
      initialView: '${initialView}',
      height: <c:out value="${height}" />,
      headerToolbar: {
        start: 'title',
        center: '',
        end: '${optionOrder} today prev,next'
      },
      buttonText: {
        today:    'Today',
        month:    'Month',
        week:     'Week',
        day:      'Day',
        list:     'List',
        timeGrid: 'Day'
      },
      selectable: false,
      eventClick: function(info) {
        if (info.event.id <= 0) {
          return;
        }
        let detailsUrl = info.event.extendedProps.detailsUrl;
        if (detailsUrl && detailsUrl.indexOf('/') === 0) {
          window.location.href='${ctx}' + detailsUrl + '?returnPage=${widgetContext.uri}';
        } else {
          window.location.href='${ctx}/calendar-event/' + info.event.extendedProps.uniqueId + '?returnPage=${widgetContext.uri}';
        }
      },
      eventMouseEnter: function(info) {
        if (info.view.type !== 'dayGridMonth') {
          return;
        }
        showTooltip(info.el, info.event);
      },
      eventMouseLeave: function(info) {
        $('#tooltip').hide();
      },
      eventSources: [
        {
          url: '/json/calendar<c:if test="${!empty calendarUniqueId}">?calendarUniqueId=<c:out value="${calendarUniqueId}" /></c:if>',
          color: '#999999',
          textColor: '#ffffff'
        }
      ]
    });
    calendar.render();
  });
</script>
