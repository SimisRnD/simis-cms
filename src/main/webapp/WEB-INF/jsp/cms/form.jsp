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
<jsp:useBean id="formFieldList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="useCaptcha" class="java.lang.String" scope="request"/>
<c:if test="${useCaptcha eq 'true' && !empty googleSiteKey}">
<script src='https://www.google.com/recaptcha/api.js' nonce="${cspNonce}"></script>
</c:if>
<c:if test="${useCaptcha eq 'true' && !empty turnstileSiteKey}">
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer nonce="${cspNonce}"></script>
</c:if>
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
  <c:if test="${useCaptcha eq 'true' && !empty googleSiteKey}">
    function onSubmit(token) {
      document.getElementById("form${widgetContext.uniqueId}").submit();
    }
  </c:if>
  $(document).ready(function() {
    $('#form${widgetContext.uniqueId} input:not([type="submit"])').on('input', function(e) {
      if (e.keyCode === 13) {
          return false;
      }
      var errorEl = document.getElementById("error-" + this.name);
      var hasValue = this.type === "checkbox" ? this.checked : this.value.trim() !== "";
      if (errorEl && hasValue) {
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
  });

  function checkForm${widgetContext.uniqueId}() {
    var hasErrors = false;
    var firstErrorField = null;
    <c:forEach items="${formFieldList}" var="formField" varStatus="status">
      <c:if test="${formField.required}">
        <c:choose>
          <c:when test="${!empty formField.listOfOptions}">
            <c:choose>
              <c:when test="${formField.type eq 'checkbox'}">
                var fieldList = document.getElementsByName("${widgetContext.uniqueId}${js:escape(formField.name)}");
                var errorEl = document.getElementById("error-${widgetContext.uniqueId}${js:escape(formField.name)}");
                var isChecked = false;
                for (var i = 0; i < fieldList.length; i++) {
                  if (fieldList[i].checked) {
                    isChecked = true;
                    break;
                  }
                }
                if (!isChecked) {
                  if (errorEl) {
                    errorEl.classList.add("show");
                  }
                  for (var i = 0; i < fieldList.length; i++) {
                    fieldList[i].classList.add("form-field-error");
                    fieldList[i].setAttribute("aria-invalid", "true");
                  }
                  hasErrors = true;
                  if (!firstErrorField && fieldList.length > 0) firstErrorField = fieldList[0];
                } else if (errorEl) {
                  errorEl.classList.remove("show");
                  for (var i = 0; i < fieldList.length; i++) {
                    fieldList[i].classList.remove("form-field-error");
                    fieldList[i].setAttribute("aria-invalid", "false");
                  }
                }
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
          </c:when>
          <c:when test="${formField.type eq 'checkbox'}">
            var field = document.getElementById("${widgetContext.uniqueId}${js:escape(formField.name)}");
            var errorEl = document.getElementById("error-${widgetContext.uniqueId}${js:escape(formField.name)}");
            if (!field.checked) {
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
  <c:if test="${!empty queryString}">
    <input type="hidden" name="queryString" value="<c:out value="${queryString}" />"/>
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
  </c:if>
  <c:if test="${!empty subtitle}">
    <p class="subheader"><c:out value="${subtitle}" /></p>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <c:forEach items="${formFieldList}" var="formField" varStatus="status">
    <c:choose>
      <c:when test="${formField.type eq 'checkbox' && !empty formField.listOfOptions}">
        <%-- Checkbox group: multiple checkboxes sharing one name, so a visitor can check several --%>
        <fieldset>
          <legend><c:out value="${formField.label}"/><c:if test="${formField.required}"> <span class="required">*</span></c:if></legend>
          <c:forEach items="${formField.listOfOptions}" var="option">
            <%-- Was this option checked on a previous same-request submission that failed validation
                 (e.g. a different required field left blank)? userValue only stores the checked
                 options' joined display LABELS, which isn't a safe reverse lookup if two options ever
                 share a label, so compare against the originally-submitted KEYS instead (same
                 membership-check idiom item-full-form.jsp uses for the "tagId" checkbox group). --%>
            <c:set var="isChecked" value="false" />
            <c:forEach var="checkedKey" items="${formField.checkedOptionKeys}">
              <c:if test="${checkedKey eq option.key}">
                <c:set var="isChecked" value="true" />
              </c:if>
            </c:forEach>
            <label>
              <input type="checkbox"
                  id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>-<c:out value="${option.key}"/>"
                  name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                  value="<c:out value="${option.key}"/>"
                  <c:if test="${isChecked eq 'true'}">checked</c:if>/>
              <c:out value="${option.value}" />
            </label>
          </c:forEach>
        </fieldset>
      </c:when>
      <c:otherwise>
        <label><c:out value="${formField.label}"/><c:if test="${formField.required}"> <span class="required">*</span></c:if>
        <c:choose>
          <c:when test="${!empty formField.listOfOptions}">
            <select id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>">
              <option value="">&lt; Please Choose &gt;</option>
              <c:forEach items="${formField.listOfOptions}" var="option">
                <option value="<c:out value="${option.key}"/>"><c:out value="${option.value}" /></option>
              </c:forEach>
            </select>
          </c:when>
          <c:when test="${formField.type eq 'textarea'}">
            <textarea id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" style="height:120px"
                <c:if test="${!empty formField.placeholder}"> placeholder="<c:out value="${formField.placeholder}" />"</c:if>
                <c:if test="${formField.required}">required</c:if>><c:if test="${!empty formField.userValue}"><c:out value="${formField.userValue}" /></c:if></textarea>
          </c:when>
          <c:when test="${formField.type eq 'checkbox'}">
            <%-- Single-toggle checkbox --%>
            <input type="checkbox"
                id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                value="true"
                <c:if test="${formField.userValue eq 'true'}">checked</c:if>>
          </c:when>
          <c:when test="${formField.type eq 'date'}">
            <%-- HTML5 date input always submits/echoes yyyy-MM-dd, so userValue round-trips as-is --%>
            <input type="date"
                id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                <c:if test="${!empty formField.userValue}">value="<c:out value="${formField.userValue}" />"</c:if>
                <c:if test="${formField.required}">required</c:if>>
          </c:when>
          <c:otherwise>
            <input type="text"
                id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                <c:if test="${!empty formField.placeholder}">placeholder="<c:out value="${formField.placeholder}" />"</c:if>
                <c:if test="${!empty formField.userValue}">value="<c:out value="${formField.userValue}" />"</c:if>
                <c:if test="${formField.required}">required</c:if>>
          </c:otherwise>
        </c:choose>
        </label>
      </c:otherwise>
    </c:choose>
    <c:if test="${formField.required}">
      <p id="error-${widgetContext.uniqueId}<c:out value="${formField.name}"/>" class="error-message" role="alert" aria-live="polite">
        <i class="fa fa-exclamation-circle"></i><c:out value="${formField.label}"/> is required
      </p>
    </c:if>
  </c:forEach>
  <c:choose>
    <c:when test="${useCaptcha eq 'true' && !empty googleSiteKey}">
      <p>
        <button class="g-recaptcha button radius large success expanded"
                data-sitekey="<c:out value="${googleSiteKey}" />"
                data-callback='onSubmit'
                data-action='submit'><c:out value="${buttonName}" /></button>
      </p>
    </c:when>
    <c:when test="${useCaptcha eq 'true' && !empty turnstileSiteKey}">
      <div class="cf-turnstile" data-sitekey="<c:out value="${turnstileSiteKey}" />"></div>
      <p>
        <input type="submit" class="button radius large success expanded" value="<c:out value="${buttonName}" />"/>
      </p>
    </c:when>
    <c:when test="${useCaptcha eq 'true'}">
      <p>
        Please enter the text value you see in the image:<br />
        <img src="/assets/captcha" class="margin-bottom-10" alt="captcha" height="40" decoding="async" /><br />
        <input type="text" name="captcha" value="" required/>
      </p>
      <p>
        <input type="submit" class="button radius large success expanded" value="<c:out value="${buttonName}" />"/>
      </p>
    </c:when>
    <c:otherwise>
      <p>
        <input type="submit" class="button radius large success expanded" value="<c:out value="${buttonName}" />"/>
      </p>
    </c:otherwise>
  </c:choose>
</form>
