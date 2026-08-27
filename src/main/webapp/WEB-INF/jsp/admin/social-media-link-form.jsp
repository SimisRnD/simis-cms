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
<jsp:useBean id="socialMediaLink" class="com.simisinc.platform.domain.model.SocialMediaLink" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Carries an edit forward to save() as an update instead of a new record; stays -1 (the
       SocialMediaLink default) for a fresh "Add a Platform" submission. --%>
  <input type="hidden" name="id" value="${socialMediaLink.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Platform Name <span class="required">*</span>
    <input type="text" placeholder="e.g. Instagram, Mastodon, Discord..." name="platformName" value="<c:out value="${socialMediaLink.platformName}"/>" required>
  </label>
  <label>URL <span class="required">*</span>
    <input type="text" placeholder="https://..." name="url" value="<c:out value="${socialMediaLink.url}"/>" required>
  </label>
  <label>Order
    <input type="number" name="linkOrder" value="<c:out value="${socialMediaLink.linkOrder}"/>">
  </label>
  <p class="help-text">Add any platform -- the name is used to look up an icon for known platforms (Facebook, Instagram, LinkedIn, X/Twitter, Flickr, YouTube, Mastodon, TikTok, Discord, GitHub); anything else shows a generic link icon. Order controls where the icon falls in the footer's row (lowest first); links that share a value fall back to alphabetical order.</p>
  <div class="button-container">
    <input type="submit" class="button radius success expanded" value="${socialMediaLink.id > -1 ? 'Save Changes' : 'Add Platform'}"/>
    <c:if test="${socialMediaLink.id > -1}">
      <a href="${ctx}/admin/social-media-settings" class="button radius secondary expanded">Cancel</a>
    </c:if>
  </div>
</form>
