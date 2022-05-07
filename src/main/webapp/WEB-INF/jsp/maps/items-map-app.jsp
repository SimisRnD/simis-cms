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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="mapCredentials" class="com.simisinc.platform.domain.model.maps.MapCredentials" scope="request"/>
<jsp:useBean id="latitude" class="java.lang.String" scope="request"/>
<jsp:useBean id="longitude" class="java.lang.String" scope="request"/>
<jsp:useBean id="mapHeight" class="java.lang.String" scope="request"/>
<jsp:useBean id="mapZoomLevel" class="java.lang.String" scope="request"/>
<jsp:useBean id="itemList" class="java.util.ArrayList" scope="request"/>
<%-- Leaflet JS + Marker Cluster --%>
<link rel="stylesheet" href="${ctx}/css/leaflet-1.6.0/leaflet.css" />
<link rel="stylesheet" href="${ctx}/css/leaflet-markercluster-1.4.1/MarkerCluster.css" />
<link rel="stylesheet" href="${ctx}/css/leaflet-markercluster-1.4.1/MarkerCluster.Default.css" />
<script src="${ctx}/javascript/leaflet-1.6.0/leaflet.js"></script>
<script src="${ctx}/javascript/leaflet-markercluster-1.4.1/leaflet.markercluster.js"></script>
<%-- Render the widget --%>
<style>
  .leaflet-popup-content h5,
  .leaflet-popup-content p {
    margin: 0 0 5px 0;
  }
  @media screen and (min-width: 40em) {
    #tableContainer${widgetContext.uniqueId} {
      height:${mapHeight};
      overflow:scroll;
    }
  }
</style>
<div class="grid-x grid-margin-x map-container">
  <div class="cell small-12 medium-5 large-4" style="padding: 0">
    <div id="tableContainer${widgetContext.uniqueId}">
      <table class="unstriped">
        <c:if test="${!empty title}">
        <thead>
          <tr>
            <th><c:out value="${title}" /></th>
          </tr>
        </thead>
        </c:if>
        <tbody id="tbody${widgetContext.uniqueId}">
          <c:forEach items="${itemList}" var="item" varStatus="status">
          <tr>
            <td>
              <a href="javascript:showMarker${widgetContext.uniqueId}(${status.index},${item.latitude},${item.longitude});"><c:out value="${item.name}" /></a>
              <c:if test="${!empty item.address}"><br />
                <small class="subheader">
                  <c:out value="${item.street}" /><br />
                  <c:if test="${!empty item.addressLine2}"><c:out value="${item.addressLine2}" /><br /></c:if>
                  <c:out value="${item.city}" />, <c:out value="${item.state}" /> <c:out value="${item.postalCode}" />
                  <c:if test="${!empty item.phoneNumber}"><br /><c:out value="${item.phoneNumber}" /></c:if>
                </small>
              </c:if>
            </td>
          </tr>
          </c:forEach>
        </tbody>
      </table>
    </div>
  </div>
  <div class="cell small-12 medium-7 large-8">
    <div id="mapid${widgetContext.uniqueId}" style="height:${mapHeight};"></div>
  </div>
</div>
<script>
  var map${widgetContext.uniqueId} = L.map('mapid${widgetContext.uniqueId}').setView([${latitude},${longitude}],${mapZoomLevel});
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
      maxZoom: 18
    }).addTo(map${widgetContext.uniqueId});
    </c:otherwise>
  </c:choose>

  <c:forEach items="${itemList}" var="item" varStatus="status">
    var marker${widgetContext.uniqueId}id${status.index} = L.marker([${item.latitude}, ${item.longitude}]).addTo(map${widgetContext.uniqueId});
    marker${widgetContext.uniqueId}id${status.index}.bindPopup("<b><c:out value="${js:escape(item.name)}" /></b><c:if test="${!fn:contains(markerText, '{')}"><br><c:out value="${js:escape(item.street)}" /></c:if>");
  </c:forEach>

  function showMarker${widgetContext.uniqueId}(id,latitude,longitude) {
    eval("marker${widgetContext.uniqueId}id" + id).openPopup();
    map${widgetContext.uniqueId}.panTo({lon: longitude, lat: latitude});
  }
</script>