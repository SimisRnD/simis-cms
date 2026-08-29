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
<jsp:useBean id="sitePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="useCaptcha" class="java.lang.String" scope="request"/>
<jsp:useBean id="onlineMailingLists" class="java.util.ArrayList" scope="request"/>
<%-- issue #484: this form previously submitted straight to EmailSubscribeAjax with no CAPTCHA or
     rate limiting at all -- the sibling EmailSubscribeWidget/FormWidget paths already had both.
     Mirrors the same captcha branches form.jsp already uses (Google invisible reCAPTCHA, Cloudflare
     Turnstile, the image+text fallback, or none -- issue #519 added Turnstile), adapted for this
     widget's AJAX (not native POST) submit. --%>
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
<script type="text/javascript" nonce="${cspNonce}">
    function validateEmail${widgetContext.uniqueId}(email) {
        var re = /\S+@\S+\.\S+/;
        return re.test(email);
    }
    // Issue #598: one mailingListId param per list the visitor kept checked -- a hidden input
    // (always included) when there's exactly one public list, so today's single-list signup
    // looks and behaves exactly as it did before this feature existed.
    function selectedMailingListParams${widgetContext.uniqueId}() {
        var params = "";
        document.querySelectorAll(".mailingListCheckbox${widgetContext.uniqueId}").forEach(function(el) {
            if (el.type !== "checkbox" || el.checked) {
                params += "&mailingListId=" + encodeURIComponent(el.value);
            }
        });
        return params;
    }
    function submitEmailSignUp${widgetContext.uniqueId}(extraParams) {
        var email = document.getElementById("email${widgetContext.uniqueId}").value;
        if (email === undefined || email.length === 0) {
            document.getElementById('emailHelpText${widgetContext.uniqueId}').innerHTML = "Please enter your email address";
            return;
        }
        if (!validateEmail${widgetContext.uniqueId}(email)) {
            document.getElementById('emailHelpText${widgetContext.uniqueId}').innerHTML = "Please re-enter your email address using a proper format.";
            return;
        }
        extraParams += selectedMailingListParams${widgetContext.uniqueId}();
        $.getJSON("${ctx}/json/emailSubscribe?token=${userSession.formToken}&email=" + encodeURIComponent(email) + extraParams, function(data) {
            if (data.status === undefined || data.status !== '0') {
                document.getElementById('emailHelpText${widgetContext.uniqueId}').innerHTML =
                    (data.message ? data.message : "Please re-enter your email address using a proper format.");
                return;
            }
            document.getElementById('emailHelpText${widgetContext.uniqueId}').innerHTML = "Almost done! Check your email to confirm your subscription to <c:out value="${js:escape(sitePropertyMap['site.name'])}"/> emails";
        });
    }
    <c:choose>
    <c:when test="${useCaptcha eq 'true' && !empty googleSiteKey}">
    // The g-recaptcha-bound submit button (below) intercepts the click itself and calls this
    // callback with the verified token once the invisible check passes -- Google's own script
    // prevents the native form submission, so this is only a harmless fallback for any other path
    // that might fire the form's submit event (e.g. pressing Enter before the script has loaded).
    function emailSignUp${widgetContext.uniqueId}() {
        return false;
    }
    function emailSignUpCaptchaCallback${widgetContext.uniqueId}(token) {
        submitEmailSignUp${widgetContext.uniqueId}("&g-recaptcha-response=" + encodeURIComponent(token));
    }
    </c:when>
    <c:when test="${useCaptcha eq 'true' && !empty turnstileSiteKey}">
    // Turnstile renders an inline widget (not a button-replacement like Google's g-recaptcha
    // class above), so the challenge is solved before submit -- its own hidden input is read here.
    function emailSignUp${widgetContext.uniqueId}() {
        var turnstileField = document.querySelector("#turnstile${widgetContext.uniqueId} input[name='cf-turnstile-response']");
        var turnstileToken = turnstileField ? turnstileField.value : "";
        if (!turnstileToken) {
            document.getElementById('emailHelpText${widgetContext.uniqueId}').innerHTML = "Please complete the verification challenge";
            return false;
        }
        submitEmailSignUp${widgetContext.uniqueId}("&cf-turnstile-response=" + encodeURIComponent(turnstileToken));
        return false;
    }
    </c:when>
    <c:when test="${useCaptcha eq 'true'}">
    function emailSignUp${widgetContext.uniqueId}() {
        var captchaValue = document.getElementById("captcha${widgetContext.uniqueId}").value;
        if (!captchaValue) {
            document.getElementById('emailHelpText${widgetContext.uniqueId}').innerHTML = "Please enter the text shown in the image";
            return false;
        }
        submitEmailSignUp${widgetContext.uniqueId}("&captcha=" + encodeURIComponent(captchaValue));
        return false;
    }
    </c:when>
    <c:otherwise>
    function emailSignUp${widgetContext.uniqueId}() {
        submitEmailSignUp${widgetContext.uniqueId}("");
        return false;
    }
    </c:otherwise>
    </c:choose>

    // issue #1188: this ran from an inline onsubmit attribute, which CSP blocks -- inline handler
    // attributes fall under script-src-attr and the nonce authorises this block, not an attribute
    // calling into it. Every branch of emailSignUp() above submits over AJAX and returns false, so
    // with it skipped the form fell through to its native GET: nobody was subscribed, and because
    // the email input carried a name attribute the visitor's address ended up in the query string
    // of a page URL -- reaching the access log, the CDN log and browser history. That name
    // attribute is dropped too; submitEmailSignUp() reads the field by id.  The Google reCAPTCHA
    // branch was the least visible of the four, and the reason this took a while to spot: there the
    // button carries the g-recaptcha class and Google's own script intercepts the click and fires
    // data-callback, so clicking Sign Up still worked. Only pressing Enter in the email field fell
    // through to the leaking native GET. The Turnstile, image-captcha and no-captcha branches had
    // no such interception and were broken outright.
    document.addEventListener('DOMContentLoaded', function () {
        var form = document.getElementById('emailSignUpForm${widgetContext.uniqueId}');
        if (form) {
            form.addEventListener('submit', function (event) {
                event.preventDefault();
                emailSignUp${widgetContext.uniqueId}();
            });
        }
    });
</script>
<form method="get" id="emailSignUpForm${widgetContext.uniqueId}">
  <div class="input-group">
    <input class="input-group-field" type="text" id="email${widgetContext.uniqueId}" placeholder="name@email.com" required>
    <div class="input-group-button">
      <c:choose>
        <c:when test="${useCaptcha eq 'true' && !empty googleSiteKey}">
          <button type="submit" class="g-recaptcha button call-to-action"
                  data-sitekey="<c:out value="${googleSiteKey}" />"
                  data-callback="emailSignUpCaptchaCallback${widgetContext.uniqueId}"
                  data-action="subscribe"><c:out value="${buttonName}" /></button>
        </c:when>
        <c:otherwise>
          <button type="submit" class="button call-to-action"><c:out value="${buttonName}" /></button>
        </c:otherwise>
      </c:choose>
    </div>
  </div>
  <c:if test="${useCaptcha eq 'true' && !empty turnstileSiteKey}">
    <div class="cf-turnstile" id="turnstile${widgetContext.uniqueId}" data-sitekey="<c:out value="${turnstileSiteKey}" />"></div>
  </c:if>
  <c:if test="${useCaptcha eq 'true' && empty googleSiteKey && empty turnstileSiteKey}">
    <p class="help-text">
      Enter the text shown: <img src="/assets/captcha" alt="captcha" style="vertical-align: middle;" height="40" decoding="async" />
      <a href="#" data-captcha-refresh class="margin-left-5" title="Get a new image" aria-label="Get a new captcha image" style="vertical-align: middle;"><i class="fa fa-sync-alt"></i></a>
      <input type="text" id="captcha${widgetContext.uniqueId}" required/>
    </p>
  </c:if>
  <c:choose>
    <c:when test="${fn:length(onlineMailingLists) > 1}">
      <p class="help-text">
        <c:forEach items="${onlineMailingLists}" var="list">
          <label class="inline-list-checkbox">
            <input type="checkbox" class="mailingListCheckbox${widgetContext.uniqueId}" value="${list.id}" checked>
            <c:out value="${list.title}" />
          </label>
        </c:forEach>
      </p>
    </c:when>
    <c:when test="${fn:length(onlineMailingLists) == 1}">
      <input type="hidden" class="mailingListCheckbox${widgetContext.uniqueId}" value="${onlineMailingLists[0].id}">
    </c:when>
  </c:choose>
  <p class="help-text" id="emailHelpText${widgetContext.uniqueId}"></p>
</form>
