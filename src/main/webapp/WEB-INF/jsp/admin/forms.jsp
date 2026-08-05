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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="formDefinitionList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="fieldCountMap" class="java.util.HashMap" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th>Name</th>
      <th width="100" class="text-center"># of Fields</th>
      <th width="150" class="text-center">Last Modified</th>
      <th width="100" class="text-center">Enabled?</th>
      <th width="100" class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${formDefinitionList}" var="formDefinition">
    <tr>
      <td>
        <a href="${ctx}/admin/forms-editor?formDefinitionId=${formDefinition.id}"><c:out value="${formDefinition.name}" /></a>
        <br /><small class="subheader"><c:out value="${formDefinition.uniqueId}" /></small>
      </td>
      <td class="text-center"><fmt:formatNumber value="${fieldCountMap[formDefinition.id]}" /></td>
      <td class="text-center"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${formDefinition.modified}" /></td>
      <td class="text-center">
        <c:choose>
          <c:when test="${formDefinition.enabled}"><span class="label success">Yes</span></c:when>
          <c:otherwise><span class="label warning">No</span></c:otherwise>
        </c:choose>
      </td>
      <td class="text-center">
        <a href="${ctx}/admin/forms-editor?formDefinitionId=${formDefinition.id}"><i class="${font:fas()} fa-edit"></i></a>
        <a href="#" onclick="return confirmPostAction('Are you sure you want to delete <c:out value="${js:escape(formDefinition.name)}" />? This also deletes all of its fields.', '${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&formDefinitionId=${formDefinition.id}');"><i class="fa fa-remove"></i></a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty formDefinitionList}">
      <tr>
        <td colspan="5">No forms were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
