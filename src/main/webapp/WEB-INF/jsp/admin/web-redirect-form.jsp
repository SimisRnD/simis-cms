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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webRedirect" class="com.simisinc.platform.domain.model.cms.WebRedirect" scope="request"/>
<c:choose>
  <c:when test="${webRedirect.id eq -1}"><h4>New Web Redirect</h4></c:when>
  <c:otherwise><h4>Edit Web Redirect</h4></c:otherwise>
</c:choose>
<%@include file="../page_messages.jspf" %>

<form method="post" autocomplete="off">
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <input type="hidden" name="id" value="${webRedirect.id}"/>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <label for="fromPath">From Path <span class="required">*</span>
        <input type="text" id="fromPath" name="fromPath" maxlength="500" placeholder="/old-page" value="<c:out value="${webRedirect.fromPath}" />" <c:if test="${webRedirect.id eq -1}">autofocus="autofocus"</c:if> required>
      </label>
      <p class="help-text" id="fromPathHelpText">The site-relative path to redirect from, starting with a /. Must be unique.</p>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <label for="toUrl">To URL <span class="required">*</span>
        <input type="text" id="toUrl" name="toUrl" maxlength="2000" placeholder="/new-page or https://example.com/page" value="<c:out value="${webRedirect.toUrl}" />" required>
      </label>
      <p class="help-text" id="toUrlHelpText">Prefer a site-relative path (e.g. <code>/new-page</code>). An absolute
        http(s) URL sends visitors to an external site -- every visitor who hits the From Path above is bounced
        there automatically, with no click and no warning that they left this site, so use one only when you mean
        to. Administrators only; content managers can redirect to a path on this site.</p>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-4 large-3 cell">
      <label for="statusCode">Status Code
        <select id="statusCode" name="statusCode">
          <option value="301" <c:if test="${webRedirect.statusCode eq 301}">selected</c:if>>301 - Permanent</option>
          <option value="302" <c:if test="${webRedirect.statusCode eq 302}">selected</c:if>>302 - Temporary</option>
        </select>
      </label>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-10 large-8 cell">
      <label>Enabled
        <input id="enabled" type="checkbox" name="enabled" value="true" <c:if test="${webRedirect.id eq -1 || webRedirect.enabled}">checked</c:if>/>
      </label>
      <p class="help-text">Disabled redirects are kept but never applied to incoming requests.</p>
    </div>
  </div>
  <div class="grid-x grid-margin-x">
    <div class="small-12 cell">
      <p>
        <input type="submit" class="button radius success" value="Save"/>
        <a class="button radius secondary" href="${ctx}/admin/web-redirects">Cancel</a>
      </p>
    </div>
  </div>
</form>
