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
<%-- enterprise.js for a key issued by Google's current console: those cannot be verified by
     the legacy siteverify endpoint at all, so they take the Enterprise assessment API and its own
     script family. The button markup below is identical either way -- Google's integration panel
     prints the same data-sitekey/data-callback form for both. Issue 1615. --%>
<c:choose>
  <c:when test="${googleEnterprise eq 'true'}">
    <script src='https://www.google.com/recaptcha/enterprise.js?render=<c:out value="${googleSiteKey}"/>' nonce="${cspNonce}"></script>
  </c:when>
  <c:otherwise>
    <script src='https://www.google.com/recaptcha/api.js' nonce="${cspNonce}"></script>
  </c:otherwise>
</c:choose>
</c:if>
<c:if test="${useCaptcha eq 'true' && !empty turnstileSiteKey}">
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer nonce="${cspNonce}"></script>
</c:if>
<style>
  .form-field-error {
    border-left: 4px solid var(--sc-danger, #ba403e) !important;
    background-color: rgba(204, 76, 40, 0.02);
  }
  .error-message {
    display: none;
    color: var(--sc-danger, #ba403e);
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
  /* Honeypot (issue #1153): off-screen rather than display:none/visibility:hidden -- some spam bots
     specifically skip those two properties when deciding what to fill in, but still find and fill an
     absolutely-positioned field a screen reader or sighted user would never encounter either. */
  .hp-field {
    position: absolute;
    left: -9999px;
    top: -9999px;
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
  <%-- Bound here instead of an inline onsubmit= -- CSP's script-src-attr is not covered by the
       nonce above, so the attribute never compiled and this validation silently never ran on a
       public form: no inline errors, no aria-invalid, no focus moved to the first bad field
       (issue #1359, same root cause as #1188). --%>
  document.addEventListener('DOMContentLoaded', function () {
    var theForm = document.getElementById('form${widgetContext.uniqueId}');
    if (theForm) {
      theForm.addEventListener('submit', function (event) {
        if (!checkForm${widgetContext.uniqueId}()) {
          event.preventDefault();
        }
      });
    }
  });
</script>
<form id="form${widgetContext.uniqueId}" method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <c:if test="${!empty queryString}">
    <input type="hidden" name="queryString" value="<c:out value="${queryString}" />"/>
  </c:if>
  <%-- Honeypot (issue #1153): a real visitor never sees this field (off-screen, aria-hidden, no tab
       stop) -- a bot that fills every input it finds, including this one, gets silently dropped by
       FormWidget#post. Baited with a common autofill-target label/name so scripted fillers are more
       likely to populate it than skip it. --%>
  <div class="hp-field" aria-hidden="true">
    <label for="${widgetContext.uniqueId}_hpWebsite">Website</label>
    <input type="text" id="${widgetContext.uniqueId}_hpWebsite" name="${widgetContext.uniqueId}_hpWebsite"
        tabindex="-1" autocomplete="off"/>
  </div>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
  </c:if>
  <c:if test="${!empty subtitle}">
    <p class="subheader"><c:out value="${subtitle}" /></p>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <c:forEach items="${formFieldList}" var="formField" varStatus="status">
    <%-- The field's initial value. "Default Value" has been offered in the form-field editor,
         saved and persisted all along, and never read here -- so filling it in did nothing, for
         every field type.

         It applies only when userValue is NULL, which is a fresh render. An EMPTY userValue is
         different: it means the form is being redisplayed after a validation error and the
         visitor left this field blank. Re-applying the default there would put text back that
         they had deliberately removed, at the exact moment the page is asking them to correct
         something.

         Keep this inside THIS loop, the one that renders the fields. <c:set> without a scope is
         page-scoped, not loop-local: computed in the validation-script loop above, it survived that
         loop and left every input, textarea and checkbox below rendering the LAST required field's
         value -- on this site, the message body appearing in name, email and phone. --%>
    <c:set var="initialValue" value="${formField.userValue == null ? formField.defaultValue : formField.userValue}"/>
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
            <label for="${widgetContext.uniqueId}<c:out value="${formField.name}"/>-<c:out value="${option.key}"/>">
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
        <label for="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"><c:out value="${formField.label}"/><c:if test="${formField.required}"> <span class="required">*</span></c:if>
        <c:choose>
          <c:when test="${!empty formField.listOfOptions}">
            <%-- A required select gets the attribute the same way the input and textarea branches
                 below do. It was the only control that did not, so a required dropdown was enforced
                 on the server and nowhere else: the visitor filled the whole form, submitted, and
                 only then got "Topic is required" back. The placeholder option immediately below
                 carries value="", which is what lets the browser treat "unchosen" as empty and
                 refuse the submit. --%>
            <select id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                <c:if test="${formField.required}">required</c:if>>
              <%-- Re-select what was chosen when a same-request validation error redisplays the
                   form. Matched on the option KEY recorded by FormWidget#post, not on userValue,
                   which holds the display label and is not a safe reverse lookup if two options
                   ever share one -- the same reason the checkbox group above tracks keys. Without
                   this the select silently resets to "< Please Choose >" while every other field
                   keeps its value, so a required choice is lost exactly when the form is telling
                   the visitor to correct something. --%>
              <%-- Fresh render falls back to the default; a redisplay honours what was chosen, and
                   honours an unchosen dropdown by leaving it unchosen. --%>
              <c:set var="selectedOptionKey" value="${!empty formField.checkedOptionKeys ? formField.checkedOptionKeys[0] : (formField.userValue == null ? formField.defaultValue : null)}"/>
              <option value="">&lt; Please Choose &gt;</option>
              <c:forEach items="${formField.listOfOptions}" var="option">
                <option value="<c:out value="${option.key}"/>"<c:if test="${selectedOptionKey eq option.key}"> selected</c:if>><c:out value="${option.value}" /></option>
              </c:forEach>
            </select>
          </c:when>
          <c:when test="${formField.type eq 'textarea'}">
            <textarea id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" style="height:120px"
                <c:if test="${!empty formField.placeholder}"> placeholder="<c:out value="${formField.placeholder}" />"</c:if>
                <c:if test="${formField.required}">required</c:if>><c:if test="${!empty initialValue}"><c:out value="${initialValue}" /></c:if></textarea>
          </c:when>
          <c:when test="${formField.type eq 'checkbox'}">
            <%-- Single-toggle checkbox --%>
            <input type="checkbox"
                id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                value="true"
                <c:if test="${initialValue eq 'true'}">checked</c:if>>
          </c:when>
          <c:when test="${formField.type eq 'date'}">
            <%-- HTML5 date input always submits/echoes yyyy-MM-dd, so userValue round-trips as-is --%>
            <input type="date"
                id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                <c:if test="${!empty initialValue}">value="<c:out value="${initialValue}" />"</c:if>
                <c:if test="${formField.required}">required</c:if>>
          </c:when>
          <c:otherwise>
            <input type="text"
                id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>"
                <c:if test="${!empty formField.placeholder}">placeholder="<c:out value="${formField.placeholder}" />"</c:if>
                <c:if test="${!empty initialValue}">value="<c:out value="${initialValue}" />"</c:if>
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
  <c:if test="${showPrivacyNotice && 'true' eq sitePropertyMap['site.privacy.policy']}">
    <p class="help-text">By submitting this form, you agree to our <a href="${ctx}/legal/privacy" target="_blank">Privacy Policy</a>.</p>
  </c:if>
  <c:choose>
    <c:when test="${useCaptcha eq 'true' && !empty googleSiteKey}">
      <p>
        <button class="g-recaptcha <c:out value="${buttonClass}" />"
                data-sitekey="<c:out value="${googleSiteKey}" />"
                data-callback='onSubmit'
                data-action='submit'><c:out value="${buttonName}" /></button>
      </p>
    </c:when>
    <c:when test="${useCaptcha eq 'true' && !empty turnstileSiteKey}">
      <div class="cf-turnstile" data-sitekey="<c:out value="${turnstileSiteKey}" />"></div>
      <p>
        <input type="submit" class="<c:out value="${buttonClass}" />" value="<c:out value="${buttonName}" />"/>
      </p>
    </c:when>
    <c:when test="${useCaptcha eq 'true'}">
      <p>
        Please enter the text value you see in the image:<br />
        <img src="/assets/captcha" class="margin-bottom-10" alt="captcha" height="40" decoding="async" />
        <a href="#" data-captcha-refresh class="margin-left-5" title="Get a new image" aria-label="Get a new captcha image"><i class="fa fa-sync-alt"></i></a><br />
        <input type="text" name="captcha" value="" required/>
      </p>
      <p>
        <input type="submit" class="<c:out value="${buttonClass}" />" value="<c:out value="${buttonName}" />"/>
      </p>
    </c:when>
    <c:otherwise>
      <p>
        <input type="submit" class="<c:out value="${buttonClass}" />" value="<c:out value="${buttonName}" />"/>
      </p>
    </c:otherwise>
  </c:choose>
</form>
