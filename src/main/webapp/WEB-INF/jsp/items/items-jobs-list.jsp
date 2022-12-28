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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="category" uri="/WEB-INF/tlds/category-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="itemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="category" class="com.simisinc.platform.domain.model.items.Category" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="${font:fas()} ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:choose>
  <c:when test="${category.id gt 0}"><c:out value="${category.name}" /></c:when>
  <%--<c:otherwise>All <c:out value="${collection.name}" /></c:otherwise>--%>
</c:choose>
<c:choose>
  <c:when test="${!empty itemList}">
    <table class="stack">
      <thead>
      <tr>
        <th>Job Title</th>
        <th>Organization</th>
        <th>Job Location</th>
<%--        <th>Apply/More Information</th>--%>
        <th>Posted</th>
      </tr>
      </thead>
      <tbody>
      <c:forEach items="${itemList}" var="item">
        <tr>
          <td>
            <c:if test="${userSession.hasRole('admin') || userSession.hasRole('data-manager')}">
              <a href="/edit/<c:out value="${item.uniqueId}" />?returnPage=${returnPage}"><i class="${font:fal()} fa-edit"></i></a>
            </c:if>
            <strong><c:out value="${text:trim(item.name, 50, true)}"/></strong><c:if test="${empty item.approved}"> <span class="label warning">Needs approval</span></c:if>
            <br /><a href="${ctx}/show/${item.uniqueId}">View Job Description</a>
          </td>
          <td>
            <c:if test="${!empty item.location}">
              <c:out value="${item.location}" />
            </c:if>
          </td>
          <td nowrap>
            <c:if test="${!empty item.city}"><c:out value="${text:trim(item.city, 30, true)}"/><c:if test="${!empty item.state}">, </c:if></c:if>
            <c:if test="${!empty item.state}"><c:out value="${item.state}" /></c:if>
          </td>
<%--
          <td>
            <c:if test="${!empty item.url}">
              <c:choose>
                <c:when test="${fn:startsWith(item.url, 'http://') || fn:startsWith(item.url, 'https://')}">
                  <a href="${url:encode(item.url)}" target="_blank" rel="nofollow"><c:out value="${text:trim(item.url, 30, true)}"/></a>
                </c:when>
                <c:otherwise>
                  <c:out value="${text:trim(item.url, 30, true)}"/>
                </c:otherwise>
              </c:choose>
              <c:if test="${!empty item.phoneNumber || !empty item.email}"><br /></c:if>
            </c:if>
            <c:if test="${!empty item.phoneNumber}">
              <c:out value="${item.phoneNumber}" />
              <c:if test="${!empty item.email}"><br /></c:if>
            </c:if>
            <c:if test="${!empty item.email}">
              <c:out value="${item.email}" />
            </c:if>
          </td>
--%>
          <td nowrap>
            <small class="subheader"><c:out value="${date:relative(item.created)}" /></small>
            <c:if test="${userSession.hasRole('admin') || userSession.hasRole('data-manager')}">
              <br /><small>by <c:out value="${user:name(item.createdBy)}"/></small>
            </c:if>
          </td>
        </tr>
      </c:forEach>
      </tbody>
    </table>
    <%-- Paging Control --%>
    <c:if test="${category.id gt 0}">
      <c:set var="recordPagingParams" scope="request" value="categoryId=${category.id}"/>
    </c:if>
    <%@include file="../paging_control.jspf" %>
  </c:when>
  <c:otherwise>
    <p class="subheader">
      No <c:out value="${fn:toLowerCase(collection.name)}"/> were found
    </p>
  </c:otherwise>
</c:choose>
