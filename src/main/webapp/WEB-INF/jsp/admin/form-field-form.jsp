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
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <p class="help-text page-help">
    Only these six field types exist -- there's no radio-button type (use <strong>Select</strong>
    for a single-choice dropdown; <strong>Checkbox</strong> with Options is a multi-select group,
    not a single-choice substitute -- see the note below), no file-upload type, and no CAPTCHA field
    (CAPTCHA is a whole-form setting on the previous "Form Settings" screen, not something you add
    per field).
  </p>
  <%-- Form Content --%>
  <label>Label <span class="required">*</span>
    <input type="text" placeholder="What the visitor sees, e.g. Full Name" name="label" value="<c:out value="${field.label}"/>" required>
  </label>
  <label>Name
    <input type="text" placeholder="Auto-generated from the label if left blank" name="name" value="<c:out value="${field.name}"/>">
  </label>
  <p class="help-text" style="margin-top:-8px">
    An internal identifier, not shown to visitors. Left blank, it's slugified from the Label.
    <strong>Known issue, fix in progress:</strong> this doesn't currently check for a collision
    against this form's other fields, so two fields can end up sharing a Name (including two
    blank-Name fields that slugify the same way, e.g. both labeled "Email"). That's not just a
    display quirk -- the live form reads each field's submitted answer back by this exact string, so
    a shared Name silently makes one field's answer overwrite the other's, and can let a blank
    second required field pass validation by inheriting the first field's value. Until this is fixed,
    give every field on a form a distinct Name yourself.
  </p>
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
  <p class="help-text" style="margin-top:-8px">
    <strong>Filling this in overrides how the field renders, for every Type, not just Select and
    Checkbox.</strong> The live form checks "does this field have Options" before it checks Type --
    so a Text, Email, Textarea, or Date field with Options stops rendering as its normal input and
    becomes a dropdown instead. This is especially easy to get wrong on an <strong>Email</strong>
    field: Options turns it into a dropdown of your option keys, but the field's email-format check
    still runs against whatever was submitted -- so unless your option keys happen to look like email
    addresses, every submission through that field will fail validation. Leave Options blank unless
    you specifically want a dropdown. A <strong>Checkbox</strong> field with Options becomes a
    multi-select group (the visitor can check several) -- not a single-choice substitute for a radio
    button; use <strong>Select</strong> for that. A Checkbox field left without Options is a single
    plain checkbox instead.
  </p>
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
<h5>Common problems and how to fix them</h5>
<ul>
  <li><strong>A visitor says they can never successfully submit the form, always getting "&lt;label&gt;
    is required" for a field that's clearly filled in.</strong> If the page the form actually lives on
    was built from a template that never renders this field's input at all, the submission looks
    identical to that field being left blank -- there's no way to tell the two apart from here, and
    no way for that visitor to ever satisfy a field their form can't physically show. Check that every
    field marked Required here is actually present on the live page.</li>
  <li><strong>Where do I see actual submissions, or find out why some are being rejected?</strong>
    Real, successful submissions are reviewed on <a href="${ctx}/admin/form-data">Form Data</a>
    (filterable by form, status, and date, with CSV export). For rejections -- missing required
    fields, invalid email format, failed CAPTCHA, rate limiting -- there's currently no page listing
    individual failed attempts, only an aggregate count by reason on the Site Analytics dashboard.
    That's enough to notice a spike, but not to see which specific submission failed or why.</li>
</ul>
