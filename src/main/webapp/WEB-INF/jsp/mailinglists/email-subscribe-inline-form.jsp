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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="sitePropertyMap" class="java.util.HashMap" scope="request"/>
<script type="text/javascript">
    function validateEmail${widgetContext.uniqueId}(email) {
        var re = /\S+@\S+\.\S+/;
        return re.test(email);
    }
    function emailSignUp${widgetContext.uniqueId}() {
        var email = document.getElementById("email${widgetContext.uniqueId}").value;
        if (email === undefined || email.length === 0) {
            document.getElementById('emailHelpText${widgetContext.uniqueId}').innerHTML = "Please enter your email address";
            return false;
        }
        if (!validateEmail${widgetContext.uniqueId}(email)) {
            document.getElementById('emailHelpText${widgetContext.uniqueId}').innerHTML = "Please re-enter your email address using a proper format.";
            return false;
        }
        $.getJSON("${ctx}/json/emailSubscribe?token=${userSession.formToken}&email=" + encodeURIComponent(email), function(data) {
            if (data.status === undefined || data.status !== '0') {
                document.getElementById('emailHelpText${widgetContext.uniqueId}').innerHTML = "Please re-enter your email address using a proper format.";
                return false;
            }
            document.getElementById('emailHelpText${widgetContext.uniqueId}').innerHTML = "Thanks for signing up for <c:out value="${js:escape(sitePropertyMap['site.name'])}"/> emails";
        });
        return false;
    }
</script>
<form method="get" onsubmit="return emailSignUp${widgetContext.uniqueId}()">
  <div class="input-group">
    <input class="input-group-field" type="text" id="email${widgetContext.uniqueId}" name="email${widgetContext.uniqueId}" placeholder="name@email.com" required>
    <div class="input-group-button">
      <button type="submit" class="button call-to-action"><c:out value="${buttonName}" /></button>
    </div>
  </div>
  <p class="help-text" id="emailHelpText${widgetContext.uniqueId}"></p>
</form>
