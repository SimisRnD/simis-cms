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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="calendar" class="com.simisinc.platform.domain.model.cms.Calendar" scope="request"/>
<link href="${ctx}/css/spectrum-1.8.1/spectrum.css" rel="stylesheet">
<script src="${ctx}/javascript/spectrum-1.8.1/spectrum.js"></script>
<c:choose>
  <c:when test="${calendar.id eq -1}"><h2 class="h4">New Calendar</h2></c:when>
  <c:otherwise>
    <h2 class="h4">Update Calendar</h2>
    <a class="button small radius primary" href="${ctx}/admin/calendar-event?calendarId=${calendar.id}&returnPage=/admin/calendar?calendarId=${calendar.id}">Add Event <i class="fa fa-arrow-circle-right"></i></a>
  </c:otherwise>
</c:choose>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${calendar.id}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>"/>
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <p class="help-text">
    <i class="fa fa-info-circle"></i> A Calendar is just a named, colored container for events --
    saving generates a Unique Id (shown in the Calendars list) that a page author sets as the
    <code>calendarUniqueId</code> preference on a calendar-related widget to display this calendar's
    events. There's no built-in support for recurring events on any calendar; every event added to
    it is independent.
  </p>
  <%-- Form Content --%>
  <label>Name <span class="required">*</span>
    <input type="text" placeholder="Events, Holidays, etc." name="name" value="<c:out value="${calendar.name}"/>" required>
  </label>
  <label>Description
    <input type="text" placeholder="Describe it..." name="description" value="<c:out value="${calendar.description}"/>">
  </label>
  <label>Color
    <input id="color" type="text" name="color" value="<c:out value="${calendar.color}"/>">
  </label>
  <input id="enabled" type="checkbox" name="enabled" value="true" <c:if test="${calendar.id == -1 || calendar.enabled}">checked</c:if>/><label for="enabled">Online?</label>
  <br/><small class="help-text"><i class="fa fa-info-circle"></i> Turning this off hides this calendar's events from their own individual event pages for public visitors (admins/content-managers can still open them directly). It does not currently remove them from calendar grids or search/upcoming-events widgets elsewhere on the site -- those don't check this setting.</small>
  <div class="button-container">
    <c:choose>
      <c:when test="${!empty returnPage}">
        <input type="submit" class="button radius success" value="Save"/>
        <a href="${returnPage}" class="button radius secondary">Cancel</a>
      </c:when>
      <c:otherwise>
        <input type="submit" class="button radius success expanded" value="Save"/>
      </c:otherwise>
    </c:choose>
  </div>
</form>
<script nonce="${cspNonce}">
  $(document).ready(function() {
    var target = document.getElementById('color');
    $("[id='color']").spectrum({
      color: target.value,
      flat: false,
      preferredFormat: "hex",
      chooseText: "Choose",
      cancelText: "Cancel",
      showPalette: true,
      palette: [
        ["#000","#444","#666","#999","#ccc","#eee","#f3f3f3","#fff"],
        ["#f00","#f90","#ff0","#0f0","#0ff","#00f","#90f","#f0f"],
        ["#f4cccc","#fce5cd","#fff2cc","#d9ead3","#d0e0e3","#cfe2f3","#d9d2e9","#ead1dc"],
        ["#ea9999","#f9cb9c","#ffe599","#b6d7a8","#a2c4c9","#9fc5e8","#b4a7d6","#d5a6bd"],
        ["#e06666","#f6b26b","#ffd966","#93c47d","#76a5af","#6fa8dc","#8e7cc3","#c27ba0"],
        ["#c00","#e69138","#f1c232","#6aa84f","#45818e","#3d85c6","#674ea7","#a64d79"],
        ["#900","#b45f06","#bf9000","#38761d","#134f5c","#0b5394","#351c75","#741b47"],
        ["#600","#783f04","#7f6000","#274e13","#0c343d","#073763","#20124d","#4c1130"]
      ],
      showSelectionPalette: true,
      localStorageKey: "site.calendars",
      showInput: true,
      showInitial: true,
      showAlpha: false,
      move: function(color) {
        var targetId = $(this).attr('id');
        changeColor(targetId, color);
      },
      hide: function(color) {
        var targetId = $(this).attr('id');
        changeColor(targetId, color);
      },
      allowEmpty:true
    });
  });
</script>