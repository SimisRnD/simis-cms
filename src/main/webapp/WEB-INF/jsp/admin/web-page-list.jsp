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
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="menuTabList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="webPageList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="webPageMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="standardPages" class="java.util.HashMap" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
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
      <th>Scheduled/Expires</th>
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
            <c:if test="${webPageMap[menuTab.link].scheduled}">
              <br /><span class="secondary label"><i class="fa fa-clock"></i> scheduled</span>
            </c:if>
            <c:if test="${webPageMap[menuTab.link].expiringSoon}">
              <br /><span class="secondary label"><i class="fa fa-hourglass-end"></i> expiring</span>
            </c:if>
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
          <td>
            <c:if test="${!empty webPageMap[menuTab.link].publishAt}">
              <small>Publishes: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuTab.link].publishAt}" /></small>
            </c:if>
            <c:if test="${!empty webPageMap[menuTab.link].publishAt && !empty webPageMap[menuTab.link].expiresAt}"><br /></c:if>
            <c:if test="${!empty webPageMap[menuTab.link].expiresAt}">
              <small>Expires: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuTab.link].expiresAt}" /></small>
            </c:if>
          </td>
        </c:when>
        <c:otherwise>
          <td></td>
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
            <c:if test="${webPageMap[menuItem.link].scheduled}">
              <br /><span class="secondary label"><i class="fa fa-clock"></i> scheduled</span>
            </c:if>
            <c:if test="${webPageMap[menuItem.link].expiringSoon}">
              <br /><span class="secondary label"><i class="fa fa-hourglass-end"></i> expiring</span>
            </c:if>
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
            <td>
              <c:if test="${!empty webPageMap[menuItem.link].publishAt}">
                <small>Publishes: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuItem.link].publishAt}" /></small>
              </c:if>
              <c:if test="${!empty webPageMap[menuItem.link].publishAt && !empty webPageMap[menuItem.link].expiresAt}"><br /></c:if>
              <c:if test="${!empty webPageMap[menuItem.link].expiresAt}">
                <small>Expires: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuItem.link].expiresAt}" /></small>
              </c:if>
            </td>
          </c:when>
          <c:otherwise>
            <td></td>
            <td></td>
            <td></td>
          </c:otherwise>
        </c:choose>
      </tr>
    </c:forEach>
  </c:forEach>
  <tr>
    <td colspan="7">
      <strong>All Web Pages</strong>
    </td>
  </tr>
  <c:forEach items="${webPageList}" var="webPage">
    <tr>
      <td nowrap="true">
        <%--<a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPageId=${group.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(webPage.link)}" />?');"><i class="fa fa-remove"></i></a>--%>
        <a href="${ctx}/admin/web-page?webPageId=${webPage.id}&returnPage=/admin/web-pages"><i class="fa fa-edit"></i></a>
        <c:if test="${userSession.hasRole('admin')}">
          <a href="${ctx}/admin/web-page-designer?webPage=${webPage.link}&returnPage=/admin/web-pages"><i class="fa fa-code"></i></a>
        </c:if>
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
        <c:if test="${webPage.scheduled}">
          <br /><span class="secondary label"><i class="fa fa-clock"></i> scheduled</span>
        </c:if>
        <c:if test="${webPage.expiringSoon}">
          <br /><span class="secondary label"><i class="fa fa-hourglass-end"></i> expiring</span>
        </c:if>
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
      <td>
        <c:if test="${!empty webPage.publishAt}">
          <small>Publishes: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPage.publishAt}" /></small>
        </c:if>
        <c:if test="${!empty webPage.publishAt && !empty webPage.expiresAt}"><br /></c:if>
        <c:if test="${!empty webPage.expiresAt}">
          <small>Expires: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPage.expiresAt}" /></small>
        </c:if>
      </td>
    </tr>
  </c:forEach>
  <c:if test="${empty webPageList}">
      <tr>
        <td colspan="7">No web pages were found</td>
      </tr>
  </c:if>
  </tbody>
</table>
<a class="button radius primary" href="${ctx}/admin/web-page?returnPage=/admin/web-pages">Add a Web Page <i class="fa fa-arrow-circle-right"></i></a>