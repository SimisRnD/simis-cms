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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="formFieldList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="useCaptcha" class="java.lang.String" scope="request"/>
<c:if test="${useCaptcha eq 'true' && !empty googleSiteKey}">
<script src='https://www.google.com/recaptcha/api.js'></script>
</c:if>
<script>
  <c:if test="${useCaptcha eq 'true' && !empty googleSiteKey}">
    function onSubmit(token) {
      document.getElementById("form${widgetContext.uniqueId}").submit();
    }
  </c:if>
  $(document).ready(function() {
    $('#form${widgetContext.uniqueId} input:not([type="submit"])').keydown(function(e) {
      if (e.keyCode === 13) {
          return false;
      }
    });
    $('textarea').keypress(function(event) {
      if (event.keyCode === 13) {
        event.preventDefault();
      }
    });
  });

  function checkForm${widgetContext.uniqueId}() {
    <c:forEach items="${formFieldList}" var="formField" varStatus="status">
      <c:if test="${formField.required}">
        <c:choose>
          <c:when test="${!empty formField.listOfOptions}">

          </c:when>
          <c:otherwise>
            if (document.getElementById("${widgetContext.uniqueId}${js:escape(formField.name)}").value.trim() === "") {
              alert("Please provide a value for '${js:escape(formField.label)}'");
              return false;
            }
          </c:otherwise>
        </c:choose>
      </c:if>
    </c:forEach>
    return true;
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
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
  </c:if>
  <c:if test="${!empty subtitle}">
    <p class="subheader"><c:out value="${subtitle}" /></p>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <c:forEach items="${formFieldList}" var="formField" varStatus="status">
    <label><c:out value="${formField.label}"/><c:if test="${formField.required}"> <span class="required">*</span></c:if>
    <c:choose>
      <c:when test="${!empty formField.listOfOptions}">
        <select id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>">
          <option value="">&lt; Please Choose &gt;</option>
          <c:forEach items="${formField.listOfOptions}" var="option">
            <option value="${option.key}"><c:out value="${option.value}" /></option>
          </c:forEach>
        </select>
      </c:when>
      <c:when test="${formField.type eq 'textarea'}">
        <textarea id="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" name="${widgetContext.uniqueId}<c:out value="${formField.name}"/>" style="height:120px"
            <c:if test="${!empty formField.placeholder}"> placeholder="<c:out value="${formField.placeholder}" />"</c:if>
            <c:if test="${formField.required}">required</c:if>><c:if test="${!empty formField.userValue}"><c:out value="${formField.userValue}" /></c:if></textarea>
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
    <c:when test="${useCaptcha eq 'true'}">
      <p>
        Please enter the text value you see in the image:<br />
        <img src="/assets/captcha" class="margin-bottom-10" /><br />
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
