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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="apiList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="callout primary radius">
  <h6>What this page shows</h6>
  <p>This is a read-only listing of every REST endpoint currently registered under <code>/api/</code> -- it's generated from the actual service registrations, so it always reflects what's really there, not something you edit here. Every call is IP-rate-limited and (outside local requests) requires an app key, managed on the <a href="${ctx}/admin/apps">Apps</a> page. The "medicine" endpoints are a real, working feature -- data for a companion mobile app's medication tracking -- not leftover or orphaned code; they simply have no admin UI of their own since nothing about them is meant to be managed from this console.</p>
  <p>Two endpoints exist but never appear as rows here because they're handled as hardcoded logic in <code>RestRequestFilter</code> rather than a declared service: <code>POST /api/session</code> (establishes a session, no key needed) and <code>POST /api/oauth2/authorize</code> (exchanges a username/password plus app key for a bearer token) -- see below.</p>
</div>

<h5>When to worry</h5>
<div class="callout warning radius">
  <p><strong>A caller gets 401 "Unauthorized, no key."</strong> The <code>X-API-Key</code> header (or a <code>key</code> query parameter) is missing entirely -- the app key wasn't sent at all, not just wrong.</p>
  <p><strong>A caller gets a plain 401 "Unauthorized" with a key present.</strong> This message doesn't distinguish between two different causes: the key doesn't match any App, or it matches an App that's currently <strong>Enabled: No</strong> on the <a href="${ctx}/admin/apps">Apps</a> page. Check that page first -- it's the faster of the two things to rule out, and a surprising number of "the API just stopped working" reports turn out to be someone having flipped Enabled off (or deliberately doing so to kill a leaked credential) rather than a code or network problem.</p>
  <p><strong>A caller gets 429 "Too many requests."</strong> There are several independent rate-limit buckets in play -- one for the overall per-IP request rate, a separate one specifically for repeated invalid-key attempts from an IP (so a client failing auth repeatedly can't also exhaust the budget for callers using a valid key), and per-app and per-app-per-user buckets once authenticated. A 429 on a request that should be well within normal traffic is worth checking against which bucket is actually being hit, since "the API is rate limited" isn't a single, uniform state.</p>
  <p><strong>A guest (unauthenticated) request unexpectedly gets rejected.</strong> An app-key-only caller with no bearer token is demoted to guest access, but guests may only <code>GET</code>/<code>HEAD</code> -- a guest <code>POST</code>/<code>PUT</code>/<code>DELETE</code> is rejected outright with 401, on the theory that this filter has no per-endpoint knowledge of which write needs which role. <code>POST /api/session</code> and <code>POST /api/oauth2/authorize</code> are the two exceptions, since they're how a client gets authenticated in the first place and are handled before this check applies.</p>
</div>

<h5>For Azure</h5>
<div class="callout radius">
  <p>Every rate-limit bucket referenced above lives in each instance's own in-memory cache (see <a href="${ctx}/admin/cache-management">Cache Management</a>), not anywhere shared. On more than one Azure App Service instance, a client hitting different instances effectively gets a separate allowance per instance rather than one enforced total -- the configured limit is per-instance, not site-wide. This doesn't make rate limiting ineffective, but it means the real ceiling on a multi-instance deployment is higher than the configured number alone would suggest.</p>
</div>

<table class="unstriped">
  <thead>
    <tr>
      <th width="10%" class="text-center">Method</th>
      <th width="45%" class="text-center">Endpoint</th>
      <th width="45%" class="text-center">Class</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${apiList}" var="api">
    <tr>
      <td align="center">
        <c:forEach var="method" items="${fn:split(api.method,',')}">
            <c:choose>
              <c:when test="${fn:trim(method) eq 'get'}">
                <span class="label radius primary">GET</span>
              </c:when>
              <c:when test="${fn:trim(method) eq 'post'}">
                <span class="label radius secondary">POST</span>
              </c:when>
              <c:when test="${fn:trim(method) eq 'put'}">
                <span class="label radius secondary">PUT</span>
              </c:when>
              <c:when test="${fn:trim(method) eq 'delete'}">
                <span class="label radius alert">DELETE</span>
              </c:when>
            </c:choose>
        </c:forEach>
      </td>
      <td>
        /api/<c:out value="${api.endpointValue}" />
      </td>
      <td>
        <c:out value="${api.serviceClass}" />
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty apiList}">
      <tr>
        <td colspan="3">No APIs were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
