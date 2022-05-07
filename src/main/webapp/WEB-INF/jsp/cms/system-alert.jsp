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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="sitePropertyMap" class="java.util.HashMap" scope="request"/>
<div class="utility-text">
  <c:if test="${!empty sitePropertyMap['site.header.line1']}">
    <c:out value="${sitePropertyMap['site.header.line1']}" />
    <c:if test="${!empty sitePropertyMap['site.header.page']}">
      <c:choose>
        <c:when test="${!empty sitePropertyMap['site.header.link']}">
          <a style="white-space: nowrap" href="${ctx}${sitePropertyMap['site.header.page']}"><c:out value="${sitePropertyMap['site.header.link']}" /></a>
        </c:when>
        <c:otherwise>
          <a href="${ctx}${sitePropertyMap['site.header.page']}">Details</a>
        </c:otherwise>
      </c:choose>
    </c:if>
  </c:if>
  <c:if test="${userSession.hasRole('admin')}">
    <a class="hollow button tiny secondary" style="padding:2px; margin:0;" href="${ctx}/admin/site-header-properties"><i class="${font:fas()} fa-edit"></i></a>
  </c:if>
</div>
