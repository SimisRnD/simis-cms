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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="image" uri="/WEB-INF/tlds/image-functions.tld" %>
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
          // Report what the server actually rejected (issue #1189). This used to always claim the
          // file was not a .jpg or .png, which is wrong -- and actively misleading -- for a
          // permission, storage, or size failure, all of which land here too.
          var message = 'There was an error with the file. Make sure to use a .jpg or .png';
          try {
            var errorData = JSON.parse(this.responseText);
            if (errorData && errorData.error) {
              message = errorData.error;
            }
          } catch (ignored) {
            // Not a JSON body (e.g. an HTML error page), so keep the generic message
          }
          alert(message);
        }
      }
    };
    xhr.open("POST", '${ctx}/image-upload?widget=imageUpload1&token=${userSession.formToken}');
    xhr.send(formData);
  }

  // Bind here rather than with onclick=/onchange= attributes (issue #1188). PageServlet sends
  // Content-Security-Policy with script-src 'self' 'nonce-...' and no 'unsafe-inline', which makes
  // the browser refuse to run inline event handlers -- they fall under script-src-attr, and a
  // nonce does not cover them. The nonce authorises THIS block to define SavePhoto; an
  // onchange="SavePhoto(...)" attribute calling it was silently blocked, so choosing a file did
  // nothing at all and no error surfaced anywhere in the UI.
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-photo-id]').forEach(function (el) {
      el.addEventListener('click', function () {
        SetPhotoId(el.getAttribute('data-photo-id'));
      });
    });
    document.querySelectorAll('[data-photo-upload]').forEach(function (el) {
      el.addEventListener('change', function () {
        SavePhoto(el, el.getAttribute('data-photo-upload'));
      });
    });
    // Logo-color card picker: clicking a card selects it (updates the group's hidden input +
    // the .selected/aria-pressed state on the cards) without submitting -- these rows live inside
    // this page's one big multi-property Save-button form alongside every other setting, so a
    // click here must not act like web-page-templates.jsp's similar card pattern, which submits
    // immediately on click (fine for a single-purpose page, wrong here).
    document.querySelectorAll('.logo-color-picker').forEach(function (group) {
      var hiddenInput = document.getElementById(group.getAttribute('data-logo-color-target'));
      group.querySelectorAll('.logo-color-card').forEach(function (card) {
        card.addEventListener('click', function () {
          hiddenInput.value = card.getAttribute('data-logo-color-value');
          group.querySelectorAll('.logo-color-card').forEach(function (sibling) {
            sibling.classList.remove('selected');
            sibling.setAttribute('aria-pressed', 'false');
          });
          card.classList.add('selected');
          card.setAttribute('aria-pressed', 'true');
        });
      });
    });
  });
</script>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%-- issue #1268: this JSP is shared by 18 different settings pages via the prefix preference --
     gate on the exact branding-related prefixes so this callout only appears on the 4 pages it's
     actually relevant to, not on unrelated pages like Mail or Captcha Settings. --%>
<c:if test="${prefix eq 'site' || prefix eq 'theme' || prefix eq 'social' || fn:startsWith(prefix, 'site.header')}">
  <div class="callout secondary radius" style="margin-bottom:1rem">
    <strong>Related settings:</strong>
    <ul class="menu" style="display:inline-block;margin-left:0.5rem">
      <c:if test="${prefix ne 'site'}"><li><a href="${ctx}/admin/site-properties">Site Settings</a></li></c:if>
      <c:if test="${prefix ne 'theme'}"><li><a href="${ctx}/admin/theme-properties">Theme Settings</a></li></c:if>
      <c:if test="${!fn:startsWith(prefix, 'site.header')}"><li><a href="${ctx}/admin/site-header-properties">Utility Bar Settings</a></li></c:if>
      <c:if test="${prefix ne 'social'}"><li><a href="${ctx}/admin/social-media-settings">Social Media Settings</a></li></c:if>
      <li><a href="${ctx}/admin/useful-links">Useful Links</a></li>
      <li><a href="${ctx}/admin/sticky-footer-links">Sticky Footer Links</a></li>
    </ul>
  </div>
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
                  <input type="password" class="no-gap" name="${siteProperty.name}" value="" autocomplete="new-password" placeholder="<c:out value="${empty siteProperty.value ? 'not set' : 'value hidden; leave blank to keep it'}"/>"
                      <c:if test="${siteProperty.name eq 'captcha.google.secretkey'}"> aria-describedby="captchaGoogleSecretkeyHelpText"</c:if>
                      <c:if test="${siteProperty.name eq 'captcha.turnstile.secretkey'}"> aria-describedby="captchaTurnstileSecretkeyHelpText"</c:if>
                      <c:if test="${siteProperty.name eq 'bi.superset.secret'}"> aria-describedby="biSupersetSecretHelpText"</c:if>
                      <c:if test="${siteProperty.name eq 'bi.metabase.secret'}"> aria-describedby="biMetabaseSecretHelpText"</c:if>
                      <c:if test="${siteProperty.name eq 'mail.password'}"> aria-describedby="mailPasswordHelpText"</c:if>
                      <c:if test="${siteProperty.name eq 'social.instagram.accessToken'}"> aria-describedby="socialInstagramAccessTokenHelpText"</c:if>
                      />
                  <%-- issue #454: optional expiry, so a credential that's known to expire (e.g. an
                       OAuth token) shows up on the /admin/integrations hub before it lapses --%>
                  <label class="no-gap"><small>Expires (optional)</small>
                    <input type="date" class="no-gap" name="${siteProperty.name}__expiresAt" value="<c:if test="${!empty siteProperty.expiresAt}"><fmt:formatDate pattern="yyyy-MM-dd" value="${siteProperty.expiresAt}" /></c:if>" />
                  </label>
                </c:otherwise>
              </c:choose>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.logo.color'}">
              <%@include file="logo-color-picker.jspf" %>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.logo.color.dark'}">
              <%@include file="logo-color-picker.jspf" %>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.footer.logo.color'}">
              <%@include file="logo-color-picker.jspf" %>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.footer.logo.color.dark'}">
              <%@include file="logo-color-picker.jspf" %>
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
              <select name="${siteProperty.name}" aria-describedby="themeFooterStyleHelpText">
                <option value="default"<c:if test="${siteProperty.value eq 'default'}"> selected</c:if>>Basic</option>
                <option value="custom"<c:if test="${siteProperty.value eq 'custom'}"> selected</c:if>>Custom XML</option>
                <option value="none"<c:if test="${siteProperty.value eq 'none'}"> selected</c:if>>None</option>
              </select>
            </c:when>
            <c:when test="${siteProperty.name eq 'theme.footer.layout'}">
              <select name="${siteProperty.name}" aria-describedby="themeFooterLayoutHelpText">
                <option value="footer.default"<c:if test="${siteProperty.value ne 'footer.4column'}"> selected</c:if>>Default Footer</option>
                <option value="footer.4column"<c:if test="${siteProperty.value eq 'footer.4column'}"> selected</c:if>>4-Column Footer</option>
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
              <input id="${siteProperty.name}" type="text" name="${siteProperty.name}" value="<c:out value="${siteProperty.value}"/>"
                  <c:if test="${siteProperty.name eq 'site.newsletter.color'}"> aria-describedby="siteNewsletterColorHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'site.newsletter.backgroundColor'}"> aria-describedby="siteNewsletterBackgroundColorHelpText"</c:if>
                  >
            </c:when>
            <c:when test="${siteProperty.type eq 'url'}">
              <div class="input-group">
                <span class="input-group-label"><i class="fa fa-link"></i></span>
                <input class="input-group-field" id="${siteProperty.id}" type="text" name="${siteProperty.name}" placeholder="http://..." value="<c:out value="${siteProperty.value}"/>"
                    <c:if test="${siteProperty.name eq 'elearning.lrs.url'}"> aria-describedby="elearningLrsUrlHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'elearning.moodle.url'}"> aria-describedby="elearningMoodleUrlHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'elearning.perls.url'}"> aria-describedby="elearningPerlsUrlHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'bi.superset.url'}"> aria-describedby="biSupersetUrlHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'bi.metabase.url'}"> aria-describedby="biMetabaseUrlHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'site.url'}"> aria-describedby="siteUrlHelpText"</c:if>
                    >
              </div>
            </c:when>
            <c:when test="${siteProperty.type eq 'image'}">
              <div class="grid-x grid-margin-x">
                <div class="small-8 cell">
                  <div class="input-group">
                    <input class="input-group-field" type="text" placeholder="Local Image URL" id="imageUrl${siteProperty.id}" name="${siteProperty.name}" value="<c:out value="${siteProperty.value}"/>"
                        <c:if test="${siteProperty.name eq 'site.image'}"> aria-describedby="siteImageHelpText"</c:if>
                        <c:if test="${siteProperty.name eq 'site.logo'}"> aria-describedby="siteLogoHelpText"</c:if>
                        <c:if test="${siteProperty.name eq 'site.logo.white'}"> aria-describedby="siteLogoWhiteHelpText"</c:if>
                        <c:if test="${siteProperty.name eq 'site.logo.mixed'}"> aria-describedby="siteLogoMixedHelpText"</c:if>
                        >
                    <span class="input-group-label" style="padding: 0;"><a class="button small primary expanded no-gap" data-open="imageBrowserReveal" data-photo-id="${siteProperty.id}">Browse Images</a></span>
                  </div>
                  <label for="imageFile${siteProperty.id}" class="button">Upload Image File...</label>
                  <input type="file" id="imageFile${siteProperty.id}" class="show-for-sr" data-photo-upload="${siteProperty.id}">
                </div>
                <div class="small-4 cell">
                  <c:set var="previewSrcset" value="${image:srcset(siteProperty.value)}"/>
                  <img id="imageUrlPreview${siteProperty.id}" src="<c:out value="${siteProperty.value}"/>" style="max-height: 150px; max-width: 150px"
                    <c:if test="${not empty previewSrcset}"> srcset="<c:out value="${previewSrcset}"/>" sizes="150px"</c:if>
                    loading="lazy" decoding="async"/>
                </div>
              </div>
            </c:when>
            <c:when test="${siteProperty.type eq 'boolean'}">
              <div class="switch large">
                <input class="switch-input" id="${siteProperty.name}-yes-no" type="checkbox" name="${siteProperty.name}" value="true"
                    <c:if test="${siteProperty.value eq 'true'}"> checked</c:if>
                    <c:if test="${siteProperty.name eq 'bi.enabled'}"> aria-describedby="biEnabledHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'bi.metabase.enabled'}"> aria-describedby="biMetabaseEnabledHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'mail.ssl'}"> aria-describedby="mailSslHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'site.online'}"> aria-describedby="siteOnlineHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'site.api'}"> aria-describedby="siteApiHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'site.sitemap.xml'}"> aria-describedby="siteSitemapXmlHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'site.cart'}"> aria-describedby="siteCartHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'site.registrations'}"> aria-describedby="siteRegistrationsHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'site.login'}"> aria-describedby="siteLoginHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'site.confirmation'}"> aria-describedby="siteConfirmationHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'site.newsletter.overlay'}"> aria-describedby="siteNewsletterOverlayHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'llms.enabled'}"> aria-describedby="llmsEnabledHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'features.layout-editor'}"> aria-describedby="featuresLayoutEditorHelpText"</c:if>
                    <c:if test="${siteProperty.name eq 'features.item-tags-facet-search'}"> aria-describedby="featuresItemTagsFacetSearchHelpText"</c:if>
                    >
                <label class="switch-paddle" for="${siteProperty.name}-yes-no">
                <span class="switch-active" aria-hidden="true">Yes</span>
                <span class="switch-inactive" aria-hidden="true">No</span>
                </label>
              </div>
            </c:when>
            <c:when test="${siteProperty.name eq 'site.timezone'}">
              <select name="${siteProperty.name}" aria-describedby="siteTimezoneHelpText">
                <c:forEach items="<%= TimeZone.getAvailableIDs() %>" var="timezone">
                  <option value="${timezone}"<c:if test="${siteProperty.value eq timezone}"> selected</c:if>><c:out value="${timezone}" /></option>
                </c:forEach>
              </select>
            </c:when>
            <c:when test="${siteProperty.type eq 'disabled'}">
              <input type="text" class="no-gap" name="${siteProperty.name}" value="${html:toHtml(siteProperty.value)}" disabled />
            </c:when>
            <c:otherwise>
              <input type="text" class="no-gap" name="${siteProperty.name}" value="${html:toHtml(siteProperty.value)}"
                  <c:if test="${siteProperty.name eq 'analytics.service'}"> aria-describedby="analyticsServiceHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'analytics.google.key'}"> aria-describedby="analyticsGoogleKeyHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'analytics.google.tagmanager'}"> aria-describedby="analyticsGoogleTagmanagerHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'analytics.simplifi.value'}"> aria-describedby="analyticsSimplifiValueHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'analytics.brandcdn.value'}"> aria-describedby="analyticsBrandcdnValueHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'analytics.brandcdn.value2'}"> aria-describedby="analyticsBrandcdnValueHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'analytics.retentionDays'}"> aria-describedby="analyticsRetentionDaysHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'captcha.service'}"> aria-describedby="captchaServiceHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'captcha.google.sitekey'}"> aria-describedby="captchaGoogleSitekeyHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'captcha.turnstile.sitekey'}"> aria-describedby="captchaTurnstileSitekeyHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'bi.superset.id'}"> aria-describedby="biSupersetIdHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'mail.from_address'}"> aria-describedby="mailFromAddressHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'mail.from_name'}"> aria-describedby="mailFromNameHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'mail.host_name'}"> aria-describedby="mailHostNameHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'mail.port'}"> aria-describedby="mailPortHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'mail.username'}"> aria-describedby="mailUsernameHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'site.name'}"> aria-describedby="siteNameHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'site.name.keyword'}"> aria-describedby="siteNameKeywordHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'site.description'}"> aria-describedby="siteDescriptionHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'site.keywords'}"> aria-describedby="siteKeywordsHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'site.confirmation.line1'}"> aria-describedby="siteConfirmationLine1HelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'site.confirmation.line2'}"> aria-describedby="siteConfirmationLine2HelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'site.confirmation.declined.text'}"> aria-describedby="siteConfirmationDeclinedTextHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'site.newsletter.headline'}"> aria-describedby="siteNewsletterHeadlineHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'site.newsletter.message'}"> aria-describedby="siteNewsletterMessageHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'llms.description'}"> aria-describedby="llmsDescriptionHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'analytics.cookieless'}"> aria-describedby="analyticsCookielessHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'analytics.anonymizeIp'}"> aria-describedby="analyticsAnonymizeIpHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'analytics.honorDnt'}"> aria-describedby="analyticsHonorDntHelpText"</c:if>
                  <c:if test="${siteProperty.name eq 'analytics.consentRequired'}"> aria-describedby="analyticsConsentRequiredHelpText"</c:if>
                  />
            </c:otherwise>
          </c:choose>
          <c:if test="${siteProperty.name eq 'analytics.service'}">
            <p class="help-text" id="analyticsServiceHelpText">Chooses which analytics provider the GA Key below is sent to. The only supported value is "google" (Google Analytics/GA4) -- any other value, including blank, disables analytics tracking entirely. This is independent of the SimpliFi and Brand CDN tags further down, which load whenever their own value is set, regardless of what's entered here.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'analytics.google.key'}">
            <p class="help-text" id="analyticsGoogleKeyHelpText">The Measurement ID for a Google Analytics 4 property, formatted like G-XXXXXXXXXX. Find it in the Google Analytics admin console under Data Streams for the site's web stream. Only takes effect when Analytics service above is set to "google".</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'analytics.google.tagmanager'}">
            <p class="help-text" id="analyticsGoogleTagmanagerHelpText">A Google Tag Manager container ID, formatted like GTM-XXXXXXX. Optional -- only set this if the site manages its tags through GTM rather than (or in addition to) the GA Key above. Find it in the Google Tag Manager admin console.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'analytics.simplifi.value'}">
            <p class="help-text" id="analyticsSimplifiValueHelpText">A SimpliFi tag id, provided by SimpliFi when setting up a conversion-tracking campaign with them. Loads a SimpliFi tracking script on every public page whenever this is set, independent of the Analytics service setting above. Leave blank if the site isn't running a SimpliFi campaign.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'analytics.brandcdn.value'}">
            <p class="help-text" id="analyticsBrandcdnValueHelpText">Two path values that together form a Brand CDN autoscript tag URL (tag.brandcdn.com/autoscript/&lt;value&gt;/&lt;value 2&gt;), provided by Brand CDN when setting up tracking with them. Both fields must be set for the tag to load; leave both blank if the site isn't using Brand CDN.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'analytics.cookieless'}">
            <p class="help-text" id="analyticsCookielessHelpText">When on, the site's analytics avoid setting a visitor-tracking cookie -- useful for staying under jurisdictions' cookie-consent-banner requirements. This is independent of the consent and Do-Not-Track settings below; all four privacy controls can be combined.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'analytics.anonymizeIp'}">
            <p class="help-text" id="analyticsAnonymizeIpHelpText">When on, the visitor's IP address is truncated before analytics records it, so individual visitors can't be pinpointed by location. Independent of the retention window below, which controls how long records (anonymized or not) are kept at all.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'analytics.honorDnt'}">
            <p class="help-text" id="analyticsHonorDntHelpText">When on, a visitor's browser-level Do Not Track or Global Privacy Control signal suppresses analytics scripts on that visit entirely -- a stronger opt-out than the anonymization above, since no record is created at all. Off by default because DNT/GPC has no legal enforcement in most jurisdictions and many sites ignore it; turn this on if the site's privacy policy commits to honoring it.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'analytics.consentRequired'}">
            <p class="help-text" id="analyticsConsentRequiredHelpText">When on, visitors see an accept/decline banner and analytics scripts (and video embeds) only load after they accept. When off (the shipped default), analytics and video load immediately for everyone and the banner never appears -- there's no in-between "banner shown but analytics load anyway" state.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'analytics.retentionDays'}">
            <p class="help-text" id="analyticsRetentionDaysHelpText">Also used outside analytics: this same window governs how long <code>web_page_hits</code> rows are kept (the nightly Web Page Hits Cleanup job deletes hits older than this many days) in addition to controlling the visitor-PII scrub on the <a href="${ctx}/admin/analytics-retention">Analytics Retention</a> page. Changing it for one reason changes both. Accepted range is 1-3650 days; blank or non-numeric input falls back to 365, and an out-of-range number is silently clamped to 1 or 3650 rather than rejected -- double-check the saved value here after submitting.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'captcha.service'}">
            <p class="help-text" id="captchaServiceHelpText">Chooses which CAPTCHA challenge protects the site's public forms. Supported values are "google" (Google reCAPTCHA v2, using the Google Site Key and Secret Key below) and "turnstile" (Cloudflare Turnstile, using the Turnstile Site Key and Secret Key below). This is a single site-wide choice -- every form uses the same provider. Leave this blank, or leave the chosen provider's Site Key blank, to fall back to the platform's built-in text-image challenge instead.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'captcha.google.sitekey'}">
            <p class="help-text" id="captchaGoogleSitekeyHelpText">The public key that connects the site's forms to Google reCAPTCHA v2. It's sent to every visitor's browser, so it's safe to expose. To get one, sign in to the <a href="https://www.google.com/recaptcha/admin/" target="_blank" rel="noreferrer">Google reCAPTCHA admin console</a>, register the site, and choose reCAPTCHA v2, Invisible reCAPTCHA badge, since these forms render a button rather than a checkbox. Google issues a Site Key and Secret Key together. Example format: 6LfPTnQUAAAAALSynteQ3vrs5MxxFd9NaSPyitRj (40 characters, letters and numbers only). A value that looks much shorter, much longer, or contains spaces was likely copied incorrectly. Only takes effect when Captcha service above is set to "google".</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'captcha.turnstile.sitekey'}">
            <p class="help-text" id="captchaTurnstileSitekeyHelpText">The public key that connects the site's forms to <a href="https://developers.cloudflare.com/turnstile/" target="_blank" rel="noreferrer">Cloudflare Turnstile</a>, a free CAPTCHA alternative to Google reCAPTCHA. It's sent to every visitor's browser, so it's safe to expose. Unlike Google reCAPTCHA v3, Turnstile needs no score-threshold tuning -- it's a pass/fail challenge, matching how this site's reCAPTCHA v2 integration already behaves. To get one, sign in to the Cloudflare dashboard's Turnstile section, add a site, and choose the widget mode of your choice; Cloudflare issues a Site Key and Secret Key together. Only takes effect when Captcha service above is set to "turnstile".</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mailing-list.service'}">
            <p class="help-text" id="mailingListServiceHelpText">The only supported value today is "mailchimp" (case-insensitive). Any other value -- including the shipped default of "None", or a different service's name -- disables mailing-list sending entirely, the same as leaving this blank; nothing routes to a different provider based on what's typed here.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mailing-list.mailchimp.apiKey'}">
            <p class="help-text" id="mailingListMailchimpApiKeyHelpText">Your MailChimp account's API key, from MailChimp's Account &gt; Extras &gt; API keys page. Both this and the Audience/List Id below must be set for MailChimp sync to work. This value is stored encrypted and always appears blank here after saving; leave it blank to keep the current key, or enter a new value to replace it.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mailing-list.mailchimp.listId'}">
            <p class="help-text" id="mailingListMailchimpListIdHelpText">The Audience ID (MailChimp calls this a "List Id" in its older API docs) of the MailChimp audience new subscribers sync to. Find it in MailChimp under Audience &gt; Settings &gt; Audience name and defaults.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mailing-list.zerobounce.apiKey'}">
            <p class="help-text" id="mailingListZerobounceApiKeyHelpText">Optional. A <a href="https://www.zerobounce.net/" target="_blank" rel="noreferrer">ZeroBounce</a> API key, used to validate email addresses' deliverability (catching typos, disposable addresses, and spam traps) in the background -- a nightly job checks any email that's never been validated, in batches, and skips cleanly with no error if this is left blank. This value is stored encrypted and always appears blank here after saving; leave it blank to keep the current key, or enter a new value to replace it.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mailing-list.quarantine.alertThresholdPercent'}">
            <p class="help-text" id="mailingListQuarantineAlertThresholdPercentHelpText">When the percentage of mailing-list members with a poor deliverability status (from ZeroBounce validation above) exceeds this, the "Mailing List Spam Rate" tile on the Community dashboard turns red. Like the Security Settings alert tiles, this is a passive dashboard indicator only -- nothing emails or pages anyone. Default is 10%.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'mailing-list.confirmation.expiryDays'}">
            <p class="help-text" id="mailingListConfirmationExpiryDaysHelpText">How many days a double opt-in confirmation link stays valid before it expires. Only relevant for mailing lists with double opt-in enabled -- a subscriber who confirms after this window has passed needs to sign up again. Default is 7 days.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'theme.footer.style'}">
            <p class="help-text" id="themeFooterStyleHelpText">"Basic" shows the platform's built-in footer (custom text, privacy/terms links, controlled by the Site Settings page). "Custom XML" shows the footer chosen below under Footer layout, editable through the on-page footer editor. "None" hides the footer entirely.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'theme.logo.color'}">
            <p class="help-text" id="themeLogoColorHelpText">Which uploaded logo variant appears in the <strong>header</strong>. Upload the corresponding image(s) on the <a href="${ctx}/admin/site-properties">Site Settings</a> page -- Full color logo, All white logo, or Mixed color logo -- or this will show nothing. This is independent of Footer logo color below, which controls the footer only.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'theme.footer.logo.color'}">
            <p class="help-text" id="themeFooterLogoColorHelpText">Which uploaded logo variant appears in the <strong>footer</strong>. Upload the corresponding image(s) on the <a href="${ctx}/admin/site-properties">Site Settings</a> page. This is independent of Logo color above, which controls the header only.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'theme.logo.color.dark'}">
            <p class="help-text" id="themeLogoColorDarkHelpText">Which uploaded logo variant appears in the <strong>header</strong> when the visitor's color scheme is dark (see Color scheme above). Independent of Logo color above, which controls light mode. Left at its default ("All white"), this reproduces the logo's prior, non-configurable dark-mode behavior.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'theme.footer.logo.color.dark'}">
            <p class="help-text" id="themeFooterLogoColorDarkHelpText">Which uploaded logo variant appears in the <strong>footer</strong> when the visitor's color scheme is dark. Independent of Footer logo color above, which controls light mode.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'theme.footer.layout'}">
            <p class="help-text" id="themeFooterLayoutHelpText">Chooses which footer design is used. Only takes effect when Footer theme above is set to "Custom XML".</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'captcha.google.secretkey'}">
            <p class="help-text" id="captchaGoogleSecretkeyHelpText">The private key the server uses to verify captcha responses with Google. Never share it or commit it to source control. Google generates it together with the Site Key above, on the same reCAPTCHA admin console page; it's a similar-length alphanumeric string. This value is stored encrypted and always appears blank here after saving. Leave it blank to keep the current key, or enter a new value to replace it.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'captcha.turnstile.secretkey'}">
            <p class="help-text" id="captchaTurnstileSecretkeyHelpText">The private key the server uses to verify captcha responses with Cloudflare Turnstile. Never share it or commit it to source control. Cloudflare generates it together with the Site Key above, on the same Turnstile dashboard page. This value is stored encrypted and always appears blank here after saving. Leave it blank to keep the current key, or enter a new value to replace it.</p>
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
          <c:if test="${siteProperty.name eq 'security.rateLimit.ipMaxAttempts'}">
            <p class="help-text" id="securityRateLimitIpMaxAttemptsHelpText">The maximum number of form submissions, login attempts, or API calls allowed from a single IP address within the time window below, before that IP is temporarily blocked. Applies to public forms, login, "forgot password," newsletter unsubscribe, and the REST API. Lower this during a spam or brute-force wave (e.g. 2-3); the default of 10 is loose enough for normal use.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'security.rateLimit.ipWindowMinutes'}">
            <p class="help-text" id="securityRateLimitIpWindowMinutesHelpText">The rolling time window, in minutes, the per-IP attempt count above is measured over. Default is 30 minutes.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'security.rateLimit.usernameMaxAttempts'}">
            <p class="help-text" id="securityRateLimitUsernameMaxAttemptsHelpText">The maximum number of login or "forgot password" attempts allowed for a single username within the time window below, regardless of which IP they come from -- protects a specific account from a distributed brute-force attempt. Default is 5.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'security.rateLimit.usernameWindowMinutes'}">
            <p class="help-text" id="securityRateLimitUsernameWindowMinutesHelpText">The rolling time window, in minutes, the per-username attempt count above is measured over. Default is 30 minutes.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'security.ipRequestRateAlertThreshold'}">
            <p class="help-text" id="securityIpRequestRateAlertThresholdHelpText">When the busiest single non-bot IP address exceeds this many page requests in an hour, the "Request Rate Spike" tile on the Site Analytics dashboard turns red. This is a passive dashboard indicator only -- nothing emails, texts, or otherwise pages anyone, so someone has to actually look at the dashboard to notice. Default is 300.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'security.geoAnomalyBaselineDays'}">
            <p class="help-text" id="securityGeoAnomalyBaselineDaysHelpText">How many days of "normal" traffic, immediately before the Recent Window below, the Geo Anomaly tile compares against to decide whether a country showing up now is new. Same passive-dashboard-only caveat as the alert threshold above -- see that note. Default is 30 days.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'security.geoAnomalyRecentHours'}">
            <p class="help-text" id="securityGeoAnomalyRecentHoursHelpText">How many hours of the most recent traffic the Geo Anomaly tile checks for a country that wasn't among the top 5 during the Baseline Window above. A shorter window reacts faster to a new source of traffic but is noisier with normal day-to-day variation. Default is 24 hours.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'elearning.lrs.url'}">
            <p class="help-text" id="elearningLrsUrlHelpText">This site's LRS xAPI integration doesn't currently forward anything to an external Learning Record Store -- see the toggle above. This field, together with LRS key and LRS secret below, is unused by any code path today. xAPI is a learning-data standard created by the DoD's Advanced Distributed Learning (ADL) Initiative and encouraged for DoD systems under DoD Instruction 1322.26. ADL's own reference LRS (<a href="https://github.com/adlnet/ADL_LRS" target="_blank" rel="noreferrer">adlnet/ADL_LRS</a>) is now archived following the Initiative's 2025 shutdown. <a href="https://github.com/yetanalytics/lrsql" target="_blank" rel="noreferrer">Yet Analytics' SQL LRS</a> -- built by the first vendor to pass the DoD's full ADL LRS Test Suite -- is an actively maintained open-source alternative, for whenever this integration is built out.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'elearning.lrs.key'}">
            <p class="help-text" id="elearningLrsKeyHelpText">Not currently used by any code path -- see the LRS URL field's help text above.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'elearning.lrs.secret'}">
            <p class="help-text" id="elearningLrsSecretHelpText">Not currently used by any code path -- see the LRS URL field's help text above. This value is stored encrypted and always appears blank here after saving.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'elearning.moodle.url'}">
            <p class="help-text" id="elearningMoodleUrlHelpText">Moodle is the world's most widely used open-source learning management system, created in 1999 by Martin Dougiamas and first released in 2002 -- now with an estimated 200+ million users and still under active development (<a href="https://github.com/moodle/moodle" target="_blank" rel="noreferrer">moodle/moodle</a>). Other actively maintained open-source LMS options include Open edX, Canvas LMS, Sakai, and Chamilo, though Moodle remains the largest by installed base.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'elearning.perls.enabled'}">
            <p class="help-text" id="elearningPerlsEnabledHelpText">This connects using real, working API/OAuth code (unlike LRS xAPI above), but the upstream PERLS service it targets was discontinued along with the rest of the ADL Initiative in 2025 -- see the URL field's help text. There's currently no known live PERLS server to point this at.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'elearning.perls.url'}">
            <p class="help-text" id="elearningPerlsUrlHelpText">PERLS (PERvasive Learning System) is a mobile, personalized microlearning app for informal and on-the-job training, developed and funded by the DoD's Advanced Distributed Learning (ADL) Initiative. It's now archived following the Initiative's 2025 shutdown (<a href="https://github.com/adlnet/perls" target="_blank" rel="noreferrer">adlnet/perls</a>) -- unlike LRS, no actively maintained open-source equivalent was found, so this integration has no known live server to connect to today even though the client code itself works. The closest comparisons are commercial microlearning platforms (e.g. Axonify, TalentCards), not open-source projects.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'elearning.perls.clientId'}">
            <p class="help-text" id="elearningPerlsClientIdHelpText">The OAuth client ID for a PERLS API application. See the toggle above for why this integration has no known live server to use it against today.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'elearning.perls.secret'}">
            <p class="help-text" id="elearningPerlsSecretHelpText">The OAuth client secret paired with the Client Id above. This value is stored encrypted and always appears blank here after saving. See the toggle above for why this integration has no known live server to use it against today.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'bi.enabled'}">
            <p class="help-text" id="biEnabledHelpText">Despite the generic name, this only turns on embedding from a separately hosted Apache Superset instance (this does not install or host Superset itself) -- it has no effect on Metabase (its own "Enable Metabase?" toggle below controls that) or on Power BI (which needs no toggle at all; see the Power BI note on this page). There is currently no admin screen for placing a dashboard on a page -- a developer adds one by hand-editing that page's XML template with a <code>dashboardValue</code> (the Superset dashboard ID) and <code>dashboardEmbeddedId</code> (the embed ID Superset generates when embedding is enabled for that dashboard).</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'bi.superset.url'}">
            <p class="help-text" id="biSupersetUrlHelpText">The base URL of your organization's Superset instance, for example <code>https://superset.example.com</code>. That instance must have the <code>EMBEDDED_SUPERSET</code> feature flag enabled and CORS configured to allow this site's domain before embedding will work.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'bi.superset.id'}">
            <p class="help-text" id="biSupersetIdHelpText">Despite the label, this is not an API client ID -- it's the <strong>username</strong> of a Superset user account. Sent together with the Superset secret below to log in to your Superset instance's API. Use a dedicated service account (with permission to read the dashboards you plan to embed and to request guest tokens) rather than a personal login.</p>
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
          <c:if test="${siteProperty.name eq 'site.name'}">
            <p class="help-text" id="siteNameHelpText">The site's name -- used as the email sender's display-name fallback, the authenticator app issuer name shown when a user sets up multi-factor login, and in outgoing order and workflow notification emails.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.url'}">
            <p class="help-text" id="siteUrlHelpText">The site's public base address (for example https://example.com, no trailing slash). Used to build absolute links in emails and to complete the Open Graph/Twitter image address below, since that field holds a site-relative path rather than a full URL.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.name.keyword'}">
            <p class="help-text" id="siteNameKeywordHelpText">An optional second identifier sent alongside the site name in outgoing emails and workflow notifications -- for example a product or division name distinct from the overall site name. Leave blank if the site name alone is enough.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.description'}">
            <p class="help-text" id="siteDescriptionHelpText">The default page-description meta tag search engines show under this site's listing, used on any page that doesn't set its own.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.keywords'}">
            <p class="help-text" id="siteKeywordsHelpText">The default keywords meta tag, used on any page that doesn't set its own. Most modern search engines no longer use this tag for ranking, but it's still included on the page.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.image'}">
            <p class="help-text" id="siteImageHelpText">The default image shown when a page is shared on social media (Open Graph and Twitter Card), used on any page without its own. Enter a site-relative path (for example /images/share.png) rather than a full URL -- it's combined with the Site URL above to form the complete address.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.online'}">
            <p class="help-text" id="siteOnlineHelpText">When off, anonymous visitors are blocked from viewing the site -- logged-in users, including admins, can still get in, so this is safe to use for maintenance without locking yourself out. The XML sitemap also stops generating while offline, independent of the Sitemap toggle below (both must be on for the sitemap to work).</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.api'}">
            <p class="help-text" id="siteApiHelpText">Turns the REST API (/api/*) on or off site-wide. When off, all API requests are rejected regardless of authentication -- this also blocks OAuth2 app integrations, since they authenticate through the same API.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.sitemap.xml'}">
            <p class="help-text" id="siteSitemapXmlHelpText">Turns /sitemap.xml on or off. Also requires "Is online?" above to be on -- both toggles are checked, and either one being off stops the sitemap from generating.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'llms.enabled'}">
            <p class="help-text" id="llmsEnabledHelpText">Turns /llms.txt on or off -- a curated, markdown-formatted summary of this site for LLM and agentic-browsing tools (a different audience from the search-engine crawlers robots.txt and sitemap.xml address). When off, /llms.txt returns a 404. Like the sitemap toggle, this also requires "Is online?" (<code>site.online</code>, set on the <a href="${ctx}/admin/site-properties">Site Settings</a> page, not here) to be on -- either one being off stops /llms.txt from generating. A static config/cms/llms.txt file on the server, if present, is always served instead of the generated version, regardless of either setting.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'features.layout-editor'}">
            <p class="help-text" id="featuresLayoutEditorHelpText">Turns the visual page-layout editing tools on the web page designer on or off. Ships on (the existing, already-shipped behavior) -- this exists as an off-switch, not an opt-in.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'features.item-tags-facet-search'}">
            <p class="help-text" id="featuresItemTagsFacetSearchHelpText">Turns on the item-tag filter in collection search results. Ships off -- a dark-launched, opt-in feature you turn on when you're ready to use it.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'security.password.requireComplexity'}">
            <p class="help-text" id="securityPasswordRequireComplexityHelpText">When on, a new password must include at least one uppercase letter, one lowercase letter, one number, and one special character, in addition to meeting the minimum length above. When off, only the length rule applies.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'llms.description'}">
            <p class="help-text" id="llmsDescriptionHelpText">Optional additional context appended to /llms.txt after the site's name and Search engine description (set on the <a href="${ctx}/admin/site-properties">Site Settings</a> page, not here) -- for example, which sections of the site an LLM should treat as authoritative, or usage terms specific to automated/agentic consumers. Leave blank to generate /llms.txt from the site's name, description, navigation, and content alone.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'maps.service.tiles'}">
            <p class="help-text" id="mapsServiceTilesHelpText">Chooses where map background tiles load from. Must be exactly <code>openstreetmap</code> (the default; no account or key needed) or <code>custom</code> (a self-hosted tile server, using the URL below). Any other value -- including "google" or "apple" -- silently falls back to openstreetmap; there's no Google Maps or Apple Maps tile integration in this app today.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'maps.custom.tileserver.url'}">
            <p class="help-text" id="mapsCustomTileserverUrlHelpText">Only used when Map tiles service above is exactly "custom". Must be a tile URL template containing the literal <code>{z}</code>, <code>{x}</code>, and <code>{y}</code> placeholders, for example <code>https://tiles.example.com/{z}/{x}/{y}.png</code>. An invalid or missing value here falls back to openstreetmap even with "custom" selected above.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'maps.service.geocoder'}">
            <p class="help-text" id="mapsServiceGeocoderHelpText">Chooses the service used to turn an item's street address into map coordinates automatically. The only supported value is <code>nominatim</code> (OpenStreetMap's free geocoder); any other value, including blank, turns this off -- items keep whatever coordinates were entered by hand. Nominatim's own usage policy caps this at 1 request per second, which the app enforces itself; if deployed across multiple instances, each instance enforces that limit independently, so the effective rate can multiply with instance count.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'security.password.minLength'}">
            <p class="help-text" id="securityPasswordMinLengthHelpText">The fewest characters a new password can have. Applies whenever a password is set or changed -- self-registration, a self-service or admin-forced reset, and guest checkout account creation -- never retroactively to a password someone already has. Never enforced below 8 characters even if set lower here.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.cart'}">
            <p class="help-text" id="siteCartHelpText">Shows or hides the shopping cart across the site -- the cart link in the menu, add-to-cart buttons, and the cart page itself all check this independently, so it's enforced everywhere it appears, not just in navigation.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.registrations'}">
            <p class="help-text" id="siteRegistrationsHelpText">Turns the public account-registration form on or off. When off, new users cannot self-register; existing accounts are unaffected.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.login'}">
            <p class="help-text" id="siteLoginHelpText">Shows or hides the Login link in the site header. This only hides the link -- it doesn't disable the /login page itself, so a direct link still works for anyone who has it.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.confirmation'}">
            <p class="help-text" id="siteConfirmationHelpText">Shows an age/content confirmation dialog to visitors, with Yes/No buttons and the message lines below. Turning this on without also filling in Confirmation Line 1 below can show a mostly-blank dialog.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.confirmation.line1'}">
            <p class="help-text" id="siteConfirmationLine1HelpText">The first line of text shown inside the confirmation dialog (see "Show site confirmation?" above). Has no effect while that toggle is off.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.confirmation.line2'}">
            <p class="help-text" id="siteConfirmationLine2HelpText">An optional second line shown below Confirmation Line 1 inside the confirmation dialog. Has no effect while "Show site confirmation?" above is off.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.confirmation.declined.text'}">
            <p class="help-text" id="siteConfirmationDeclinedTextHelpText">The message shown when a visitor clicks "No" on the confirmation dialog. Has no effect while "Show site confirmation?" above is off.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.newsletter.overlay'}">
            <p class="help-text" id="siteNewsletterOverlayHelpText">Shows a dismissible newsletter sign-up popup to visitors, using the headline, message, and colors below.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.newsletter.headline'}">
            <p class="help-text" id="siteNewsletterHeadlineHelpText">The headline shown in the newsletter sign-up popup. Has no effect while "Show subscribe to newsletter overlay?" above is off.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.newsletter.message'}">
            <p class="help-text" id="siteNewsletterMessageHelpText">The body text shown in the newsletter sign-up popup, below the headline. Has no effect while "Show subscribe to newsletter overlay?" above is off.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.newsletter.color'}">
            <p class="help-text" id="siteNewsletterColorHelpText">The text color used in the newsletter sign-up popup. Has no effect while "Show subscribe to newsletter overlay?" above is off.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.newsletter.backgroundColor'}">
            <p class="help-text" id="siteNewsletterBackgroundColorHelpText">The background color of the newsletter sign-up popup. Has no effect while "Show subscribe to newsletter overlay?" above is off.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.logo'}">
            <p class="help-text" id="siteLogoHelpText">The site's primary, full-color logo -- used as the logo image in outgoing emails, and shown in the header and/or footer if their color settings on the <a href="${ctx}/admin/theme-properties">Theme Settings</a> page are set to "Full color". Header and footer each have their own, independent logo-color setting.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.logo.white'}">
            <p class="help-text" id="siteLogoWhiteHelpText">An all-white version of the logo, for use against dark backgrounds. Shown in the header and/or footer depending on their independent Logo color / Footer logo color settings on the <a href="${ctx}/admin/theme-properties">Theme Settings</a> page.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.logo.mixed'}">
            <p class="help-text" id="siteLogoMixedHelpText">A mixed-color logo variant. Shown in the header and/or footer depending on their independent Logo color / Footer logo color settings on the <a href="${ctx}/admin/theme-properties">Theme Settings</a> page.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.timezone'}">
            <p class="help-text" id="siteTimezoneHelpText">The site's default timezone, used wherever the platform displays or schedules something by time without a more specific timezone already available.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.online'}">
            <p class="help-text" id="siteOnlineHelpText">Turning this off swaps the homepage to a "coming soon" splash, hides the main nav menu, and blocks guest (keyless) API access and /sitemap.xml. It does not take other pages offline -- a web page, blog post, wiki page, or item reached by direct URL still renders normally for anonymous visitors while this is off.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.login'}">
            <p class="help-text" id="siteLoginHelpText">Hides the Login link and blocks sign-in for everyone except existing admins, who can always still sign in even while this is off. Unlike "Allow registrations?", this only affects the password sign-in form -- an OAuth/SSO login (if configured) is not gated by this setting.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'site.header.page'}">
            <p class="help-text" id="siteHeaderPageHelpText">A page path (e.g. <code>/about-us</code>), not a full URL -- and this same field is also editable from the <a href="${ctx}/admin/site-header-properties">Utility Bar Settings</a> page.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'social.email'}">
            <p class="help-text" id="socialEmailHelpText">A contact email address shown in the site footer, next to the Telephone number below if both are set. Leave blank to omit the whole contact line from the footer.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'social.phone'}">
            <p class="help-text" id="socialPhoneHelpText">A contact phone number shown in the site footer, next to the Email address above if both are set. Leave blank to omit the whole contact line from the footer.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'social.instagram.accessToken'}">
            <p class="help-text" id="socialInstagramAccessTokenHelpText">A long-lived Instagram Graph API access token, used only by the Instagram feed-embed integration -- unrelated to the Social Profile Links above, which just link out to the platform. Generate one from a Facebook Developer app with the Instagram Graph API product added. This value is stored encrypted and always appears blank here after saving; leave it blank to keep the current token.</p>
          </c:if>
          <c:if test="${siteProperty.name eq 'social.instagram.facebookPageValue'}">
            <p class="help-text" id="socialInstagramFacebookPageValueHelpText">The Facebook Page ID connected to the Instagram Business account being embedded -- required alongside the Access Token above for the Instagram feed-embed integration to authenticate.</p>
          </c:if>
        </td>
      </tr>
    </c:forEach>
    </tbody>
  </table>
  <c:if test="${prefix eq 'analytics'}">
    <p class="help-text">The four privacy toggles above (Cookieless, Anonymize IP, Honor Do-Not-Track, Require consent) each control a different, independent slice of tracking -- turning one on doesn't turn on the others. Analytics service and the keys below it are unaffected by any of them and load whenever they're set.</p>
  </c:if>
  <c:if test="${prefix eq 'social'}">
    <p class="help-text">The Social Profile Links list above (Facebook, Instagram, etc.) controls the footer icon row. Everything on this page below is unrelated contact info and the separate Instagram feed-embed integration, not more platform links.</p>
  </c:if>
  <c:if test="${prefix eq 'captcha'}">
    <p class="help-text">Google reCAPTCHA v2 or Cloudflare Turnstile -- whichever is chosen above as the Captcha service -- protects public forms across the site (for example, the contact form, account registration, newsletter signup, and job/business listings) wherever that form has captcha enabled. Changes take effect immediately on next page load.</p>
    <p><a href="${ctx}/contact-us" target="_blank" class="button radius secondary">Test CAPTCHA</a></p>
  </c:if>
  <c:if test="${prefix eq 'robots'}">
    <div class="callout primary radius">
      <h6>What this page shows</h6>
      <p>Controls what <a href="${ctx}/robots.txt" target="_blank" rel="noreferrer">/robots.txt</a> tells web crawlers. Admin pages are always excluded regardless of these settings. Each toggle below opts a specific AI crawler out of reading this site -- on by default, matching how the site behaved before these controls existed. A crawler being "off" here is a request, not an enforcement mechanism: well-behaved crawlers honor robots.txt, but nothing stops a crawler from ignoring it.</p>
      <p><strong>A static <code>config/cms/robots.txt</code> file on the server, if present, is always served verbatim instead of the generated output -- every toggle below is ignored while that file exists.</strong> If changing a toggle here has no visible effect on the live <code>/robots.txt</code>, that file is almost always why; check with whoever manages the deployment.</p>
      <p>Unlike <a href="${ctx}/admin/llms-properties">llms.txt</a>, robots.txt has no "Is online?" gate -- it's served the same whether the site is online or not, since it carries no content of its own to protect.</p>
    </div>

    <h5>When to worry</h5>
    <div class="callout warning radius">
      <p><strong>A crawler you disallowed is still showing up in traffic.</strong> robots.txt is an honor-system request, not a block -- a non-compliant crawler (or one ignoring robots.txt entirely, as several vendors' own documentation admits for their on-demand fetchers) will visit regardless. For actual enforcement, that traffic needs to be blocked at the <a href="${ctx}/admin/blocked-ip-list">Blocked IP list</a> or a layer in front of the application, not here.</p>
      <p><strong>The sitemap line is missing from /robots.txt.</strong> It's only included when both <code>site.url</code> is configured and the <a href="${ctx}/admin/seo-sitemap">sitemap</a> itself is enabled -- a disabled sitemap correctly omits the line rather than advertising a URL that would 404.</p>
    </div>
  </c:if>
  <c:if test="${prefix eq 'llms'}">
    <div class="callout primary radius">
      <h6>What this page shows</h6>
      <p>Controls <a href="${ctx}/llms.txt" target="_blank" rel="noopener">/llms.txt <i class="fa fa-external-link"></i></a>, a curated, markdown-formatted summary of this site for LLM and agentic-browsing tools -- a different audience and format from robots.txt (crawler permissions) and sitemap.xml (page inventory). When enabled, it's generated automatically from the site's name, description, navigation, and content, optionally supplemented by the description field below.</p>
      <p>A static <code>config/cms/llms.txt</code> file on the server, if present, is always served instead of the generated version, regardless of either toggle below -- if a change here doesn't show up on the live page, check for that file first.</p>
    </div>

    <h5>When to worry</h5>
    <div class="callout warning radius">
      <p><strong>/llms.txt returns a 404 even though it's enabled here.</strong> It also requires the site to be online (<code>site.online</code>, on the <a href="${ctx}/admin/site-properties">Site Settings</a> page) -- either gate being off stops generation.</p>
      <p><strong>A change to the description or content doesn't appear.</strong> Saving invalidates the cache immediately, so this isn't a staleness issue in normal operation -- check for the static override file above before assuming something's stuck.</p>
    </div>
  </c:if>
  <c:if test="${prefix eq 'security'}">
    <p class="help-text">This page has two unrelated groups of settings: the four rate-limit fields above throttle repeated automated attempts (spam form submissions, login brute-forcing); the three alert-threshold fields below tune two passive indicator tiles ("Request Rate Spike" and "Geo Anomaly") on the Site Analytics dashboard. Neither group sends an email, text, or any other push notification -- someone has to open the dashboard to see them. For a hard block on a specific address, use the IP Allow/Block List page instead of tightening these numbers.</p>
    <p class="help-text">A rate-limit change applies to new attempts right away, but an IP address or username that's already being watched keeps its old limit until it stops making attempts for 30 minutes straight -- which won't happen while an attack is still in progress. If you're mid-incident and need the new, stricter limit to apply immediately, restarting the app is the reliable way to do that (it clears the in-memory tracking for everyone, not just the attacker).</p>
    <p class="help-text">Running on Azure App Service: rate-limit tracking lives in each instance's own memory, not a shared store, so scaling the App Service Plan out to N instances effectively multiplies these limits by N (a request round-robins to whichever instance is free, and each one counts independently). If you scale out and need a hard cap regardless of instance count, put a rate-limiting rule in front of the app (e.g. Azure Front Door or Application Gateway/WAF) rather than relying on these settings alone.</p>
  </c:if>
  <c:if test="${prefix eq 'features'}">
    <p class="help-text">Feature flags are on/off switches for specific pieces of functionality, stored here as plain settings so a feature can be turned on or off without a code deployment -- useful for a staged rollout, or for turning something off quickly if it misbehaves. Each toggle below has its own description of exactly what it controls, since "feature flag" alone doesn't say what a given one does. Running on Azure App Service: a toggle takes effect immediately on the instance you saved it from, and within about a minute on any other instance if the App Service Plan is scaled out.</p>
  </c:if>
  <c:if test="${prefix eq 'bi'}">
    <p class="help-text">This page configures embedded BI dashboards from Superset and Metabase -- separately hosted analytics tools this site links to, not something installed or run by this application. There's a third option, Power BI, that isn't configured here at all: a Power BI report published with "Publish to web" is embedded by placing its URL directly in a page's layout XML (see the <code>powerBi</code> widget), with no site property or admin form involved.</p>
    <p class="help-text">Best practice: use a dedicated service account for the Superset username/password above, not a personal login, and rotate the Metabase embedding secret if you ever suspect it's been exposed -- anyone holding it can view any dashboard published for embedding. Since Power BI's "Publish to web" reports are public to anyone with the link (no login, no row-level security), never publish anything confidential that way.</p>
  </c:if>
  <c:if test="${prefix eq 'mail'}">
    <p class="help-text">If emails aren't sending, these settings are usually the first place to check -- especially the host, port, username/password, and SSL toggle above. Form submissions, newsletters, and every other outgoing email all go through this same configuration, so a mistake here is site-wide. After making a change, use the Mail Test panel to send yourself a confirmation email before relying on it for real traffic.</p>
  </c:if>
  <c:if test="${prefix eq 'mailing-list'}">
    <p class="help-text">Save the API Key and Audience/List Id above first, then use Test Connection to confirm they're valid without leaving this page.</p>
    <c:if test="${!empty mailChimpTestResult}">
      <p class="callout radius ${mailChimpTestResult.success ? 'success' : 'alert'}" style="margin-top: -0.5rem;">
        <c:choose>
          <c:when test="${mailChimpTestResult.success}"><i class="fa fa-check-circle"></i> </c:when>
          <c:otherwise><i class="fa fa-exclamation-circle"></i> </c:otherwise>
        </c:choose>
        <c:out value="${mailChimpTestResult.message}" />
      </p>
    </c:if>
    <p><button type="submit" name="action" value="testMailChimpConnection" formnovalidate class="button radius secondary">Test Connection</button></p>
  </c:if>
  <c:if test="${prefix eq 'elearning'}">
    <p class="help-text">Connects this site to external learning platforms so course listings and calendar events can be pulled in automatically. Of the three integrations below, only Moodle has a real, working connection today -- LRS xAPI isn't wired to anything external yet, and PERLS has working client code but no live server left to connect to (each section's help text below explains why). The "Enable e-learning?" toggle above is a master switch: turning it off disables all three regardless of their own individual toggles.</p>
  </c:if>
  <c:if test="${prefix eq 'site'}">
    <p class="help-text">Header text and links have their own settings page (<a href="${ctx}/admin/site-header-properties">Utility Bar Settings</a>); logo colors, fonts, and site-wide colors have their own (<a href="${ctx}/admin/theme-properties">Theme Settings</a>). Some of these fields only take effect together with another one above or below them -- the description for each notes when that's the case.</p>
    <p class="help-text">This page also has no extra re-authentication step, unlike the MFA and Security pages -- "Is online?" and "Is API enabled?" below are the two most consequential toggles here, and any already-logged-in admin can flip them.</p>
  </c:if>
  <c:if test="${prefix eq 'theme'}">
    <p class="help-text">Changes here restyle the live site immediately for every visitor. "Custom XML" for Menu theme or Footer theme means the header/footer layout is built in the Website Designer (${ctx}/admin/web-container-designer), not on this page -- every other option here is a built-in template. "Match device, let visitor choose" for Color scheme only has a visible effect once a developer/admin places the color-scheme-toggle widget somewhere on a page; it isn't added automatically.</p>
    <p class="help-text">The three System Alert colors below are the same values shown on the <a href="${ctx}/admin/site-header-properties">Utility Bar Settings</a> page -- editing either page changes what the other shows.</p>
  </c:if>
  <c:if test="${widgetContext.sharedRequestValueMap['stepUpRequired'] eq 'true'}">
    <div class="callout radius warning">
      <p><strong>Re-authentication required</strong> — this page's settings are security-sensitive.
        Enter your password or 6-digit authenticator code, then click Save again.</p>
      <input type="password" name="stepUpCredential" maxlength="255"
             placeholder="Password or authenticator code"
             title="Enter your password or 6-digit authenticator code"/>
    </div>
  </c:if>
  <div class="button-container">
    <input type="submit" class="button radius success" value="Save" />
    <a href="${ctx}/admin" class="button radius secondary">Cancel</a>
  </div>
</form>
<div class="reveal large" id="imageBrowserReveal" data-reveal data-animation-in="slide-in-down fast" role="dialog" aria-modal="true" aria-label="Image Browser">
  <iframe id="imageBrowserFrame" title="Image Browser" style="width: 100%; height: 70vh; border: 0;"></iframe>
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
  <c:when test="${siteProperty.name eq 'theme.link.color'}">colorSelectorList.push('a');</c:when>
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
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.activeBackgroundColor'}">colorSelectorList.push('#platform-menu ul.menu .active > a');</c:when>
  <c:when test="${siteProperty.name eq 'theme.topbar.menu.activeTextColor'}">colorSelectorList.push('#platform-menu ul.menu .active > a');</c:when>
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
  // Load the image browser in an iframe so its own nonce-valid script runs and can populate
  // the parent field via top.document. Injecting the fragment's HTML with .html() instead
  // stripped the nonce (issue #1207) and reinterpreted the fetched markup as HTML
  // (CodeQL js/xss-through-dom). currentPhotoId selects which property field to target; the
  // fragment itself closes this modal via top.jQuery once an image is selected.
  $('#imageBrowserReveal').on('open.zf.reveal', function () {
    // currentPhotoId comes from a data-photo-id attribute; a site-property id is always
    // numeric, so restrict it to digits before composing the iframe src -- this keeps
    // DOM-derived text out of the URL (CodeQL js/xss-through-dom).
    var photoId = String(currentPhotoId).replace(/[^0-9]/g, '');
    document.getElementById('imageBrowserFrame').src = '${ctx}/image-browser?inputId=imageUrl' + photoId + '&view=reveal';
  });
  $('#imageBrowserReveal').on('closed.zf.reveal', function () {
    document.getElementById('imageBrowserFrame').removeAttribute('src');
  });
</script>
