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
<jsp:useBean id="titleHtml" class="java.lang.String" scope="request"/>
<jsp:useBean id="latitude" class="java.lang.String" scope="request"/>
<jsp:useBean id="longitude" class="java.lang.String" scope="request"/>
<jsp:useBean id="mapHeight" class="java.lang.String" scope="request"/>
<jsp:useBean id="mapZoomLevel" class="java.lang.String" scope="request"/>
<jsp:useBean id="showMarker" class="java.lang.String" scope="request"/>
<jsp:useBean id="markerTitle" class="java.lang.String" scope="request"/>
<jsp:useBean id="markerText" class="java.lang.String" scope="request"/>
<jsp:useBean id="recordList" class="java.util.ArrayList" scope="request"/>
<%-- Leaflet JS + Marker Cluster --%>
<link rel="stylesheet" href="${ctx}/javascript/leaflet-1.9.3/leaflet.css" />
<link rel="stylesheet" href="${ctx}/javascript/leaflet.markercluster-1.5.3/MarkerCluster.css" />
<link rel="stylesheet" href="${ctx}/javascript/leaflet.markercluster-1.5.3/MarkerCluster.Default.css" />
<script src="${ctx}/javascript/leaflet-1.9.3/leaflet.js"></script>
<script src="${ctx}/javascript/leaflet.markercluster-1.5.3/leaflet.markercluster.js"></script>
<%-- Render the widget --%>
<style>
  .leaflet-popup-content h5,
  .leaflet-popup-content p {
    margin: 0 0 5px 0;
  }
  .map-information .card {
    margin: 0 8px 4px 0;
    padding: 5px;
  }
  .map-information .card h4, .map-information .card .subheader { padding: 0; margin: 0; }
</style>
<div class="grid-x grid-margin-x map-information" style="padding: 8px 30px">
  <div class="cell small-12">
    <small><strong>INFORMATION ON THIS AREA</strong></small>
  </div>
  <div class="cell small-12 clearfix">
    <div class="float-left size-125 card">
      <h4 class="no-gap">${recordList.size()}</h4>
      <span class="subheader">Properties</span>
    </div>
    <div class="float-left size-125 card">
      <h4 id="interstateDistance" class="no-gap">--</h4>
      <span class="subheader">Interstate</span>
    </div>
    <div class="float-left size-125 card">
      <h4 id="portDistance" class="no-gap">--</h4>
      <span class="subheader">Port</span>
    </div>
    <div class="float-left size-125 card">
      <h4 id="airportDistance" class="no-gap">--</h4>
      <span class="subheader">Airport</span>
    </div>
    <div class="float-left size-125 card">
      <h4 id="hospitalDistance" class="no-gap">--</h4>
      <span class="subheader">Hospital</span>
    </div>
    <c:if test="${!empty titleHtml}">
    <div class="float-left map-title">
      ${titleHtml}
    </div>
    </c:if>
  </div>
</div>
<div class="grid-x grid-margin-x map-container">
  <div class="cell small-12 medium-4 large-3" style="padding: 30px 0 30px 30px;">
    <form id="searchForm${widgetContext.uniqueId}" onsubmit="return updateMap${widgetContext.uniqueId}()" autocomplete="off">
      <div class="input-group">
        <span class="input-group-label">
          <i class="fa fa-search"></i>
        </span>
        <input class="input-group-field" type="text" id="searchText" placeholder="Search for a property" autocomplete="off">
      </div>
      <div class="input-group">
        <span class="input-group-label">
          Acres
        </span>
        <input class="input-group-field" type="text" id="acresMin" placeholder="Min" autocomplete="off">
        <input class="input-group-field" type="text" id="acresMax" placeholder="Max" autocomplete="off">
      </div>
      <div class="input-group">
        <span class="input-group-label">
          Square Footage
        </span>
        <input class="input-group-field" type="text" id="sqFtMin" placeholder="Min" autocomplete="off">
        <input class="input-group-field" type="text" id="sqFtMax" placeholder="Max" autocomplete="off">
      </div>
      <div class="input-group">
        <span class="input-group-label">
          Zoning
        </span>
        <select class="input-group-field" id="zoning" onchange="updateMap${widgetContext.uniqueId}()">
          <option value=""></option>
          <c:forEach items="${zoningList}" var="zone">
            <option value="${js:escape(zone)}"><c:out value="${zone}" /></option>
          </c:forEach>
        </select>
      </div>
      <div class="input-group">
        <span class="input-group-label">
          Enterprise Zone #
        </span>
        <div class="input-group-button">
          <input type="button" class="button secondary" id="enterpriseZone0" value="1" onclick="updateEnterpriseZone(0)">
        </div>
        <div class="input-group-button">
          <input type="button" class="button secondary" id="enterpriseZone1" value="2" onclick="updateEnterpriseZone(1)">
        </div>
      </div>
      <div class="input-group">
        <span class="input-group-label">
          Hub Zone
        </span>
        <div class="input-group-button">
          <input type="button" class="button primary" id="hubZone0" value="Unset" onclick="updateHubZone(0)">
        </div>
        <div class="input-group-button">
          <input type="button" class="button secondary" id="hubZone1" value="Yes" onclick="updateHubZone(1)">
        </div>
      </div>
      <input type="submit" class="button success" value="Show Properties" />
      <input type="reset" class="button secondary" value="Reset Form" onClick="resetForm${widgetContext.uniqueId}()" />
    </form>
  </div>
  <div class="cell small-12 medium-4 large-3" style="padding: 30px 0">
    <div id="tableContainer" style="height:56vh; overflow:scroll">
      <table>
        <thead>
          <tr>
            <th id="propertyTitle">Property</th>
            <th colspan="2">Zoning</th>
          </tr>
        </thead>
        <tbody id="tbody${widgetContext.uniqueId}">
          <tr>
            <td colspan="3" class="subheader">Select criteria to display properties</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
  <div class="cell small-12 medium-4 large-6">
    <div id="mapid${widgetContext.uniqueId}" style="height: ${mapHeight};"></div>
  </div>
</div>
<script>
  var map${widgetContext.uniqueId} = L.map('mapid${widgetContext.uniqueId}').setView([${latitude}, ${longitude}], ${mapZoomLevel});
  <c:choose>
    <c:when test="${mapCredentials.service eq 'mapbox'}">
    L.tileLayer('https://api.mapbox.com/styles/v1/{id}/tiles/{z}/{x}/{y}?access_token={accessToken}', {
      attribution: '&copy; <a href="https://www.mapbox.com/about/maps/">Mapbox</a> &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      tileSize: 512,
      maxZoom: 18,
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
  <c:if test="${showMarker eq 'true'}">
    var marker${widgetContext.uniqueId} = L.marker([${latitude}, ${longitude}]).addTo(map${widgetContext.uniqueId});
    <c:if test="${!empty markerTitle && !empty markerText}">
      marker${widgetContext.uniqueId}.bindPopup("<b><c:out value="${js:escape(markerTitle)}" /></b><c:if test="${!fn:contains(markerText, '{')}"><br><c:out value="${js:escape(markerText)}" /></c:if>").openPopup();
    </c:if>
  </c:if>

  var features = [
    <c:forEach items="${recordList}" var="feature" varStatus="status">
    {
      "type": "Feature",
      "properties": {
        "name": "<c:out value="${js:escape(feature.properties['SITE_ADDRE'])}" />",
        "objectId": "<c:out value="${js:escape(feature.properties['OBJECTID'])}" />",
        "acres": "<c:out value="${js:escape(feature.properties['ACRES'])}" />",
        "zoning": "<c:out value="${js:escape(feature.properties['ZONING'])}" />",
        "totalSqFt": "<c:out value="${js:escape(feature.properties['TOT_SQ_FT'])}" />",
        "hubZone": "<c:out value="${js:escape(feature.properties['Hub_zone'])}" />",
        "enterpriseZone": "<c:out value="${js:escape(feature.properties['Ent_Zone'])}" />",
        "latitude": "<c:out value="${js:escape(feature.properties['latitude'])}" />",
        "longitude": "<c:out value="${js:escape(feature.properties['longitude'])}" />",
        "airportDistance": "<c:out value="${js:escape(feature.properties['airportDistance'])}" />",
        "hospitalDistance": "<c:out value="${js:escape(feature.properties['hospitalDistance'])}" />",
        "interstateDistance": "<c:out value="${js:escape(feature.properties['interstateDistance'])}" />",
        "portDistance": "<c:out value="${js:escape(feature.properties['portDistance'])}" />",
        "popupContent":
          "<h5><c:out value="${js:escape(feature.properties['SITE_ADDRE'])}" /></h5>" +
          "<p>" +
          <c:if test="${feature.properties.containsKey('StreetView')}">
          "<a class=\"small button success no-gap\" target=\"_blank\" href=\"<c:out value="${js:escape(feature.properties['StreetView'])}" />\">Street View</a>&nbsp;" +
          </c:if>
          <c:choose>
          <c:when test="${feature.properties.containsKey('Flyer') && !empty feature.properties['Flyer']}">
          "<a class=\"small button success no-gap\" target=\"_blank\" href=\"<c:out value="${js:escape(feature.properties['Flyer'])}" />\">Flyer</a>&nbsp;" +
          </c:when>
          <c:when test="${feature.properties.containsKey('RealEstate') && !empty feature.properties['RealEstate']}">
          "<a class=\"small button success no-gap\" target=\"_blank\" href=\"<c:out value="${js:escape(feature.properties['RealEstate'])}" />\">Real Estate</a>&nbsp;" +
          </c:when>
          </c:choose>
          "<a class=\"small button success no-gap\" target=\"_blank\" href=\"${ctx}/contact-us\">Contact Us</a>" +
          "</p>" +
          <c:if test="${feature.properties['ACRES'] != '0' || feature.properties['TOT_SQ_FT'] != '0'}">
            "<p>" +
              <c:if test="${feature.properties['ACRES'] != '0'}">
              "<fmt:formatNumber value="${feature.properties['ACRES']}" /> acres" +
              </c:if>
              <c:if test="${feature.properties['TOT_SQ_FT'] != '0'}">
                <c:if test="${feature.properties['ACRES'] != '0'}">"<br />" +</c:if>
                "<fmt:formatNumber value="${feature.properties['TOT_SQ_FT']}" /> total sq ft" +
              </c:if>
            "</p>" +
          </c:if>
          <c:if test="${feature.properties.containsKey('ZONING') || feature.properties.containsKey('Ent_Zone') || feature.properties.containsKey('Hub_zone')}">
          "<p>" +
          <c:if test="${feature.properties.containsKey('ZONING')}">
          "<span class=\"label radius\"><c:out value="${js:escape(feature.properties['ZONING'])}" /></span>&nbsp;" +
          </c:if>
          <c:if test="${feature.properties.containsKey('Ent_Zone') && !empty feature.properties['Ent_Zone']}">
          "<span class=\"label radius\">Enterprise Zone <c:out value="${js:escape(feature.properties['Ent_Zone'])}" /></span>&nbsp;" +
          </c:if>
          <c:if test="${feature.properties.containsKey('Hub_zone') && feature.properties['Hub_zone'] eq 'Yes'}">
          "<span class=\"label radius\">Hub Zone</span>" +
          </c:if>
          "</p>" +
          </c:if>
          ""
      },
      "geometry": {
        "type": "Polygon",
        "coordinates": [[
          <c:if test="${!empty feature.geometry}">
          <c:forEach items="${feature.geometry.exteriorRing}" var="point" varStatus="pointStatus">
          [${point.longitude}, ${point.latitude}]<c:if test="${!pointStatus.last}">,</c:if>
          </c:forEach>
          </c:if>
        ]]
      }
    }
    <c:if test="${!status.last}">,</c:if>
    </c:forEach>
  ];

  <c:if test="${!empty recordList}">
    var style = {
      color: "#000",
      fillColor: "#ff7800",
      weight: 1,
      opacity: 1,
      fillOpacity: 0.8
    };

    function whenClicked(e) {
      updateInfo${widgetContext.uniqueId}(e.target.feature.properties);
    }

    function onEachFeature(feature, layer) {
      if (feature.properties && feature.properties.popupContent) {
        layer.bindPopup(feature.properties.popupContent);
      }
      layer.on({click: whenClicked});
    }
    geojson = L.geoJSON(features, {
      style: style,
      onEachFeature: onEachFeature
    });
    geojson.addTo(map${widgetContext.uniqueId});
  </c:if>

  function centerMap${widgetContext.uniqueId}(objectId, latitude, longitude) {
    geojson.eachLayer(function(feature){
      if (feature.feature.properties.objectId == objectId) {
        feature.openPopup();
        updateInfo${widgetContext.uniqueId}(feature.feature.properties);
      }
    });
    map${widgetContext.uniqueId}.panTo({lon: longitude, lat: latitude});
  }

  function parseNumber(value) {
    if (!value) {
      return 0;
    }
    var a = parseFloat(value.replace(/,/g, ''));
    if (isNaN(a)) {
      return 0;
    }
    return a;
  }

  function featureMatches(feature, settings) {
    if (settings['searchText']) {
      var searchText = settings['searchText'];
      if (!feature['name'].toLowerCase().includes(searchText.toLowerCase())) {
        return false;
      }
    }

    if (settings['acresMin']) {
      var acresMin = settings['acresMin'];
      var acres = parseNumber(feature['acres']);
      if (acres < acresMin) {
        return false;
      }
    }

    if (settings['acresMax']) {
      var acresMax = settings['acresMax'];
      var acres = parseNumber(feature['acres']);
      if (acres > acresMax) {
        return false;
      }
    }

    if (settings['sqFtMin']) {
      var sqFtMin = settings['sqFtMin'];
      var sqFt = parseNumber(feature['totalSqFt']);
      if (sqFt < sqFtMin) {
        return false;
      }
    }

    if (settings['sqFtMax']) {
      var sqFtMax = settings['sqFtMax'];
      var sqFt = parseNumber(feature['totalSqFt']);
      if (sqFt > sqFtMax) {
        return false;
      }
    }

    if (settings['enterpriseZone1'] || settings['enterpriseZone2']) {
      if (!feature['enterpriseZone']) {
        return false;
      }
      var hasMatchingZone = false;
      if (settings['enterpriseZone1'] && feature['enterpriseZone'] == 1) {
        hasMatchingZone = true;
      }
      if (settings['enterpriseZone2'] && feature['enterpriseZone'] == 2) {
        hasMatchingZone = true;
      }
      if (hasMatchingZone === false) {
        return false;
      }
    }

    if (settings['hubZone']) {
      if (!feature['hubZone']) {
        return false;
      }
      if (feature['hubZone'] !== 'Yes') {
        return false;
      }
    }

    if (settings['zoning']) {
      if (feature['zoning'] !== settings['zoning']) {
        return false;
      }
    }

    return true;
  }

  function resetForm${widgetContext.uniqueId}() {
    document.getElementById('searchForm${widgetContext.uniqueId}').reset();
    document.getElementById("hubZone0").classList.add('primary');
    document.getElementById("hubZone1").classList.add('secondary');
    document.getElementById("enterpriseZone0").classList.add('secondary');
    document.getElementById("enterpriseZone1").classList.add('secondary');
    document.getElementById("hubZone0").classList.remove('secondary');
    document.getElementById("hubZone1").classList.remove('primary');
    document.getElementById("enterpriseZone0").classList.remove('primary');
    document.getElementById("enterpriseZone1").classList.remove('primary');
    updateMap${widgetContext.uniqueId}();
  }

  function updateEnterpriseZone(button) {
    var button0 = document.getElementById("enterpriseZone0");
    var button1 = document.getElementById("enterpriseZone1");
    if (button === 0) {
      if (button0.classList.contains('secondary')) {
        button0.classList.add('primary');
        button0.classList.remove('secondary');
      } else {
        button0.classList.add('secondary');
        button0.classList.remove('primary');
      }
    }
    if (button === 1) {
      if (button1.classList.contains('secondary')) {
        button1.classList.add('primary');
        button1.classList.remove('secondary');
      } else {
        button1.classList.add('secondary');
        button1.classList.remove('primary');
      }
    }
    updateMap${widgetContext.uniqueId}();
  }

  function updateHubZone(button) {
    var button0 = document.getElementById("hubZone0");
    var button1 = document.getElementById("hubZone1");
    if (button === 0) {
      if (button0.classList.contains('secondary')) {
        button0.classList.add('primary');
        button0.classList.remove('secondary');
        button1.classList.add('secondary');
        button1.classList.remove('primary');
      }
    }
    if (button === 1) {
      if (button1.classList.contains('secondary')) {
        button1.classList.add('primary');
        button1.classList.remove('secondary');
        button0.classList.add('secondary');
        button0.classList.remove('primary');
      }
    }
    updateMap${widgetContext.uniqueId}();
  }

  function updateInfo${widgetContext.uniqueId}(properties) {
    document.getElementById('interstateDistance').textContent = properties.interstateDistance;
    document.getElementById('portDistance').textContent = properties.portDistance;
    document.getElementById('airportDistance').textContent = properties.airportDistance;
    document.getElementById('hospitalDistance').textContent = properties.hospitalDistance;
  }

  function updateMap${widgetContext.uniqueId}() {

    // Determine the search settings
    var settings = {};
    var searchText = document.getElementById('searchText').value;
    if (searchText) {
      searchText = searchText.trim();
      if (searchText.length > 0) {
        settings['searchText'] = searchText;
      }
    }

    var acresMin = parseNumber(document.getElementById('acresMin').value);
    if (acresMin > 0) {
      settings['acresMin'] = acresMin;
    }

    var acresMax = parseNumber(document.getElementById('acresMax').value);
    if (acresMax > 0) {
      settings['acresMax'] = acresMax;
    }

    var sqFtMin = parseNumber(document.getElementById('sqFtMin').value);
    if (sqFtMin > 0) {
      settings['sqFtMin'] = sqFtMin;
    }

    var sqFtMax = parseNumber(document.getElementById('sqFtMax').value);
    if (sqFtMax > 0) {
      settings['sqFtMax'] = sqFtMax;
    }

    if (document.getElementById("enterpriseZone0").classList.contains("primary")) {
      settings['enterpriseZone1'] = true;
    }

    if (document.getElementById("enterpriseZone1").classList.contains("primary")) {
      settings['enterpriseZone2'] = true;
    }

    if (document.getElementById("hubZone1").classList.contains("primary")) {
      settings['hubZone'] = true;
    }

    var zoningElement = document.getElementById("zoning");
    var zoning = zoningElement.options[zoningElement.selectedIndex].value;
    if (zoning.length > 0) {
      settings['zoning'] = zoning;
    }

    var hasSearch = Object.keys(settings).length !== 0;

    // Show the results
    var tbody = document.getElementById("tbody${widgetContext.uniqueId}");
    var newTbody = document.createElement('tbody');
    newTbody.setAttribute("id", "tbody${widgetContext.uniqueId}");

    var count = 0;
    for (var i = 0; i < features.length; i++) {
      var feature = features[i].properties;
      if (hasSearch && !featureMatches(feature, settings)) {
        continue;
      }
      ++count;

      var newRow = newTbody.insertRow(newTbody.rows.length);

      var newCell = newRow.insertCell(0);
      // var nameText = document.createTextNode(feature.name);
      // newCell.appendChild(nameText);
      newCell.innerHTML = '<a href="javascript:centerMap${widgetContext.uniqueId}(' + feature.objectId + ',' + feature.latitude + ',' + feature.longitude + ')">' + feature.name + '</a>';

      var newCell2 = newRow.insertCell(1);
      newCell2.innerHTML = '<small><span class="label radius">' + feature.zoning + '</span></small>';

      var newCell3 = newRow.insertCell(2);
      newCell3.innerHTML = '<a href="javascript:centerMap${widgetContext.uniqueId}(' + feature.objectId + ',' + feature.latitude + ',' + feature.longitude + ')"><i class="fa fa-info-circle"></i></a>';
    }

    document.getElementById('propertyTitle').textContent = 'Property (' + count + ')';
    document.getElementById('tableContainer').scrollTop = 0;
    tbody.parentNode.replaceChild(newTbody, tbody);
    return false;
  }

  // Auto-update on input
  $('#searchText').on('input', updateMap${widgetContext.uniqueId});
  $('#acresMin').on('input', updateMap${widgetContext.uniqueId});
  $('#acresMax').on('input', updateMap${widgetContext.uniqueId});
  $('#sqFtMin').on('input', updateMap${widgetContext.uniqueId});
  $('#sqFtMax').on('input', updateMap${widgetContext.uniqueId});
</script>