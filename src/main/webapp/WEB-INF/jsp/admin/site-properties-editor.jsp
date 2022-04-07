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
<%@ page import="java.util.TimeZone" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="sitePropertyList" class="java.util.ArrayList" scope="request"/>
<link href="${ctx}/css/spectrum-1.8.1/spectrum.css" rel="stylesheet">
<script src="${ctx}/javascript/spectrum-1.8.1/spectrum.js"></script>
<%-- Handle image uploads --%>
<script>

  var currentPhotoId = 'none';
  function SetPhotoId(id) {
    currentPhotoId = id;
  }

  function SavePhoto(e,id) {
    var file = e.files[0]; // similar to: document.getElementById("file").files[0]
    var formData = new FormData();
    formData.append("file", file);
    var xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function() {
      if (this.readyState === 4) {
        if (this.status === 200) {
          var fileData = JSON.parse(this.responseText);
          document.getElementById("imageUrl" + id).value = fileData.location;
          document.getElementById("imageUrlPreview" + id).src = fileData.location;
        } else {
          document.getElementById("imageFile" + id).value = "";
          alert('There was an error with the file. Make sure to use a .jpg or .png');
        }
      }
    };
    xhr.open("POST", '${ctx}/image-upload?widget=imageUpload1&token=${userSession.formToken}');
    xhr.send(formData);
  }
</script>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form Content --%>
  <%@include file="../page_messages.jspf" %>
  <table class="unstriped">
    <thead>
    <tr>
      <th width="200">Name</th>
      <th>Value</th>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${sitePropertyList}" var="siteProperty">
      <tr>
        <td><c:out value="${siteProperty.label}" /></td>
        <td nowrap>
          <c:choose>
            <c:when test="${siteProperty.name eq 'theme.logo.color'}">
              <select name="${siteProperty.name}">
                <option value="full-color"<c:if test="${siteProperty.value eq 'full-color'}"> selected</c:if>>Full color</option>
                <option value="all-white"<c:if test="${siteProperty.value eq 'all-white'}"> selected</c:if>>All white</option>
                <option value="color-and-white"<c:if test="${siteProperty.value eq 'color-and-white'}"> selected</c:if>>Color and White</option>
                <option value="text-only"<c:if test="${siteProperty.value eq 'text-only'}"> selected</c:if>>Text only</option>
                <option value="none"<c:if test="${siteProperty.value eq 'none'}"> selected</c:if>>No logo</option>
              </select>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.footer.logo.color'}">
              <select name="${siteProperty.name}">
                <option value="full-color"<c:if test="${siteProperty.value eq 'full-color'}"> selected</c:if>>Full color</option>
                <option value="all-white"<c:if test="${siteProperty.value eq 'all-white'}"> selected</c:if>>All white</option>
                <option value="color-and-white"<c:if test="${siteProperty.value eq 'color-and-white'}"> selected</c:if>>Color and White</option>
                <option value="text-only"<c:if test="${siteProperty.value eq 'text-only'}"> selected</c:if>>Text only</option>
                <option value="none"<c:if test="${siteProperty.value eq 'none'}"> selected</c:if>>No logo</option>
              </select>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.menu.location'}">
              <select name="${siteProperty.name}">
                <option value="center"<c:if test="${siteProperty.value eq 'center'}"> selected</c:if>>Centered</option>
                <option value="left"<c:if test="${siteProperty.value eq 'left'}"> selected</c:if>>Left Justified</option>
                <option value="right"<c:if test="${siteProperty.value eq 'right'}"> selected</c:if>>Right Justified</option>
                <option value="pro"<c:if test="${siteProperty.value eq 'pro'}"> selected</c:if>>Expanded</option>
                <option value="custom"<c:if test="${siteProperty.value eq 'custom'}"> selected</c:if>>Custom XML</option>
                <option value="none"<c:if test="${siteProperty.value eq 'none'}"> selected</c:if>>None</option>
              </select>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.footer.style'}">
              <select name="${siteProperty.name}">
                <option value="default"<c:if test="${siteProperty.value eq 'default'}"> selected</c:if>>Basic</option>
                <option value="custom"<c:if test="${siteProperty.value eq 'custom'}"> selected</c:if>>Custom XML</option>
                <option value="none"<c:if test="${siteProperty.value eq 'none'}"> selected</c:if>>None</option>
              </select>
            </c:when>
            <c:when test="${siteProperty.type eq 'font'}">
              <select name="${siteProperty.name}">
                <option value=""<c:if test="${siteProperty.value eq ''}"> selected</c:if>>Default (Use CSS)</option>
                <option value="abel"<c:if test="${siteProperty.value eq 'abel'}"> selected</c:if>>Abel</option>
                <option value="lato"<c:if test="${siteProperty.value eq 'lato'}"> selected</c:if>>Lato</option>
                <option value="libre-baskerville"<c:if test="${siteProperty.value eq 'libre-baskerville'}"> selected</c:if>>Libre Baskerville</option>
                <option value="muli"<c:if test="${siteProperty.value eq 'muli'}"> selected</c:if>>Muli</option>
                <option value="open-sans"<c:if test="${siteProperty.value eq 'open-sans'}"> selected</c:if>>Open Sans</option>
                <option value="oswald"<c:if test="${siteProperty.value eq 'oswald'}"> selected</c:if>>Oswald</option>
                <option value="questrial"<c:if test="${siteProperty.value eq 'questrial'}"> selected</c:if>>Questrial</option>
                <option value="rubik"<c:if test="${siteProperty.value eq 'rubik'}"> selected</c:if>>Rubik</option>
                <option value="source-sans-pro"<c:if test="${siteProperty.value eq 'source-sans-pro'}"> selected</c:if>>Source Sans Pro</option>
              </select> <a href="https://fonts.google.com" target="_blank" rel="noreferrer"><i class="fa fa-external-link-square"></i></a>
            </c:when>
            <c:when test="${siteProperty.type eq 'color'}">
              <input id="${siteProperty.name}" type="text" name="${siteProperty.name}" value="<c:out value="${siteProperty.value}"/>">
            </c:when>
            <c:when test="${siteProperty.type eq 'url'}">
              <div class="input-group">
                <span class="input-group-label"><i class="fa fa-link"></i></span>
                <input class="input-group-field" id="${siteProperty.id}" type="text" name="${siteProperty.name}" placeholder="http://..." value="<c:out value="${siteProperty.value}"/>">
              </div>
            </c:when>
            <c:when test="${siteProperty.type eq 'image'}">
              <div class="grid-x grid-margin-x">
                <div class="small-8 cell">
                  <div class="input-group">
                    <input class="input-group-field" type="text" placeholder="Local Image URL" id="imageUrl${siteProperty.id}" name="${siteProperty.name}" value="<c:out value="${siteProperty.value}"/>">
                    <span class="input-group-label" style="padding: 0;"><a class="button small primary expanded no-gap" data-open="imageBrowserReveal" onclick="SetPhotoId(${siteProperty.id});">Browse Images</a></span>
                  </div>
                  <label for="imageFile${siteProperty.id}" class="button">Upload Image File...</label>
                  <input type="file" id="imageFile${siteProperty.id}" class="show-for-sr" onchange="SavePhoto(this,${siteProperty.id})">
                </div>
                <div class="small-4 cell">
                  <img id="imageUrlPreview${siteProperty.id}" src="<c:out value="${siteProperty.value}"/>" style="max-height: 150px; max-width: 150px"/>
                </div>
              </div>
            </c:when>
            <c:when test="${siteProperty.type eq 'boolean'}">
              <div class="switch large">
                <input class="switch-input" id="${siteProperty.name}-yes-no" type="checkbox" name="${siteProperty.name}" value="true"<c:if test="${siteProperty.value eq 'true'}"> checked</c:if>>
                <label class="switch-paddle" for="${siteProperty.name}-yes-no">
                <span class="switch-active" aria-hidden="true">Yes</span>
                <span class="switch-inactive" aria-hidden="true">No</span>
                </label>
              </div>
            </c:when>
            <c:when test="${siteProperty.name eq 'site.timezone'}">
              <select name="${siteProperty.name}">
                <c:forEach items="<%= TimeZone.getAvailableIDs() %>" var="timezone">
                  <option value="${timezone}"<c:if test="${siteProperty.value eq timezone}"> selected</c:if>><c:out value="${timezone}" /></option>
                </c:forEach>
              </select>
            </c:when>
            <c:when test="${siteProperty.type eq 'disabled'}">
              <input type="text" class="no-gap" name="${siteProperty.name}" value="${html:toHtml(siteProperty.value)}" disabled />
            </c:when>
            <c:otherwise>
              <input type="text" class="no-gap" name="${siteProperty.name}" value="${html:toHtml(siteProperty.value)}" />
            </c:otherwise>
          </c:choose>
        </td>
      </tr>
    </c:forEach>
    </tbody>
  </table>
  <p>
    <input type="submit" class="button radius success" value="Save" />
    <a href="${ctx}/admin" class="button radius secondary">Cancel</a>
  </p>
</form>
<div class="reveal large" id="imageBrowserReveal" data-reveal data-animation-in="slide-in-down fast">
  <h3>Loading...</h3>
</div>
<script>
  <%-- Map the variable property to the mapped CSS classes --%>
  var colorIdList = [];
  var colorSelectorList = [];
  <c:forEach items="${sitePropertyList}" var="siteProperty">
  <c:if test="${siteProperty.type eq 'color'}">
  colorIdList.push('${siteProperty.name}');
  <c:choose>
  <c:when test="${siteProperty.name eq 'theme.body.text.color'}">colorSelectorList.push('body');</c:when>
  <c:when test="${siteProperty.name eq 'theme.body.backgroundColor'}">colorSelectorList.push('body');</c:when>
  <c:when test="${siteProperty.name eq 'theme.utilitybar.text.color'}">colorSelectorList.push('#platform-menu .utility-bar');</c:when>
  <c:when test="${siteProperty.name eq 'theme.utilitybar.link.color'}">colorSelectorList.push('#platform-menu .utility-bar a');</c:when>
  <c:when test="${siteProperty.name eq 'theme.utilitybar.backgroundColor'}">colorSelectorList.push('#platform-menu .utility-bar');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.text.color'}">colorSelectorList.push('#platform-menu,#platform-menu .menu-text,#platform-menu .menu-text a,#platform-menu .menu-text a:hover');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.backgroundColor'}">colorSelectorList.push('#platform-menu,#platform-small-menu,#platform-small-menu .title-bar');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.text.color'}">colorSelectorList.push('#platform-menu ul.menu li a');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.arrow.color'}">colorSelectorList.push('.dropdown.menu>li.is-dropdown-submenu-parent>a::after');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.text.hoverBackgroundColor'}">colorSelectorList.push('#platform-menu ul.menu li a:hover,#platform-menu .is-active');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.hoverTextColor'}">colorSelectorList.push('#platform-menu ul.menu li > a:hover,#platform-menu ul.menu li.is-active > a,#platform-menu .is-active .is-dropdown-submenu-item a:hover');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.dropdown.backgroundColor'}">colorSelectorList.push('#platform-menu ul.is-dropdown-submenu li.is-dropdown-submenu-item');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.dropdown.text.color'}">colorSelectorList.push('#platform-menu ul.is-dropdown-submenu li.is-dropdown-submenu-item a');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.text.color'}">colorSelectorList.push('.button');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.default.backgroundColor'}">colorSelectorList.push('.button.base');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.default.hoverBackgroundColor'}">colorSelectorList.push('.button.base:hover, .button.base:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.primary.backgroundColor'}">colorSelectorList.push('.button.primary');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.primary.hoverBackgroundColor'}">colorSelectorList.push('.button.primary:hover, .button.primary:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.secondary.backgroundColor'}">colorSelectorList.push('.button.secondary');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.secondary.hoverBackgroundColor'}">colorSelectorList.push('.button.secondary:hover, .button.secondary:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.success.backgroundColor'}">colorSelectorList.push('.button.success');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.success.hoverBackgroundColor'}">colorSelectorList.push('.button.success:hover, .button.success:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.warning.backgroundColor'}">colorSelectorList.push('.button.warning');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.warning.hoverBackgroundColor'}">colorSelectorList.push('.button.warning:hover, .button.warning:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.alert.backgroundColor'}">colorSelectorList.push('.button.alert');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.alert.hoverBackgroundColor'}">colorSelectorList.push('.button.alert:hover, .button.alert:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.backgroundColor'}">colorSelectorList.push('.callout.base');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.text.color'}">colorSelectorList.push('.callout.base,.callout.base label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.primary.backgroundColor'}">colorSelectorList.push('.callout.primary');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.primary.text.color'}">colorSelectorList.push('.callout.primary,.callout.primary label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.secondary.backgroundColor'}">colorSelectorList.push('.callout.secondary');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.secondary.text.color'}">colorSelectorList.push('.callout.secondary,.callout.secondary label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.success.backgroundColor'}">colorSelectorList.push('.callout.success');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.success.text.color'}">colorSelectorList.push('.callout.success,.callout.success label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.warning.backgroundColor'}">colorSelectorList.push('.callout.warning');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.warning.text.color'}">colorSelectorList.push('.callout.warning,.callout.warning label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.alert.backgroundColor'}">colorSelectorList.push('.callout.alert');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.alert.text.color'}">colorSelectorList.push('.callout.alert,.callout.alert label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.footer.backgroundColor'}">colorSelectorList.push('.platform-footer');</c:when>
  <c:when test="${siteProperty.name eq 'theme.footer.text.color'}">colorSelectorList.push('.platform-footer');</c:when>
  <c:when test="${siteProperty.name eq 'theme.footer.links.color'}">colorSelectorList.push('.platform-footer a');</c:when>
  <c:otherwise>colorSelectorList.push('');</c:otherwise>
  </c:choose>
  </c:if>
  </c:forEach>

  function changeColor(targetId, color) {
    var idx = colorIdList.indexOf(targetId);
    var colorSelector = colorSelectorList[idx];
    if (colorSelector.length === 0) {
      return;
    }
    // Handle dynamic elements
    if (targetId.indexOf('theme.topbar.menu.dropdown.text.color') > -1) {
      $("head").append('<style>' + colorSelector + '{color: ' + color.toHexString() + '}</style>');
      return;
    } else if (targetId.indexOf('theme.topbar.menu.hoverTextColor') > -1) {
      $("head").append('<style>' + colorSelector + '{color: ' + color.toHexString() + '}</style>');
      return;
    } else if (targetId.indexOf('theme.topbar.menu.text.hoverBackgroundColor') > -1) {
      $("head").append('<style>' + colorSelector + '{background-color: ' + color.toHexString() + '}</style>');
      return;
    }
    // Adjust static elements
    var list = document.querySelectorAll(colorSelector);
    for (var i = 0; i < list.length; i++) {
      if (targetId.indexOf('theme.topbar.menu.arrow.color') > -1) {
        list[i].style.borderColor = color.toHexString() + ' transparent transparent';
      } else if (targetId.indexOf('ackgroundColor') > -1) {
        list[i].style.backgroundColor = color.toHexString();
      } else {
        list[i].style.color = color.toHexString();
      }
    }
  }

  $(document).ready(function() {
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
        localStorageKey: "site.properties",
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
        allowEmpty:false
      });
    }
  });
</script>
<script>
  $('#imageBrowserReveal').on('open.zf.reveal', function () {
    $('#imageBrowserReveal').html("<h3>Loading...</h3>");
    $.ajax({
      url: '${ctx}/image-browser?inputId=imageUrl' + currentPhotoId + '&view=reveal',
      cache: false,
      dataType: 'html'
    }).done(function (content) {
      setTimeout(function () {
        $('#imageBrowserReveal').html(content);
        $('#imageBrowserReveal').trigger('resizeme.zf.trigger');
      }, 1000);
    });
  })
</script>
