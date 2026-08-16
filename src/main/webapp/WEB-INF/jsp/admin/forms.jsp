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
<div class="callout primary radius">
  <p style="margin-bottom:0">
    Each row is a form definition -- its settings, success/notification text, and fields, built here
    and placed on a live page separately as a "Form" widget. Click a name (or the pencil) to open its
    full editor for Fields and Form Settings; use the "Add a form" panel to create a new one.
  </p>
</div>
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

<h5>Putting a form on a live page</h5>
<p>
  Building or editing a form here doesn't publish it anywhere by itself. A "Form" widget still needs
  to be placed on a page (through page/content editing) and pointed at this form via its numeric
  database id, not the name or the unique id shown under it. That numeric id isn't displayed directly
  in this list -- the easiest way to find it is to click a form's edit pencil and read the
  <code>formDefinitionId</code> value out of the resulting URL. The unique id shown here (under the
  name) is a different, internal value -- it's what submitted data is matched against, and it's what
  regenerates if you rename the form -- but it isn't what a page uses to display the form.
</p>

<h5>Deleting a form</h5>
<p>
  Deleting a form is permanent and also deletes all of its fields -- the confirmation prompt says so.
  It does <strong>not</strong> touch any data already submitted through it: submissions are matched to
  a form by that internal unique id, not a database link back to this row, so prior submissions stay
  exactly as they were in <a href="${ctx}/admin/form-data">Form Data</a>. One consequence worth
  knowing: deleting a form frees its unique id for reuse, so a brand-new form later given the same
  name could end up sharing that internal id with the deleted form's old, now-orphaned submissions.
</p>

<h5>Common problems and how to fix them</h5>
<ul>
  <li><strong>"Form could not be deleted."</strong> Unlike some other admin pages, nothing here is
    designed to block a delete (no existing-submissions check, no "still referenced elsewhere"
    guard) -- if you see this, it's most likely a transient database-level failure. Try again, and
    check the application server logs if it keeps happening.</li>
</ul>
