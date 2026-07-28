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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="cancelUrl" class="java.lang.String" scope="request"/>
<script src="${ctx}/javascript/tinymce-7.9.3/tinymce.min.js"></script>
<script nonce="${cspNonce}">
  tinymce.init({
    license_key: 'gpl',
    selector: '.html-field',
    branding: false,
    width: '100%',
    height: 300,
    menubar: false,
    relative_urls: false,
    convert_urls: true,
    browser_spellcheck: true,
    plugins: 'advlist autolink lists charmap preview anchor searchreplace visualblocks code wordcount',
    toolbar: 'undo redo | blocks | bold italic backcolor  | bullist numlist outdent indent | removeformat | visualblocks code'
  });
</script>
<jsp:useBean id="fieldList" class="java.util.ArrayList" scope="request"/>
<style>
  .form-field-error {
    border-left: 4px solid #cc4c28 !important;
    background-color: rgba(204, 76, 40, 0.02);
  }
  .error-message {
    display: none;
    color: #cc4c28;
    font-weight: 500;
    margin-top: 0.5rem;
    font-size: 0.9rem;
  }
  .error-message i {
    margin-right: 0.5rem;
  }
  .error-message.show {
    display: block;
  }
</style>

<script nonce="${cspNonce}">
  $(document).ready(function() {
    $('textarea').on('input', function(event) {
      var errorEl = document.getElementById("error-" + this.id);
      if (errorEl && this.value.trim() !== "") {
        errorEl.classList.remove("show");
        this.classList.remove("form-field-error");
        this.setAttribute("aria-invalid", "false");
      }
    });
    $('textarea').keypress(function(event) {
      if (event.keyCode === 13) {
        event.preventDefault();
      }
    });
    $('#form${widgetContext.uniqueId} input:not([type="submit"])').on('input', function(e) {
      var errorEl = document.getElementById("error-" + this.id);
      if (errorEl && this.value.trim() !== "") {
        errorEl.classList.remove("show");
        this.classList.remove("form-field-error");
        this.setAttribute("aria-invalid", "false");
      }
    });
    $('#form${widgetContext.uniqueId} input:not([type="submit"])').keydown(function(e) {
      if (e.keyCode === 13) {
          return false;
      }
    });
  });

  function checkForm${widgetContext.uniqueId}() {
    var hasErrors = false;
    var firstErrorField = null;
    <c:forEach items="${fieldList}" var="formField" varStatus="status">
    <c:if test="${formField.required}">
    <c:choose>
    <c:when test="${!empty formField.listOfOptions}">

    </c:when>
    <c:otherwise>
    var field = document.getElementById("${widgetContext.uniqueId}${js:escape(formField.name)}");
    var errorEl = document.getElementById("error-${widgetContext.uniqueId}${js:escape(formField.name)}");
    if (field.value.trim() === "") {
      if (errorEl) {
        errorEl.classList.add("show");
      }
      field.classList.add("form-field-error");
      field.setAttribute("aria-invalid", "true");
      hasErrors = true;
      if (!firstErrorField) firstErrorField = field;
    } else if (errorEl) {
      errorEl.classList.remove("show");
      field.classList.remove("form-field-error");
      field.setAttribute("aria-invalid", "false");
    }
    </c:otherwise>
    </c:choose>
    </c:if>
    </c:forEach>
    if (hasErrors && firstErrorField) {
      firstErrorField.focus();
    }
    return !hasErrors;
  }
</script>
<form id="form${widgetContext.uniqueId}" method="post" onsubmit="return checkForm${widgetContext.uniqueId}()">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
  </c:if>
  <c:if test="${!empty subtitle}">
    <p class="subheader"><c:out value="${subtitle}" /></p>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <c:forEach items="${fieldList}" var="formField" varStatus="status">
    <label><c:out value="${formField.label}"/><c:if test="${formField.required}"> <span class="required">*</span></c:if>
      <c:choose>
        <c:when test="${!empty formField.listOfOptions}">
          <select id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>">
            <option value="">&lt; Please Choose &gt;</option>
            <c:forEach items="${formField.listOfOptions}" var="option">
              <c:choose>
                <c:when test="${option.value eq formField.value}">
                  <option value="<c:out value="${option.key}"/>" selected><c:out value="${option.value}" /></option>
                </c:when>
                <c:otherwise>
                  <option value="<c:out value="${option.key}"/>"><c:out value="${option.value}" /></option>
                </c:otherwise>
              </c:choose>
            </c:forEach>
          </select>
        </c:when>
        <c:when test="${formField.type eq 'html'}">
          <textarea id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" class="html-field" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                  <c:if test="${!empty formField.placeholder}"> placeholder="<c:out value="${formField.placeholder}" />"</c:if>
                  <c:if test="${formField.required}">required</c:if>><c:if test="${!empty formField.value}"><c:out value="${formField.value}" /></c:if></textarea>
        </c:when>
        <c:when test="${formField.type eq 'textarea'}">
          <textarea id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" style="height:120px"
                  <c:if test="${!empty formField.placeholder}"> placeholder="<c:out value="${formField.placeholder}" />"</c:if>
                  <c:if test="${formField.required}">required</c:if>><c:if test="${!empty formField.value}"><c:out value="${formField.value}" /></c:if></textarea>
        </c:when>
        <c:otherwise>
          <input type="text"
                 id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                 <c:if test="${!empty formField.placeholder}">placeholder="<c:out value="${formField.placeholder}" />"</c:if>
                 <c:if test="${!empty formField.value}">value="<c:out value="${formField.value}" />"</c:if>
                 <c:if test="${formField.required}">required</c:if>>
        </c:otherwise>
      </c:choose>
    </label>
    <c:if test="${formField.required && empty formField.listOfOptions}">
      <p id="error-${widgetContext.uniqueId}<c:out value="${formField.name}"/>" class="error-message" role="alert" aria-live="polite">
        <i class="fa fa-exclamation-circle"></i><c:out value="${formField.label}"/> is required
      </p>
    </c:if>
  </c:forEach>
  <div class="button-container">
    <input type="submit" class="button radius success" value="Save"/>
    <c:if test="${!empty cancelUrl}">
      <a class="button radius secondary" href="${ctx}${cancelUrl}">Cancel</a>
    </c:if>
  </div>
</form>
