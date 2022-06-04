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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="numberValue" class="java.lang.String" scope="request"/>
<jsp:useBean id="title" class="java.lang.String" scope="request"/>
<jsp:useBean id="label" class="java.lang.String" scope="request"/>
<jsp:useBean id="label1" class="java.lang.String" scope="request"/>
<jsp:useBean id="link" class="java.lang.String" scope="request"/>
<style>
    .statistic-card-icon${widgetContext.uniqueId} {
        color: <c:out value="${iconColor}" />;
    }
    .statistic-card-value {
        font-size: 40px;
        font-weight: bold;
        line-height: 1;
    }
</style>
<div class="grid-x align-middle text-middle">
  <c:if test="${!empty icon}">
    <div class="small-5 cell">
      <i id="icon${widgetContext.uniqueId}" class="fa ${icon} statistic-card-icon${widgetContext.uniqueId}"></i>
    </div>
  </c:if>
  <div class="auto cell">
    <p class="statistic-card-value no-gap"><fmt:formatNumber value="${numberValue}" /></p>
    <p class="statistic-card-label no-gap">
      <c:choose>
        <c:when test="${!empty label1 && numberValue eq '1'}">
          <c:out value="${label1}" />
        </c:when>
        <c:when test="${!empty label}">
          <c:out value="${label}" />
        </c:when>
      </c:choose>
      <c:if test="${!empty label && !empty label1 && !empty title}">
        <br />
      </c:if>
      <c:if test="${!empty title}">
        <c:out value="${title}"/>
      </c:if>
    </p>
  </div>
  <c:if test="${!empty link}">
    <div class="small-1 cell">
      <a href="<c:out value="${link}" />"><i class="fa fa-2x fa-chevron-right"></i></a>
    </div>
  </c:if>
</div>
<script>
  function updateFontSize${widgetContext.uniqueId}() {
    let value = Math.round($('#icon${widgetContext.uniqueId}').closest('.cell').outerWidth()*.6);
    $("#icon${widgetContext.uniqueId}").css({'font-size': value + 'px'});
  }

  $(document).ready(function() {
    updateFontSize${widgetContext.uniqueId}();
    <%--$('#icon${widgetContext.uniqueId}').fadeIn(200);--%>
  });

  $(window).resize(function() {
    updateFontSize${widgetContext.uniqueId}();
  });
</script>