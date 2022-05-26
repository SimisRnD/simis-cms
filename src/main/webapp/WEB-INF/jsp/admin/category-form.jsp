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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="category" class="com.simisinc.platform.domain.model.items.Category" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<link href="${ctx}/css/spectrum-1.8.1/spectrum.css" rel="stylesheet">
<script src="${ctx}/javascript/spectrum-1.8.1/spectrum.js"></script>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
  <input type="hidden" name="token" value="${userSession.formToken}" />
  <%-- Form specific --%>
  <input type="hidden" name="id" value="${category.id}" />
  <input type="hidden" name="collectionId" value="${collection.id}" />
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Name
    <input type="text" placeholder="Give it a name..." name="name" value="<c:out value="${category.name}"/>">
  </label>
  <label>Description
    <input type="text" placeholder="Describe it..." name="description" value="<c:out value="${category.description}"/>">
  </label>
  <label>Icon (<a href="https://fontawesome.com/search?m=free&s=solid" target="_blank">view</a>)
    <input type="text" placeholder="" name="icon" value="<c:out value="${category.icon}"/>">
  </label>
  <label>
    <input id="headerBgColor" type="text" name="headerBgColor" value="<c:out value="${category.headerBgColor}"/>">
    Header Background Color
  </label>
  <label>
    <input id="headerTextColor" type="text" name="headerTextColor" value="<c:out value="${category.headerTextColor}"/>">
    Header Text Color
  </label>
  <p><input type="submit" class="button radius success expanded margin-top-20" value="Save" /></p>
</form>
<script>
  var colorIdList = [];
  colorIdList.push('headerBgColor');
  colorIdList.push('headerTextColor');

  $(document).ready(function () {
    for (var i = 0; i < colorIdList.length; i++) {
      var target = document.getElementById(colorIdList[i]);
      $("[id='" + colorIdList[i] + "']").spectrum({
        color: target.value,
        flat: false,
        preferredFormat: "hex",
        chooseText: "Choose",
        cancelText: "Cancel",
        showPalette: true,
        palette: [
          ["#000", "#444", "#666", "#999", "#ccc", "#eee", "#f3f3f3", "#fff"],
          ["#f00", "#f90", "#ff0", "#0f0", "#0ff", "#00f", "#90f", "#f0f"],
          ["#f4cccc", "#fce5cd", "#fff2cc", "#d9ead3", "#d0e0e3", "#cfe2f3", "#d9d2e9", "#ead1dc"],
          ["#ea9999", "#f9cb9c", "#ffe599", "#b6d7a8", "#a2c4c9", "#9fc5e8", "#b4a7d6", "#d5a6bd"],
          ["#e06666", "#f6b26b", "#ffd966", "#93c47d", "#76a5af", "#6fa8dc", "#8e7cc3", "#c27ba0"],
          ["#c00", "#e69138", "#f1c232", "#6aa84f", "#45818e", "#3d85c6", "#674ea7", "#a64d79"],
          ["#900", "#b45f06", "#bf9000", "#38761d", "#134f5c", "#0b5394", "#351c75", "#741b47"],
          ["#600", "#783f04", "#7f6000", "#274e13", "#0c343d", "#073763", "#20124d", "#4c1130"]
        ],
        showSelectionPalette: true,
        localStorageKey: "category.properties",
        showInput: true,
        showInitial: true,
        showAlpha: true,
        move: function (color) {
          var targetId = $(this).attr('id');
          // changeColor(targetId, color);
        },
        hide: function (color) {
          var targetId = $(this).attr('id');
          // changeColor(targetId, color);
        },
        change: function(color) {
          // Update the original form value
          var targetId = $(this).attr('id');
          let thisInput = document.getElementById(targetId + '');
          if (thisInput) {
            let thisColor = color + '';
            if (thisColor) {
              if (thisColor.indexOf("rgba") > -1 && thisColor.indexOf(", 0)") > -1) {
                thisColor = 'transparent';
              }
              thisInput.value = thisColor;
            }
          }
        },
        allowEmpty: false
      });
    }
  });
</script>