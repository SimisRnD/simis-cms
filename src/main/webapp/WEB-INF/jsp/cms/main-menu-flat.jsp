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
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="masterMenuTabList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="webPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<jsp:useBean id="pagePath" class="java.lang.String" scope="request"/>
<jsp:useBean id="useHighlight" class="java.lang.String" scope="request"/>
<ul class="menu<c:if test="${!empty menuClass}"> <c:out value="${menuClass}" /></c:if>">
  <c:set var="isFirst" value="true" scope="request" />
  <c:forEach items="${masterMenuTabList}" var="menuTab" varStatus="menuTabStatus">
    <c:choose>
      <c:when test="${menuTab.link eq '/' && menuTabStatus.index == 0}">
        <%-- Hide Home (the first one) --%>
      </c:when>
      <c:when test="${!empty menuTab.menuItemList}">
        <%-- See if any submenu item is the current page for highlighting the active menu item --%>
        <c:set var="isParent" scope="request" value="false"/>
        <c:if test="${useHighlight eq 'true'}">
          <c:if test="${menuTab.link eq pagePath}">
            <c:set var="isParent" scope="request" value="true"/>
          </c:if>
          <c:forEach items="${menuTab.menuItemList}" var="menuItem">
            <c:if test="${menuItem.link eq pagePath}">
              <c:set var="isParent" scope="request" value="true"/>
            </c:if>
          </c:forEach>
        </c:if>
        <%-- Display the menu --%>
        <li<c:if test="${isParent eq 'true'}"> class="active"</c:if>>
          <c:if test="${isFirst eq 'false'}">
            <hr />
          </c:if>
          <c:set var="isFirst" value="false" scope="request" />
          <a href="${ctx}${menuTab.link}"><c:out value="${menuTab.name}" /></a>
          <ul class="menu vertical">
            <c:forEach items="${menuTab.menuItemList}" var="menuItem">
              <li<c:if test="${useHighlight eq 'true' && menuItem.link eq pagePath}"> class="active"</c:if>><a href="${ctx}${menuItem.link}"><c:if test="${!empty submenuIcon}"><i class="fa <c:out value="${submenuIcon}" /><c:if test="${!empty submenuIconClass}"> <c:out value="${submenuIconClass}" /></c:if>"></i></c:if> <c:out value="${menuItem.name}" /></a></li>
            </c:forEach>
          </ul>
        </li>
      </c:when>
      <c:otherwise>
        <c:set var="isFirst" value="false" scope="request" />
        <li<c:if test="${useHighlight eq 'true' && fn:startsWith(pagePath, menuTab.link)}"> class="active"</c:if>><a href="${ctx}${menuTab.link}"><c:out value="${menuTab.name}" /></a></li>
      </c:otherwise>
    </c:choose>
  </c:forEach>
</ul>