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
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="statisticCard" class="com.simisinc.platform.domain.model.dashboard.StatisticCard" scope="request"/>
<jsp:useBean id="iconColor" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
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
<div class="grid-y align-middle text-middle">
  <c:if test="${!empty statisticCard.icon}">
    <div class="small-5 cell padding-width-30 text-center">
      <i id="icon${widgetContext.uniqueId}" class="fa fa-<c:out value="${statisticCard.icon}" /> statistic-card-icon${widgetContext.uniqueId}"></i>
    </div>
  </c:if>
  <div class="small-4 cell padding-top-20">
    <p class="statistic-card-value no-gap"><fmt:formatNumber value="${statisticCard.value}" /></p>
    <p class="statistic-card-label no-gap"><c:out value="${statisticCard.label}" /></p>
  </div>
<%--  <c:if test="${!empty statisticCard.link}">--%>
<%--    <div class="small-1 cell">--%>
<%--      <a href="<c:out value="${statisticCard.link}" />"><i class="fa fa-2x fa-chevron-right"></i></a>--%>
<%--    </div>--%>
<%--  </c:if>--%>
</div>
<script>
  function updateFontSize${widgetContext.uniqueId}() {
    let value = Math.round($('#icon${widgetContext.uniqueId}').closest('.cell').outerWidth()*.50);
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