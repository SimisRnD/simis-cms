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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="menuTabList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="webPageList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="webPageMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="standardPages" class="java.util.HashMap" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th width="45"></th>
      <th width="60"></th>
      <th>Title</th>
      <th>Link</th>
      <th>Keywords, Description</th>
      <th>Modified</th>
    </tr>
  </thead>
  <tbody>
  <c:forEach items="${menuTabList}" var="menuTab">
    <tr>
      <c:choose>
        <c:when test="${menuTab.link eq '/'}">
          <td></td>
          <td><span class="success label">live</span></td>
        </c:when>
        <c:when test="${fn:contains(standardPages, menuTab.link)}">
          <td>
            <%--<a href="${ctx}${menuTab.link}"><i class="fa fa-check-circle"></i></a>--%>
            <a href="${ctx}/admin/web-page?webPage=${menuTab.link}&returnPage=/admin/web-pages"><i class="fa fa-edit"></i></a>
          </td>
          <td><span class="success label">live</span></td>
        </c:when>
        <c:when test="${fn:contains(webPageMap, menuTab.link)}">
          <td>
              <%--<a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPageId=${group.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(webPage.link)}" />?');"><i class="fa fa-remove"></i></a>--%>
            <a href="${ctx}/admin/web-page?webPageId=${webPageMap[menuTab.link].id}&returnPage=/admin/web-pages"><i class="fa fa-edit"></i></a>
          </td>
          <td>
            <c:choose>
              <c:when test="${webPageMap[menuTab.link].draft}"><span class="warning label">draft</span></c:when>
              <c:when test="${!empty webPageMap[menuTab.link].redirectUrl}"><span class="primary label">301</span></c:when>
              <c:when test="${empty webPageMap[menuTab.link].pageXml}">
                <span class="alert label">404</span>
              </c:when>
              <c:otherwise><span class="success label">live</span></c:otherwise>
            </c:choose>
          </td>
        </c:when>
        <c:otherwise>
          <td>
            <a href="${ctx}${menuTab.link}"><i class="fa fa-plus"></i></a>
          </td>
          <td>
            <span class="alert label">404</span>
          </td>
        </c:otherwise>
      </c:choose>
      <td>
        <c:out value="${menuTab.name}" />
      </td>
      <td><a href="${ctx}${menuTab.link}"><c:out value="${menuTab.link}" /></a></td>
      <c:choose>
        <c:when test="${fn:contains(webPageMap, menuTab.link)}">
          <td>
            <c:if test="${!empty webPageMap[menuTab.link].keywords}">
              <small class="subheader">{<c:out value="${webPageMap[menuTab.link].keywords}" />}</small>
            </c:if>
            <c:if test="${!empty webPageMap[menuTab.link].keywords && !empty webPageMap[menuTab.link].description}">
              <br />
            </c:if>
            <c:if test="${!empty webPageMap[menuTab.link].description}">
              <small class="subheader"><c:out value="${webPageMap[menuTab.link].description}" /></small>
            </c:if>
          </td>
          <td>
            <small><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuTab.link].modified}" /></small>
          </td>
        </c:when>
        <c:otherwise>
          <td></td>
          <td></td>
        </c:otherwise>
      </c:choose>
    </tr>
    <c:forEach items="${menuTab.menuItemList}" var="menuItem">
      <tr>
        <c:choose>
          <c:when test="${fn:contains(standardPages, menuItem.link)}">
            <td>
              <a href="${ctx}/admin/web-page?webPage=${menuItem.link}&returnPage=/admin/web-pages"><i class="fa fa-edit"></i></a>
            </td>
            <td><span class="success label">live</span></td>
          </c:when>
          <c:when test="${fn:contains(webPageMap, menuItem.link)}">
            <td>
              <%--<a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPageId=${group.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(webPage.link)}" />?');"><i class="fa fa-remove"></i></a>--%>
              <a href="${ctx}/admin/web-page?webPageId=${webPageMap[menuItem.link].id}&returnPage=/admin/web-pages"><i class="fa fa-edit"></i></a>
            </td>
            <td>
            <c:choose>
              <c:when test="${webPageMap[menuItem.link].draft}"><span class="warning label">draft</span></c:when>
              <c:when test="${!empty webPageMap[menuItem.link].redirectUrl}"><span class="primary label">301</span></c:when>
              <c:when test="${fn:startsWith(menuItem.link, '/directory/')}"><span class="success label">live</span></c:when>
              <c:when test="${empty webPageMap[menuItem.link].pageXml}"><span class="alert label">404</span></c:when>
              <c:otherwise><span class="success label">live</span></c:otherwise>
            </c:choose>
            </td>
          </c:when>
          <c:when test='${fn:contains(menuItem.link, "#")}'>
            <td></td>
            <td></td>
          </c:when>
          <c:otherwise>
            <td>
              <a href="${ctx}${menuItem.link}"><i class="fa fa-plus"></i></a>
            </td>
            <td>
              <c:choose>
                <c:when test="${fn:startsWith(menuItem.link, '/directory/')}"><span class="success label">live</span></c:when>
                <c:otherwise><span class="alert label">404</span></c:otherwise>
              </c:choose>
            </td>
          </c:otherwise>
        </c:choose>
        <td>
          <i class="fa fa-angle-right"></i>
          <c:out value="${menuItem.name}" />
        </td>
        <td><a href="${ctx}${menuItem.link}"><c:out value="${menuItem.link}" /></a></td>
        <c:choose>
          <c:when test="${fn:contains(webPageMap, menuItem.link)}">
            <td>
              <c:if test="${!empty webPageMap[menuItem.link].keywords}">
                <small class="subheader">{<c:out value="${webPageMap[menuItem.link].keywords}" />}</small>
              </c:if>
              <c:if test="${!empty webPageMap[menuItem.link].description}">
                <br /><small class="subheader"><c:out value="${webPageMap[menuItem.link].description}" /></small>
              </c:if>
            </td>
            <td>
              <small><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuItem.link].modified}" /></small>
            </td>
          </c:when>
          <c:otherwise>
            <td></td>
            <td></td>
          </c:otherwise>
        </c:choose>
      </tr>
    </c:forEach>
  </c:forEach>
  <tr>
    <td colspan="6">
      <strong>All Web Pages</strong>
    </td>
  </tr>
  <c:forEach items="${webPageList}" var="webPage">
    <tr>
      <td>
        <%--<a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPageId=${group.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(webPage.link)}" />?');"><i class="fa fa-remove"></i></a>--%>
        <a href="${ctx}/admin/web-page?webPageId=${webPage.id}&returnPage=/admin/web-pages"><i class="fa fa-edit"></i></a>
      </td>
      <td>
        <c:choose>
          <c:when test="${webPage.draft}"><span class="warning label">draft</span></c:when>
          <c:when test="${!empty webPage.redirectUrl}"><span class="primary label">301</span></c:when>
          <c:when test="${fn:contains(standardPages, webPage.link)}">
            <span class="success label">live</span>
          </c:when>
          <c:when test="${fn:startsWith(webPage.link, '/directory/')}"><span class="success label">live</span></c:when>
          <c:when test="${empty webPage.pageXml}"><span class="alert label">404</span></c:when>
          <c:otherwise><span class="success label">live</span></c:otherwise>
        </c:choose>
      </td>
      <td>
        <c:out value="${webPage.title}" />
      </td>
      <td>
        <a href="${ctx}${webPage.link}"><c:out value="${webPage.link}" /></a>
        <c:if test="${!empty webPage.redirectUrl}">
          <i class="fa fa-long-arrow-right"></i> <c:out value="${webPage.redirectUrl}" />
        </c:if>
      </td>
      <td>
        <c:if test="${!empty webPage.keywords}">
          <small class="subheader">{<c:out value="${webPage.keywords}" />}</small>
        </c:if>
        <c:if test="${!empty webPage.description}">
          <br /><small class="subheader"><c:out value="${webPage.description}" /></small>
        </c:if>
      </td>
      <td>
        <small><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPage.modified}" /></small>
      </td>
    </tr>
  </c:forEach>
  <c:if test="${empty webPageList}">
      <tr>
        <td colspan="6">No web pages were found</td>
      </tr>
  </c:if>
  </tbody>
</table>
<a class="button radius primary" href="${ctx}/admin/web-page?returnPage=/admin/web-pages">Add a Web Page <i class="fa fa-arrow-circle-right"></i></a>