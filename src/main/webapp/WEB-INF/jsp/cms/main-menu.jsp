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
<jsp:useBean id="masterMenuTabList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="webPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<jsp:useBean id="pagePath" class="java.lang.String" scope="request"/>
<jsp:useBean id="useHighlight" class="java.lang.String" scope="request"/>
<ul class="dropdown menu<c:if test="${!empty menuClass}"> <c:out value="${menuClass}" /></c:if>" data-dropdown-menu>
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
        <%-- Display the submenu --%>
        <li class="is-dropdown-submenu-parent<c:if test="${isParent eq 'true'}"> active</c:if>">
          <a href="${ctx}${menuTab.link}"><c:if test="${!empty menuTab.icon}"><i class="${font:fas()} fa-fw fa-<c:out value="${menuTab.icon}" />"></i> </c:if><c:out value="${menuTab.name}" /></a>
          <ul class="menu vertical">
            <c:forEach items="${menuTab.menuItemList}" var="menuItem">
              <li<c:if test="${useHighlight eq 'true' && menuItem.link eq pagePath}"> class="active"</c:if>><a href="${ctx}${menuItem.link}"><c:if test="${!empty submenuIcon}"><i class="fa <c:out value="${submenuIcon}" /><c:if test="${!empty submenuIconClass}"> <c:out value="${submenuIconClass}" /></c:if>"></i></c:if> <c:out value="${menuItem.name}" /></a></li>
            </c:forEach>


                <%-- @todo Check for content to append here --%>
<%--
                <li class="menu-item-callout" style="padding:0 16px 16px 16px">
                  <div style="border-top: 2px solid #E0A590;padding-top: 8px;font-weight:400">
                    NEW! Hemp Seed Oil Products<br />
                    <a target="_blank" class="button primary" style="margin: 10px 0 0 0;padding: 8px;" href="https://example.com">Visit Example.com</a>
                  </div>
                </li>
--%>


          </ul>
        </li>
      </c:when>
      <c:otherwise>
        <li class="is-standalone<c:if test="${useHighlight eq 'true' && (menuTab.link eq pagePath || (menuTab.link ne '/' && fn:startsWith(pagePath, menuTab.link)))}"> active</c:if>"><a href="${ctx}${menuTab.link}"><c:if test="${!empty menuTab.icon}"><i class="${font:fas()} fa-fw fa-<c:out value="${menuTab.icon}" />"></i> </c:if><c:out value="${menuTab.name}" /></a></li>
      </c:otherwise>
    </c:choose>
  </c:forEach>
</ul>
