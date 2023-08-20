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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="mapCredentials" class="com.simisinc.platform.domain.model.maps.MapCredentials" scope="request"/>
<jsp:useBean id="mapHeight" class="java.lang.String" scope="request"/>
<%-- Leaflet JS + Marker Cluster --%>
<link rel="stylesheet" href="${ctx}/javascript/leaflet-1.9.4/leaflet.css" />
<link rel="stylesheet" href="${ctx}/javascript/leaflet.markercluster-1.5.3/MarkerCluster.css" />
<link rel="stylesheet" href="${ctx}/javascript/leaflet.markercluster-1.5.3/MarkerCluster.Default.css" />
<script src="${ctx}/javascript/leaflet-1.9.4/leaflet.js"></script>
<script src="${ctx}/javascript/leaflet.markercluster-1.5.3/leaflet.markercluster.js"></script>
<%-- Render the widget --%>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<div id="mapid${widgetContext.uniqueId}" style="height: 320px;"></div>
<c:if test="${empty sessionList}">
  <p>No locations were found</p>
</c:if>
<script>
  <%--var mymap = L.map('mapid${widgetContext.uniqueId}').fitWorld();--%>
  var map${widgetContext.uniqueId} = L.map('mapid${widgetContext.uniqueId}', {
    <c:choose>
      <c:when test="${!empty centerPoint}">
        center: L.latLng(${centerPoint.latitude}, ${centerPoint.longitude}),
        zoom: 3
      </c:when>
      <c:otherwise>
        center: L.latLng(10, 0),
        zoom: 2
      </c:otherwise>
    </c:choose>
  });
  <c:choose>
  <c:when test="${mapCredentials.service eq 'mapbox'}">
  L.tileLayer('https://api.mapbox.com/styles/v1/{id}/tiles/{z}/{x}/{y}?access_token={accessToken}', {
      attribution: '&copy; <a href="https://www.mapbox.com/about/maps/">Mapbox</a> &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      tileSize: 512,
      maxZoom: 12,
      zoomOffset: -1,
      id: 'mapbox/streets-v11',
    accessToken: '${mapCredentials.accessToken}'
  }).addTo(map${widgetContext.uniqueId});
  </c:when>
  <c:otherwise>
  L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors',
    minZoom: 1,
    maxZoom: 10
  }).addTo(map${widgetContext.uniqueId});
  </c:otherwise>
  </c:choose>

  var markers${widgetContext.uniqueId} = L.markerClusterGroup();
  <c:forEach items="${sessionList}" var="session">
  <%--L.marker([${session.latitude}, ${session.longitude}]).addTo(mymap);--%>
  markers${widgetContext.uniqueId}.addLayer(L.marker([${session.latitude}, ${session.longitude}]));
  </c:forEach>
  map${widgetContext.uniqueId}.addLayer(markers${widgetContext.uniqueId});
</script>
