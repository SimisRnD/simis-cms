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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="latestChecks" class="java.util.ArrayList" scope="request"/>
<meta http-equiv="refresh" content="30">
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="callout primary radius">
  <h6>What this page shows</h6>
  <p>Two dependencies are checked every minute by a background job, plus on demand with <strong>Run Check Now</strong>:</p>
  <ul>
    <li><strong>Database</strong> &mdash; can the app get a valid connection from its pool right now.</li>
    <li><strong>File Store</strong> &mdash; is the configured file-storage root present and writable (in Azure, this is the mounted Azure Files share).</li>
  </ul>
  <p><strong>Uptime</strong> is the percentage of checks in the trailing window that came back UP. History is kept for 30 days.</p>
</div>
<p class="text-right">
  <small class="subheader">This page refreshes automatically every 30 seconds.</small>
  <a href="#" onclick="return confirmPostAction('Run all health checks now?', '${widgetContext.uri}?command=runCheckNow&widget=${widgetContext.uniqueId}&token=${userSession.formToken}');" class="button tiny">
    <i class="fa fa-rotate"></i> Run Check Now
  </a>
</p>
<c:choose>
  <c:when test="${empty latestChecks}">
    <p>No health checks have run yet. The scheduled check runs every minute -- use "Run Check Now" above for an immediate reading.</p>
  </c:when>
  <c:otherwise>
    <table class="unstriped">
      <thead>
        <tr>
          <th>Service</th>
          <th>Status</th>
          <th>Response Time</th>
          <th>Last Checked</th>
          <th>Uptime (last <c:out value="${uptimeWindowHours}"/>h)</th>
          <th>Error</th>
        </tr>
      </thead>
      <tbody>
        <c:forEach items="${latestChecks}" var="check">
          <tr>
            <td><c:out value="${check.serviceLabel}"/></td>
            <td>
              <c:choose>
                <c:when test="${check.up}"><span class="label success radius">UP</span></c:when>
                <c:otherwise><span class="label alert radius">DOWN</span></c:otherwise>
              </c:choose>
            </td>
            <td><c:out value="${check.responseTimeMs}"/> ms</td>
            <td><span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${check.checkedAt}' />"><c:out value="${date:relative(check.checkedAt)}" /></span></td>
            <td>
              <c:set var="uptime" value="${uptimeByService[check.serviceName]}"/>
              <c:choose>
                <c:when test="${empty uptime}">&#8212;</c:when>
                <c:otherwise><fmt:formatNumber value="${uptime}" maxFractionDigits="1"/>%</c:otherwise>
              </c:choose>
            </td>
            <td><c:out value="${check.errorMessage}"/></td>
          </tr>
        </c:forEach>
      </tbody>
    </table>
  </c:otherwise>
</c:choose>
<h5>When a check goes DOWN</h5>
<div class="callout warning radius">
  <h6>Database</h6>
  <p><strong>"Connection could not be validated"</strong> &mdash; the pool couldn't get back a usable connection. Common causes on Azure:</p>
  <ul>
    <li>The Azure Database for PostgreSQL server is stopped, restarting, or has hit its connection limit.</li>
    <li>A firewall rule, VNet integration, or private endpoint change blocked the App Service from reaching the database.</li>
    <li><code>DB_SERVER_NAME</code> / <code>DB_USER</code> / <code>DB_PASSWORD</code> app settings are missing, wrong, or a rotated credential wasn't updated here.</li>
  </ul>
  <p>Check the database server's status and firewall/networking rules in the Azure portal first, then confirm the app settings above still match. A single slow/timed-out check is often transient (a brief connection-pool blip); repeated DOWN readings across several minutes point to a real outage.</p>
</div>
<div class="callout warning radius">
  <h6>File Store</h6>
  <p><strong>"File store root path is not configured"</strong> &mdash; the file-storage root setting (<code>CMS_PATH</code>) is empty. Set it to the mount path used in this environment.</p>
  <p><strong>"File store root is missing or not writable: &lt;path&gt;"</strong> &mdash; the path is set but isn't there or can't be written to. On Azure this is almost always:</p>
  <ul>
    <li>The Azure Files share isn't mounted (or was detached during a restart/scale event) at that path.</li>
    <li>The mount path configured in the App Service doesn't match <code>CMS_PATH</code>.</li>
    <li>The storage account key or SAS token used for the mount expired or was rotated.</li>
  </ul>
  <p>The application container otherwise runs with a read-only filesystem by design (see the app's runtime hardening) &mdash; this mounted path is the one place it's expected to write, so a permissions issue here is almost always the mount itself, not the app.</p>
</div>

<h5>The <code>/healthz</code> endpoint</h5>
<div class="callout radius">
  <p><code>GET /healthz</code> is a separate, unauthenticated endpoint (not this page) meant for load balancers and container platforms &mdash; it answers before hostname, blocked-IP, or SSL checks run, so it can't be locked out by those. It returns <code>200 {"status":"UP"}</code> when the app finished startup, the database is reachable, and the file store is writable, or <code>503 {"status":"DOWN"}</code> otherwise &mdash; no version or topology detail, by design.</p>
  <p class="callout alert radius" style="font-size:0.875rem"><strong>Readiness, not liveness.</strong> Wire this to Azure App Service's <em>Health check</em> path (Monitoring blade) so an unhealthy instance is taken out of load-balancer rotation. Do <strong>not</strong> use it to trigger a restart/recycle policy: the database is a dependency shared by every instance, so a single DB blip would fail <code>/healthz</code> on all of them at once &mdash; a restart-on-failure policy would crash-loop the entire fleet simultaneously instead of just routing traffic around it while it recovers. The same applies if this app is deployed on Azure Container Apps or AKS instead: map <code>/healthz</code> to a <em>readiness</em> probe, never a <em>liveness</em> probe.</p>
</div>
