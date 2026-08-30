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
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
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
      <button type="submit" name="action" value="sendTestEmail" formnovalidate class="button radius secondary tiny">Send Test Email</button>
      <p class="help-text" style="margin-top:-8px">Comma-separated for multiple addresses. Checked
        for valid email syntax when you save -- but a wrong-yet-valid-looking address (a typo of a
        real one) can't be caught that way. "Send Test Email" sends a real message to whatever's
        typed above right now, saved or not, so you can confirm it actually reaches the right inbox.</p>
    </div>
  </div>
  <label>Success Message
    <input type="text" placeholder="Optional message shown after a successful submission..." name="successMessage" value="<c:out value="${formDefinition.successMessage}"/>">
  </label>
  <label>Notification Email Subject
    <input type="text" placeholder="Leave blank for: New Contact Us inquiry - Acme Defense (Sales)" name="notificationSubject" value="<c:out value="${formDefinition.notificationSubject}"/>">
  </label>
  <p class="help-text" style="margin-top:-8px">
    The subject of the email sent to "Email submissions to" above. Leave it blank and every form
    gets the same readable default -- <em>New &lt;form name&gt; inquiry - &lt;who it is from&gt;
    (&lt;their dropdown choice&gt;)</em> -- which is usually what you want.
    Set it to override that for this form. You can include an answer with
    <code>{{fieldName}}</code>, using the field's Name (not its Label), e.g.
    <code>RFI: {{organization}}</code>. Values a visitor typed are stripped of line breaks and
    shortened before they reach the subject.
  </p>
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
  <input id="showPrivacyNotice" type="checkbox" name="showPrivacyNotice" value="true" <c:if test="${formDefinition.showPrivacyNotice}">checked</c:if>/><label for="showPrivacyNotice">Show a privacy notice near the submit button?</label>
  <p class="help-text" style="margin-top:-8px">
    Links to the site's Privacy Policy page. Only actually appears on the live form if that page is
    also turned on under Site Settings -- checking this box here alone does nothing if it isn't.
  </p>
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
