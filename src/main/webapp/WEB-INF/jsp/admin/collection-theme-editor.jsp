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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="collectionTabList" class="java.util.ArrayList" scope="request"/>
<link href="${ctx}/css/spectrum-1.8.1/spectrum.css" rel="stylesheet">
<script src="${ctx}/javascript/spectrum-1.8.1/spectrum.js"></script>
<form class="table-of-contents-editor" method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form Content --%>
  <input type="hidden" name="collectionId" value="${collection.id}"/>
  <input type="hidden" name="returnPage" value="${returnPage}"/>
  <div class="grid-x grid-padding-x">
    <div class="small-12 medium-4 large-3 cell">
      <table class="unstriped">
        <thead>
        <tr>
          <th width="200">Name</th>
          <th>Value</th>
        </tr>
        </thead>
        <tbody>
        <tr>
          <td>Header Background Color</td>
          <td nowrap><input id="headerBgColor" type="text" name="headerBgColor" value="<c:out value="${collection.headerBgColor}"/>"></td>
        </tr>
        <tr>
          <td>Header Text Color</td>
          <td nowrap><input id="headerTextColor" type="text" name="headerTextColor" value="<c:out value="${collection.headerTextColor}"/>"></td>
        </tr>
        </tbody>
      </table>
    </div>
    <div class="small-12 medium-4 large-3 cell">
      <table class="unstriped">
        <thead>
        <tr>
          <th width="200">Name</th>
          <th>Value</th>
        </tr>
        </thead>
        <tbody>
        <tr>
          <td>Menu Text Color</td>
          <td nowrap><input id="menuTextColor" type="text" name="menuTextColor" value="<c:out value="${collection.menuTextColor}"/>"></td>
        </tr>
        <tr>
          <td>Menu Background Color</td>
          <td nowrap><input id="menuBgColor" type="text" name="menuBgColor" value="<c:out value="${collection.menuBgColor}"/>"></td>
        </tr>
        <tr>
          <td>Menu Border Color</td>
          <td nowrap><input id="menuBorderColor" type="text" name="menuBorderColor" value="<c:out value="${collection.menuBorderColor}"/>"></td>
        </tr>
        </tbody>
      </table>
    </div>
    <div class="small-12 medium-4 large-3 cell">
      <table class="unstriped">
        <thead>
        <tr>
          <th width="200">Name</th>
          <th>Value</th>
        </tr>
        </thead>
        <tbody>
        <tr>
          <td>Menu Active Text Color</td>
          <td nowrap><input id="menuActiveTextColor" type="text" name="menuActiveTextColor" value="<c:out value="${collection.menuActiveTextColor}"/>"></td>
        </tr>
        <tr>
          <td>Menu Active Background Color</td>
          <td nowrap><input id="menuActiveBgColor" type="text" name="menuActiveBgColor" value="<c:out value="${collection.menuActiveBgColor}"/>"></td>
        </tr>
        <tr>
          <td>Menu Active Border Color</td>
          <td nowrap><input id="menuActiveBorderColor" type="text" name="menuActiveBorderColor" value="<c:out value="${collection.menuActiveBorderColor}"/>"></td>
        </tr>
        </tbody>
      </table>
    </div>
    <div class="small-12 medium-4 large-3 cell">
      <table class="unstriped">
        <thead>
        <tr>
          <th width="200">Name</th>
          <th>Value</th>
        </tr>
        </thead>
        <tbody>
        <tr>
          <td>Menu Hover Text Color</td>
          <td nowrap><input id="menuHoverTextColor" type="text" name="menuHoverTextColor" value="<c:out value="${collection.menuHoverTextColor}"/>"></td>
        </tr>
        <tr>
          <td>Menu Hover Background Color</td>
          <td nowrap><input id="menuHoverBgColor" type="text" name="menuHoverBgColor" value="<c:out value="${collection.menuHoverBgColor}"/>"></td>
        </tr>
        <tr>
          <td>Menu Hover Border Color</td>
          <td nowrap><input id="menuHoverBorderColor" type="text" name="menuHoverBorderColor" value="<c:out value="${collection.menuHoverBorderColor}"/>"></td>
        </tr>
        </tbody>
      </table>
    </div>
  </div>
  <p>
    <input type="submit" class="button radius success" value="Save"/>
    <a href="${returnPage}" class="button radius secondary">Cancel</a>
  </p>
</form>
<script>
  <%-- Map the variable property to the mapped CSS classes --%>
  var colorIdList = [];
  var colorSelectorList = [];

  colorIdList.push('headerBgColor');
  colorSelectorList.push('.item-menu.menu-bar,.item-menu.title-bar');

  colorIdList.push('headerTextColor');
  colorSelectorList.push('.item-menu.menu-bar, .item-menu.menu-bar .menu-text, .item-menu.menu-bar .collection-name, .item-menu.menu-bar i');

  colorIdList.push('menuTextColor');
  colorSelectorList.push('.item-menu.menu-bar div > ul > li > a');

  colorIdList.push('menuBgColor');
  colorSelectorList.push('.item-menu.menu-bar div > ul > li > a');

  colorIdList.push('menuBorderColor');
  colorSelectorList.push('.item-menu.menu-bar div > ul > li > a');

  colorIdList.push('menuActiveTextColor');
  colorSelectorList.push('.item-menu.menu-bar div > ul > li.is-selected > a');

  colorIdList.push('menuActiveBgColor');
  colorSelectorList.push('.item-menu.menu-bar div > ul > li.is-selected > a');

  colorIdList.push('menuActiveBorderColor');
  colorSelectorList.push('.item-menu.menu-bar div > ul > li.is-selected > a');

  colorIdList.push('menuHoverTextColor');
  colorSelectorList.push('.item-menu.menu-bar div > ul > li > a:hover, .item-menu.menu-bar .dropdown.menu > li.is-active > a');

  colorIdList.push('menuHoverBgColor');
  colorSelectorList.push('.item-menu.menu-bar div > ul > li > a:hover, .item-menu.menu-bar .dropdown.menu > li.is-active > a');

  colorIdList.push('menuHoverBorderColor');
  colorSelectorList.push('.item-menu.menu-bar div > ul > li > a:hover, .item-menu.menu-bar .dropdown.menu > li.is-active > a');

  function changeColor(targetId, color) {
    let idx = colorIdList.indexOf(targetId);
    let colorSelector = colorSelectorList[idx];
    if (colorSelector.length === 0) {
      return;
    }
    // Adjust static elements
    let list = document.querySelectorAll(colorSelector);
    for (let i = 0; i < list.length; i++) {
      if (targetId.indexOf('BgColor') > -1) {
        if (color.toName() === 'transparent') {
          list[i].style.backgroundColor = 'transparent';
        } else if (color.getAlpha() && color.getAlpha() < 1) {
          list[i].style.backgroundColor = color.toRgbString();
        } else {
          list[i].style.backgroundColor = color.toHexString();
        }
      } else if (targetId.indexOf('BorderColor') > -1) {
        if (color.toName() === 'transparent') {
          list[i].style.border = '1px solid transparent';
        } else if (color.getAlpha() && color.getAlpha() < 1) {
          list[i].style.border = '1px solid ' + color.toRgbString();
        } else {
          list[i].style.border = '1px solid ' + color.toHexString();
        }
      } else {
        list[i].style.color = color.toHexString();
      }
    }
  }

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
        localStorageKey: "collection.properties",
        showInput: true,
        showInitial: true,
        showAlpha: true,
        move: function (color) {
          var targetId = $(this).attr('id');
          changeColor(targetId, color);
        },
        hide: function (color) {
          var targetId = $(this).attr('id');
          changeColor(targetId, color);
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