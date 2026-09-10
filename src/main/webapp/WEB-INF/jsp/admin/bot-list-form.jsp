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
<jsp:useBean id="botList" class="com.simisinc.platform.domain.model.BotUserAgent" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${botList.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Partial User Agent to Match <span class="required">*</span>
    <input type="text" placeholder="e.g. Googlebot" name="userAgent" value="<c:out value="${botList.userAgent}"/>" required>
  </label>
  <label>Label
    <input type="text" placeholder="e.g. Google" name="label" value="<c:out value="${botList.label}"/>">
  </label>
  <p class="help-text">Matching is a plain substring check against the visitor's raw User-Agent header, and it's <strong>case-sensitive</strong> -- copy the signature in the exact case from an actual server log rather than guessing. Prefer something specific over something short; a generic fragment can still match real browsers even at the minimum length. This only affects analytics counting, not what a bot is allowed to do.</p>
  <p class="help-text">New entries only affect sessions created after you save -- test from a fresh/incognito session, since a session's bot status is decided once, at creation, and never re-evaluated.</p>
  <div class="button-container">
    <input type="submit" class="button radius success expanded" value="Save"/>
  </div>
</form>
