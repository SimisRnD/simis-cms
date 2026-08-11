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
<jsp:useBean id="formDefinition" class="com.simisinc.platform.domain.model.cms.FormDefinition" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${formDefinition.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <p class="help-text">
    <strong>Name is admin-only</strong> -- it labels this form throughout the admin list and editor,
    and it's the text this form's internal id is generated from, but a visitor never sees it. Title
    and Subtitle are what actually appear on the public form itself. "Email submissions to" and
    Success Title/Message control what happens after a visitor submits -- there's no
    redirect-to-a-URL option; success is a message shown on its own success page.
  </p>
  <%-- Form Content --%>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-6 cell">
      <label>Name <span class="required">*</span>
        <input type="text" placeholder="e.g. Contact Us" name="name" value="<c:out value="${formDefinition.name}"/>" required>
      </label>
    </div>
    <div class="small-12 medium-6 cell">
      <label>Button Label
        <input type="text" placeholder="Submit" name="buttonName" value="<c:out value="${formDefinition.buttonName}"/>">
      </label>
    </div>
  </div>
  <label>Title
    <input type="text" placeholder="Heading shown above the form..." name="title" value="<c:out value="${formDefinition.title}"/>">
  </label>
  <label>Subtitle
    <input type="text" placeholder="Optional text shown under the title..." name="subtitle" value="<c:out value="${formDefinition.subtitle}"/>">
  </label>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-6 cell">
      <label>Success Title
        <input type="text" placeholder="Shown after a successful submission..." name="successTitle" value="<c:out value="${formDefinition.successTitle}"/>">
      </label>
    </div>
    <div class="small-12 medium-6 cell">
      <label>Email submissions to
        <input type="text" placeholder="name@example.com" name="emailTo" value="<c:out value="${formDefinition.emailTo}"/>">
      </label>
      <p class="help-text" style="margin-top:-8px">Free text, not validated as an email address -- a
        typo here means notifications silently go nowhere, with no error shown to you or the
        submitter.</p>
    </div>
  </div>
  <label>Success Message
    <input type="text" placeholder="Optional message shown after a successful submission..." name="successMessage" value="<c:out value="${formDefinition.successMessage}"/>">
  </label>
  <input id="sendConfirmationToSubmitter" type="checkbox" name="sendConfirmationToSubmitter" value="true" <c:if test="${formDefinition.sendConfirmationToSubmitter}">checked</c:if>/><label for="sendConfirmationToSubmitter">Send a confirmation email to the person who submitted this form?</label>
  <p class="help-text" style="margin-top:-8px">
    Only sent if the form has an "Email" type field and the visitor's answer to it is a valid
    address -- there's no other way to know who to reply to. The Success Title/Message above are
    shown on the page itself either way; this is a separate email, in addition to that.
  </p>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-6 cell">
      <label>Confirmation Email Subject
        <input type="text" placeholder="e.g. We received your message" name="confirmationSubject" value="<c:out value="${formDefinition.confirmationSubject}"/>">
      </label>
    </div>
    <div class="small-12 medium-6 cell">
      <label>Confirmation Email Message
        <input type="text" placeholder="e.g. Thanks for reaching out -- we'll reply within 2 business days." name="confirmationMessage" value="<c:out value="${formDefinition.confirmationMessage}"/>">
      </label>
    </div>
  </div>
  <input id="useCaptcha" type="checkbox" name="useCaptcha" value="true" <c:if test="${formDefinition.useCaptcha}">checked</c:if>/><label for="useCaptcha">Use Captcha?</label>
  <input id="checkForSpam" type="checkbox" name="checkForSpam" value="true" <c:if test="${formDefinition.checkForSpam}">checked</c:if>/><label for="checkForSpam">Check for spam?</label>
  <input id="enabled" type="checkbox" name="enabled" value="true" <c:if test="${formDefinition.enabled}">checked</c:if>/><label for="enabled">Enabled?</label>
  <div class="callout radius warning" style="margin-top:10px">
    <p style="margin-bottom:0">
      <i class="fa fa-exclamation-triangle"></i> <strong>Known issue:</strong> unchecking "Enabled?"
      or "Check for spam?" currently has no effect -- both are silently saved as still-on no matter
      what's checked here, on both a new form and an edit. A fix is in progress. Until it ships, the
      only way to actually take a form offline is to remove its "Form" widget from the page it's
      placed on -- disabled-looking submissions here will still go through if the widget stays in
      place.
    </p>
  </div>
  <div class="button-container">
    <c:choose>
      <c:when test="${formDefinition.id eq -1}">
        <input type="submit" class="button radius success expanded" value="Save"/>
      </c:when>
      <c:otherwise>
        <input type="submit" class="button radius success" value="Save"/>
        <a href="${ctx}/admin/forms" class="button radius secondary">Cancel</a>
      </c:otherwise>
    </c:choose>
  </div>
</form>
