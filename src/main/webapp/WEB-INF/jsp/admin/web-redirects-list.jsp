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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webRedirectList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="help-text">Redirects send an incoming request path to another URL with a 301 (permanent) or 302 (temporary) status. They are checked before the legacy CMS_PATH/config/cms/redirects.csv file, so a redirect added here can shadow a not-yet-migrated CSV entry for the same path. They're also checked before anything else on the site, including an actual live page -- there's nothing stopping a From Path here from matching a page that already works, and if it does, this redirect wins: every visitor hits the redirect instead, silently, with no warning at save time.</p>
<a class="button small radius primary float-left" href="${ctx}/admin/web-redirect">Add a Redirect <i class="fa fa-arrow-circle-right"></i></a>
<table class="unstriped">
  <thead>
    <tr>
      <th>From Path</th>
      <th>To URL</th>
      <th>Status</th>
      <th>State</th>
      <th width="220">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${webRedirectList}" var="webRedirect">
      <tr>
        <td>
          <a href="${ctx}/admin/web-redirect?webRedirectId=${webRedirect.id}"><c:out value="${webRedirect.fromPath}" /></a>
        </td>
        <td>
          <c:out value="${webRedirect.toUrl}" />
        </td>
        <td>
          <c:out value="${webRedirect.statusCode}" />
        </td>
        <td>
          <c:choose>
            <c:when test="${webRedirect.enabled}">
              <span class="label success">Enabled</span>
            </c:when>
            <c:otherwise>
              <span class="label warning">Disabled</span>
            </c:otherwise>
          </c:choose>
        </td>
        <td>
          <a href="${ctx}/admin/web-redirect?webRedirectId=${webRedirect.id}" title="Edit"><i class="fa fa-edit"></i></a>
          <c:choose>
            <c:when test="${webRedirect.enabled}">
              <a href="#" title="Disable" data-confirm-post="Disable this redirect?" data-post-url="${widgetContext.uri}?action=toggleEnabled&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webRedirectId=${webRedirect.id}"><i class="fa fa-toggle-on"></i></a>
            </c:when>
            <c:otherwise>
              <a href="#" title="Enable" data-confirm-post="Enable this redirect?" data-post-url="${widgetContext.uri}?action=toggleEnabled&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webRedirectId=${webRedirect.id}"><i class="fa fa-toggle-off"></i></a>
            </c:otherwise>
          </c:choose>
          <a href="#" title="Delete" data-confirm-post="Are you sure you want to delete the redirect for <c:out value="${webRedirect.fromPath}" />? This cannot be undone." data-post-url="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webRedirectId=${webRedirect.id}"><i class="fa fa-remove"></i></a>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty webRedirectList}">
      <tr>
        <td colspan="5">No web redirects were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<h5>Common problems and how to fix them</h5>
<ul>
  <li><strong>"The from path conflicts with a reserved system path."</strong> A short list of prefixes --
    <code>/admin</code>, <code>/login</code>, <code>/logout</code>, <code>/api</code>, and the site's static
    asset paths, among a few others -- can never be used as a From Path, for any role, so a redirect can't take
    down the admin console or turn the login page into a phishing link.</li>
  <li><strong>"This redirect would create a redirect loop."</strong> Saving is blocked if the chain starting
    from To URL eventually leads back to From Path, whether that's a direct A&harr;B pair or a longer chain that
    only closes the loop once this redirect is added.</li>
  <li><strong>"A redirect for that from path already exists."</strong> From Path must be unique -- including
    against a <em>disabled</em> redirect for the same path, not just enabled ones. Edit or delete the existing
    row instead of trying to add a second one.</li>
  <li><strong>Disabling a redirect doesn't bring back a legacy CSV entry for the same path.</strong> Once a From
    Path exists as a row here at all, it's fully governed by that row for the life of this server -- a disabled
    row falls through to normal page handling, never to the legacy redirects.csv fallback, even if the CSV still
    has an entry for that same path.</li>
</ul>
