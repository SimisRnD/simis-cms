<%--
  ~ Copyright 2026 SimIS Inc.
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
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="tagList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="help-text">Tags are used to label and filter items in this collection.</p>
<table class="unstriped">
  <thead>
    <tr>
      <th>
        Tags
      </th>
      <th width="120" class="text-center"># of items</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${tagList}" var="tag">
    <tr>
      <td>
        <a href="${ctx}/admin/tag?collectionId=${collection.id}&tagId=${tag.id}"><c:out value="${tag.name}" /></a>
        <a href="#" onclick="return confirmPostAction('Are you sure you want to delete <c:out value="${js:escape(tag.name)}" />?', '${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&tagId=${tag.id}');"><i class="fa fa-remove"></i></a>
      </td>
      <td class="text-center"><fmt:formatNumber value="${tag.itemCount}" /></td>
    </tr>
    </c:forEach>
    <c:if test="${empty tagList}">
      <tr>
        <td colspan="2" class="subheader">No tags were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
