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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="doPassword" class="java.lang.String" scope="request"/>
<jsp:useBean id="confirmation" class="java.lang.String" scope="request"/>
<c:choose>
  <c:when test="${empty doPassword || doPassword ne 'true'}">
    <h4 class="text-center">Your account has been validated</h4>
  </c:when>
  <c:otherwise>
    <h5 class="text-center">Please create your password to continue</h5>
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
          <label>New Password
            <input name="password" type="password" placeholder="Password" autocomplete="off" required>
          </label>
          <label>Re-Enter Password
            <input name="password2" type="password" placeholder="Re-Enter Password" autocomplete="off" required>
          </label>
          <div class="button-container">
            <input type="submit" class="button radius success expanded" value="Create Password"/>
          </div>
        </div>
      </div>
    </form>
  </c:otherwise>
</c:choose>
