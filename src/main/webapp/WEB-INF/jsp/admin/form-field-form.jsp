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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="field" class="com.simisinc.platform.domain.model.cms.FormField" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${field.id}"/>
  <input type="hidden" name="formDefinitionId" value="${field.formDefinitionId}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Label <span class="required">*</span>
    <input type="text" placeholder="What the visitor sees, e.g. Full Name" name="label" value="<c:out value="${field.label}"/>" required>
  </label>
  <label>Name
    <input type="text" placeholder="Auto-generated from the label if left blank" name="name" value="<c:out value="${field.name}"/>">
  </label>
  <label>Type
    <select name="type">
      <option value="text" <c:if test="${empty field.type || field.type eq 'text'}">selected</c:if>>Text</option>
      <option value="email" <c:if test="${field.type eq 'email'}">selected</c:if>>Email</option>
      <option value="textarea" <c:if test="${field.type eq 'textarea'}">selected</c:if>>Textarea</option>
      <option value="select" <c:if test="${field.type eq 'select'}">selected</c:if>>Select (dropdown)</option>
      <option value="checkbox" <c:if test="${field.type eq 'checkbox'}">selected</c:if>>Checkbox</option>
      <option value="date" <c:if test="${field.type eq 'date'}">selected</c:if>>Date</option>
    </select>
  </label>
  <label>Placeholder
    <input type="text" placeholder="Optional hint text shown inside the field..." name="placeholder" value="<c:out value="${field.placeholder}"/>">
  </label>
  <label>Default Value
    <input type="text" name="defaultValue" value="<c:out value="${field.defaultValue}"/>">
  </label>
  <label>Options
    <input type="text" placeholder="Select/Checkbox only, e.g. red=Red,blue=Blue,green=Green" name="options" value="<c:out value="${optionsText}"/>">
  </label>
  <input id="fieldRequired" type="checkbox" name="required" value="true" <c:if test="${field.required}">checked</c:if>/><label for="fieldRequired">Required?</label>
  <div class="button-container">
    <c:choose>
      <c:when test="${field.id eq -1}">
        <input type="submit" class="button radius success expanded" value="Save"/>
      </c:when>
      <c:otherwise>
        <input type="submit" class="button radius success" value="Save"/>
        <a href="${ctx}/admin/forms-editor?formDefinitionId=${field.formDefinitionId}" class="button radius secondary">Cancel</a>
      </c:otherwise>
    </c:choose>
  </div>
</form>
