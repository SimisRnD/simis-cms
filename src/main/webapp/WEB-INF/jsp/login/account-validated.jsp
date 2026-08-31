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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="doPassword" class="java.lang.String" scope="request"/>
<jsp:useBean id="confirmation" class="java.lang.String" scope="request"/>
<c:choose>
  <c:when test="${empty doPassword || doPassword ne 'true'}">
    <h2 class="text-center h4">Your account has been validated</h2>
  </c:when>
  <c:otherwise>
    <h2 class="text-center h5">Please create your password to continue</h2>
    <form method="post">
        <%-- Required by controller --%>
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
        <%-- Form values --%>
      <input type="hidden" name="confirmation" value="<c:out value="${confirmation}"/>"/>
      <%@include file="../page_messages.jspf" %>
        <%-- Form Content --%>
      <small>&nbsp;</small>
      <div class="grid-x grid-padding-x align-center">
        <div class="small-12 medium-10 cell">
          <%-- Both fields get a toggle, not just the first: the whole purpose of the re-enter field
               is catching a typo, and being able to compare the two is what makes that possible. --%>
          <label for="new-password">New Password</label>
          <div class="password-field">
            <input id="new-password" name="password" type="password" placeholder="Password" autocomplete="off" required>
            <button type="button" class="secret-reveal-toggle" data-reveal-secret hidden
                    aria-pressed="false" aria-label="Show the value while typing"
                    title="Show the value while typing"><i class="fa fa-eye" aria-hidden="true"></i></button>
          </div>
          <label for="new-password-confirm">Re-Enter Password</label>
          <div class="password-field">
            <input id="new-password-confirm" name="password2" type="password" placeholder="Re-Enter Password" autocomplete="off" required>
            <button type="button" class="secret-reveal-toggle" data-reveal-secret hidden
                    aria-pressed="false" aria-label="Show the value while typing"
                    title="Show the value while typing"><i class="fa fa-eye" aria-hidden="true"></i></button>
          </div>
          <div class="button-container">
            <input type="submit" class="button radius success expanded" value="Create Password"/>
          </div>
        </div>
      </div>
    </form>
  </c:otherwise>
</c:choose>
