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
<%@ page import="java.util.TimeZone" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="sitePropertyList" class="java.util.ArrayList" scope="request"/>
<link href="${ctx}/css/spectrum-1.8.1/spectrum.css" rel="stylesheet">
<script src="${ctx}/javascript/spectrum-1.8.1/spectrum.js"></script>
<%-- Handle image uploads --%>
<script nonce="${cspNonce}">

  var currentPhotoId = 'none';
  function SetPhotoId(id) {
    currentPhotoId = id;
  }

  function SavePhoto(e,id) {
    var file = e.files[0]; // similar to: document.getElementById("file").files[0]
    var formData = new FormData();
    formData.append("file", file);
    var xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function() {
      if (this.readyState === 4) {
        if (this.status === 200) {
          var fileData = JSON.parse(this.responseText);
          document.getElementById("imageUrl" + id).value = fileData.location;
          document.getElementById("imageUrlPreview" + id).src = fileData.location;
        } else {
          document.getElementById("imageFile" + id).value = "";
          alert('There was an error with the file. Make sure to use a .jpg or .png');
        }
      }
    };
    xhr.open("POST", '${ctx}/image-upload?widget=imageUpload1&token=${userSession.formToken}');
    xhr.send(formData);
  }
</script>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form Content --%>
  <%@include file="../page_messages.jspf" %>
  <table class="unstriped">
    <thead>
    <tr>
      <th width="200">Name</th>
      <th>Value</th>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${sitePropertyList}" var="siteProperty">
      <tr>
        <td><c:out value="${siteProperty.label}" /></td>
        <td nowrap>
          <c:choose>
            <%-- Secret values are never rendered back to the browser --%>
            <c:when test="${secretPropertyNames.contains(siteProperty.name)}">
              <c:choose>
                <c:when test="${siteProperty.type eq 'disabled'}">
                  <input type="password" class="no-gap" value="" placeholder="<c:out value="${empty siteProperty.value ? 'not set' : 'value hidden'}"/>" disabled />
                </c:when>
                <c:otherwise>
                  <input type="password" class="no-gap" name="${siteProperty.name}" value="" autocomplete="new-password" placeholder="<c:out value="${empty siteProperty.value ? 'not set' : 'value hidden; leave blank to keep it'}"/>"<c:if test="${siteProperty.name eq 'captcha.google.secretkey'}"> aria-describedby="captchaGoogleSecretkeyHelpText"</c:if><c:if test="${siteProperty.name eq 'bi.superset.secret'}"> aria-describedby="biSupersetSecretHelpText"</c:if><c:if test="${siteProperty.name eq 'bi.metabase.secret'}"> aria-describedby="biMetabaseSecretHelpText"</c:if><c:if test="${siteProperty.name eq 'mail.password'}"> aria-describedby="mailPasswordHelpText"</c:if> />
                </c:otherwise>
              </c:choose>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.logo.color'}">
              <select name="${siteProperty.name}">
                <option value="full-color"<c:if test="${siteProperty.value eq 'full-color'}"> selected</c:if>>Full color</option>
                <option value="all-white"<c:if test="${siteProperty.value eq 'all-white'}"> selected</c:if>>All white</option>
                <option value="color-and-white"<c:if test="${siteProperty.value eq 'color-and-white'}"> selected</c:if>>Color and White</option>
                <option value="text-only"<c:if test="${siteProperty.value eq 'text-only'}"> selected</c:if>>Text only</option>
                <option value="none"<c:if test="${siteProperty.value eq 'none'}"> selected</c:if>>No logo</option>
              </select>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.footer.logo.color'}">
              <select name="${siteProperty.name}">
                <option value="full-color"<c:if test="${siteProperty.value eq 'full-color'}"> selected</c:if>>Full color</option>
                <option value="all-white"<c:if test="${siteProperty.value eq 'all-white'}"> selected</c:if>>All white</option>
                <option value="color-and-white"<c:if test="${siteProperty.value eq 'color-and-white'}"> selected</c:if>>Color and White</option>
                <option value="text-only"<c:if test="${siteProperty.value eq 'text-only'}"> selected</c:if>>Text only</option>
                <option value="none"<c:if test="${siteProperty.value eq 'none'}"> selected</c:if>>No logo</option>
              </select>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.ui.mode'}">
              <select name="${siteProperty.name}">
                <option value="light"<c:if test="${siteProperty.value ne 'dark' && siteProperty.value ne 'auto' && siteProperty.value ne 'user'}"> selected</c:if>>Light only</option>
                <option value="dark"<c:if test="${siteProperty.value eq 'dark'}"> selected</c:if>>Dark only</option>
                <option value="auto"<c:if test="${siteProperty.value eq 'auto'}"> selected</c:if>>Match visitor's device</option>
                <option value="user"<c:if test="${siteProperty.value eq 'user'}"> selected</c:if>>Match device, let visitor choose</option>
              </select>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.menu.location'}">
              <select name="${siteProperty.name}">
                <option value="center"<c:if test="${siteProperty.value eq 'center'}"> selected</c:if>>Centered</option>
                <option value="left"<c:if test="${siteProperty.value eq 'left'}"> selected</c:if>>Left Justified</option>
                <option value="right"<c:if test="${siteProperty.value eq 'right'}"> selected</c:if>>Right Justified</option>
                <option value="pro"<c:if test="${siteProperty.value eq 'pro'}"> selected</c:if>>Expanded</option>
                <option value="custom"<c:if test="${siteProperty.value eq 'custom'}"> selected</c:if>>Custom XML</option>
                <option value="none"<c:if test="${siteProperty.value eq 'none'}"> selected</c:if>>None</option>
              </select>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.footer.style'}">
              <select name="${siteProperty.name}">
                <option value="default"<c:if test="${siteProperty.value eq 'default'}"> selected</c:if>>Basic</option>
                <option value="custom"<c:if test="${siteProperty.value eq 'custom'}"> selected</c:if>>Custom XML</option>
                <option value="none"<c:if test="${siteProperty.value eq 'none'}"> selected</c:if>>None</option>
              </select>
            </c:when>
            <c:when test="${siteProperty.type eq 'font'}">
              <select name="${siteProperty.name}">
                <option value=""<c:if test="${siteProperty.value eq ''}"> selected</c:if>>Default (Use CSS)</option>
                <option value="abel"<c:if test="${siteProperty.value eq 'abel'}"> selected</c:if>>Abel</option>
                <option value="bakbak-one"<c:if test="${siteProperty.value eq 'bakbak-one'}"> selected</c:if>>Bakbak One</option>
                <option value="inter"<c:if test="${siteProperty.value eq 'inter'}"> selected</c:if>>Inter</option>
                <option value="lato"<c:if test="${siteProperty.value eq 'lato'}"> selected</c:if>>Lato</option>
                <option value="libre-baskerville"<c:if test="${siteProperty.value eq 'libre-baskerville'}"> selected</c:if>>Libre Baskerville</option>
                <option value="muli"<c:if test="${siteProperty.value eq 'muli'}"> selected</c:if>>Muli</option>
                <option value="open-sans"<c:if test="${siteProperty.value eq 'open-sans'}"> selected</c:if>>Open Sans</option>
                <option value="oswald"<c:if test="${siteProperty.value eq 'oswald'}"> selected</c:if>>Oswald</option>
                <option value="oxygen"<c:if test="${siteProperty.value eq 'oxygen'}"> selected</c:if>>Oxygen</option>
                <option value="poppins"<c:if test="${siteProperty.value eq 'poppins'}"> selected</c:if>>Poppins</option>
                <option value="questrial"<c:if test="${siteProperty.value eq 'questrial'}"> selected</c:if>>Questrial</option>
                <option value="rubik"<c:if test="${siteProperty.value eq 'rubik'}"> selected</c:if>>Rubik</option>
                <option value="source-sans-pro"<c:if test="${siteProperty.value eq 'source-sans-pro'}"> selected</c:if>>Source Sans Pro</option>
              </select> <a href="https://fonts.google.com" target="_blank" rel="noreferrer"><i class="fa fa-external-link-square"></i></a>
            </c:when>
            <c:when test="${siteProperty.type eq 'color'}">
              <input id="${siteProperty.name}" type="text" name="${siteProperty.name}" value="<c:out value="${siteProperty.value}"/>">
            </c:when>
            <c:when test="${siteProperty.type eq 'url'}">
              <div class="input-group">
                <span class="input-group-label"><i class="fa fa-link"></i></span>
                <input class="input-group-field" id="${siteProperty.id}" type="text" name="${siteProperty.name}" placeholder="http://..." value="<c:out value="${siteProperty.value}"/>"<c:if test="${siteProperty.name eq 'elearning.lrs.url'}"> aria-describedby="elearningLrsUrlHelpText"</c:if><c:if test="${siteProperty.name eq 'elearning.moodle.url'}"> aria-describedby="elearningMoodleUrlHelpText"</c:if><c:if test="${siteProperty.name eq 'elearning.perls.url'}"> aria-describedby="elearningPerlsUrlHelpText"</c:if><c:if test="${siteProperty.name eq 'bi.superset.url'}"> aria-describedby="biSupersetUrlHelpText"</c:if><c:if test="${siteProperty.name eq 'bi.metabase.url'}"> aria-describedby="biMetabaseUrlHelpText"</c:if>>
              </div>
            </c:when>
            <c:when test="${siteProperty.type eq 'image'}">
              <div class="grid-x grid-margin-x">
                <div class="small-8 cell">
                  <div class="input-group">
                    <input class="input-group-field" type="text" placeholder="Local Image URL" id="imageUrl${siteProperty.id}" name="${siteProperty.name}" value="<c:out value="${siteProperty.value}"/>">
                    <span class="input-group-label" style="padding: 0;"><a class="button small primary expanded no-gap" data-open="imageBrowserReveal" onclick="SetPhotoId(${siteProperty.id});">Browse Images</a></span>
                  </div>
                  <label for="imageFile${siteProperty.id}" class="button">Upload Image File...</label>
                  <input type="file" id="imageFile${siteProperty.id}" class="show-for-sr" onchange="SavePhoto(this,${siteProperty.id})">
                </div>
                <div class="small-4 cell">
                  <img id="imageUrlPreview${siteProperty.id}" src="<c:out value="${siteProperty.value}"/>" style="max-height: 150px; max-width: 150px"/>
                </div>
              </div>
            </c:when>
            <c:when test="${siteProperty.type eq 'boolean'}">
              <div class="switch large">
                <input class="switch-input" id="${siteProperty.name}-yes-no" type="checkbox" name="${siteProperty.name}" value="true"<c:if test="${siteProperty.value eq 'true'}"> checked</c:if><c:if test="${siteProperty.name eq 'bi.enabled'}"> aria-describedby="biEnabledHelpText"</c:if><c:if test="${siteProperty.name eq 'bi.metabase.enabled'}"> aria-describedby="biMetabaseEnabledHelpText"</c:if><c:if test="${siteProperty.name eq 'mail.ssl'}"> aria-describedby="mailSslHelpText"</c:if>>
                <label class="switch-paddle" for="${siteProperty.name}-yes-no">
                <span class="switch-active" aria-hidden="true">Yes</span>
                <span class="switch-inactive" aria-hidden="true">No</span>
                </label>
              </div>
            </c:when>
            <c:when test="${siteProperty.name eq 'site.timezone'}">
              <select name="${siteProperty.name}">
                <c:forEach items="<%= TimeZone.getAvailableIDs() %>" var="timezone">
                  <option value="${timezone}"<c:if test="${siteProperty.value eq timezone}"> selected</c:if>><c:out value="${timezone}" /></option>
                </c:forEach>
              </select>
            </c:when>
            <c:when test="${siteProperty.type eq 'disabled'}">
              <input type="text" class="no-gap" name="${siteProperty.name}" value="${html:toHtml(siteProperty.value)}" disabled />
            </c:when>
            <c:otherwise>
              <input type="text" class="no-gap" name="${siteProperty.name}" value="${html:toHtml(siteProperty.value)}"<c:if test="${siteProperty.name eq 'captcha.service'}"> aria-describedby="captchaServiceHelpText"</c:if><c:if test="${siteProperty.name eq 'captcha.google.sitekey'}"> aria-describedby="captchaGoogleSitekeyHelpText"</c:if><c:if test="${siteProperty.name eq 'bi.superset.id'}"> aria-describedby="biSupersetIdHelpText"</c:if><c:if test="${siteProperty.name eq 'mail.from_address'}"> aria-describedby="mailFromAddressHelpText"</c:if><c:if test="${siteProperty.name eq 'mail.from_name'}"> aria-describedby="mailFromNameHelpText"</c:if><c:if test="${siteProperty.name eq 'mail.host_name'}"> aria-describedby="mailHostNameHelpText"</c:if><c:if test="${siteProperty.name eq 'mail.port'}"> aria-describedby="mailPortHelpText"</c:if><c:if test="${siteProperty.name eq 'mail.username'}"> aria-describedby="mailUsernameHelpText"</c:if> />
            </c:otherwise>
          </c:choose>
          <c:if test="${siteProperty.name eq 'captcha.service'}">
            <p class="help-text" id="captchaServiceHelpText">Chooses which CAPTCHA challenge protects the site's public forms. The supported value is "google", which uses Google reCAPTCHA v2 with the Site Key and Secret Key below. Leave the Site Key blank to fall back to the platform's built-in text-image challenge instead.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'captcha.google.sitekey'}">
            <p class="help-text" id="captchaGoogleSitekeyHelpText">The public key that connects the site's forms to Google reCAPTCHA v2. It's sent to every visitor's browser, so it's safe to expose. To get one, sign in to the <a href="https://www.google.com/recaptcha/admin/" target="_blank" rel="noreferrer">Google reCAPTCHA admin console</a>, register the site, and choose reCAPTCHA v2, Invisible reCAPTCHA badge, since these forms render a button rather than a checkbox. Google issues a Site Key and Secret Key together. Example format: 6LfPTnQUAAAAALSynteQ3vrs5MxxFd9NaSPyitRj (40 characters, letters and numbers only). A value that looks much shorter, much longer, or contains spaces was likely copied incorrectly.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'captcha.google.secretkey'}">
            <p class="help-text" id="captchaGoogleSecretkeyHelpText">The private key the server uses to verify captcha responses with Google. Never share it or commit it to source control. Google generates it together with the Site Key above, on the same reCAPTCHA admin console page; it's a similar-length alphanumeric string. This value is stored encrypted and always appears blank here after saving. Leave it blank to keep the current key, or enter a new value to replace it.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'robots.ai.gptbot'}">
            <p class="help-text">OpenAI's crawler for collecting training data for GPT models. Turning this off opts out of GPT model training only -- OpenAI's separate live-citation crawler (OAI-SearchBot, below) and on-demand user fetcher (ChatGPT-User, below) are controlled independently.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'robots.ai.oai-searchbot'}">
            <p class="help-text">OpenAI's crawler for real-time ChatGPT search results and citations. Unlike GPTBot above, this doesn't train future models -- it fetches pages to help answer live user queries, so turning this off is closer to an "exclude from ChatGPT answers" opt-out than a training opt-out.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'robots.ai.chatgpt-user'}">
            <p class="help-text">OpenAI's on-demand fetcher, triggered when a specific ChatGPT user's query causes the assistant to visit this page directly (for example, via browsing or a GPT Action). Per OpenAI's own documentation, because these fetches are initiated by a live user, this crawler may not always honor this opt-out.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'robots.ai.claudebot'}">
            <p class="help-text">Anthropic's crawler for collecting training data for Claude models. Turning this off opts out of Claude model training only -- Anthropic's separate search-indexing crawler (Claude-SearchBot, below) and on-demand user fetcher (Claude-User, below) are controlled independently.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'robots.ai.claude-searchbot'}">
            <p class="help-text">Anthropic's crawler for indexing pages so Claude can find and cite them in real-time answers -- separate from ClaudeBot's model-training use above.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'robots.ai.claude-user'}">
            <p class="help-text">Anthropic's on-demand fetcher, triggered when a specific Claude user's query causes the assistant to visit this page directly. Per Anthropic's own documentation, because these fetches are initiated by a live user, this crawler may not always honor this opt-out.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'robots.ai.google-extended'}">
            <p class="help-text">Controls whether Google may use this site's content to train Gemini and other generative AI features. Distinct from Google's regular search crawler (Googlebot) -- turning this off does not remove the site from Google Search results, only from AI training use. Google states no separate crawler or markup governs appearing in AI Overviews/AI Mode; that rides on standard Search indexing.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'robots.ai.perplexitybot'}">
            <p class="help-text">Perplexity's crawler that discovers and indexes pages for its AI answer engine. Perplexity states this crawler is not used for AI model training, though that specific claim is less independently verifiable than the equivalent OpenAI/Anthropic training-crawler distinctions above.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'robots.ai.perplexity-user'}">
            <p class="help-text">Perplexity's on-demand fetcher, triggered when a specific user's query causes Perplexity to visit this page directly to help answer it. Per Perplexity's own documentation, this crawler generally does not honor robots.txt rules at all, so this opt-out is more of a stated preference than an enforceable block.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'robots.ai.ccbot'}">
            <p class="help-text">Common Crawl's general-purpose crawler. Its dataset is reused by many different research labs and companies to train a wide range of models, not just one -- turning this off is the broadest of these opt-outs, but doesn't name a specific model or company.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'elearning.lrs.url'}">
            <p class="help-text" id="elearningLrsUrlHelpText">This connects to a Learning Record Store (LRS) using xAPI, a learning-data standard created by the DoD's Advanced Distributed Learning (ADL) Initiative and encouraged for DoD systems under DoD Instruction 1322.26. ADL's own reference LRS (<a href="https://github.com/adlnet/ADL_LRS" target="_blank" rel="noreferrer">adlnet/ADL_LRS</a>) is now archived following the Initiative's 2025 shutdown. <a href="https://github.com/yetanalytics/lrsql" target="_blank" rel="noreferrer">Yet Analytics' SQL LRS</a> -- built by the first vendor to pass the DoD's full ADL LRS Test Suite -- is an actively maintained open-source alternative.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'elearning.moodle.url'}">
            <p class="help-text" id="elearningMoodleUrlHelpText">Moodle is the world's most widely used open-source learning management system, created in 1999 by Martin Dougiamas and first released in 2002 -- now with an estimated 200+ million users and still under active development (<a href="https://github.com/moodle/moodle" target="_blank" rel="noreferrer">moodle/moodle</a>). Other actively maintained open-source LMS options include Open edX, Canvas LMS, Sakai, and Chamilo, though Moodle remains the largest by installed base.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'elearning.perls.url'}">
            <p class="help-text" id="elearningPerlsUrlHelpText">PERLS (PERvasive Learning System) is a mobile, personalized microlearning app for informal and on-the-job training, developed and funded by the DoD's Advanced Distributed Learning (ADL) Initiative. Like ADL's LRS above, it's now archived following the Initiative's 2025 shutdown (<a href="https://github.com/adlnet/perls" target="_blank" rel="noreferrer">adlnet/perls</a>). Unlike LRS, no actively maintained open-source equivalent was found -- the closest comparisons are commercial microlearning platforms (e.g. Axonify, TalentCards), not open-source projects.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'bi.enabled'}">
            <p class="help-text" id="biEnabledHelpText">Turns on embedding dashboards from a separately hosted Apache Superset instance (this does not install or host Superset itself). There is currently no admin screen for placing a dashboard on a page -- a developer adds one by hand-editing that page's XML template with a <code>dashboardValue</code> (the Superset dashboard ID) and <code>dashboardEmbeddedId</code> (the embed ID Superset generates when embedding is enabled for that dashboard).</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'bi.superset.url'}">
            <p class="help-text" id="biSupersetUrlHelpText">The base URL of your organization's Superset instance, for example <code>https://superset.example.com</code>. That instance must have the <code>EMBEDDED_SUPERSET</code> feature flag enabled and CORS configured to allow this site's domain before embedding will work.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'bi.superset.id'}">
            <p class="help-text" id="biSupersetIdHelpText">Despite the label, this is not an API client ID -- it's the <strong>username</strong> of a Superset user account. Sent together with the Superset Secret below to log in to your Superset instance's API. Use a dedicated service account (with permission to read the dashboards you plan to embed and to request guest tokens) rather than a personal login.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'bi.superset.secret'}">
            <p class="help-text" id="biSupersetSecretHelpText">Despite the label, this is not a static API secret -- it's the <strong>password</strong> for the Superset account named above. This value is stored encrypted and always appears blank here after saving; leave it blank to keep the current password, or enter a new value to replace it.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'bi.metabase.enabled'}">
            <p class="help-text" id="biMetabaseEnabledHelpText">Turns on embedding dashboards from a separately hosted Metabase instance (this does not install or host Metabase itself), independent of the Superset setting above -- both can be enabled at once if you have dashboards in each. As with Superset, there is currently no admin screen for placing a dashboard on a page -- a developer adds one by hand-editing that page's XML template with a <code>dashboardValue</code> (the Metabase dashboard ID).</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'bi.metabase.url'}">
            <p class="help-text" id="biMetabaseUrlHelpText">The base URL of your organization's Metabase instance, for example <code>https://metabase.example.com</code>. Unlike Superset, no feature flag needs to be enabled on the Metabase side -- static embedding is available out of the box. In Metabase, go to Admin settings &gt; Embedding and turn on embedding, then Share &gt; Embed on each dashboard you want to make embeddable here.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'bi.metabase.secret'}">
            <p class="help-text" id="biMetabaseSecretHelpText">The embedding secret key from Metabase's Admin settings &gt; Embedding page. Unlike Superset, this is a single shared key (not a username/password pair) used to sign embed requests directly -- anyone with this value can view any dashboard you've published for embedding, so treat it like a password. This value is stored encrypted and always appears blank here after saving; leave it blank to keep the current key, or enter a new value to replace it.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mail.from_address'}">
            <p class="help-text" id="mailFromAddressHelpText">The address that appears in the "From" field of every email SimIS sends -- form submissions, newsletters, and other notifications all go out from here. Must be an address your mail server or provider is actually authorized to send as; most providers (Microsoft 365, SendGrid, etc.) reject or bounce messages from an unrecognized From address. Example: noreply@yourdomain.com.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mail.from_name'}">
            <p class="help-text" id="mailFromNameHelpText">The display name shown next to the From address above, so recipients see something like "Your Organization &lt;noreply@yourdomain.com&gt;" instead of the bare address. Leave blank to send with only the address and no display name.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mail.host_name'}">
            <p class="help-text" id="mailHostNameHelpText">The hostname of the SMTP server SimIS connects to for sending mail. Get this from whoever manages your organization's email. Common examples: <a href="https://support.google.com/a/answer/176600" target="_blank" rel="noreferrer">smtp.gmail.com for Google Workspace/Gmail</a>, <a href="https://docs.sendgrid.com/for-developers/sending-email/getting-started-smtp" target="_blank" rel="noreferrer">smtp.sendgrid.net for SendGrid</a>, and <a href="https://learn.microsoft.com/en-us/exchange/mail-flow-best-practices/how-to-set-up-a-multifunction-device-or-application-to-send-email-using-microsoft-365-or-office-365" target="_blank" rel="noreferrer">smtp.office365.com for Microsoft 365</a> -- but see the SSL note below before assuming any of these will connect, since not every provider supports the encryption method this platform uses.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mail.port'}">
            <p class="help-text" id="mailPortHelpText">The TCP port SimIS connects to on the SMTP server above. There's no universal default -- it depends on your provider and the SSL toggle below. Common values are 25 or 587 for a plain (unencrypted) connection, and 465 for implicit SSL/TLS paired with the SSL toggle turned on. Check your provider's SMTP documentation for the exact port to use.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mail.username'}">
            <p class="help-text" id="mailUsernameHelpText">The username SimIS authenticates with on the SMTP server above -- typically a full mailbox address (e.g. your Gmail address) or an API key ID (SendGrid uses the literal username "apikey"). Leave this and the password below blank if your server accepts unauthenticated connections, which is uncommon outside a local or internal mail relay.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mail.password'}">
            <p class="help-text" id="mailPasswordHelpText">The password or API key SimIS authenticates with, paired with the username above -- for SendGrid this is your API key, not your account password. This value is stored encrypted and always appears blank here after saving; leave it blank to keep the current password, or enter a new value to replace it.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mail.ssl'}">
            <p class="help-text" id="mailSslHelpText">When on, SimIS connects to the SMTP server above using implicit SSL/TLS -- the connection is encrypted from the moment it opens, traditionally on port 465. When off, the connection starts unencrypted. This platform does not support STARTTLS (the scheme where a connection starts in plain text, commonly on port 587, and is only upgraded to encryption afterward), which some providers require and offer no alternative to. Per each provider's own documentation: Gmail's smtp.gmail.com supports both port 465 (SSL) and port 587 (STARTTLS), so port 465 with this toggle on works. SendGrid's current setup guide documents only STARTTLS on port 587, with no implicit-SSL port listed. Microsoft 365 explicitly documents that its client SMTP submission does not support port 465 at all and requires STARTTLS on port 587 -- which this platform cannot do, so Microsoft 365 cannot be used here. When unsure, ask your provider whether they offer an explicit "SSL" or "implicit TLS" port as an alternative to their default STARTTLS port.</p>
          </c:if>
        </td>
      </tr>
    </c:forEach>
    </tbody>
  </table>
  <c:if test="${prefix eq 'captcha'}">
    <p class="help-text">reCAPTCHA v2 protects public forms across the site (for example, the contact form, account registration, newsletter signup, and job/business listings) wherever that form has captcha enabled. Changes take effect immediately on next page load.</p>
    <p><a href="${ctx}/contact-us" target="_blank" class="button radius secondary">Test CAPTCHA</a></p>
  </c:if>
  <c:if test="${prefix eq 'robots'}">
    <p class="help-text">Controls what <a href="${ctx}/robots.txt" target="_blank" rel="noreferrer">/robots.txt</a> tells web crawlers. Admin pages are always excluded regardless of these settings. Each toggle below opts a specific AI crawler out of reading this site -- on by default, matching how the site behaved before these controls existed. A crawler being "off" here is a request, not an enforcement mechanism: well-behaved crawlers honor robots.txt, but nothing stops a crawler from ignoring it.</p>
  </c:if>
  <c:if test="${prefix eq 'mail'}">
    <p class="help-text">If emails aren't sending, these settings are usually the first place to check -- especially the host, port, username/password, and SSL toggle above. Form submissions, newsletters, and every other outgoing email all go through this same configuration, so a mistake here is site-wide. After making a change, use the Mail Test panel to send yourself a confirmation email before relying on it for real traffic.</p>
  </c:if>
  <div class="button-container">
    <input type="submit" class="button radius success" value="Save" />
    <a href="${ctx}/admin" class="button radius secondary">Cancel</a>
  </div>
</form>
<div class="reveal large" id="imageBrowserReveal" data-reveal data-animation-in="slide-in-down fast" role="dialog" aria-modal="true" aria-label="Image Browser">
  <h3>Loading...</h3>
</div>
<script nonce="${cspNonce}">
  <%-- Map the variable property to the mapped CSS classes --%>
  var colorIdList = [];
  var colorSelectorList = [];
  <c:forEach items="${sitePropertyList}" var="siteProperty">
  <c:if test="${siteProperty.type eq 'color'}">
  colorIdList.push('${siteProperty.name}');
  <c:choose>
  <c:when test="${siteProperty.name eq 'theme.body.text.color'}">colorSelectorList.push('body');</c:when>
  <c:when test="${siteProperty.name eq 'theme.body.backgroundColor'}">colorSelectorList.push('body');</c:when>
  <c:when test="${siteProperty.name eq 'theme.utilitybar.text.color'}">colorSelectorList.push('#platform-menu .utility-bar');</c:when>
  <c:when test="${siteProperty.name eq 'theme.utilitybar.link.color'}">colorSelectorList.push('#platform-menu .utility-bar a');</c:when>
  <c:when test="${siteProperty.name eq 'theme.utilitybar.backgroundColor'}">colorSelectorList.push('#platform-menu .utility-bar');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.text.color'}">colorSelectorList.push('#platform-menu,#platform-menu .menu-text,#platform-menu .menu-text a,#platform-menu .menu-text a:hover');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.backgroundColor'}">colorSelectorList.push('#platform-menu,#platform-small-menu,#platform-small-menu .title-bar');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.text.color'}">colorSelectorList.push('#platform-menu ul.menu li a');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.arrow.color'}">colorSelectorList.push('.dropdown.menu>li.is-dropdown-submenu-parent>a::after');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.text.hoverBackgroundColor'}">colorSelectorList.push('#platform-menu ul.menu li a:hover,#platform-menu .is-active');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.hoverTextColor'}">colorSelectorList.push('#platform-menu ul.menu li > a:hover,#platform-menu ul.menu li.is-active > a,#platform-menu .is-active .is-dropdown-submenu-item a:hover');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.dropdown.backgroundColor'}">colorSelectorList.push('#platform-menu ul.is-dropdown-submenu li.is-dropdown-submenu-item');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.dropdown.text.color'}">colorSelectorList.push('#platform-menu ul.is-dropdown-submenu li.is-dropdown-submenu-item a');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.text.color'}">colorSelectorList.push('.button');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.default.backgroundColor'}">colorSelectorList.push('.button.base');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.default.hoverBackgroundColor'}">colorSelectorList.push('.button.base:hover, .button.base:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.primary.backgroundColor'}">colorSelectorList.push('.button.primary');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.primary.hoverBackgroundColor'}">colorSelectorList.push('.button.primary:hover, .button.primary:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.secondary.backgroundColor'}">colorSelectorList.push('.button.secondary');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.secondary.hoverBackgroundColor'}">colorSelectorList.push('.button.secondary:hover, .button.secondary:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.success.backgroundColor'}">colorSelectorList.push('.button.success');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.success.hoverBackgroundColor'}">colorSelectorList.push('.button.success:hover, .button.success:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.warning.backgroundColor'}">colorSelectorList.push('.button.warning');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.warning.hoverBackgroundColor'}">colorSelectorList.push('.button.warning:hover, .button.warning:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.alert.backgroundColor'}">colorSelectorList.push('.button.alert');</c:when>
  <c:when test="${siteProperty.name eq 'theme.button.alert.hoverBackgroundColor'}">colorSelectorList.push('.button.alert:hover, .button.alert:focus');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.backgroundColor'}">colorSelectorList.push('.callout.base');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.text.color'}">colorSelectorList.push('.callout.base,.callout.base label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.primary.backgroundColor'}">colorSelectorList.push('.callout.primary');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.primary.text.color'}">colorSelectorList.push('.callout.primary,.callout.primary label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.secondary.backgroundColor'}">colorSelectorList.push('.callout.secondary');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.secondary.text.color'}">colorSelectorList.push('.callout.secondary,.callout.secondary label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.success.backgroundColor'}">colorSelectorList.push('.callout.success');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.success.text.color'}">colorSelectorList.push('.callout.success,.callout.success label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.warning.backgroundColor'}">colorSelectorList.push('.callout.warning');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.warning.text.color'}">colorSelectorList.push('.callout.warning,.callout.warning label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.alert.backgroundColor'}">colorSelectorList.push('.callout.alert');</c:when>
  <c:when test="${siteProperty.name eq 'theme.callout.alert.text.color'}">colorSelectorList.push('.callout.alert,.callout.alert label');</c:when>
  <c:when test="${siteProperty.name eq 'theme.footer.backgroundColor'}">colorSelectorList.push('.platform-footer');</c:when>
  <c:when test="${siteProperty.name eq 'theme.footer.text.color'}">colorSelectorList.push('.platform-footer');</c:when>
  <c:when test="${siteProperty.name eq 'theme.footer.links.color'}">colorSelectorList.push('.platform-footer a');</c:when>
  <c:otherwise>colorSelectorList.push('');</c:otherwise>
  </c:choose>
  </c:if>
  </c:forEach>

  function changeColor(targetId, color) {
    var idx = colorIdList.indexOf(targetId);
    var colorSelector = colorSelectorList[idx];
    if (colorSelector.length === 0) {
      return;
    }
    // Handle dynamic elements
    if (targetId.indexOf('theme.topbar.menu.dropdown.text.color') > -1) {
      $("head").append('<style>' + colorSelector + '{color: ' + color.toHexString() + '}</style>');
      return;
    } else if (targetId.indexOf('theme.topbar.menu.hoverTextColor') > -1) {
      $("head").append('<style>' + colorSelector + '{color: ' + color.toHexString() + '}</style>');
      return;
    } else if (targetId.indexOf('theme.topbar.menu.text.hoverBackgroundColor') > -1) {
      $("head").append('<style>' + colorSelector + '{background-color: ' + color.toHexString() + '}</style>');
      return;
    }
    // Adjust static elements
    var list = document.querySelectorAll(colorSelector);
    for (var i = 0; i < list.length; i++) {
      if (targetId.indexOf('theme.topbar.menu.arrow.color') > -1) {
        list[i].style.borderColor = color.toHexString() + ' transparent transparent';
      } else if (targetId.indexOf('ackgroundColor') > -1) {
        list[i].style.backgroundColor = color.toHexString();
      } else {
        list[i].style.color = color.toHexString();
      }
    }
  }

  $(document).ready(function() {
    for (var i = 0; i < colorIdList.length; i++) {
      var target = document.getElementById(colorIdList[i]);
      $("[id='" + colorIdList[i] + "']").spectrum({
        color: target.value,
        flat: false,
        preferredFormat: "hex",
        chooseText: "Choose",
        cancelText: "Cancel",
        showPalette: true,
        palette: [
          ["#000","#444","#666","#999","#ccc","#eee","#f3f3f3","#fff"],
          ["#f00","#f90","#ff0","#0f0","#0ff","#00f","#90f","#f0f"],
          ["#f4cccc","#fce5cd","#fff2cc","#d9ead3","#d0e0e3","#cfe2f3","#d9d2e9","#ead1dc"],
          ["#ea9999","#f9cb9c","#ffe599","#b6d7a8","#a2c4c9","#9fc5e8","#b4a7d6","#d5a6bd"],
          ["#e06666","#f6b26b","#ffd966","#93c47d","#76a5af","#6fa8dc","#8e7cc3","#c27ba0"],
          ["#c00","#e69138","#f1c232","#6aa84f","#45818e","#3d85c6","#674ea7","#a64d79"],
          ["#900","#b45f06","#bf9000","#38761d","#134f5c","#0b5394","#351c75","#741b47"],
          ["#600","#783f04","#7f6000","#274e13","#0c343d","#073763","#20124d","#4c1130"]
        ],
        showSelectionPalette: true,
        localStorageKey: "site.properties",
        showInput: true,
        showInitial: true,
        showAlpha: false,
        move: function(color) {
          var targetId = $(this).attr('id');
          changeColor(targetId, color);
        },
        hide: function(color) {
          var targetId = $(this).attr('id');
          changeColor(targetId, color);
        },
        allowEmpty:false
      });
    }
  });
</script>
<script nonce="${cspNonce}">
  $('#imageBrowserReveal').on('open.zf.reveal', function () {
    $('#imageBrowserReveal').html("<h3>Loading...</h3>");
    $.ajax({
      url: '${ctx}/image-browser?inputId=imageUrl' + currentPhotoId + '&view=reveal',
      cache: false,
      dataType: 'html'
    }).done(function (content) {
      setTimeout(function () {
        $('#imageBrowserReveal').html(content);
        $('#imageBrowserReveal').trigger('resizeme.zf.trigger');
      }, 1000);
    });
  })
</script>
