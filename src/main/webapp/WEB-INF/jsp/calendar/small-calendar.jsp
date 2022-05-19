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
<link rel="stylesheet" href="${ctx}/javascript/fullcalendar-3.10.3/fullcalendar.min.css" />
<link rel="stylesheet" href="${ctx}/css/platform-calendar.css?v=<%= VERSION %>" />
<script src="${ctx}/javascript/fullcalendar-3.10.3/moment.min.js"></script>
<script src="${ctx}/javascript/fullcalendar-3.10.3/fullcalendar.min.js"></script>
<%-- Render the widget --%>
<div id="calendar-small"></div>
<div id="tooltip" class="tooltip top align-center under-reveal" style="display:none"></div>
<script>
  function showTooltip(data, event) {
    <%-- Change the tooltip content --%>
    var content = "<h5>" + data.title+"</h5>";
    if (data.allDay === undefined || !data.allDay) {
      content += "<p>";
      content += data.start.format("LT") + "-";
      content += data.end.format("LT");
      content += "</p>";
    }
    if (data.location) {
      content += "<p><i class='fa fa-map-marker'></i> " + data.location + "</p>";
    }
    if (data.description || data.detailsUrl) {
      content += "<p class='no-gap'>(click for more details)</p>";
    }
    <%--
    // if (data.description) {
    //   content += "<p>" + data.description + "</p>";
    // }
    // if (data.detailsUrl) {
    //   content += "Web page <i class='fa fa-external-link'></i>";
    // }
    --%>
    $("#tooltip").html(content);
    <%-- Center and show it --%>
    var element = $(event.target).closest('.fc-event');
    var top = element.offset().top;
    var left = element.offset().left;
    var width = element.outerWidth();
    var tHeight = $("#tooltip").outerHeight();
    var tWidth = $("#tooltip").outerWidth();
    $('#tooltip').css({top: top - tHeight - 12, left: left + (width/2) - (tWidth/2)});
    // $('#tooltip').fadeIn(300);
    $('#tooltip').show();
  }

  $(function () {
    $('#calendar-small').fullCalendar({
      <c:choose>
        <c:when test="${defaultView eq 'list'}">
          header: {
            left: 'title',
            center: '',
            right:  'listWeek,agendaDay,month today prev,next'
          },
          defaultView: 'listWeek',
        </c:when>
        <c:when test="${defaultView eq 'day'}">
          header: {
            left: 'title',
            center: '',
            right:  'agendaDay,listWeek,month today prev,next'
          },
          defaultView: 'agendaDay',
        </c:when>
        <c:otherwise>
          header: {
            left: 'title',
            center: '',
            right:  'month,listWeek,agendaDay today prev,next'
          },
          defaultView: 'month',
        </c:otherwise>
      </c:choose>
      selectable: false,
      selectHelper: false,
      // aspectRatio: 1,
      height: <c:out value="${height}" />,
      views: {
        month: {
          titleFormat: 'MMMM YYYY'
        },
        week: {
          titleFormat: "MMM D, YYYY"
        },
        day: {
          titleFormat: 'D MMM, YYYY'
        }
      },
      editable: false,
      eventLimit: true,
      eventClick: function(event) {
        // Determine if this event has a page
        if (event.id <= 0) {
          return;
        }
        if (event.detailsUrl.indexOf('/') == 0) {
          window.location.href='${ctx}' + event.detailsUrl + '?returnPage=${widgetContext.uri}';
        } else {
          window.location.href='${ctx}/calendar-event/' + event.uniqueId + '?returnPage=${widgetContext.uri}';
        }
        <%--
        if (event.detailsUrl) {
          window.open(event.detailsUrl);
          return false;
        }
        --%>
      },
      eventMouseover: function(data, event, view) {
        showTooltip(data, event);
      },
      eventMouseout: function(data, event, view) {
        $('#tooltip').hide();
      },
      timezone: 'local',
      eventSources: [
        {
          url: '/json/calendar<c:if test="${!empty calendarUniqueId}">?calendarUniqueId=<c:out value="${calendarUniqueId}" /></c:if>',
          color: '#999999',
          textColor: '#ffffff'
        }
      ]
    });
  });
</script>