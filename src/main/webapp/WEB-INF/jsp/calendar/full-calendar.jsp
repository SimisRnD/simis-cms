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
<c:if test="${(userSession.hasRole('admin') || userSession.hasRole('content-manager'))}">
<style>
  .fc-day:hover{
    background: #DDECF7;
    cursor: cell;
  }
  <%-- Allow pointer-events through --%>
  .fc-slats, /*horizontals*/
  .fc-content-skeleton, /*day numbers*/
  .fc-bgevent-skeleton /*events container*/{
    pointer-events:none
  }
  <%-- Turn pointer events back on --%>
  .fc-bgevent,
  .fc-event-container{
    pointer-events:auto; /*events*/
  }
</style>
</c:if>
<%-- Render the widget --%>
<%@include file="../page_messages.jspf" %>
<div id="calendar"><c:if test="${(userSession.hasRole('admin') || userSession.hasRole('content-manager'))}"><small><i class="fa fa-calendar-plus-o"></i> Select a date range to create events</small></c:if></div>
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
    $('#calendar').fullCalendar({
      header: {
        left: 'title',
        center: '',
        right:  'today prev,next'
      },
      // defaultView: 'listWeek',
      // defaultView: 'timelineDay',
      <c:if test="${(userSession.hasRole('admin') || userSession.hasRole('content-manager'))}">
        selectable: true,
      </c:if>
      // eventLimit: true,
      height: <c:out value="${height}" />,
      views: {
        month: {
          titleFormat: 'MMMM YYYY'
        },
        week: {
          titleFormat: "MMMM D, YYYY"
        },
        day: {
          titleFormat: 'D MMM, YYYY'
        }
      },
      <c:if test="${(userSession.hasRole('admin') || userSession.hasRole('content-manager'))}">
        select: function (start, end) {
          if (start) {
            document.getElementById("calendarEventForm").reset();
            document.getElementById('formTitle').innerHTML = "Add an Event";
            document.getElementById('id').value = "-1";
            document.getElementById('startDate').value = start.format('MM-DD-YYYY') + ' 08:00';
            if (end) {
              document.getElementById('endDate').value = end.subtract(1, 'days').format('MM-DD-YYYY') + ' 17:00';
            }
            if (start.format('MM-DD-YYYY') !== end.format('MM-DD-YYYY')) {
              $('#allDay')[0].checked = true;
            } else {
              $('#allDay')[0].checked = false;
            }
            // Show the form
            $('#duplicateButton').hide();
            $('#deleteButton').hide();
            var $modal = $('#formReveal');
            $modal.foundation('open');
          }
        },
      </c:if>
      <%--
        dayClick: function(date, event, view) {
          if ($(event.target).is('.fc-event-container *, .fc-more') ) {
            alert('hello ' + view.name);
          } else {
            alert('found ' + view.name);
          }
        },
      --%>
      <c:choose>
        <c:when test="${(userSession.hasRole('admin') || userSession.hasRole('content-manager'))}">
          eventClick: function(event) {

            // Reset form
            document.getElementById("calendarEventForm").reset();

            // Determine if this event has a page
            if (event.id <= 0) {
              return;
            }

            // Get the data and populate the form
            $.getJSON("${ctx}/json/calendarEvent?id=" + event.id, function( data ) {

              // {
              //   "id": 22,
              //     "allDay": true,
              //     "start": "2018-12-03",
              //     "end": "2018-12-05T24:00",
              //     "location": "Washington, DC",
              //     "title": "DC"
              // }
              document.getElementById('formTitle').innerHTML = "Update an Event";
              document.getElementById('id').value = data.id;
              document.getElementById('eventLinkInput').value = '${ctx}/calendar-event/' + event.uniqueId + '?returnPage=${widgetContext.uri}';
              if ($('#calendarId').is('input, select')) {
                $("#calendarId").val(data.calendarId);
              } else {
                document.getElementById('calendarId').value = data.calendarId;
              }
              if (data.hasOwnProperty('allDay')) {
                $('#allDay')[0].checked = true;
              }
              document.getElementById('startDate').value = moment(data.start).format('MM-DD-YYYY HH:mm');
              document.getElementById('endDate').value = moment(data.end).format('MM-DD-YYYY HH:mm');
              if (data.hasOwnProperty('location')) {
                document.getElementById('location').value = data.location;
              }
              if (data.hasOwnProperty('description')) {
                document.getElementById('summary').value = data.description;
              }
              if (data.hasOwnProperty('detailsUrl')) {
                document.getElementById('detailsUrl').value = data.detailsUrl;
              }
              if (data.hasOwnProperty('signUpUrl')) {
                document.getElementById('signUpUrl').value = data.signUpUrl;
              }
              document.getElementById('title').value = data.title;

              // Show the form dialog
              $('#duplicateButton').show();
              $('#deleteButton').show();
              var $modal = $('#modalReveal');
              $modal.foundation('open');
            });
          },
        </c:when>
        <c:otherwise>
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
        </c:otherwise>
      </c:choose>
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
        <%--
        // { googleCalendarId: 'abcd1234@group.calendar.google.com' },
        {
          events: [
            <c:forEach items="${calendarEventList}" var="calendarEvent" varStatus="status">
              {
                id: ${calendarEvent.id},
                <c:choose>
                  <c:when test="${calendarEvent.allDay}">
                    allDay: true,
                    start: '<fmt:formatDate pattern="yyyy-MM-dd" value="${calendarEvent.startDate}" />',
                    end: '<fmt:formatDate pattern="yyyy-MM-dd" value="${calendarEvent.endDate}" />T24:00',
                </c:when>
                  <c:otherwise>
                    start: '<fmt:formatDate pattern="yyyy-MM-dd" value="${calendarEvent.startDate}" />T<fmt:formatDate pattern="HH:mm" value="${calendarEvent.startDate}" />',
                    end: '<fmt:formatDate pattern="yyyy-MM-dd" value="${calendarEvent.endDate}" />T<fmt:formatDate pattern="HH:mm" value="${calendarEvent.endDate}" />',
                  </c:otherwise>
                </c:choose>
                <c:forEach items="${calendarList}" var="calendar">
                  <c:if test="${calendar.id eq calendarEvent.calendarId}">color: '${js:escape(calendar.color)}',</c:if>
                </c:forEach>
                <c:choose>
                  <c:when test="${!empty calendarEvent.detailsUrl}">
                    url: '${url:encode(calendarEvent.detailsUrl)}',
                  </c:when>
                  <c:when test="${!empty calendarEvent.signUpUrl}">
                    url: '${url:encode(calendarEvent.signUpUrl)}',
                  </c:when>
                </c:choose>
                <c:if test="${!empty calendarEvent.summary}">
                  description: '${js:escape(calendarEvent.summary)}',
                </c:if>
                title: '${js:escape(calendarEvent.title)}'
              }
              <c:if test="${!status.last}">,</c:if>
            </c:forEach>
          ],
          color: '#79C554',
          textColor: '#ffffff'
        }
        --%>
      ]
    });
  });
</script>
<c:if test="${(userSession.hasRole('admin') || userSession.hasRole('content-manager'))}">
  <div class="reveal tiny" id="modalReveal" data-reveal>
    <h3>Event Options</h3>
    <p>Would you like to make changes or view the details of this event?</p>
    <button class="button" data-open="formReveal">Edit this Event</button>
    <input type="hidden" id="eventLinkInput" value="#" />
    <button id="eventLink" class="button">View the Event Details</button>
    <button class="close-button" data-close aria-label="Close modal" type="button">
      <span aria-hidden="true">&times;</span>
    </button>
  </div>
  <div class="reveal small" id="formReveal" data-reveal data-close-on-esc="false" data-close-on-click="false" data-animation-in="slide-in-down fast">
    <button class="close-button" data-close aria-label="Close modal" type="button">
      <span aria-hidden="true">&times;</span>
    </button>
    <h4 id="formTitle">Create an Event</h4>
    <form id="calendarEventForm" method="post" autocomplete="off">
      <%-- Required by controller --%>
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <%-- Form --%>
      <input type="hidden" name="id" id="id" value="-1"/>
      <c:if test="${empty calendarList}">
        <div class="callout alert">A calendar must be configured before events can be added</div>
      </c:if>
      <c:choose>
        <c:when test="${calendarList.size() eq 1}">
          <label>Event Title
            <input type="text" placeholder="Event Title" name="title" id="title" value="" required>
          </label>
          <input type="hidden" name="calendarId" id="calendarId" value="${calendarList[0].id}" />
        </c:when>
        <c:otherwise>
        <div class="grid-x grid-margin-x">
          <div class="small-3 cell">
            <label>Calendar
              <select name="calendarId" id="calendarId">
                <c:forEach items="${calendarList}" var="calendar" varStatus="status">
                  <option value="${calendar.id}"><c:out value="${calendar.name}" /></option>
                </c:forEach>
              </select>
            </label>
          </div>
          <div class="small-9 cell">
          <label>Event Title
            <input type="text" placeholder="Event Title" name="title" id="title" value="" required>
          </label>
          </div>
        </div>
        </c:otherwise>
      </c:choose>
      <small>All day?</small>
      <div class="switch large">
        <input class="switch-input" id="allDay" type="checkbox" name="allDay" value="true"<c:if test="${calendarEvent.allDay}"> checked</c:if>>
        <label class="switch-paddle" for="allDay">
          <span class="switch-active" aria-hidden="true">Yes</span>
          <span class="switch-inactive" aria-hidden="true">No</span>
        </label>
      </div>
      <div class="grid-x grid-margin-x">
        <div class="medium-6 cell">
          <label>Start Date
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-calendar"></i></span>
              <input class="input-group-field" type="text" placeholder="mm-dd-yyyy time" id="startDate" name="startDate" value="" required>
            </div>
          </label>
          <script>
            $(function(){
              $('#startDate').fdatepicker({
                format: 'mm-dd-yyyy hh:ii',
                disableDblClickSelection: true,
                pickTime: true
              });
            });
          </script>
        </div>
        <div class="medium-6 cell">
          <label>End Date
            <div class="input-group">
              <span class="input-group-label"><i class="fa fa-calendar"></i></span>
              <input class="input-group-field" type="text" placeholder="mm-dd-yyyy time" id="endDate" name="endDate" value="" required>
            </div>
          </label>
          <script>
            $(function(){
              $('#endDate').fdatepicker({
                format: 'mm-dd-yyyy hh:ii',
                disableDblClickSelection: true,
                pickTime: true
              });
            });
          </script>
        </div>
      </div>
      <label>Location
        <input type="text" placeholder="Name of Location" name="location" id="location" value="">
      </label>
      <label>Summary
        <input type="text" placeholder="Event Summary" name="summary" id="summary" value="">
      </label>
      <div class="grid-x grid-margin-x">
        <div class="medium-6 cell">
          <label>URL for more information
            <input type="text" placeholder="Details Url" name="detailsUrl" id="detailsUrl" value="">
          </label>
        </div>
        <div class="medium-6 cell">
          <label>URL to sign up
            <input type="text" placeholder="Sign Up Url" name="signUpUrl" id="signUpUrl" value="">
          </label>
        </div>
      </div>
      <p>
        <input type="submit" class="button radius success expanded" value="Save" />
        <input id="duplicateButton" style="display:none" type="submit" class="button radius primary expanded" name="duplicate" value="Duplicate" />
        <span id="deleteButton" style="display:none"><a href="javascript:deleteCalendarEvent()" class="button radius alert expanded">Delete</a></span>
      </p>
    </form>
  </div>
  <script>
    function deleteCalendarEvent() {
      if (!confirm("Are you sure you want to DELETE this event?")) {
        return;
      }
      window.location.href = '${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&id=' + document.getElementById('id').value;
    }
    // Handle the modal and click event
    var eventLink = $('#eventLink');
    eventLink.on('click', function () {
      var $modal = $('#modalReveal');
      $modal.foundation('close');
      window.location.href = document.getElementById('eventLinkInput').value;
    });
  </script>
</c:if>