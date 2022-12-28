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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th>
        Categories
      </th>
      <th width="120" class="text-center"># of items</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${categoryList}" var="category">
    <tr>
      <td>
        <c:choose>
          <c:when test="${!empty category.headerBgColor && !empty category.headerTextColor}">
            <c:choose>
              <c:when test="${!empty category.icon}">
                <span class="padding-10 padding-width-10 margin-right-10" style="background-color:<c:out value="${category.headerBgColor}" />;color:<c:out value="${category.headerTextColor}" />">
                  <i class="${font:far()} fa-fw fa-<c:out value="${category.icon}" />"></i>
                </span>
              </c:when>
              <c:otherwise>
              <span class="padding-10 padding-width-10 margin-right-10" style="background-color:<c:out value="${category.headerBgColor}" />;color:<c:out value="${category.headerTextColor}" />">
                <i class="${font:far()} fa-fw"></i>
              </span>
              </c:otherwise>
            </c:choose>
          </c:when>
          <c:otherwise>
            <span class="padding-10 padding-width-10 margin-right-10">
              <i class="${font:far()} fa-fw"></i>
            </span>
          </c:otherwise>
        </c:choose>
        <a href="${ctx}/admin/category?collectionId=${collection.id}&categoryId=${category.id}"><c:out value="${category.name}" /></a>
        <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&categoryId=${category.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(category.name)}" />?');"><i class="fa fa-remove"></i></a>
        <c:if test="${!empty category.description}">
          <br /><small><c:out value="${category.description}" /></small>
        </c:if>
      </td>
      <td class="text-center"><fmt:formatNumber value="${category.itemCount}" /></td>
    </tr>
    </c:forEach>
    <c:if test="${empty categoryList}">
      <tr>
        <td colspan="2" class="subheader">No categories were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
