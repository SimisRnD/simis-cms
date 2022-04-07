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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="mapCredentials" class="com.simisinc.platform.domain.model.maps.MapCredentials" scope="request"/>
<jsp:useBean id="latitude" class="java.lang.String" scope="request"/>
<jsp:useBean id="longitude" class="java.lang.String" scope="request"/>
<jsp:useBean id="mapHeight" class="java.lang.String" scope="request"/>
<jsp:useBean id="markerTitle" class="java.lang.String" scope="request"/>
<jsp:useBean id="markerText" class="java.lang.String" scope="request"/>
<%-- Apple Maps --%>

NOT IMPLEMENTED

<%-- Render the widget --%>
<div id="mapid" style="height: ${mapHeight}px;"></div>
<script>
  var mymap = L.map('mapid').setView([${latitude}, ${longitude}], 13);

  <c:choose>
  <c:when test="${mapCredentials.service eq 'mapbox'}">
  L.tileLayer('https://api.mapbox.com/styles/v1/{id}/tiles/{z}/{x}/{y}?access_token={accessToken}', {
      attribution: '&copy; <a href="https://www.mapbox.com/about/maps/">Mapbox</a> &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      tileSize: 512,
      maxZoom: 12,
      zoomOffset: -1,
      id: 'mapbox/streets-v11',
    accessToken: '${mapCredentials.accessToken}'
  }).addTo(mymap);
  </c:when>
  <c:otherwise>
  L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors',
    minZoom: 8,
    maxZoom: 12
  }).addTo(mymap);
  </c:otherwise>
  </c:choose>

  var marker = L.marker([${latitude}, ${longitude}]).addTo(mymap);
  <c:if test="${!empty markerTitle && !empty markerText}">
  marker.bindPopup("<b><c:out value="${js:escape(markerTitle)}" /></b><c:if test="${!fn:contains(markerText, '{')}"><br><c:out value="${js:escape(markerText)}" /></c:if>").openPopup();
  </c:if>
</script>