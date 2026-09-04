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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="formDefinition" class="com.simisinc.platform.domain.model.cms.FormDefinition" scope="request"/>
<jsp:useBean id="fieldList" class="java.util.ArrayList" scope="request"/>
<link rel="stylesheet" href="${ctx}/javascript/dragula-3.7.3/dragula.min.css"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<p class="help-text page-help">
  Drag the <i class="fa fa-arrows"></i> handle to reorder fields, click <i class="${font:fas()} fa-edit"></i> to edit
  a field, or <i class="fa fa-circle-xmark"></i> to remove it. Reordering is staged in the browser
  only, as you drag -- nothing is saved until you click <strong>Save Field Order</strong> below;
  navigating away first discards it. There's no limit on how many fields a form can have.
</p>
<p class="help-text page-help">
  Deleting a field is permanent and immediate (no separate save step) -- but it only affects the
  field definition itself, not anything already submitted through it. Every past submission stores
  its own independent snapshot of each field's label, name, type, and answer at the moment it was
  submitted, so removing (or renaming) a field afterward doesn't touch or reformat data already
  sitting in <a href="${ctx}/admin/form-data">Form Data</a> -- old submissions keep showing the old
  field as it was, new ones reflect whatever the form looks like now.
</p>
<%@include file="../page_messages.jspf" %>
<c:if test="${empty fieldList}">
  <p class="subheader">No fields were found, add one!</p>
</c:if>
<form method="post" id="fieldOrderForm">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="formDefinitionId" value="${formDefinition.id}"/>
  <input type="hidden" id="fieldOrder" name="fieldOrder" value=""/>
  <div id="form-fields-container" class="form-fields-container">
    <c:forEach items="${fieldList}" var="field">
      <div id="form-field-row-${field.id}" class="form-field-row">
        <div style="position: absolute; right: 5px; top: 5px;">
          <small>
            <a href="${ctx}/admin/forms-editor?formDefinitionId=${formDefinition.id}&fieldId=${field.id}" title="Edit this field"><i class="${font:fas()} fa-edit"></i></a>
            <a href="#" data-js-call="deleteField" data-js-arg1="${field.id}" data-js-arg2="<c:out value="${field.label}"/>" title="Delete this field"><i class="fa fa-circle-xmark"></i></a>
          </small>
        </div>
        <div>
          <i class="fa fa-arrows form-field-drag-handle" title="Drag to reorder"></i>
          <%-- The name links to the same editor as the pencil. Those icons are absolutely
               positioned at the far right of the row, so on a wide screen they sit a long way from
               the field they act on and read as decoration -- the field's own name is where someone
               looking to change it clicks first. --%>
          <a href="${ctx}/admin/forms-editor?formDefinitionId=${formDefinition.id}&fieldId=${field.id}"
             title="Edit this field"><strong><c:out value="${field.label}"/></strong></a><c:if test="${field.required}"> <span class="required">*</span></c:if>
        </div>
        <div>
          <small class="subheader">
            <c:out value="${field.name}"/> &middot; <c:out value="${field.type}"/>
            <c:if test="${!empty field.placeholder}"> &middot; placeholder: "<c:out value="${field.placeholder}"/>"</c:if>
            <%-- The other editable property the row never mentioned. It is not cosmetic: form.jsp
                 uses it as a field's initial value, and preselects a dropdown option with it, so a
                 default silently decides what a visitor sees before they touch anything. --%>
            <c:if test="${!empty field.defaultValue}"> &middot; default: "<c:out value="${field.defaultValue}"/>"</c:if>
            <%-- A select's choices are the thing an editor most often comes here to change, and the
                 row said nothing about them -- so the list read as fixed and the only way to find
                 out otherwise was to open a field on the off-chance. Listed rather than counted:
                 seeing the current choices is what tells you whether this is the field you want. --%>
            <c:if test="${!empty field.listOfOptions}"> &middot; options:
              <c:forEach items="${field.listOfOptions}" var="option" varStatus="optionStatus"><c:out
                  value="${option.value}"/><c:if test="${!optionStatus.last}">, </c:if></c:forEach></c:if>
          </small>
        </div>
      </div>
    </c:forEach>
  </div>
  <c:if test="${!empty fieldList}">
    <div class="button-container">
      <input type="submit" class="button radius success" value="Save Field Order"/>
    </div>
  </c:if>
</form>
<script src="${ctx}/javascript/dragula-3.7.3/dragula.min.js"></script>
<script nonce="${cspNonce}">
  dragula([document.getElementById('form-fields-container')], {
    moves: function (el, container, handle) {
      return handle.classList.contains('form-field-drag-handle');
    }
  });

  function deleteField(fieldId, label) {
    if (!confirm("Delete the field \"" + label + "\"? This cannot be undone.")) {
      return;
    }
    postAction('${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&fieldId=' + fieldId);
  }

  function checkFieldOrder() {
    var container = document.getElementById("form-fields-container");
    var rows = container.querySelectorAll(".form-field-row");
    var order = "";
    for (var i = 0; i < rows.length; i++) {
      if (i > 0) {
        order += ",";
      }
      order += rows[i].id.substring(rows[i].id.lastIndexOf("-") + 1);
    }
    document.getElementById("fieldOrder").value = order;
    return true;
  }

  // issue #1188: this ran from an inline onsubmit attribute, which CSP blocks -- inline handler
  // attributes fall under script-src-attr and the nonce authorises this block, not an attribute
  // calling into it. Because a blocked handler is skipped rather than treated as a cancel, the form
  // still posted, but with the hidden fieldOrder input left empty. FormFieldsListWidget.post()
  // guards the reorder with StringUtils.isNotBlank(fieldOrder) and then redirects either way, so
  // "Save Field Order" silently discarded the drag and reported nothing.
  document.addEventListener('DOMContentLoaded', function () {
    var form = document.getElementById('fieldOrderForm');
    if (form) {
      form.addEventListener('submit', function (event) {
        if (!checkFieldOrder()) {
          event.preventDefault();
        }
      });
    }
  });
</script>
