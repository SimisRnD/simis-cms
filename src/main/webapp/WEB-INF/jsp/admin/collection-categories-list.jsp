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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="help-text page-help">Categories are a structured alternative to a free-text custom field -- a fixed list of choices (e.g. departments, regions) that items in this collection can be sorted into, rather than a value someone types differently every time. Use them when you want reliable grouping, filtering, or reporting by that value.</p>
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
        <a href="#" data-confirm-post="Are you sure you want to delete <c:out value="${category.name}" />?" data-post-url="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&categoryId=${category.id}"><i class="fa fa-remove"></i></a>
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
